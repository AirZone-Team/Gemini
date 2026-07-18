package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.OptionalInt;
import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * Post-processing pipeline for the Hypernova Kill Effect.
 *
 * <h3>Render pass chain</h3>
 * <pre>
 * [Scene FBO]
 *     │
 *     ├── Bright Pass (Edge) ──→ Ping A   (extract HDR + depth-edge highlights)
 *     ├── Blur H            ──→ Ping B   (separable horizontal gaussian)
 *     ├── Blur V            ──→ Ping A   (separable vertical gaussian)
 *     │                                    ↓
 *     ├── Composite         ──→ Ping B   (scene + bloom)
 *     ├── Screen Lighting   ──→ Ping A   (dynamic N·L lighting, depth-aware)  [NEW]
 *     ├── Distortion        ──→ Ping B   (screen-space heat haze)
 *     ├── GodRay            ──→ Ping A   (screen-space radial blur)
 *     ├── Volumetric GodRay ──→ Ping B   (ray-marched shafts, depth-aware)  [NEW]
 *     ├── SSRT              ──→ Ping A   (screen-space reflections, depth)   [NEW]
 *     ├── Chromatic         ──→ Ping B   (RGB channel separation)
 *     ├── BH Center         ──→ Ping A   (gravitational lensing + 3-layer)
 *     ├── Glow Flash        ──→ Ping B   (pre-supernova pulse)
 *     ├── Flash Screen      ──→ Ping A   (full-screen white flash)
 *     ├── Shockwave         ──→ Ping B   (expanding shock rings)
 *     ├── Afterimage        ──→ Ping A   (temporal motion trails)
 *     └── ACES              ──→ Main FB  (HDR→LDR tone mapping)
 * </pre>
 *
 * <h3>Uniform layout (std140, 160 bytes = 10 × vec4)</h3>
 * <pre>
 * vec4 Params:       fbWidth, fbHeight, bloomStrength, threshold
 * vec4 TimePack:     timeSec, frameIndex, 0, 0
 * vec4 Center1:      ndcX, ndcY, worldDist, 0
 * vec4 Center2:      ndcX, ndcY, 0, 0
 * vec4 PassParams:   distortionStr, godRayStr, chromaticStr, bloomRadius
 * vec4 BHParams:     bhRadiusUV, bhStage, bhProgress, bhIntensity
 * vec4 CameraParams: fovRad, aspect, near, far
 * vec4 LightViewPos: viewX, viewY, viewZ, radius
 * vec4 LightColor:   r, g, b, intensity
 * vec4 MiscParams:   ssrIntensity, volumetricSteps, chainFade, 0
 * </pre>
 *
 * <p><b>chainFade</b> (MiscParams.z): 0→1 smoothstep ramp driven by effect
 * age. The ACES pass crossfades between the raw scene and the tone-mapped
 * output by this value, so the global tone/vignette shift eases in rather
 * than popping the moment the chain activates (flash fix).</p>
 */
public final class KillEffectPostProcessor {

    private KillEffectPostProcessor() {}

    // ── Shader path ────────────────────────────────────────────────

    private static final String POST_VSH = "core/kill_effect_post";
    private static final String POST_FSH = "core/kill_effect_post";

    // ── Uniform layout (10 × vec4 = 160 bytes) ──────────────────────

    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4()  // Params:       fbWidth, fbHeight, bloomStrength, threshold
            .putVec4()  // TimePack:     time, frameIndex, 0, 0
            .putVec4()  // Center1:      ndcX, ndcY, worldDist, 0
            .putVec4()  // Center2:      ndcX, ndcY, 0, 0
            .putVec4()  // PassParams:   distortion, godRay, chromatic, bloomRadius
            .putVec4()  // BHParams:     bhRadius, stage, progress, intensity
            .putVec4()  // CameraParams: fovRad, aspect, near, far
            .putVec4()  // LightViewPos: viewX, viewY, viewZ, radius
            .putVec4()  // LightColor:   r, g, b, intensity
            .putVec4()  // MiscParams:   ssrIntensity, volumetricSteps, 0, 0
            .get();

    // ── Ping-pong texture targets ──────────────────────────────────

    private static TextureTarget ping;    // ping-pong A
    private static TextureTarget pong;    // ping-pong B
    private static TextureTarget sceneCopy; // original scene snapshot

    // Temporal afterimage history buffers (2-frame ring)
    private static TextureTarget history1; // previous frame (N-1)
    private static TextureTarget history2; // two frames ago (N-2)

    // ── Uniform buffer ─────────────────────────────────────────────

    private static GpuBuffer uniforms;

    // ── Pipelines (one per pass) ───────────────────────────────────

    private static RenderPipeline brightPipe;
    private static RenderPipeline blurHPipe;
    private static RenderPipeline blurVPipe;
    private static RenderPipeline compositePipe;
    private static RenderPipeline distortionPipe;
    private static RenderPipeline godRayPipe;
    private static RenderPipeline chromaticPipe;
    private static RenderPipeline blackHolePipe;
    private static RenderPipeline glowFlashPipe;
    private static RenderPipeline shockwavePipe;
    private static RenderPipeline flashScreenPipe;
    private static RenderPipeline afterimagePipe;
    private static RenderPipeline acesPipe;

    // ── Extended passes (depth-aware) ─────────────────────────────────
    private static RenderPipeline brightEdgePipe;
    private static RenderPipeline volumetricGodRayPipe;
    private static RenderPipeline screenLightingPipe;
    private static RenderPipeline ssrtPipe;

    // ════════════════════════════════════════════════════════════════
    //  Initialization
    // ════════════════════════════════════════════════════════════════

    private static void ensureInit() {
        if (uniforms == null) {
            uniforms = RenderSystem.getDevice().createBuffer(
                    () -> "KillEffectPostUniforms",
                    GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
                    UNIFORM_SIZE);
        }
        if (brightPipe == null)           brightPipe           = buildPipe("kill_effect_bright",      "BRIGHT_PASS",      false);
        if (brightEdgePipe == null)       brightEdgePipe       = buildPipe("kill_effect_bright_edge", "BRIGHT_PASS_EDGE", true);
        if (blurHPipe == null)            blurHPipe            = buildPipe("kill_effect_blur_h",      "BLUR_H",           false);
        if (blurVPipe == null)            blurVPipe            = buildPipe("kill_effect_blur_v",      "BLUR_V",           false);
        if (compositePipe == null)        compositePipe        = buildPipe("kill_effect_composite",   "COMPOSITE",        false);
        if (screenLightingPipe == null)   screenLightingPipe   = buildPipe("kill_effect_sclight",    "SCREEN_LIGHTING",  true);
        if (distortionPipe == null)       distortionPipe       = buildPipe("kill_effect_distort",     "DISTORTION",       false);
        if (godRayPipe == null)           godRayPipe           = buildPipe("kill_effect_godray",      "GODRAY",           false);
        if (volumetricGodRayPipe == null) volumetricGodRayPipe = buildPipe("kill_effect_vgodray",    "VOLUMETRIC_GODRAY", true);
        if (ssrtPipe == null)             ssrtPipe             = buildPipe("kill_effect_ssrt",        "SSRT",             true);
        if (chromaticPipe == null)        chromaticPipe        = buildPipe("kill_effect_chromatic",   "CHROMATIC",        false);
        if (blackHolePipe == null)        blackHolePipe        = buildPipe("kill_effect_bh",          "BLACK_HOLE",       false);
        if (glowFlashPipe == null)        glowFlashPipe        = buildPipe("kill_effect_flash",       "GLOW_FLASH",       false);
        if (shockwavePipe == null)        shockwavePipe        = buildPipe("kill_effect_shock",       "SHOCKWAVE",        false);
        if (flashScreenPipe == null)      flashScreenPipe      = buildPipe("kill_effect_fscreen",     "FLASH_SCREEN",     false);
        if (afterimagePipe == null)       afterimagePipe       = buildPipe("kill_effect_afterimg",    "AFTERIMAGE",       false);
        if (acesPipe == null)             acesPipe             = buildPipe("kill_effect_aces",        "ACES",             false);
    }

    private static RenderPipeline buildPipe(String loc, String define, boolean needsDepth) {
        var builder = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(getIdentifier("pipeline/" + loc))
                .withVertexShader(getIdentifier(POST_VSH))
                .withFragmentShader(getIdentifier(POST_FSH))
                .withShaderDefine(define)
                .withUniform("PostUniforms", UniformType.UNIFORM_BUFFER)
                .withSampler("SceneSampler")
                .withSampler("BloomSampler");
        if (needsDepth) {
            builder.withSampler("DepthSampler");
        }
        return builder.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .build();
    }

    // ── Registration ───────────────────────────────────────────────

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        ensureInit();
        registry.accept(brightPipe);
        registry.accept(brightEdgePipe);
        registry.accept(blurHPipe);
        registry.accept(blurVPipe);
        registry.accept(compositePipe);
        registry.accept(screenLightingPipe);
        registry.accept(distortionPipe);
        registry.accept(godRayPipe);
        registry.accept(volumetricGodRayPipe);
        registry.accept(ssrtPipe);
        registry.accept(chromaticPipe);
        registry.accept(blackHolePipe);
        registry.accept(glowFlashPipe);
        registry.accept(shockwavePipe);
        registry.accept(flashScreenPipe);
        registry.accept(afterimagePipe);
        registry.accept(acesPipe);
    }

    // ════════════════════════════════════════════════════════════════
    //  Resource management
    // ════════════════════════════════════════════════════════════════

    private static void ensureTargets(int w, int h) {
        if (ping == null)  ping  = new TextureTarget("KillPing", w, h, false);
        if (pong == null)  pong  = new TextureTarget("KillPong", w, h, false);
        if (sceneCopy == null) sceneCopy = new TextureTarget("KillScene", w, h, false);
        if (history1 == null) history1 = new TextureTarget("KillHist1", w, h, false);
        if (history2 == null) history2 = new TextureTarget("KillHist2", w, h, false);
        if (ping.width != w || ping.height != h)  { ping.resize(w, h);  pong.resize(w, h); }
        if (sceneCopy.width != w || sceneCopy.height != h) sceneCopy.resize(w, h);
        if (history1.width != w || history1.height != h) history1.resize(w, h);
        if (history2.width != w || history2.height != h) history2.resize(w, h);
    }

    public static void destroy() {
        ping = null;  pong = null;  sceneCopy = null;
        history1 = null;  history2 = null;
        if (uniforms != null)  { uniforms.close();  uniforms = null; }
        brightPipe = brightEdgePipe = blurHPipe = blurVPipe = compositePipe = null;
        screenLightingPipe = distortionPipe = godRayPipe = volumetricGodRayPipe = null;
        ssrtPipe = chromaticPipe = blackHolePipe = acesPipe = null;
        glowFlashPipe = shockwavePipe = flashScreenPipe = afterimagePipe = null;
    }

    // ════════════════════════════════════════════════════════════════
    //  NDC: simplified direction-to-screen projection.
    //  Used within processFrame() inline — no separate method needed.
    // ════════════════════════════════════════════════════════════════

    // ════════════════════════════════════════════════════════════════
    //  Per-pass rendering helpers
    // ════════════════════════════════════════════════════════════════

    /**
     * Execute a single post-processing pass.
     *
     * @param encoder        active command encoder
     * @param pipe           the pipeline for this pass
     * @param sceneSrc       texture bound to SceneSampler
     * @param bloomSrc       texture bound to BloomSampler
     * @param dest           output render target (or main FB for final)
     * @param label          debug label for the render pass
     */
    private static void runPass(CommandEncoder encoder, RenderPipeline pipe,
                                 GpuTextureView sceneSrc, GpuTextureView bloomSrc,
                                 GpuTextureView dest, String label) {
        boolean toMainFb = (dest == null);
        RenderTarget fb = mc.getMainRenderTarget();

        try (RenderPass pass = encoder.createRenderPass(
                () -> label,
                toMainFb ? fb.getColorTextureView() : dest,
                toMainFb ? OptionalInt.empty() : OptionalInt.empty())) {
            pass.setPipeline(pipe);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("PostUniforms", uniforms);
            pass.bindTexture("SceneSampler", sceneSrc,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.bindTexture("BloomSampler", bloomSrc,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(0, 3);
        }
    }

    /**
     * Execute a post-processing pass with depth texture binding.
     *
     * <p>Used by depth-aware passes (BRIGHT_PASS_EDGE, SCREEN_LIGHTING,
     * VOLUMETRIC_GODRAY, SSRT) that need to reconstruct view-space
     * position from the depth buffer.</p>
     *
     * @param encoder        active command encoder
     * @param pipe           the pipeline for this pass
     * @param sceneSrc       texture bound to SceneSampler
     * @param bloomSrc       texture bound to BloomSampler
     * @param depthSrc       texture bound to DepthSampler (main FB depth)
     * @param dest           output render target
     * @param label          debug label for the render pass
     */
    private static void runPassWithDepth(CommandEncoder encoder, RenderPipeline pipe,
                                          GpuTextureView sceneSrc, GpuTextureView bloomSrc,
                                          GpuTextureView depthSrc, GpuTextureView dest,
                                          String label) {
        try (RenderPass pass = encoder.createRenderPass(
                () -> label, dest, OptionalInt.empty())) {
            pass.setPipeline(pipe);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("PostUniforms", uniforms);
            pass.bindTexture("SceneSampler", sceneSrc,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.bindTexture("BloomSampler", bloomSrc,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.bindTexture("DepthSampler", depthSrc,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(0, 3);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Main entry point
    // ════════════════════════════════════════════════════════════════

    /**
     * Run the full post-processing pipeline.
     *
     * @param bloomStrength     bloom intensity (0=none)
     * @param threshold         luminance threshold for bright extraction
     * @param distortionStr     heat distortion strength (0=none)
     * @param godRayStr         god ray strength (0=none)
     * @param chromaticStr      chromatic aberration strength (0=none)
     * @param bloomRadius       gaussian blur radius in pixels
     * @param center1World      world-space position of primary effect (or null)
     * @param center2World      world-space position of secondary effect (or null)
     * @param bhStage           black hole stage ID (0=inactive)
     * @param bhProgress        black hole stage progress 0→1
     * @param bhIntensity       black hole intensity multiplier
     * @param lightWorldPos     world-space {x, y, z, radius} of glow sphere (or null)
     * @param lightColor        light color {r, g, b, intensity} (or null)
     * @param ssrIntensity      screen-space reflection strength (0=none)
     * @param volumetricSteps   ray-march step count for volumetric god rays (8-32)
     * @param chainFade         global chain fade-in 0→1 (smoothstep); crossfades
     *                          the ACES output with the raw scene to avoid pops
     */
    public static void processFrame(float bloomStrength, float threshold,
                                     float distortionStr, float godRayStr,
                                     float chromaticStr, float bloomRadius,
                                     double[] center1World, double[] center2World,
                                     int bhStage, float bhProgress, float bhIntensity,
                                     float[] lightWorldPos, float[] lightColor,
                                     float ssrIntensity, int volumetricSteps,
                                     float chainFade) {
        boolean lightActive = lightWorldPos != null && lightColor != null
                && lightColor.length >= 4 && lightColor[3] > 0.01f;
        if (bloomStrength <= 0f && distortionStr <= 0f
                && godRayStr <= 0f && chromaticStr <= 0f
                && bhStage <= 0 && ssrIntensity <= 0f && !lightActive) return;

        ensureInit();
        RenderTarget fb = mc.getMainRenderTarget();
        if (fb.getColorTexture() == null || fb.getColorTextureView() == null) return;

        int fbW = mc.getWindow().getWidth();
        int fbH = mc.getWindow().getHeight();
        ensureTargets(fbW, fbH);

        // ── Camera & view-projection data ──────────────────────────
        var cam = mc.gameRenderer.getMainCamera();
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;

        // Derive camera basis from rotation quaternion
        org.joml.Quaternionf camRot = cam.rotation();
        org.joml.Vector3f forward = camRot.transform(new org.joml.Vector3f(0, 0, -1));
        org.joml.Vector3f up      = camRot.transform(new org.joml.Vector3f(0, 1, 0));
        org.joml.Vector3f right   = camRot.transform(new org.joml.Vector3f(1, 0, 0));

        float fovRad = (float) Math.toRadians(70.0);
        float h = (float) Math.tan(fovRad * 0.5);
        float aspect = (float) fbW / (float) fbH;
        float zNear = 0.05f;
        float zFar = Math.max(mc.options.getEffectiveRenderDistance() * 16f * 4f, 256f);

        // ── NDC projection for center1 / center2 ───────────────────
        float ndc1x = 0f, ndc1y = 0f, worldDist1 = 0f;
        float ndc2x = 0f, ndc2y = 0f;

        if (center1World != null) {
            float rx = (float)(center1World[0] - cx);
            float ry = (float)(center1World[1] - cy);
            float rz = (float)(center1World[2] - cz);

            float viewX = rx * right.x + ry * right.y + rz * right.z;
            float viewY = rx * up.x    + ry * up.y    + rz * up.z;
            float viewZ = rx * forward.x + ry * forward.y + rz * forward.z;

            worldDist1 = (float) Math.sqrt(rx*rx + ry*ry + rz*rz);
            if (viewZ > 0.05f) {
                ndc1x = viewX / (viewZ * aspect * h);
                ndc1y = viewY / (viewZ * h);
                ndc1x = Math.clamp(ndc1x, -2f, 2f);
                ndc1y = Math.clamp(ndc1y, -2f, 2f);
            }
        }
        if (center2World != null) {
            float rx = (float)(center2World[0] - cx);
            float ry = (float)(center2World[1] - cy);
            float rz = (float)(center2World[2] - cz);

            float viewX = rx * right.x + ry * right.y + rz * right.z;
            float viewY = rx * up.x    + ry * up.y    + rz * up.z;
            float viewZ = rx * forward.x + ry * forward.y + rz * forward.z;

            if (viewZ > 0.05f) {
                ndc2x = viewX / (viewZ * aspect * h);
                ndc2y = viewY / (viewZ * h);
                ndc2x = Math.clamp(ndc2x, -2f, 2f);
                ndc2y = Math.clamp(ndc2y, -2f, 2f);
            }
        }

        // ── BH screen-space radius ─────────────────────────────────
        float bhRadiusUV = 0f;
        if (center1World != null) {
            float rx = (float)(center1World[0] - cx);
            float ry = (float)(center1World[1] - cy);
            float rz = (float)(center1World[2] - cz);
            float viewZ = rx * forward.x + ry * forward.y + rz * forward.z;
            if (viewZ > 0.05f) {
                float bhWorldSize = 3.0f;
                float angularSize = bhWorldSize / viewZ;
                float ndcRadius = angularSize / h;
                bhRadiusUV = ndcRadius * 0.5f;
                bhRadiusUV = Math.clamp(bhRadiusUV, 0.005f, 2.0f);
            }
        }

        // ── Light position in view space ────────────────────────────
        float lvX = 0f, lvY = 0f, lvZ = 0f, lvRadius = 1f;
        float lcR = 1f, lcG = 0.85f, lcB = 0.55f, lcI = 0f;
        if (lightWorldPos != null && lightWorldPos.length >= 4) {
            float rx = lightWorldPos[0] - cx;
            float ry = lightWorldPos[1] - cy;
            float rz = lightWorldPos[2] - cz;
            lvX = rx * right.x + ry * right.y + rz * right.z;
            lvY = rx * up.x    + ry * up.y    + rz * up.z;
            lvZ = rx * forward.x + ry * forward.y + rz * forward.z;
            lvRadius = Math.max(lightWorldPos[3], 1f);
        }
        if (lightColor != null && lightColor.length >= 4) {
            lcR = lightColor[0];
            lcG = lightColor[1];
            lcB = lightColor[2];
            lcI = lightColor[3];
        }

        // ── Depth texture availability ──────────────────────────────
        GpuTextureView depthView = fb.getDepthTextureView();
        boolean hasDepth = (depthView != null);

        // ── Write shared uniforms ───────────────────────────────────
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        float time = System.currentTimeMillis() / 1000f;
        float frameIdx = (float) (time * 60f); // approximate frame counter
        int steps = Math.clamp(volumetricSteps, 8, 32);
        try (GpuBuffer.MappedView view = encoder.mapBuffer(uniforms, false, true)) {
            Std140Builder b = Std140Builder.intoBuffer(view.data());
            b.putVec4(fbW, fbH, bloomStrength, threshold);
            b.putVec4(time, frameIdx, 0f, 0f);
            b.putVec4(ndc1x, ndc1y, worldDist1, 0f);
            b.putVec4(ndc2x, ndc2y, 0f, 0f);
            b.putVec4(distortionStr, godRayStr, chromaticStr, bloomRadius);
            b.putVec4(bhRadiusUV, (float)bhStage, bhProgress, bhIntensity);
            b.putVec4(fovRad, aspect, zNear, zFar);                    // CameraParams
            b.putVec4(lvX, lvY, lvZ, lvRadius);                       // LightViewPos
            b.putVec4(lcR, lcG, lcB, lcI);                            // LightColor
            b.putVec4(ssrIntensity, (float)steps, chainFade, 0f);     // MiscParams
        }

        // ══════════════════════════════════════════════════════════
        //  Copy scene to sceneCopy
        // ══════════════════════════════════════════════════════════

        encoder.copyTextureToTexture(
                fb.getColorTexture(), sceneCopy.getColorTexture(),
                0, 0, 0, 0, 0, fbW, fbH);

        GpuTextureView sceneView  = sceneCopy.getColorTextureView();
        GpuTextureView pingView   = ping.getColorTextureView();
        GpuTextureView pongView   = pong.getColorTextureView();
        GpuTextureView dummyBloom = pingView;

        // ══════════════════════════════════════════════════════════
        //  Pass chain
        // ══════════════════════════════════════════════════════════

        // ── Pass 1: Bright Extract ────────────────────────────────
        if (hasDepth) {
            runPassWithDepth(encoder, brightEdgePipe, sceneView, dummyBloom,
                    depthView, pingView, "Kill BrightEdge");
        } else {
            runPass(encoder, brightPipe, sceneView, dummyBloom, pingView, "Kill Bright");
        }
        GpuTextureView bloomSrc = pingView;

        // ── Pass 2a: Blur H ───────────────────────────────────────
        runPass(encoder, blurHPipe, bloomSrc, dummyBloom, pongView, "Kill BlurH");
        bloomSrc = pongView;

        // ── Pass 2b: Blur V ───────────────────────────────────────
        runPass(encoder, blurVPipe, bloomSrc, dummyBloom, pingView, "Kill BlurV");
        GpuTextureView blurredBloom = pingView;

        // ── Pass 3: Composite (scene + bloom) ─────────────────────
        runPass(encoder, compositePipe, sceneView, blurredBloom, pongView, "Kill Composite");
        GpuTextureView composited = pongView;

        // ── Pass 3b: Screen-Space Lighting ────────────────────────
        // Pseudo ray-traced residual light: active whenever the light
        // source is alive (flash → hypernova → afterglow → fade-out).
        if (hasDepth && lightWorldPos != null && lcI > 0.01f) {
            GpuTextureView slDest = (composited == pingView) ? pongView : pingView;
            runPassWithDepth(encoder, screenLightingPipe, composited, dummyBloom,
                    depthView, slDest, "Kill ScreenLight");
            composited = slDest;
        }

        // ── Pass 4: Distortion ────────────────────────────────────
        if (distortionStr > 0.001f) {
            GpuTextureView dDest = (composited == pingView) ? pongView : pingView;
            runPass(encoder, distortionPipe, composited, dummyBloom, dDest, "Kill Distort");
            composited = dDest;
        }

        // ── Pass 5a: God Rays (screen-space) ──────────────────────
        if (godRayStr > 0.001f) {
            GpuTextureView grDest = (composited == pingView) ? pongView : pingView;
            runPass(encoder, godRayPipe, composited, dummyBloom, grDest, "Kill GodRay");
            composited = grDest;
        }

        // ── Pass 5b: Volumetric God Rays (ray-marched) ────────────
        // Active during BH + hypernova stages (3-8), and during
        // afterglow/fade-out whenever the residual glow ball is alive —
        // the final glow ball becomes the ray-marched light source.
        if (hasDepth && lightWorldPos != null && godRayStr > 0.01f
                && ((bhStage >= 3 && bhStage <= 8) || lcI > 0.01f)) {
            GpuTextureView vgrDest = (composited == pingView) ? pongView : pingView;
            runPassWithDepth(encoder, volumetricGodRayPipe, composited, dummyBloom,
                    depthView, vgrDest, "Kill VolGodRay");
            composited = vgrDest;
        }

        // ── Pass 5c: SSRT ────────────────────────────────────────
        // Active during hypernova (stage 7-8) near light
        if (hasDepth && lightWorldPos != null && ssrIntensity > 0.01f
                && (bhStage == 7 || bhStage == 8)) {
            GpuTextureView ssrtDest = (composited == pingView) ? pongView : pingView;
            runPassWithDepth(encoder, ssrtPipe, composited, dummyBloom,
                    depthView, ssrtDest, "Kill SSRT");
            composited = ssrtDest;
        }

        // ── Pass 6: Chromatic Aberration ──────────────────────────
        if (chromaticStr > 0.001f) {
            GpuTextureView chDest = (composited == pingView) ? pongView : pingView;
            runPass(encoder, chromaticPipe, composited, dummyBloom, chDest, "Kill Chromatic");
            composited = chDest;
        }

        // ── Pass 7a: Black Hole Center (stages 3-5) ──────────────
        if (bhRadiusUV > 0.005f && bhStage >= 3 && bhStage <= 5) {
            GpuTextureView bhDest = (composited == pingView) ? pongView : pingView;
            runPass(encoder, blackHolePipe, composited, dummyBloom, bhDest, "Kill BH Center");
            composited = bhDest;
        }

        // ── Pass 7b: Glow Flash (stage 7) ────────────────────────
        if (bhStage == 7) {
            GpuTextureView gfDest = (composited == pingView) ? pongView : pingView;
            runPass(encoder, glowFlashPipe, composited, dummyBloom, gfDest, "Kill Glow Flash");
            composited = gfDest;
        }

        // ── Pass 7c: Flash Screen (stages 7-8) ───────────────────
        if (bhStage == 7 || bhStage == 8) {
            GpuTextureView fsDest = (composited == pingView) ? pongView : pingView;
            runPass(encoder, flashScreenPipe, composited, dummyBloom, fsDest, "Kill Flash Screen");
            composited = fsDest;
        }

        // ── Pass 7d: Shockwave (stage 8) ─────────────────────────
        if (bhStage == 8) {
            GpuTextureView swDest = (composited == pingView) ? pongView : pingView;
            runPass(encoder, shockwavePipe, composited, dummyBloom, swDest, "Kill Shockwave");
            composited = swDest;
        }

        // ── Pass 7e: Temporal Afterimage (stage 8) ───────────────
        if (bhStage == 8) {
            GpuTextureView aiDest = (composited == pingView) ? pongView : pingView;
            TextureTarget compositedTarget = (composited == pingView) ? ping : pong;
            TextureTarget aiDestTarget = (composited == pingView) ? pong : ping;

            runPass(encoder, afterimagePipe, composited,
                    history1.getColorTextureView(), aiDest, "Kill Afterimage");
            composited = aiDest;

            encoder.copyTextureToTexture(
                    aiDestTarget.getColorTexture(), history1.getColorTexture(),
                    0, 0, 0, 0, 0, fbW, fbH);
        }

        // ── Pass 8: ACES Tone Mapping → Main FB ──────────────────
        runPass(encoder, acesPipe, composited, dummyBloom, null, "Kill ACES");
    }
}

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
 * <h3>Render pass chain (configurable order)</h3>
 * <pre>
 * [Scene FBO]
 *     │
 *     ├── Bright Pass ──→ Ping A   (extract HDR highlights)
 *     ├── Blur H      ──→ Ping B   (separable horizontal gaussian)
 *     ├── Blur V      ──→ Ping A   (separable vertical gaussian)
 *     │                              ↓
 *     ├── Composite   ──→ Ping B   (scene + bloom, reads SceneSampler + BloomSampler)
 *     ├── Distortion  ──→ Ping A   (screen-space heat haze from effect center)
 *     ├── GodRay      ──→ Ping B   (screen-space radial blur from effect center)
 *     ├── Chromatic   ──→ Ping A   (RGB channel separation)
 *     ├── BH Center   ──→ Ping B   (screen-space BH: lensing + 3-layer, stage 3-5)
 *     ├── Glow Flash  ──→ Ping A   (pre-supernova bright pulsing spot, stage 6)
 *     ├── Shockwave   ──→ Ping B   (expanding concentric shock rings, stage 7)
 *     └── ACES        ──→ Main FB  (HDR→LDR tone mapping, writes to framebuffer)
 * </pre>
 *
 * <h3>Uniform layout (std140, 96 bytes)</h3>
 * <pre>
 * vec4 Params:      fbWidth, fbHeight, bloomStrength, threshold
 * vec4 TimePack:    timeSec, 0, 0, 0
 * vec4 Center1:     ndcX, ndcY, bhRadiusUV, 0  (primary effect NDC + BH radius)
 * vec4 Center2:     ndcX, ndcY, 0, 0           (secondary effect NDC)
 * vec4 PassParams:  distortionStr, godRayStr, chromaticStr, bloomRadius
 * vec4 BHParams:    bhRadiusUV, bhStage, bhProgress, bhIntensity
 * </pre>
 */
public final class KillEffectPostProcessor {

    private KillEffectPostProcessor() {}

    // ── Shader path ────────────────────────────────────────────────

    private static final String POST_VSH = "core/kill_effect_post";
    private static final String POST_FSH = "core/kill_effect_post";

    // ── Uniform layout (6 × vec4 = 96 bytes) ──────────────────────

    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4()  // Params:     fbWidth, fbHeight, bloomStrength, threshold
            .putVec4()  // TimePack:   time, 0, 0, 0
            .putVec4()  // Center1:    ndcX, ndcY, bhRadiusUV, 0
            .putVec4()  // Center2:    ndcX, ndcY, 0, 0
            .putVec4()  // PassParams: distortion, godRay, chromatic, bloomRadius
            .putVec4()  // BHParams:   bhRadius, stage, progress, intensity
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
        if (brightPipe == null)      brightPipe      = buildPipe("kill_effect_bright",     "BRIGHT_PASS");
        if (blurHPipe == null)       blurHPipe       = buildPipe("kill_effect_blur_h",    "BLUR_H");
        if (blurVPipe == null)       blurVPipe       = buildPipe("kill_effect_blur_v",    "BLUR_V");
        if (compositePipe == null)   compositePipe   = buildPipe("kill_effect_composite", "COMPOSITE");
        if (distortionPipe == null)  distortionPipe  = buildPipe("kill_effect_distort",   "DISTORTION");
        if (godRayPipe == null)      godRayPipe      = buildPipe("kill_effect_godray",    "GODRAY");
        if (chromaticPipe == null)   chromaticPipe   = buildPipe("kill_effect_chromatic", "CHROMATIC");
        if (blackHolePipe == null)   blackHolePipe   = buildPipe("kill_effect_bh",        "BLACK_HOLE");
        if (glowFlashPipe == null)   glowFlashPipe   = buildPipe("kill_effect_flash",     "GLOW_FLASH");
        if (shockwavePipe == null)   shockwavePipe   = buildPipe("kill_effect_shock",     "SHOCKWAVE");
        if (flashScreenPipe == null) flashScreenPipe = buildPipe("kill_effect_fscreen",   "FLASH_SCREEN");
        if (afterimagePipe == null)  afterimagePipe  = buildPipe("kill_effect_afterimg",  "AFTERIMAGE");
        if (acesPipe == null)        acesPipe        = buildPipe("kill_effect_aces",      "ACES");
    }

    private static RenderPipeline buildPipe(String loc, String define) {
        return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(getIdentifier("pipeline/" + loc))
                .withVertexShader(getIdentifier(POST_VSH))
                .withFragmentShader(getIdentifier(POST_FSH))
                .withShaderDefine(define)
                .withUniform("PostUniforms", UniformType.UNIFORM_BUFFER)
                .withSampler("SceneSampler")
                .withSampler("BloomSampler")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .build();
    }

    // ── Registration ───────────────────────────────────────────────

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        ensureInit();
        registry.accept(brightPipe);
        registry.accept(blurHPipe);
        registry.accept(blurVPipe);
        registry.accept(compositePipe);
        registry.accept(distortionPipe);
        registry.accept(godRayPipe);
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
        // TextureTarget has no explicit close/destroy — release references for GC.
        // Minecraft manages GPU texture lifecycle internally.
        ping = null;  pong = null;  sceneCopy = null;
        history1 = null;  history2 = null;
        if (uniforms != null)  { uniforms.close();  uniforms = null; }
        brightPipe = blurHPipe = blurVPipe = compositePipe = null;
        distortionPipe = godRayPipe = chromaticPipe = blackHolePipe = acesPipe = null;
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
     * @param bhStage           black hole stage ID (0=inactive, 1-7)
     * @param bhProgress        black hole stage progress 0→1
     * @param bhIntensity       black hole intensity multiplier
     */
    public static void processFrame(float bloomStrength, float threshold,
                                     float distortionStr, float godRayStr,
                                     float chromaticStr, float bloomRadius,
                                     double[] center1World, double[] center2World,
                                     int bhStage, float bhProgress, float bhIntensity) {
        if (bloomStrength <= 0f && distortionStr <= 0f
                && godRayStr <= 0f && chromaticStr <= 0f
                && bhStage <= 0) return;

        ensureInit();
        RenderTarget fb = mc.getMainRenderTarget();
        if (fb.getColorTexture() == null || fb.getColorTextureView() == null) return;

        int fbW = mc.getWindow().getWidth();
        int fbH = mc.getWindow().getHeight();
        ensureTargets(fbW, fbH);

        // ── Compute NDC coordinates via proper camera-basis projection ──
        // Uses camera rotation quaternion to derive forward/right/up vectors,
        // then applies perspective divide. This correctly handles any camera
        // orientation — no more drift when the player looks around.
        float ndc1x = 0f, ndc1y = 0f, ndc2x = 0f, ndc2y = 0f;

        var cam = mc.gameRenderer.getMainCamera();
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;

        // Derive camera basis from rotation quaternion (same as KillAuraIndicatorRenderer)
        org.joml.Quaternionf camRot = cam.rotation();
        org.joml.Vector3f forward = camRot.transform(new org.joml.Vector3f(0, 0, -1));
        org.joml.Vector3f up      = camRot.transform(new org.joml.Vector3f(0, 1, 0));
        org.joml.Vector3f right   = camRot.transform(new org.joml.Vector3f(1, 0, 0));

        float fovRad = (float) Math.toRadians(70.0);
        float h = (float) Math.tan(fovRad * 0.5);
        float aspect = (float) fbW / (float) fbH;

        if (center1World != null) {
            float rx = (float)(center1World[0] - cx);
            float ry = (float)(center1World[1] - cy);
            float rz = (float)(center1World[2] - cz);

            // Project world offset onto camera basis → view-space
            float viewX = rx * right.x + ry * right.y + rz * right.z;
            float viewY = rx * up.x    + ry * up.y    + rz * up.z;
            float viewZ = rx * forward.x + ry * forward.y + rz * forward.z;

            if (viewZ > 0.05f) {
                // Perspective projection → NDC [-1,1]
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

        // ── Compute BH screen-space radius from world position ────
        float bhRadiusUV = 0f;
        if (center1World != null) {
            float rx = (float)(center1World[0] - cx);
            float ry = (float)(center1World[1] - cy);
            float rz = (float)(center1World[2] - cz);
            float viewZ = rx * forward.x + ry * forward.y + rz * forward.z;
            if (viewZ > 0.05f) {
                // Approximate billboard half-width in world space (~3 blocks)
                float bhWorldSize = 3.0f;
                // Angular size ≈ worldSize / distance (radians)
                float angularSize = bhWorldSize / viewZ;
                // NDC radius = angularSize / h  (where h = tan(fov/2))
                float ndcRadius = angularSize / h;
                // Convert to UV space [0,1]: UV = NDC/2
                bhRadiusUV = ndcRadius * 0.5f;
                bhRadiusUV = Math.clamp(bhRadiusUV, 0.005f, 2.0f);
            }
        }

        // ── Write shared uniforms ──────────────────────────────────
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        float time = System.currentTimeMillis() / 1000f;
        try (GpuBuffer.MappedView view = encoder.mapBuffer(uniforms, false, true)) {
            Std140Builder b = Std140Builder.intoBuffer(view.data());
            b.putVec4(fbW, fbH, bloomStrength, threshold);
            b.putVec4(time, 0f, 0f, 0f);
            b.putVec4(ndc1x, ndc1y, bhRadiusUV, 0f);          // Center1.z = bhRadiusUV
            b.putVec4(ndc2x, ndc2y, 0f, 0f);
            b.putVec4(distortionStr, godRayStr, chromaticStr, bloomRadius);
            b.putVec4(bhRadiusUV, (float)bhStage, bhProgress, bhIntensity);
        }

        // ══════════════════════════════════════════════════════════
        //  Copy scene to sceneCopy (preserved for composite)
        // ══════════════════════════════════════════════════════════

        encoder.copyTextureToTexture(
                fb.getColorTexture(), sceneCopy.getColorTexture(),
                0, 0, 0, 0, 0, fbW, fbH);

        GpuTextureView sceneView  = sceneCopy.getColorTextureView();
        GpuTextureView pingView   = ping.getColorTextureView();
        GpuTextureView pongView   = pong.getColorTextureView();
        // dummy for passes that don't use BloomSampler
        GpuTextureView dummyBloom = pingView;

        // ══════════════════════════════════════════════════════════
        //  Pass chain
        //  Conventions: "src" → texture we read from via SceneSampler
        //               "dst" → texture we write to
        //  After each pass, src/dst swap for ping-pong.
        // ══════════════════════════════════════════════════════════

        // ── Pass 1: Bright Extract ────────────────────────────────
        // Read scene, write to ping
        runPass(encoder, brightPipe, sceneView, dummyBloom, pingView, "Kill Bright");
        GpuTextureView bloomSrc = pingView;   // current bloom source
        GpuTextureView workSrc  = sceneView;  // current working copy

        // ── Pass 2a: Blur H ───────────────────────────────────────
        // Read bloomSrc (ping), write to pong
        runPass(encoder, blurHPipe, bloomSrc, dummyBloom, pongView, "Kill BlurH");
        bloomSrc = pongView;

        // ── Pass 2b: Blur V ───────────────────────────────────────
        // Read bloomSrc (pong), write to ping
        runPass(encoder, blurVPipe, bloomSrc, dummyBloom, pingView, "Kill BlurV");
        GpuTextureView blurredBloom = pingView; // final blurred bloom

        // ── Pass 3: Composite (scene + bloom) ─────────────────────
        // Read scene (sceneCopy) + blurredBloom → write to pong
        runPass(encoder, compositePipe, sceneView, blurredBloom, pongView, "Kill Composite");
        GpuTextureView composited = pongView;

        // ── Pass 4: Distortion ────────────────────────────────────
        if (distortionStr > 0.001f) {
            runPass(encoder, distortionPipe, composited, dummyBloom, pingView, "Kill Distort");
            composited = pingView;
        }

        // ── Pass 5: God Rays ──────────────────────────────────────
        if (godRayStr > 0.001f) {
            runPass(encoder, godRayPipe, composited, dummyBloom, pongView, "Kill GodRay");
            composited = pongView;
        }

        // ── Pass 6: Chromatic Aberration ──────────────────────────
        if (chromaticStr > 0.001f) {
            runPass(encoder, chromaticPipe, composited, dummyBloom, pingView, "Kill Chromatic");
            composited = pingView;
        }

        // ── Pass 7a: Black Hole Center (stages 3-5) ──────────────
        if (bhRadiusUV > 0.005f && bhStage >= 3 && bhStage <= 5) {
            GpuTextureView bhDest = (composited == pingView) ? pongView : pingView;
            runPass(encoder, blackHolePipe, composited, dummyBloom, bhDest, "Kill BH Center");
            composited = bhDest;
        }

        // ── Pass 7b: Glow Flash (stage 7 — post-void light pulse) ──
        if (bhStage == 7) {
            GpuTextureView gfDest = (composited == pingView) ? pongView : pingView;
            runPass(encoder, glowFlashPipe, composited, dummyBloom, gfDest, "Kill Glow Flash");
            composited = gfDest;
        }

        // ── Pass 7c: Flash Screen (stages 7-8 — full-screen white flash) ──
        if (bhStage == 7 || bhStage == 8) {
            GpuTextureView fsDest = (composited == pingView) ? pongView : pingView;
            runPass(encoder, flashScreenPipe, composited, dummyBloom, fsDest, "Kill Flash Screen");
            composited = fsDest;
        }

        // ── Pass 7d: Shockwave (stage 8 — hypernova explosion) ──
        if (bhStage == 8) {
            GpuTextureView swDest = (composited == pingView) ? pongView : pingView;
            runPass(encoder, shockwavePipe, composited, dummyBloom, swDest, "Kill Shockwave");
            composited = swDest;
        }

        // ── Pass 7e: Temporal Afterimage (stage 8 — motion trails) ──
        // Blends current frame with previous frame stored in history1.
        // After blending, saves current result → history1 for next frame.
        if (bhStage == 8) {
            GpuTextureView aiDest = (composited == pingView) ? pongView : pingView;
            // Determine which TextureTarget composited currently points to
            TextureTarget compositedTarget = (composited == pingView) ? ping : pong;
            TextureTarget aiDestTarget = (composited == pingView) ? pong : ping;

            // Run afterimage: SceneSampler=current, BloomSampler=history1(prev frame)
            runPass(encoder, afterimagePipe, composited, history1.getColorTextureView(), aiDest, "Kill Afterimage");
            composited = aiDest;

            // Save current result → history1 for next frame
            encoder.copyTextureToTexture(
                    aiDestTarget.getColorTexture(), history1.getColorTexture(),
                    0, 0, 0, 0, 0, fbW, fbH);
        }

        // ── Pass 8: ACES Tone Mapping (writes to main FB) ─────────
        runPass(encoder, acesPipe, composited, dummyBloom, null, "Kill ACES");
    }
}

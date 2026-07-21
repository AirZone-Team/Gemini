package geminiclient.gemini.customRenderer.glsl;

import com.mojang.blaze3d.buffers.GpuBufferSlice;

import geminiclient.gemini.customRenderer.GeminiRenderPipelines;
import geminiclient.gemini.customRenderer.GeminiRenderTargets;
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
import geminiclient.gemini.customRenderer.glsl.modules.MipBloomProcessor;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.Optional;
import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.customRenderer.SlangShaderAssets.variant;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * Unified VFX Manager — central orchestrator for the "god-tier light
 * pollution" post-processing pipeline.
 *
 * <h3>Architecture</h3>
 * <pre>
 * [Scene FBO after all 3D module rendering]
 *     │
 *     ├── Distortion Pass     (heat haze / gravitational lensing)
 *     ├── God Rays            (screen-space radial blur)
 *     ├── Chromatic Aberration(RGB channel separation)
 *     ├── Mip Bloom           (5-level gaussian pyramid)
 *     ├── Lens Dirt           (texture overlay)
 *     ├── Motion Blur         (velocity-based)
 *     └── Vignette + Tonemap  (final output)
 * </pre>
 *
 * <h3>Usage</h3>
 * <p>Call {@link #processFrame} once per frame, after all 3D module
 * rendering is complete.  Pass enabled flags + parameters based on
 * which visual modules are active.</p>
 *
 * <h3>Uniform layout (std140, 96 bytes = 6 × vec4)</h3>
 * <pre>
 * vec4 Params:       fbWidth, fbHeight, time, 0
 * vec4 DistortPack:  strength, 0, 0, 0
 * vec4 GodRayPack:   strength, 0, 0, 0
 * vec4 ChromPack:    strength, 0, 0, 0
 * vec4 BloomPack:    threshold, strength, 0, 0
 * vec4 LensPack:     intensity, 0, 0, 0
 * </pre>
 */
public final class VFXManager {

    private VFXManager() {}

    // ── Shader path ────────────────────────────────────────────────

    private static final String VFX_VSH = "core/vfx_post";
    private static final String VFX_FSH = "core/vfx_post";

    // ── Uniform layout ──────────────────────────────────────────────

    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4()  // Params:       fbWidth, fbHeight, time, 0
            .putVec4()  // DistortPack:  strength, 0, 0, 0
            .putVec4()  // GodRayPack:   strength, 0, 0, 0
            .putVec4()  // ChromPack:    strength, 0, 0, 0
            .putVec4()  // BloomPack:    threshold, strength, 0, 0
            .putVec4()  // LensPack:     intensity, 0, 0, 0
            .get();

    // ── Texture targets (ping-pong) ─────────────────────────────────

    private static TextureTarget ping;
    private static TextureTarget pong;
    private static TextureTarget sceneCopy;
    private static GpuBuffer uniforms;

    // ── Pipelines ───────────────────────────────────────────────────

    private static RenderPipeline distortPipe;
    private static RenderPipeline godRayPipe;
    private static RenderPipeline chromaticPipe;
    private static RenderPipeline bloomCompositePipe;
    private static RenderPipeline vignettePipe;

    // ════════════════════════════════════════════════════════════════
    //  Initialization
    // ════════════════════════════════════════════════════════════════

    private static void ensureInit() {
        if (uniforms == null) {
            uniforms = RenderSystem.getDevice().createBuffer(
                    () -> "VFXUniforms",
                    GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
                    UNIFORM_SIZE);
        }
        if (distortPipe == null)       distortPipe       = buildPipe("vfx_distort",   "VFX_DISTORT");
        if (godRayPipe == null)        godRayPipe        = buildPipe("vfx_godray",    "VFX_GODRAY");
        if (chromaticPipe == null)     chromaticPipe     = buildPipe("vfx_chromatic", "VFX_CHROMATIC");
        if (bloomCompositePipe == null)bloomCompositePipe= buildPipe("vfx_bloom_comp","VFX_BLOOM_COMPOSITE");
        if (vignettePipe == null)      vignettePipe      = buildPipe("vfx_vignette",  "VFX_VIGNETTE");
    }

    private static RenderPipeline buildPipe(String loc, String define) {
        return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(getIdentifier("pipeline/" + loc))
                .withVertexShader(getIdentifier(VFX_VSH))
                .withFragmentShader(getIdentifier(variant(VFX_FSH, define)))
                .withBindGroupLayout(GeminiRenderPipelines.uniformAndSamplers(
                        "VFXUniforms", "SceneSampler", "BloomSampler"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .build();
    }

    // ── Registration ───────────────────────────────────────────────

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        ensureInit();
        registry.accept(distortPipe);
        registry.accept(godRayPipe);
        registry.accept(chromaticPipe);
        registry.accept(bloomCompositePipe);
        registry.accept(vignettePipe);
    }

    // ── Resource management ─────────────────────────────────────────

    private static void ensureTargets(int w, int h) {
        if (ping == null)  ping  = GeminiRenderTargets.colorTarget("VFXPing", w, h, false);
        if (pong == null)  pong  = GeminiRenderTargets.colorTarget("VFXPong", w, h, false);
        if (sceneCopy == null) sceneCopy = GeminiRenderTargets.colorTarget("VFXScene", w, h, false);
        if (ping.width != w || ping.height != h)  { ping.resize(w, h);  pong.resize(w, h); }
        if (sceneCopy.width != w || sceneCopy.height != h) sceneCopy.resize(w, h);
    }

    public static void destroy() {
        ping = null;  pong = null;  sceneCopy = null;
        if (uniforms != null) { uniforms.close(); uniforms = null; }
        distortPipe = godRayPipe = chromaticPipe = bloomCompositePipe = vignettePipe = null;
    }

    // ════════════════════════════════════════════════════════════════
    //  Per-pass helper
    // ════════════════════════════════════════════════════════════════

    private static void runPass(CommandEncoder encoder, RenderPipeline pipe,
                                 GpuTextureView sceneSrc, GpuTextureView bloomSrc,
                                 GpuTextureView dest, String label) {
        boolean toMainFb = (dest == null);
        RenderTarget fb = mc.gameRenderer.mainRenderTarget();
        try (RenderPass pass = encoder.createRenderPass(
                () -> label,
                toMainFb ? fb.getColorTextureView() : dest,
                Optional.empty())) {
            pass.setPipeline(pipe);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("VFXUniforms", uniforms);
            pass.bindTexture("SceneSampler", sceneSrc,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.bindTexture("BloomSampler", bloomSrc,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Main entry point
    // ════════════════════════════════════════════════════════════════

    /**
     * Run the unified post-processing pipeline.
     *
     * <p>Call this <b>after</b> all 3D module rendering (Trail, Ghost,
     * KillEffect, MagicHalo, etc.) has been flushed to the main FBO.</p>
     *
     * @param distortionStr    heat distortion strength (0=none)
     * @param godRayStr        god ray strength (0=none)
     * @param chromaticStr     chromatic aberration strength (0=none)
     * @param bloomThreshold   luminance threshold for bloom
     * @param bloomStrength    bloom intensity (0=none)
     * @param lensDirtIntensity lens dirt overlay intensity (0=none)
     * @param vignetteStr      vignette strength (0=none)
     */
    public static void processFrame(
            float distortionStr, float godRayStr, float chromaticStr,
            float bloomThreshold, float bloomStrength,
            float lensDirtIntensity, float vignetteStr) {

        // ── Early-out: nothing to do ──────────────────────────────
        if (distortionStr <= 0f && godRayStr <= 0f && chromaticStr <= 0f
                && bloomStrength <= 0f && lensDirtIntensity <= 0f
                && vignetteStr <= 0f) return;

        ensureInit();
        RenderTarget fb = mc.gameRenderer.mainRenderTarget();
        if (fb.getColorTexture() == null || fb.getColorTextureView() == null) return;

        int fbW = mc.getWindow().getWidth();
        int fbH = mc.getWindow().getHeight();
        ensureTargets(fbW, fbH);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        float time = System.currentTimeMillis() / 1000f;

        // ── Write shared uniforms ──────────────────────────────────
        try (GpuBufferSlice.MappedView view = uniforms.map(false, true)) {
            Std140Builder b = Std140Builder.intoBuffer(view.data());
            b.putVec4(fbW, fbH, time, 0f);
            b.putVec4(distortionStr, 0f, 0f, 0f);
            b.putVec4(godRayStr, 0f, 0f, 0f);
            b.putVec4(chromaticStr, 0f, 0f, 0f);
            b.putVec4(bloomThreshold, bloomStrength, 0f, 0f);
            b.putVec4(lensDirtIntensity, 0f, 0f, 0f);
        }

        // ── Copy scene ──────────────────────────────────────────────
        encoder.copyTextureToTexture(
                fb.getColorTexture(), sceneCopy.getColorTexture(),
                0, 0, 0, 0, 0, fbW, fbH);

        GpuTextureView sceneView = sceneCopy.getColorTextureView();
        GpuTextureView pingView  = ping.getColorTextureView();
        GpuTextureView pongView  = pong.getColorTextureView();
        GpuTextureView current   = sceneView;
        GpuTextureView dummy     = pingView;

        // ══════════════════════════════════════════════════════════
        //  Pass chain
        // ══════════════════════════════════════════════════════════

        // ── Distortion ────────────────────────────────────────────
        if (distortionStr > 0.001f) {
            GpuTextureView next = (current == pingView) ? pongView : pingView;
            runPass(encoder, distortPipe, current, dummy, next, "VFX Distort");
            current = next;
        }

        // ── God Rays ──────────────────────────────────────────────
        if (godRayStr > 0.001f) {
            GpuTextureView next = (current == pingView) ? pongView : pingView;
            runPass(encoder, godRayPipe, current, dummy, next, "VFX GodRay");
            current = next;
        }

        // ── Chromatic Aberration ──────────────────────────────────
        if (chromaticStr > 0.001f) {
            GpuTextureView next = (current == pingView) ? pongView : pingView;
            runPass(encoder, chromaticPipe, current, dummy, next, "VFX Chromatic");
            current = next;
        }

        // ── Bloom Composite ───────────────────────────────────────
        // (Bloom is computed separately via MipBloomProcessor;
        //  here we composite the bloom texture with the scene)
        if (bloomStrength > 0.001f && bloomThreshold > 0f) {
            // Run the mip bloom pipeline
            MipBloomProcessor.processFrame(bloomThreshold, false);
            // Composite bloom with the current processed scene
            GpuTextureView next = (current == pingView) ? pongView : pingView;
            runPass(encoder, bloomCompositePipe, current,
                    MipBloomProcessor.getBloomOutput(), next, "VFX Bloom Comp");
            current = next;
        }

        // ── Lens Dirt (placeholder — requires texture asset) ──────
        // Lens dirt is a simple overlay texture; skip if no asset
        if (lensDirtIntensity > 0.01f) {
            // Future: bind lens dirt texture to BloomSampler
            // and run lens dirt composite pass
        }

        // ── Vignette (final) ──────────────────────────────────────
        if (vignetteStr > 0.001f) {
            runPass(encoder, vignettePipe, current, dummy, null, "VFX Vignette → FB");
        } else if (current != sceneView) {
            // No vignette, but we have processed content — output to main FB
            runPass(encoder, vignettePipe, current, dummy, null, "VFX Final → FB");
        }

        // If current == sceneView and no passes ran, nothing is drawn to FB
        // (the scene is already there from module rendering)
    }
}

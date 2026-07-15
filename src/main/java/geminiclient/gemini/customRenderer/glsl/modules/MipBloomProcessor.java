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
 * 5-Level Mip-Mapped Bloom processor.
 *
 * <h3>Algorithm</h3>
 * <pre>
 * [Scene FBO]
 *     │
 *     ├── Bright Pass (full res) ──→ Mip0 (1920×1080)
 *     │       │
 *     │       ├── Blur H0, Blur V0 ──→ Mip0_blurred
 *     │       ├── Downscale (×½) ────→ Mip1 (960×540)
 *     │       │       ├── Blur H1, Blur V1 ──→ Mip1_blurred
 *     │       │       ├── Downscale (×½) ────→ Mip2 (480×270)
 *     │       │       │       ├── Blur H2, Blur V2 ──→ Mip2_blurred
 *     │       │       │       ├── Downscale (×½) ────→ Mip3 (240×135)
 *     │       │       │       │       ├── Blur H3, Blur V3 ──→ Mip3_blurred
 *     │       │       │       │       ├── Downscale (×½) ────→ Mip4 (120×67)
 *     │       │       │       │       │       └── Blur H4, Blur V4 ──→ Mip4_blurred
 *     │       │       │       │       │
 *     │       │       │       │       └── Upsample + Add ──→ Mip3
 *     │       │       │       └── Upsample + Add ──→ Mip2
 *     │       │       └── Upsample + Add ──→ Mip1
 *     │       └── Upsample + Add ──→ Mip0
 *     │
 *     └── Composite (scene + mip0×0.5 + mip1×0.7 + mip2×1.0 + mip3×1.5 + mip4×2.0)
 *          ──→ [Main FB / Output Target]
 * </pre>
 *
 * <h3>Uniform layout (std140, 32 bytes = 2 × vec4)</h3>
 * <pre>
 * vec4 Params:   fbWidth, fbHeight, threshold, unused
 * vec4 Weights:  w0, w1, w2, w3  (mip level weights, w4=1-w0-w1-w2-w3)
 * </pre>
 */
public final class MipBloomProcessor {

    private MipBloomProcessor() {}

    // ── Shader path ────────────────────────────────────────────────

    private static final String BLOOM_VSH = "core/mip_bloom";
    private static final String BLOOM_FSH = "core/mip_bloom";

    // ── Mip level constants ─────────────────────────────────────────

    private static final int MIP_COUNT = 5;

    // ── Uniform layout ──────────────────────────────────────────────

    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4()  // Params:  fbWidth, fbHeight, threshold, 0
            .putVec4()  // Weights: w0, w1, w2, w3 (w4 implied)
            .get();

    // ── Texture targets ─────────────────────────────────────────────

    private static final TextureTarget[] mipBright = new TextureTarget[MIP_COUNT];  // bright-extracted per level
    private static final TextureTarget[] mipPing   = new TextureTarget[MIP_COUNT];  // ping-pong A per level
    private static final TextureTarget[] mipPong   = new TextureTarget[MIP_COUNT];  // ping-pong B per level

    // Full-res scene snapshot
    private static TextureTarget sceneCopy;

    // ── Uniform buffer ──────────────────────────────────────────────

    private static GpuBuffer uniforms;

    // ── Pipelines ───────────────────────────────────────────────────

    private static RenderPipeline brightPipe;
    private static RenderPipeline blurHPipe;
    private static RenderPipeline blurVPipe;
    private static RenderPipeline downsamplePipe;
    private static RenderPipeline upsamplePipe;
    private static RenderPipeline compositePipe;

    // ════════════════════════════════════════════════════════════════
    //  Initialization
    // ════════════════════════════════════════════════════════════════

    private static void ensureInit() {
        if (uniforms == null) {
            uniforms = RenderSystem.getDevice().createBuffer(
                    () -> "MipBloomUniforms",
                    GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
                    UNIFORM_SIZE);
        }
        if (brightPipe == null)     brightPipe     = buildPipe("mip_bloom_bright",   "MIP_BRIGHT");
        if (blurHPipe == null)      blurHPipe      = buildPipe("mip_bloom_blur_h",  "MIP_BLUR_H");
        if (blurVPipe == null)      blurVPipe      = buildPipe("mip_bloom_blur_v",  "MIP_BLUR_V");
        if (downsamplePipe == null) downsamplePipe = buildPipe("mip_bloom_down",    "MIP_DOWNSAMPLE");
        if (upsamplePipe == null)   upsamplePipe   = buildPipe("mip_bloom_up",      "MIP_UPSAMPLE");
        if (compositePipe == null)  compositePipe  = buildPipe("mip_bloom_comp",    "MIP_COMPOSITE");
    }

    private static RenderPipeline buildPipe(String loc, String define) {
        return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(getIdentifier("pipeline/" + loc))
                .withVertexShader(getIdentifier(BLOOM_VSH))
                .withFragmentShader(getIdentifier(BLOOM_FSH))
                .withShaderDefine(define)
                .withUniform("BloomUniforms", UniformType.UNIFORM_BUFFER)
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
        registry.accept(downsamplePipe);
        registry.accept(upsamplePipe);
        registry.accept(compositePipe);
    }

    // ── Resource management ─────────────────────────────────────────

    private static void ensureTargets(int w, int h) {
        if (sceneCopy == null) sceneCopy = new TextureTarget("MBScene", w, h, false);

        int mw = w, mh = h;
        for (int i = 0; i < MIP_COUNT; i++) {
            if (mipBright[i] == null) mipBright[i] = new TextureTarget("MBBright" + i, mw, mh, false);
            if (mipPing[i] == null)   mipPing[i]   = new TextureTarget("MBPing" + i, mw, mh, false);
            if (mipPong[i] == null)   mipPong[i]   = new TextureTarget("MBPong" + i, mw, mh, false);

            if (mipBright[i].width != mw || mipBright[i].height != mh) mipBright[i].resize(mw, mh);
            if (mipPing[i].width != mw || mipPing[i].height != mh)     mipPing[i].resize(mw, mh);
            if (mipPong[i].width != mw || mipPong[i].height != mh)     mipPong[i].resize(mw, mh);

            mw = Math.max(mw / 2, 1);
            mh = Math.max(mh / 2, 1);
        }

        if (sceneCopy.width != w || sceneCopy.height != h) sceneCopy.resize(w, h);
    }

    public static void destroy() {
        sceneCopy = null;
        for (int i = 0; i < MIP_COUNT; i++) {
            mipBright[i] = null;
            mipPing[i] = null;
            mipPong[i] = null;
        }
        if (uniforms != null) { uniforms.close(); uniforms = null; }
        brightPipe = blurHPipe = blurVPipe = downsamplePipe = upsamplePipe = compositePipe = null;
    }

    // ════════════════════════════════════════════════════════════════
    //  Per-pass helpers
    // ════════════════════════════════════════════════════════════════

    private static void runPass(CommandEncoder encoder, RenderPipeline pipe,
                                 GpuTextureView sceneSrc, GpuTextureView bloomSrc,
                                 GpuTextureView dest, String label) {
        try (RenderPass pass = encoder.createRenderPass(() -> label, dest, OptionalInt.empty())) {
            pass.setPipeline(pipe);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("BloomUniforms", uniforms);
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
     * Run the full 5-level mip bloom pipeline.
     *
     * @param threshold   luminance threshold for bright extraction (0.5–2.0)
     * @param toMainFb    if true, composite final result to the main framebuffer
     */
    public static void processFrame(float threshold, boolean toMainFb) {
        ensureInit();
        RenderTarget fb = mc.getMainRenderTarget();
        if (fb.getColorTexture() == null || fb.getColorTextureView() == null) return;

        int fbW = mc.getWindow().getWidth();
        int fbH = mc.getWindow().getHeight();
        ensureTargets(fbW, fbH);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        // ── Write uniforms ──────────────────────────────────────────
        try (GpuBuffer.MappedView view = encoder.mapBuffer(uniforms, false, true)) {
            Std140Builder b = Std140Builder.intoBuffer(view.data());
            b.putVec4(fbW, fbH, threshold, 0f);
            // Default weights: w0=0.5, w1=0.7, w2=1.0, w3=1.5 (w4=2.0 implied by sum)
            b.putVec4(0.5f, 0.7f, 1.0f, 1.5f);
        }

        // ══════════════════════════════════════════════════════════
        //  Copy scene
        // ══════════════════════════════════════════════════════════
        encoder.copyTextureToTexture(
                fb.getColorTexture(), sceneCopy.getColorTexture(),
                0, 0, 0, 0, 0, fbW, fbH);

        GpuTextureView sceneView = sceneCopy.getColorTextureView();

        // ══════════════════════════════════════════════════════════
        //  Mip 0: Bright pass + Blur H + Blur V (full res)
        // ══════════════════════════════════════════════════════════
        runPass(encoder, brightPipe, sceneView,
                mipPing[0].getColorTextureView(), mipBright[0].getColorTextureView(),
                "MB Bright0");
        runPass(encoder, blurHPipe, mipBright[0].getColorTextureView(),
                mipPing[0].getColorTextureView(), mipPong[0].getColorTextureView(),
                "MB BlurH0");
        runPass(encoder, blurVPipe, mipPong[0].getColorTextureView(),
                mipPing[0].getColorTextureView(), mipPing[0].getColorTextureView(),
                "MB BlurV0");
        // Result: mipPing[0] = blurred bloom level 0

        // ══════════════════════════════════════════════════════════
        //  Mip 1–4: Downscale → Blur H → Blur V
        // ══════════════════════════════════════════════════════════
        for (int i = 1; i < MIP_COUNT; i++) {
            // Downscale from previous level: hardware auto-scales when
            // dest texture is smaller than source
            encoder.copyTextureToTexture(
                    mipBright[i - 1].getColorTexture(),
                    mipBright[i].getColorTexture(),
                    0, 0, 0, 0, 0,
                    mipBright[i - 1].width, mipBright[i - 1].height);

            // Alternatively use downsample shader for filtered downscale
            // runPass(encoder, downsamplePipe, ...)

            // Blur H
            runPass(encoder, blurHPipe, mipBright[i].getColorTextureView(),
                    mipPing[i].getColorTextureView(), mipPong[i].getColorTextureView(),
                    "MB BlurH" + i);
            // Blur V
            runPass(encoder, blurVPipe, mipPong[i].getColorTextureView(),
                    mipPing[i].getColorTextureView(), mipPing[i].getColorTextureView(),
                    "MB BlurV" + i);
            // Result: mipPing[i] = blurred bloom level i
        }

        // ══════════════════════════════════════════════════════════
        //  Iterative upsample chain (coarse → fine)
        //
        //  Starting from the coarsest level (MIP_COUNT-1), upsample
        //  each level and add it to the next finer level.  This
        //  accumulates the full mip-pyramid bloom into mipPing[0].
        // ══════════════════════════════════════════════════════════

        for (int i = MIP_COUNT - 1; i > 0; i--) {
            // Upsample mipPing[i] (coarser) and add to mipPing[i-1] (finer)
            // BloomSampler = coarser level, SceneSampler = finer level
            // Output goes to the finer level's ping-pong
            GpuTextureView dst = (mipPing[i - 1] == mipPing[i - 1])
                    ? mipPong[i - 1].getColorTextureView()
                    : mipPing[i - 1].getColorTextureView();

            runPass(encoder, upsamplePipe,
                    mipPing[i - 1].getColorTextureView(),    // SceneSampler = finer (to add to)
                    mipPing[i].getColorTextureView(),         // BloomSampler = coarser (to upsample)
                    dst,
                    "MB Upsample" + i);

            // Swap: the result is now the new mipPing[i-1] content
            // (mipPing[i-1] gets updated for the next iteration)
            TextureTarget tmp = mipPing[i - 1];
            mipPing[i - 1] = (dst == mipPong[i - 1].getColorTextureView()) ? mipPong[i - 1] : mipPing[i - 1];
            // Restore mipPong[i-1] as the spare
            if (mipPing[i - 1] == mipPong[i - 1]) {
                mipPong[i - 1] = tmp;
            }
        }

        // Final result: mipPing[0] contains all mip levels combined

        // ══════════════════════════════════════════════════════════
        //  Composite: scene + combined bloom
        // ══════════════════════════════════════════════════════════
        if (toMainFb) {
            runPass(encoder, compositePipe, sceneView,
                    mipPing[0].getColorTextureView(), null,
                    "MB Composite → FB");
        }
    }

    // ── Public query ──────────────────────────────────────────────────

    /**
     * Returns the final combined bloom texture (all mip levels merged).
     * Only valid after {@link #processFrame} has been called.
     */
    public static GpuTextureView getBloomOutput() {
        if (mipPing[0] == null) return null;
        return mipPing[0].getColorTextureView();
    }
}

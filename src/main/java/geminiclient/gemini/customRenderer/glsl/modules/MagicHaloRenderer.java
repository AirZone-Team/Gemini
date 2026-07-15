package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * GPU-accelerated Magic Halo (魔法光环) renderer — 湮灭日食 variant.
 *
 * <h3>Visual design</h3>
 * <p>A dark sci-fi high-contrast halo rendered as a horizontal billboard
 * floating above the player's head. The fragment shader produces:</p>
 * <ul>
 *   <li>Central circular ring — structural backbone</li>
 *   <li>Inner reverse-rotating dashed rune ring — astrolabe motif</li>
 *   <li>Floating shards — noise-driven fractured crystal spikes replacing
 *       uniform triangular rays, giving an irregular piercing look</li>
 *   <li>Forked crown spikes — compound geometric tines at the top</li>
 *   <li>Void palette — deep purple → crimson → aurora silver</li>
 *   <li>Accretion-disk core glow — dense, throbbing singularity pulse</li>
 *   <li>Orbiting sparkles — dynamic circling star-points with trails</li>
 *   <li>Harsh broken-crystal edges with radiant aura</li>
 * </ul>
 *
 * <h3>Time flow</h3>
 * <p>High-precision animation time is embedded in
 * {@code DynamicTransforms.ModelOffset.x} (a {@code vec3} field that is
 * unused for geometry in this renderer).  The fragment shader aliases
 * {@code ModelOffset.x} as {@code u_time} via a {@code #define}.</p>
 *
 * <h3>Vertex encoding (POSITION_TEX_COLOR)</h3>
 * <ul>
 *   <li>Color.r — 1.0 (reserved)</li>
 *   <li>Color.g — spike count / 16.0 (normalised)</li>
 *   <li>Color.b — intensity multiplier</li>
 *   <li>Color.a — master alpha</li>
 * </ul>
 */
public final class MagicHaloRenderer {

    private MagicHaloRenderer() {}

    // ════════════════════════════════════════════════════════════════════
    //  Pipeline
    // ════════════════════════════════════════════════════════════════════

    private static final DepthStencilState HALO_DEPTH =
            new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -1.0F);

    private static final ColorTargetState HALO_BLEND = new ColorTargetState(new BlendFunction(
            SourceFactor.SRC_ALPHA, DestFactor.ONE,
            SourceFactor.ONE, DestFactor.ZERO));

    /**
     * Magic Halo pipeline — additive blend, depth-tested, horizontal billboard.
     * Time is passed via {@code DynamicTransforms.ModelOffset.x} (see class
     * javadoc), so no extra uniform block is needed.
     */
    public static final RenderPipeline HALO_PIPE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/magic_halo"))
            .withVertexShader(getIdentifier("core/magic_halo"))
            .withFragmentShader(getIdentifier("core/magic_halo"))
            // Must use TRIANGLES to match Tesselator's auto-generated triangle indices
            // (BufferBuilder.begin() still uses QUADS — it auto-generates 0-1-2 and 0-2-3)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLES)
            .withDepthStencilState(HALO_DEPTH)
            .withColorTargetState(HALO_BLEND)
            .withCull(false)
            .build();

    // ════════════════════════════════════════════════════════════════════
    //  Registration
    // ════════════════════════════════════════════════════════════════════

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(HALO_PIPE);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Drawing
    // ════════════════════════════════════════════════════════════════════

    /**
     * Draw the magic halo above the player's head.
     *
     * <p>Animation time is packed into {@code DynamicTransforms.ModelOffset.x}
     * — a standard std140 field that the {@code MATRICES_PROJECTION_SNIPPET}
     * declares but that is unused for geometry in this (manually-positioned)
     * billboard.  The fragment shader reads it as {@code u_time}.</p>
     *
     * @param poseStack  current pose stack (from {@code Render3DEvent})
     * @param x          world X of the halo center
     * @param y          world Y of the halo center
     * @param z          world Z of the halo center
     * @param radius     half-size of the billboard quad
     * @param time       elapsed animation time in seconds
     * @param spikeCount number of regular spikes (4–16)
     * @param intensity  brightness multiplier (0.0–2.0)
     * @param alpha      master transparency (0.0–1.0)
     */
    public static void draw(PoseStack poseStack,
                            double x, double y, double z,
                            float radius,
                            float time,
                            int spikeCount,
                            float intensity,
                            float alpha) {
        if (radius <= 0f || alpha < 0.001f || intensity < 0.001f) return;

        Camera camera = mc.getEntityRenderDispatcher().camera;
        float cx = (float) camera.position().x;
        float cy = (float) camera.position().y;
        float cz = (float) camera.position().z;

        // ── Apply VIEW_OFFSET_Z_LAYERING (same as all world-space RenderTypes) ─
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        RenderSystem.getProjectionType().applyLayeringTransform(modelViewStack, 1.0F);

        // ── Translate to halo position (camera-relative) ───────────
        poseStack.pushPose();
        poseStack.translate(x - cx, y - cy, z - cz);

        // 【关键修复 2】在 CPU 端烘焙顶点到相机空间
        Matrix4f poseMatrix = poseStack.last().pose();

        // ── Build mesh ─────────────────────────────────────────────
        float spikeNorm = clamp((float) spikeCount / 16.0f, 0.0f, 1.0f);
        int rgba = packColor(1.0f, spikeNorm, intensity, alpha); // R = 1.0

        // begin 使用 QUADS — 系统自动为 TRIANGLES 管线生成 0-1-2 和 0-2-3 索引
        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        buf.addVertex(poseMatrix, -radius, 0f, -radius).setUv(0f, 0f).setColor(rgba);
        buf.addVertex(poseMatrix, -radius, 0f,  radius).setUv(0f, 1f).setColor(rgba);
        buf.addVertex(poseMatrix,  radius, 0f,  radius).setUv(1f, 1f).setColor(rgba);
        buf.addVertex(poseMatrix,  radius, 0f, -radius).setUv(1f, 0f).setColor(rgba);

        MeshData mesh = buf.buildOrThrow();

        try {
            // ════════════════════════════════════════════════════════
            //  Upload vertex / index buffers
            // ════════════════════════════════════════════════════════
            GpuBuffer vertices = HALO_PIPE.getVertexFormat()
                    .uploadImmediateVertexBuffer(mesh.vertexBuffer());

            GpuBuffer indices;
            VertexFormat.IndexType indexType;
            if (mesh.indexBuffer() == null) {
                RenderSystem.AutoStorageIndexBuffer autoIndices =
                        RenderSystem.getSequentialBuffer(mesh.drawState().mode());
                indices = autoIndices.getBuffer(mesh.drawState().indexCount());
                indexType = autoIndices.type();
            } else {
                indices = HALO_PIPE.getVertexFormat()
                        .uploadImmediateIndexBuffer(mesh.indexBuffer());
                indexType = mesh.drawState().indexType();
            }

            // ════════════════════════════════════════════════════════
            //  Dynamic transforms — identity ModelView (CPU already baked)
            //  ModelOffset.x still carries animation time for the shader
            // ════════════════════════════════════════════════════════
            GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                    .writeTransform(
                            new Matrix4f(),                           // Identity — CPU already baked vertices into view space
                            new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),   // ColorModulator
                            new Vector3f(time, 0f, 0f),              // ModelOffset.x = u_time
                            new Matrix4f());                          // TextureMat (identity)

            // ════════════════════════════════════════════════════════
            //  Render target
            // ════════════════════════════════════════════════════════
            RenderTarget mainTarget = mc.getMainRenderTarget();
            GpuTextureView colorTexture = RenderSystem.outputColorTextureOverride != null
                    ? RenderSystem.outputColorTextureOverride
                    : mainTarget.getColorTextureView();
            GpuTextureView depthTexture = mainTarget.useDepth
                    ? (RenderSystem.outputDepthTextureOverride != null
                        ? RenderSystem.outputDepthTextureOverride
                        : mainTarget.getDepthTextureView())
                    : null;

            // ════════════════════════════════════════════════════════
            //  Render pass
            // ════════════════════════════════════════════════════════
            var encoder = RenderSystem.getDevice().createCommandEncoder();

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "MagicHalo",
                    colorTexture,
                    OptionalInt.empty(),
                    depthTexture,
                    OptionalDouble.empty())) {

                pass.setPipeline(HALO_PIPE);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", dynamicTransforms);

                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indices, indexType);
                pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1);
            }
        } finally {
            mesh.close();
            poseStack.popPose();
            modelViewStack.popMatrix();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════════

    private static int packColor(float r, float g, float b, float a) {
        int ir = (int) (clamp(r, 0f, 1f) * 255f);
        int ig = (int) (clamp(g, 0f, 1f) * 255f);
        int ib = (int) (clamp(b, 0f, 1f) * 255f);
        int ia = (int) (clamp(a, 0f, 1f) * 255f);
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}

package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import geminiclient.gemini.customRenderer.GeminiRenderPipelines;
import geminiclient.gemini.customRenderer.GeminiRenderTargets;
import geminiclient.gemini.customRenderer.GeminiTesselator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.DynamicGpuData;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * Renders the black hole pet: five world-space layers plus one frame pass.
 *
 * <p>Every layer except the post pass is real geometry placed with the pet's
 * own basis vectors, so the hole foreshortens, casts into the depth buffer and
 * keeps its orientation when the player spins — there is no card pinned to the
 * screen anywhere in here. The mesh each layer submits is only a (u,v) grid;
 * the vertex stage lifts it into a sphere, a disk or a jet cone from
 * {@code BHUniforms}, which is what lets the disk nod and precess without the
 * CPU ever touching a vertex.</p>
 *
 * <p>Layer order matters: the horizon writes depth, so it eats the far half of
 * the accretion disk and the back-going jet exactly as a real shadow would.
 * Everything after it adds light and writes no depth.</p>
 */
public final class BlackHolePetRenderer {

    /** Swirl vertex stage selector: full accretion disk. */
    private static final int MODE_DISK = 0;
    /** Swirl vertex stage selector: light-bent image of the disk's far rim. */
    private static final int MODE_ARC = 1;

    /** Complete look for one frame, in blocks and seconds. */
    public record Settings(
            float time,
            float rainbowSpeed,
            int palette,
            float spin,
            float flare,
            float centerX,
            float centerY,
            float centerZ,
            float horizonRadius,
            float innerRadius,
            float outerRadius,
            float bandThickness,
            float arcRadius,
            float arcSpan,
            float jetLength,
            float dustWidth,
            float noise,
            float turbulence,
            float redshift,
            float doppler,
            float opacity,
            float brightness,
            int hotColor,
            int coolColor,
            int tintColor,
            float[] basis,
            boolean horizon,
            boolean disk,
            boolean arc,
            boolean jets
    ) {}

    private static final DepthStencilState DEPTH_WRITE =
            new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true, 1f, 1f);
    private static final DepthStencilState DEPTH_READ =
            new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 1f, 1f);
    /**
     * Emissive layers add, and they add through rgb alone: every fragment
     * shader in this renderer writes alpha 0 so the destination alpha channel
     * is left exactly as the level renderer made it.
     */
    private static final ColorTargetState ADDITIVE = new ColorTargetState(BlendFunction.ADDITIVE);

    public static final RenderPipeline HORIZON_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/black_hole_horizon"))
            .withVertexShader(getIdentifier("core/black_hole_void"))
            .withFragmentShader(getIdentifier("core/black_hole_void"))
            .withBindGroupLayout(GeminiRenderPipelines.uniform("BHUniforms"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DEPTH_WRITE)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    public static final RenderPipeline SWIRL_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/black_hole_swirl"))
            .withVertexShader(getIdentifier("core/black_hole_swirl"))
            .withFragmentShader(getIdentifier("core/black_hole_swirl"))
            .withBindGroupLayout(GeminiRenderPipelines.uniform("BHUniforms"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DEPTH_READ)
            .withColorTargetState(ADDITIVE)
            .withCull(false)
            .build();

    public static final RenderPipeline JET_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/black_hole_jet"))
            .withVertexShader(getIdentifier("core/black_hole_jet"))
            .withFragmentShader(getIdentifier("core/black_hole_jet"))
            .withBindGroupLayout(GeminiRenderPipelines.uniform("BHUniforms"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DEPTH_READ)
            .withColorTargetState(ADDITIVE)
            .withCull(false)
            .build();

    public static final RenderPipeline DUST_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/black_hole_dust"))
            .withVertexShader(getIdentifier("core/black_hole_dust"))
            .withFragmentShader(getIdentifier("core/black_hole_dust"))
            .withBindGroupLayout(GeminiRenderPipelines.uniform("BHUniforms"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DEPTH_READ)
            .withColorTargetState(ADDITIVE)
            .withCull(false)
            .build();

    public static final RenderPipeline LENS_PIPE = RenderPipeline.builder(
                    RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(getIdentifier("pipeline/black_hole_lens"))
            .withVertexShader(getIdentifier("core/black_hole_post"))
            .withFragmentShader(getIdentifier("core/black_hole_post"))
            .withBindGroupLayout(GeminiRenderPipelines.uniformAndSamplers(
                    "BHPostUniforms", "SceneSampler"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4().putVec4().putVec4().putVec4().putVec4()
            .putVec4().putVec4().putVec4().putVec4().putVec4()
            .get();
    private static final int POST_UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4().putVec4().putVec4().putVec4()
            .get();

    private static GpuBuffer uniforms;
    private static GpuBuffer postUniforms;
    private static TextureTarget sceneCopy;

    private BlackHolePetRenderer() {}

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(HORIZON_PIPE);
        registry.accept(SWIRL_PIPE);
        registry.accept(JET_PIPE);
        registry.accept(DUST_PIPE);
        registry.accept(LENS_PIPE);
    }

    // ════════════════════════════════════════════════════════════════
    //  World-space layers
    // ════════════════════════════════════════════════════════════════

    public static void draw(PoseStack poseStack, Settings s, BlackHoleDust dust) {
        Matrix4f view = new Matrix4f(poseStack.last().pose());

        if (s.horizon()) drawGrid(HORIZON_PIPE, view, s, MODE_DISK, 28, 14);
        if (s.disk()) drawGrid(SWIRL_PIPE, view, s, MODE_DISK, 10, 96);
        if (s.arc()) drawGrid(SWIRL_PIPE, view, s, MODE_ARC, 5, 72);
        if (s.jets()) drawGrid(JET_PIPE, view, s, MODE_DISK, 20, 16);
        if (dust != null && dust.count > 0) drawDust(view, s, dust);
    }

    /**
     * Submit an (u,v) grid over [0,1]². The vertex stage does the placement, so
     * this is the entire geometry cost of a layer.
     */
    private static void drawGrid(RenderPipeline pipe, Matrix4f view, Settings s,
                                 int mode, int uCells, int vCells) {
        BufferBuilder buffer = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int j = 0; j < vCells; j++) {
            float v0 = (float) j / vCells;
            float v1 = (float) (j + 1) / vCells;
            for (int i = 0; i < uCells; i++) {
                float u0 = (float) i / uCells;
                float u1 = (float) (i + 1) / uCells;
                writeCorner(buffer, u0, v0);
                writeCorner(buffer, u1, v0);
                writeCorner(buffer, u1, v1);
                writeCorner(buffer, u0, v1);
            }
        }
        writeUniforms(mode, s);
        submit(buffer, pipe, view, "BlackHole " + (pipe == HORIZON_PIPE ? "horizon"
                : pipe == JET_PIPE ? "jets" : mode == MODE_ARC ? "arc" : "disk"));
    }

    private static void writeCorner(BufferBuilder buffer, float u, float v) {
        buffer.addVertex(0f, 0f, 0f).setUv(u, v).setColor(0xFFFFFFFF);
    }

    /**
     * Infalling matter. Each mote is stretched along the path it travelled since
     * the previous frame and ribboned towards the camera, so the streak lies on
     * the orbit it is actually flying.
     */
    private static void drawDust(Matrix4f view, Settings s, BlackHoleDust dust) {
        BufferBuilder buffer = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float cx = s.centerX(), cy = s.centerY(), cz = s.centerZ();
        float width = Math.max(s.dustWidth(), 0.0008f);
        int drawn = 0;

        for (int i = 0; i < dust.count; i++) {
            float hx = dust.x[i] + cx;
            float hy = dust.y[i] + cy;
            float hz = dust.z[i] + cz;
            // Both ends of the trail are offsets from the hole, so the delta is
            // the mote's travel alone; folding the centre in would point the
            // streak outwards from the pet instead of along its orbit.
            float dx = dust.x[i] - dust.previousX[i];
            float dy = dust.y[i] - dust.previousY[i];
            float dz = dust.z[i] - dust.previousZ[i];
            float travelled = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (travelled < 1e-6f) continue;

            // A mote's own orbit is only a few millimetres of travel per frame,
            // so amplify it into a visible trail — but never past a fraction of
            // the hole, which is what a hitched frame would otherwise draw.
            float stretch = Math.min(9.0f, 4.0f + dust.temperature[i] * 9.0f);
            float tail = Math.min(travelled * stretch, s.horizonRadius() * 2.2f);
            float scale = tail / Math.max(travelled, 1e-7f);
            float tailX = hx - dx * scale;
            float tailY = hy - dy * scale;
            float tailZ = hz - dz * scale;

            // Ribbon normal: perpendicular to both the flight direction and the
            // line of sight, which keeps the trail's width on screen while its
            // axis stays locked to the orbit.
            float toCamX = -hx, toCamY = -hy, toCamZ = -hz;
            float nx = dy * toCamZ - dz * toCamY;
            float ny = dz * toCamX - dx * toCamZ;
            float nz = dx * toCamY - dy * toCamX;
            float nLength = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nLength < 1e-7f) continue;
            float half = width * (0.35f + dust.temperature[i]);
            nx = nx / nLength * half;
            ny = ny / nLength * half;
            nz = nz / nLength * half;

            int color = packByte(dust.temperature[i], dust.glow[i]);
            buffer.addVertex(tailX - nx, tailY - ny, tailZ - nz).setUv(0f, 0f).setColor(color);
            buffer.addVertex(tailX + nx, tailY + ny, tailZ + nz).setUv(0f, 1f).setColor(color);
            buffer.addVertex(hx + nx, hy + ny, hz + nz).setUv(1f, 1f).setColor(color);
            buffer.addVertex(hx - nx, hy - ny, hz - nz).setUv(1f, 0f).setColor(color);
            drawn += 4;
        }

        if (drawn == 0) return;
        writeUniforms(MODE_DISK, s);
        submit(buffer, DUST_PIPE, view, "BlackHole dust");
    }

    private static void submit(BufferBuilder buffer, RenderPipeline pipe,
                               Matrix4f view, String label) {
        MeshData mesh = buffer.buildOrThrow();
        if (mesh.drawState().vertexCount() == 0) {
            mesh.close();
            return;
        }
        try {
            GpuBuffer vertices = GeminiTesselator.uploadVertexBuffer(
                    pipe.getVertexFormatBinding(0), mesh.vertexBuffer());
            GpuBuffer indices;
            IndexType indexType;
            if (mesh.indexBuffer() == null) {
                RenderSystem.AutoStorageIndexBuffer autoIndices =
                        RenderSystem.getSequentialBuffer(mesh.drawState().primitiveTopology());
                indices = autoIndices.getBuffer(mesh.drawState().indexCount());
                indexType = autoIndices.type();
            } else {
                indices = GeminiTesselator.uploadIndexBuffer(
                        pipe.getVertexFormatBinding(0), mesh.indexBuffer());
                indexType = mesh.drawState().indexType();
            }

            // The vertex stages build camera-relative world positions, so the
            // level's own view matrix has to arrive through DynamicTransforms
            // rather than being baked into the vertices.
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                    view, new Vector4f(1f, 1f, 1f, 1f), new Vector3f(), new Matrix4f());

            RenderTarget target = mc.gameRenderer.mainRenderTarget();
            GpuTextureView color = target.getColorTextureView();
            GpuTextureView depth = target.hasDepth() ? target.getDepthTextureView() : null;

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(
                    () -> label, color, Optional.empty(), depth, OptionalDouble.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(pipe));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setUniform("BHUniforms", uniforms);
                pass.setVertexBuffer(0, vertices.slice());
                pass.setIndexBuffer(indices, indexType);
                pass.drawIndexed(mesh.drawState().indexCount(), 1, 0, 0, 0);
            }
        } finally {
            mesh.close();
        }
    }

    private static void writeUniforms(int mode, Settings s) {
        ensureBuffers();
        float[] hot = rgb(s.hotColor());
        float[] cool = rgb(s.coolColor());
        float[] tint = rgb(s.tintColor());
        float[] b = s.basis();

        try (GpuBufferSlice.MappedView view = uniforms.map(false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec4(s.time(), mode, s.spin(), s.flare())
                    .putVec4(s.centerX(), s.centerY(), s.centerZ(), s.horizonRadius())
                    .putVec4(b[0], b[1], b[2], s.innerRadius())
                    .putVec4(b[3], b[4], b[5], s.outerRadius())
                    .putVec4(b[6], b[7], b[8], s.bandThickness())
                    .putVec4(s.noise(), s.turbulence(), s.jetLength(), s.redshift())
                    .putVec4(hot[0], hot[1], hot[2], s.opacity())
                    .putVec4(cool[0], cool[1], cool[2], s.brightness())
                    .putVec4(tint[0], tint[1], tint[2], s.doppler())
                    .putVec4(s.palette(), s.rainbowSpeed(), s.arcRadius(), s.arcSpan());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Frame pass: gravitational lensing
    // ════════════════════════════════════════════════════════════════

    /**
     * Bend the rendered frame around the hole. The disk has already been drawn
     * into that frame, so warping it here is what folds its far rim over the
     * shadow — the one part of the look that geometry on its own cannot do.
     *
     * @param worldX      the pet's position in world space
     * @param shadowBlocks apparent horizon radius, blocks
     * @param strength    0 disables the pass entirely
     */
    public static void processLens(double worldX, double worldY, double worldZ,
                                   float shadowBlocks, float strength, float ring,
                                   float swirl, float capture, float chromatic,
                                   int tintColor) {
        if (strength <= 0.001f) return;
        Minecraft minecraft = mc;
        if (minecraft == null || minecraft.gameRenderer == null) return;

        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        if (target.getColorTexture() == null || target.getColorTextureView() == null) return;

        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width <= 0 || height <= 0) return;

        var camera = minecraft.gameRenderer.mainCamera();
        if (camera == null) return;

        var eye = camera.position();
        float rx = (float) (worldX - eye.x);
        float ry = (float) (worldY - eye.y);
        float rz = (float) (worldZ - eye.z);

        var rotation = camera.rotation();
        Vector3f forward = rotation.transform(new Vector3f(0, 0, -1));
        Vector3f up = rotation.transform(new Vector3f(0, 1, 0));
        Vector3f right = rotation.transform(new Vector3f(1, 0, 0));

        float viewX = rx * right.x + ry * right.y + rz * right.z;
        float viewY = rx * up.x + ry * up.y + rz * up.z;
        float viewZ = rx * forward.x + ry * forward.y + rz * forward.z;
        if (viewZ <= 0.05f) return;   // behind us: nothing to bend

        float tanHalfFov = (float) Math.tan(Math.toRadians(camera.getFov()) * 0.5);
        float aspect = (float) width / (float) height;
        // Framebuffer UV runs the same way as NDC here (y up), which is how the
        // scene copy is sampled; a flipped centre put the lens below the hole
        // whenever the camera looked up.
        float centreU = 0.5f + viewX / (2.0f * viewZ * aspect * tanHalfFov);
        float centreV = 0.5f + viewY / (2.0f * viewZ * tanHalfFov);
        if (centreU < -0.4f || centreU > 1.4f || centreV < -0.4f || centreV > 1.4f) return;

        // Height-normalised UV radius, then converted into the width-normalised
        // metric the lens pass measures distances in.
        float radius = (shadowBlocks / viewZ) / tanHalfFov * 0.5f * ((float) height / width);
        radius = Math.clamp(radius, 0.0015f, 1.2f);

        ensureBuffers();
        if (sceneCopy == null) {
            sceneCopy = GeminiRenderTargets.colorTarget("BlackHoleScene", width, height, false);
        } else if (sceneCopy.width != width || sceneCopy.height != height) {
            sceneCopy.resize(width, height);
        }

        float[] tint = rgb(tintColor);
        try (GpuBufferSlice.MappedView mapped = postUniforms.map(false, true)) {
            Std140Builder.intoBuffer(mapped.data())
                    .putVec4(width, height, System.currentTimeMillis() / 1000f, 0f)
                    .putVec4(centreU, centreV, radius, strength)
                    .putVec4(ring, swirl, capture, chromatic)
                    .putVec4(tint[0], tint[1], tint[2], 0f);
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(target.getColorTexture(), sceneCopy.getColorTexture(),
                0, 0, 0, 0, 0, width, height);
        try (RenderPass pass = encoder.createRenderPass(
                () -> "BlackHole lensing", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(LENS_PIPE));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("BHPostUniforms", postUniforms);
            pass.setUniform("SceneSampler", sceneCopy.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
    }

    // ════════════════════════════════════════════════════════════════

    private static void ensureBuffers() {
        if (uniforms == null) {
            uniforms = RenderSystem.getDevice().createBuffer(
                    () -> "Gemini BlackHolePet Uniforms",
                    GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
                    UNIFORM_SIZE);
        }
        if (postUniforms == null) {
            postUniforms = RenderSystem.getDevice().createBuffer(
                    () -> "Gemini BlackHolePet Lens Uniforms",
                    GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
                    POST_UNIFORM_SIZE);
        }
    }

    /** Temperature and brightness ride the byte channels as bounded values. */
    private static int packByte(float temperature, float glow) {
        int t = Math.round(Math.clamp(temperature, 0f, 1f) * 255f);
        int g = Math.round(Math.clamp(glow, 0f, 1f) * 255f);
        return (g << 24) | (t << 16);
    }

    private static float[] rgb(int argb) {
        return new float[]{
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f
        };
    }
}

package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.IndexType;

import geminiclient.gemini.customRenderer.GeminiTesselator;

import geminiclient.gemini.customRenderer.GeminiRenderPipelines;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import org.joml.*;

import java.lang.Math;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Optional;
import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;
import com.mojang.renderpearl.api.vertex.VertexFormat;

/**
 * GPU renderer for the instanced particle sigils.
 *
 * <p>Each particle is one camera-facing quad whose in-plane roll is baked into
 * the basis, so the shader never has to decode an angle. Per-particle
 * configuration and the shared material block ride as two 32-bit words split
 * across the four 16-bit components of UV1/UV2 and forwarded as flat integer
 * varyings — the same contract {@code JumpCircleRenderer} uses. A shape id sent
 * through a byte colour channel quantises to 1/255 and picks up its neighbour's
 * value from interpolation, which is how the previous version selected shapes.</p>
 */
public final class InstancedParticleRenderer {

    private static final VertexFormat PARTICLE_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("UV1", GpuFormat.RG16_SINT)
            .addAttribute("UV2", GpuFormat.RG16_SINT)
            .build();

    /** Everything the fragment shader needs that is the same for all particles. */
    public record Settings(
            int colorMode,
            int ornament,
            int detail,
            int quality,
            float thickness,
            float glow,
            float clarity,
            float brightness,
            float opacity,
            float dynamics,
            float accent,
            boolean orbit
    ) {}

    // ── Pipeline ─────────────────────────────────────────────────

    private static final DepthStencilState PARTICLE_DEPTH =
            new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 1.0F, 1.0F);

    private static final ColorTargetState PARTICLE_BLEND = new ColorTargetState(new BlendFunction(
            BlendFactor.SRC_ALPHA, BlendFactor.ONE,
            BlendFactor.ONE, BlendFactor.ZERO));

    public static final RenderPipeline INSTANCED_PARTICLE_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/particle_instanced"))
            .withVertexShader(getIdentifier("core/particle_instanced"))
            .withFragmentShader(getIdentifier("core/particle_instanced"))
            .withVertexBinding(0, PARTICLE_FORMAT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(PARTICLE_DEPTH)
            .withColorTargetState(PARTICLE_BLEND)
            .withCull(false)
            .build();

    // ── Registration ──────────────────────────────────────────────

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(INSTANCED_PARTICLE_PIPE);
    }

    // ══════════════════════════════════════════════════════════════
    //  Drawing
    // ══════════════════════════════════════════════════════════════

    private static final Vector3f CAM_UP    = new Vector3f();
    private static final Vector3f CAM_RIGHT = new Vector3f();

    private static void updateCameraVectors() {
        var cam = mc.getEntityRenderDispatcher().camera;
        var rot = cam.rotation();
        CAM_UP.set(0, 1, 0);
        rot.transform(CAM_UP);
        CAM_RIGHT.set(1, 0, 0);
        rot.transform(CAM_RIGHT);
    }

    public static final int MAX_PARTICLES = 6000;

    public static void draw(PoseStack poseStack,
                            List<ParticleData> particles,
                            Settings settings) {
        if (particles.isEmpty()) return;

        updateCameraVectors();
        var cam = mc.getEntityRenderDispatcher().camera;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        var vm = poseStack.last().pose();

        int materialBits = packMaterialBits(settings);

        var buf = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, PARTICLE_FORMAT);

        int drawn = 0;
        for (ParticleData p : particles) {
            if (!p.alive) continue;
            if (drawn >= MAX_PARTICLES) break;

            float progress = p.progress();
            float halfSize = p.size * 0.5f;
            float rx = p.x - cx;
            float ry = p.y - cy;
            float rz = p.z - cz;

            // Roll the billboard about its own normal. Doing it here keeps the
            // angle exact; the shader's shape fields stay isotropic.
            float cos = (float) Math.cos(p.rotation);
            float sin = (float) Math.sin(p.rotation);
            float arx = (CAM_RIGHT.x * cos + CAM_UP.x * sin) * halfSize;
            float ary = (CAM_RIGHT.y * cos + CAM_UP.y * sin) * halfSize;
            float arz = (CAM_RIGHT.z * cos + CAM_UP.z * sin) * halfSize;
            float aux = (CAM_UP.x * cos - CAM_RIGHT.x * sin) * halfSize;
            float auy = (CAM_UP.y * cos - CAM_RIGHT.y * sin) * halfSize;
            float auz = (CAM_UP.z * cos - CAM_RIGHT.z * sin) * halfSize;

            int argb = packColor(p.r, p.g, p.b, progress);
            int styleBits = packStyleBits(p, settings);
            int lowStyle = styleBits & 0xFFFF;
            int highStyle = styleBits >>> 16 & 0xFFFF;
            int lowMaterial = materialBits & 0xFFFF;
            int highMaterial = materialBits >>> 16 & 0xFFFF;

            addVertex(buf, vm, argb, lowStyle, highStyle, lowMaterial, highMaterial,
                    rx - arx - aux, ry - ary - auy, rz - arz - auz, 0f, 0f);
            addVertex(buf, vm, argb, lowStyle, highStyle, lowMaterial, highMaterial,
                    rx - arx + aux, ry - ary + auy, rz - arz + auz, 0f, 1f);
            addVertex(buf, vm, argb, lowStyle, highStyle, lowMaterial, highMaterial,
                    rx + arx + aux, ry + ary + auy, rz + arz + auz, 1f, 1f);
            addVertex(buf, vm, argb, lowStyle, highStyle, lowMaterial, highMaterial,
                    rx + arx - aux, ry + ary - auy, rz + arz - auz, 1f, 0f);

            drawn++;
        }

        if (drawn == 0) return;

        MeshData mesh = buf.buildOrThrow();
        if (mesh.drawState().vertexCount() == 0) { mesh.close(); return; }

        drawMesh(mesh, System.currentTimeMillis() / 1000f);
    }

    private static void addVertex(BufferBuilder buffer, Matrix4f matrix, int argb,
                                  int lowStyle, int highStyle,
                                  int lowMaterial, int highMaterial,
                                  float x, float y, float z, float u, float v) {
        buffer.addVertex(matrix, x, y, z)
                .setColor(argb)
                .setUv(u, v)
                .setUv1(lowStyle, highStyle)
                .setUv2(lowMaterial, highMaterial);
    }

    // ══════════════════════════════════════════════════════════════
    //  Manual mesh draw
    // ══════════════════════════════════════════════════════════════

    private static void drawMesh(MeshData mesh, float time) {
        try {
            var vertices = GeminiTesselator.uploadVertexBuffer(INSTANCED_PARTICLE_PIPE.getVertexFormatBinding(0), mesh.vertexBuffer());

            GpuBuffer indices;
            IndexType indexType;
            if (mesh.indexBuffer() == null) {
                var autoIndices = RenderSystem.getSequentialBuffer(mesh.drawState().primitiveTopology());
                indices = autoIndices.getBuffer(mesh.drawState().indexCount());
                indexType = autoIndices.type();
            } else {
                indices = GeminiTesselator.uploadIndexBuffer(INSTANCED_PARTICLE_PIPE.getVertexFormatBinding(0), mesh.indexBuffer());
                indexType = mesh.drawState().indexType();
            }

            var dynamicTransforms = RenderSystem.getDynamicUniforms()
                    .writeTransform(
                            new Matrix4f(),
                            new Vector4f(1f, 1f, 1f, 1f),
                            new Vector3f(time, 0f, 0f),       // ModelOffset.x = time
                            new Matrix4f());

            var mainTarget = mc.gameRenderer.mainRenderTarget();
            var colorTexture = mainTarget.getColorTextureView();
            var depthTexture = mainTarget.hasDepth() ? mainTarget.getDepthTextureView() : null;

            var encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "InstancedParticle",
                    colorTexture,
                    Optional.empty(),
                    depthTexture,
                    OptionalDouble.empty())) {

                pass.setPipeline(RenderSystem.getCompiledPipeline(INSTANCED_PARTICLE_PIPE));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", dynamicTransforms);

                pass.setVertexBuffer(0, vertices.slice());
                pass.setIndexBuffer(indices, indexType);
                pass.drawIndexed(mesh.drawState().indexCount(), 1, 0, 0, 0);
            }
        } finally {
            mesh.close();
        }
    }

    // ── Bit packing ───────────────────────────────────────────────

    /** Layout read by particle_instanced.frag.slang; bits 16..25 are the seed. */
    static int packStyleBits(ParticleData p, Settings settings) {
        int bits = p.type & 0x7;
        bits |= (settings.colorMode() & 0x3) << 3;
        bits |= (settings.ornament() & 0x7) << 5;
        bits |= (settings.detail() & 0x3) << 8;
        bits |= (p.variant & 0x7) << 10;
        bits |= ((p.echoCount - 1) & 0x3) << 13;
        if (p.burst) bits |= 1 << 15;
        bits |= (int) (clamp01(p.seed) * 1023f) << 16;
        return bits;
    }

    static int packMaterialBits(Settings s) {
        int bits = quantize(s.thickness(), 0.15f, 2.5f);
        bits |= quantize(s.glow(), 0f, 2.5f) << 4;
        bits |= quantize(s.clarity(), 0.4f, 2.2f) << 8;
        bits |= quantize(s.brightness(), 0.25f, 2.5f) << 12;
        bits |= quantize(s.opacity(), 0.05f, 1f, 7) << 16;
        bits |= quantize(s.dynamics(), 0f, 2f) << 19;
        bits |= quantize(s.accent(), 0f, 1f, 7) << 23;
        bits |= (s.quality() & 0x3) << 26;
        if (s.orbit()) bits |= 1 << 28;
        return bits;
    }

    private static int quantize(float value, float min, float max) {
        return Math.round(clamp01((value - min) / (max - min)) * 15f);
    }

    private static int quantize(float value, float min, float max, int levels) {
        return Math.round(clamp01((value - min) / (max - min)) * levels);
    }
    private static int packColor(float r, float g, float b, float a) {
        int ir = (int) (clamp01(r) * 255f);
        int ig = (int) (clamp01(g) * 255f);
        int ib = (int) (clamp01(b) * 255f);
        int ia = (int) (clamp01(a) * 255f);
        return (ia << 24) | (ib << 16) | (ig << 8) | ir;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}

package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * Layered procedural renderer for {@link SweepAttackInstance}. All artistic
 * controls are supplied per draw through one std140 block, allowing presets
 * and live customization without rebuilding shader pipelines.
 */
public final class SweepAttackRenderer {

    private SweepAttackRenderer() {}

    public record Config(
            int style,
            int colorMode,
            int quality,
            int layers,
            int echoes,
            int ringCount,
            int speedLineCount,
            boolean arc,
            boolean speedLines,
            boolean particles,
            boolean lightning,
            boolean ring,
            boolean coreBurst,
            float intensity,
            float opacity,
            float radius,
            float thickness,
            float verticalLift,
            float glow,
            float noise,
            float flowSpeed,
            float echoSpacing,
            float ringScale,
            float ringThickness,
            float lineLength,
            float lineWidth,
            float lightningWidth,
            float primaryR,
            float primaryG,
            float primaryB,
            float accentR,
            float accentG,
            float accentB,
            float coreR,
            float coreG,
            float coreB
    ) {}

    private static final DepthStencilState DEPTH_WRITE =
            new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, -1f, -1f);
    private static final DepthStencilState DEPTH_NO_WRITE =
            new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1f, -1f);
    private static final ColorTargetState ADDITIVE_BLEND = new ColorTargetState(new BlendFunction(
            SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ZERO));
    private static final ColorTargetState TRANSLUCENT_BLEND =
            new ColorTargetState(BlendFunction.TRANSLUCENT);

    public static final RenderPipeline SWEEP_ARC_PIPE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/sweep_arc"))
            .withVertexShader(getIdentifier("core/sweep_arc"))
            .withFragmentShader(getIdentifier("core/sweep_arc"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withUniform("SweepUniforms", UniformType.UNIFORM_BUFFER)
            .withDepthStencilState(DEPTH_WRITE)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    public static final RenderPipeline SWEEP_PARTICLE_PIPE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/sweep_particle"))
            .withVertexShader(getIdentifier("core/sweep_particle"))
            .withFragmentShader(getIdentifier("core/sweep_particle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withUniform("SweepUniforms", UniformType.UNIFORM_BUFFER)
            .withDepthStencilState(DEPTH_WRITE)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    public static final RenderPipeline SWEEP_RING_PIPE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/sweep_ring"))
            .withVertexShader(getIdentifier("core/sweep_arc"))
            .withFragmentShader(getIdentifier("core/sweep_arc"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withUniform("SweepUniforms", UniformType.UNIFORM_BUFFER)
            .withDepthStencilState(DEPTH_NO_WRITE)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    public static final RenderPipeline SWEEP_POST_PIPE = RenderPipeline.builder(
                    RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(getIdentifier("pipeline/sweep_post"))
            .withVertexShader(getIdentifier("core/sweep_post"))
            .withFragmentShader(getIdentifier("core/sweep_post"))
            .withUniform("SweepPostUniforms", UniformType.UNIFORM_BUFFER)
            .withSampler("SceneSampler")
            .withColorTargetState(TRANSLUCENT_BLEND)
            .withCull(false)
            .build();

    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4().putVec4().putVec4().putVec4()
            .putVec4().putVec4().putVec4().putVec4()
            .get();
    private static final int POST_UNIFORM_SIZE =
            new Std140SizeCalculator().putVec4().putVec4().putVec4().get();

    private static GpuBuffer uniforms;
    private static GpuBuffer postUniforms;
    private static TextureTarget sceneCopy;

    private static final Vector3f CAM_UP = new Vector3f();
    private static final Vector3f CAM_RIGHT = new Vector3f();

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(SWEEP_ARC_PIPE);
        registry.accept(SWEEP_PARTICLE_PIPE);
        registry.accept(SWEEP_RING_PIPE);
        registry.accept(SWEEP_POST_PIPE);
    }

    private static void ensureInit() {
        if (uniforms == null) {
            uniforms = RenderSystem.getDevice().createBuffer(
                    () -> "SweepUniforms",
                    GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
                    UNIFORM_SIZE);
        }
        if (postUniforms == null) {
            postUniforms = RenderSystem.getDevice().createBuffer(
                    () -> "SweepPostUniforms",
                    GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
                    POST_UNIFORM_SIZE);
        }
    }

    private static void updateCameraVectors() {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        var rotation = camera.rotation();
        CAM_UP.set(0f, 1f, 0f);
        rotation.transform(CAM_UP);
        CAM_RIGHT.set(1f, 0f, 0f);
        rotation.transform(CAM_RIGHT);
    }

    public static void draw(PoseStack poseStack, SweepAttackInstance inst,
                            long nowMs, Config config) {
        ensureInit();
        updateCameraVectors();

        float sweep = inst.sweepProgress(nowMs);
        float effect = inst.effectProgress(nowMs);
        float particleAlpha = inst.particleAlpha(nowMs);
        float ringProgress = inst.ringProgress(nowMs);
        float ringAlpha = inst.ringAlpha(nowMs);
        float lightningAlpha = inst.lightningAlpha(nowMs);
        float arcAlpha = inst.arcAlpha(nowMs);
        float burstAlpha = inst.burstAlpha(nowMs);

        writeUniforms(inst, nowMs, sweep, effect, config);

        if (config.arc()) drawArc(poseStack, inst, sweep, arcAlpha, config);
        if (config.speedLines()) drawSpeedLines(poseStack, inst, sweep, arcAlpha, config);
        if (config.particles()) drawParticles(poseStack, inst, particleAlpha, config);
        if (config.lightning()) drawLightning(poseStack, inst, lightningAlpha, config);
        if (config.ring()) drawPhotonRings(poseStack, inst, ringProgress, ringAlpha, config);
        if (config.coreBurst()) drawCoreBurst(poseStack, inst, effect, burstAlpha, config);
    }

    private static void writeUniforms(SweepAttackInstance inst, long nowMs,
                                      float sweep, float effect, Config config) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(uniforms, false, true)) {
            Std140Builder builder = Std140Builder.intoBuffer(view.data());
            builder.putVec4(nowMs / 1000f, sweep, effect, config.intensity());
            builder.putVec4(config.radius(), config.thickness(),
                    config.verticalLift(), config.glow());
            builder.putVec4(config.style(), config.colorMode(),
                    config.layers(), config.noise());
            builder.putVec4(config.flowSpeed(), config.echoSpacing(),
                    config.ringThickness(), config.ringCount());
            builder.putVec4(config.primaryR(), config.primaryG(),
                    config.primaryB(), config.opacity());
            builder.putVec4(config.accentR(), config.accentG(), config.accentB(), 0f);
            builder.putVec4(config.coreR(), config.coreG(), config.coreB(), 0f);
            builder.putVec4(config.ringScale(), config.lineLength(),
                    config.lineWidth(), inst.seed);
        }
    }

    private static void drawArc(PoseStack poseStack, SweepAttackInstance inst,
                                float sweepProgress, float arcAlpha, Config config) {
        if (sweepProgress < 0.001f || arcAlpha < 0.004f) return;
        Camera camera = mc.getEntityRenderDispatcher().camera;
        float cameraX = (float) camera.position().x;
        float cameraY = (float) camera.position().y;
        float cameraZ = (float) camera.position().z;
        Matrix4f matrix = poseStack.last().pose();
        int segments = switch (config.quality()) {
            case 0 -> 32;
            case 1 -> 48;
            case 3 -> 96;
            default -> 72;
        };

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int echoCount = Math.min(Math.max(config.echoes(), 0), 5);
        for (int echo = echoCount; echo >= 0; echo--) {
            float echoProgress = clamp01(sweepProgress - echo * config.echoSpacing());
            if (echoProgress < 0.002f) continue;
            float echoFade = echo == 0 ? 1f : (float) Math.pow(0.62f, echo);
            float radius = config.radius() * (1f - echo * 0.035f);
            float innerRadius = radius * 0.42f;
            float outerRadius = radius * 1.22f;
            float endAngle = inst.arcStart + (inst.arcEnd - inst.arcStart) * echoProgress;

            for (int i = 0; i < segments; i++) {
                float t0 = (float) i / segments;
                float t1 = (float) (i + 1) / segments;
                float angle0 = inst.arcStart + (endAngle - inst.arcStart) * t0;
                float angle1 = inst.arcStart + (endAngle - inst.arcStart) * t1;
                float lift0 = config.verticalLift() * (float) Math.sin(t0 * Math.PI);
                float lift1 = config.verticalLift() * (float) Math.sin(t1 * Math.PI);
                int color0 = packColor(0.55f + 0.45f * (float) Math.sin(t0 * Math.PI),
                        0f, echo / 5f, config.intensity() * arcAlpha * echoFade);
                int color1 = packColor(0.55f + 0.45f * (float) Math.sin(t1 * Math.PI),
                        0f, echo / 5f, config.intensity() * arcAlpha * echoFade);

                addArcVertex(buffer, matrix, inst, angle0, innerRadius, lift0,
                        cameraX, cameraY, cameraZ, t0, 0f, color0);
                addArcVertex(buffer, matrix, inst, angle0, outerRadius, lift0,
                        cameraX, cameraY, cameraZ, t0, 1f, color0);
                addArcVertex(buffer, matrix, inst, angle1, outerRadius, lift1,
                        cameraX, cameraY, cameraZ, t1, 1f, color1);
                addArcVertex(buffer, matrix, inst, angle1, innerRadius, lift1,
                        cameraX, cameraY, cameraZ, t1, 0f, color1);
            }
        }

        drawBuiltMesh(buffer, SWEEP_ARC_PIPE, nowSeconds());
    }

    private static void addArcVertex(BufferBuilder buffer, Matrix4f matrix,
                                     SweepAttackInstance inst, float angle, float radius,
                                     float lift, float cameraX, float cameraY, float cameraZ,
                                     float u, float v, int color) {
        buffer.addVertex(matrix,
                        (float) (inst.x + Math.cos(angle) * radius) - cameraX,
                        (float) inst.y + lift - cameraY,
                        (float) (inst.z + Math.sin(angle) * radius) - cameraZ)
                .setUv(u, v)
                .setColor(color);
    }

    private static void drawSpeedLines(PoseStack poseStack, SweepAttackInstance inst,
                                       float sweepProgress, float arcAlpha, Config config) {
        int count = Math.min(Math.max(config.speedLineCount(), 0), 64);
        float alpha = sweepProgress * arcAlpha * config.intensity() * 0.72f;
        if (count == 0 || alpha < 0.004f) return;

        Camera camera = mc.getEntityRenderDispatcher().camera;
        float cameraX = (float) camera.position().x;
        float cameraY = (float) camera.position().y;
        float cameraZ = (float) camera.position().z;
        Matrix4f matrix = poseStack.last().pose();
        float range = positiveAngleRange(inst.arcStart, inst.arcEnd);
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < count; i++) {
            float t = (i + 0.35f) / count;
            float jitter = (float) Math.sin(inst.seed * 1.7f + i * 12.9898f) * 0.018f;
            float angle = inst.arcStart + range * clamp01(t + jitter);
            float radialX = (float) Math.cos(angle);
            float radialZ = (float) Math.sin(angle);
            float perpendicularX = -radialZ;
            float perpendicularZ = radialX;
            float startRadius = config.radius() * (0.62f + 0.2f * pseudo(i, inst.seed));
            float length = config.radius() * config.lineLength()
                    * (0.55f + 0.7f * pseudo(i + 17, inst.seed));
            float endRadius = startRadius + length;
            float width = config.lineWidth() * (0.55f + pseudo(i + 41, inst.seed));
            float y = (float) inst.y
                    + config.verticalLift() * (float) Math.sin(t * Math.PI);
            float startX = (float) inst.x + radialX * startRadius;
            float startZ = (float) inst.z + radialZ * startRadius;
            float endX = (float) inst.x + radialX * endRadius;
            float endZ = (float) inst.z + radialZ * endRadius;
            int color = packColor(pseudo(i + 9, inst.seed), 1f, 0f,
                    alpha * (0.45f + 0.55f * pseudo(i + 3, inst.seed)));

            buffer.addVertex(matrix, startX - perpendicularX * width - cameraX,
                            y - cameraY, startZ - perpendicularZ * width - cameraZ)
                    .setUv(0f, -1f).setColor(color);
            buffer.addVertex(matrix, startX + perpendicularX * width - cameraX,
                            y - cameraY, startZ + perpendicularZ * width - cameraZ)
                    .setUv(0f, 1f).setColor(color);
            buffer.addVertex(matrix, endX + perpendicularX * width - cameraX,
                            y - cameraY, endZ + perpendicularZ * width - cameraZ)
                    .setUv(1f, 1f).setColor(color);
            buffer.addVertex(matrix, endX - perpendicularX * width - cameraX,
                            y - cameraY, endZ - perpendicularZ * width - cameraZ)
                    .setUv(1f, -1f).setColor(color);
        }
        drawBuiltMesh(buffer, SWEEP_PARTICLE_PIPE, nowSeconds());
    }

    private static void drawParticles(PoseStack poseStack, SweepAttackInstance inst,
                                      float particleAlpha, Config config) {
        if (particleAlpha < 0.004f || inst.particleCount == 0) return;
        boolean hasVisibleParticle = false;
        for (int i = 0; i < inst.particleCount; i++) {
            if (inst.particleLife[i] > 0f) {
                hasVisibleParticle = true;
                break;
            }
        }
        if (!hasVisibleParticle) return;
        Camera camera = mc.getEntityRenderDispatcher().camera;
        float cameraX = (float) camera.position().x;
        float cameraY = (float) camera.position().y;
        float cameraZ = (float) camera.position().z;
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int drawn = 0;

        for (int i = 0; i < inst.particleCount; i++) {
            if (inst.particleLife[i] <= 0f) continue;
            float life = inst.particleLife[i] / Math.max(inst.particleMaxLife[i], 0.001f);
            float alpha = life * life * particleAlpha * config.intensity();
            if (alpha < 0.004f) continue;
            float halfSize = inst.particleSize[i] * 0.5f;
            float x = inst.particleX[i] - cameraX;
            float y = inst.particleY[i] - cameraY;
            float z = inst.particleZ[i] - cameraZ;
            float rightX = CAM_RIGHT.x * halfSize;
            float rightY = CAM_RIGHT.y * halfSize;
            float rightZ = CAM_RIGHT.z * halfSize;
            float upX = CAM_UP.x * halfSize;
            float upY = CAM_UP.y * halfSize;
            float upZ = CAM_UP.z * halfSize;
            int color = packColor(life, 0f, pseudo(i, inst.seed), alpha);

            buffer.addVertex(matrix, x - rightX - upX, y - rightY - upY, z - rightZ - upZ)
                    .setUv(0f, 0f).setColor(color);
            buffer.addVertex(matrix, x - rightX + upX, y - rightY + upY, z - rightZ + upZ)
                    .setUv(0f, 1f).setColor(color);
            buffer.addVertex(matrix, x + rightX + upX, y + rightY + upY, z + rightZ + upZ)
                    .setUv(1f, 1f).setColor(color);
            buffer.addVertex(matrix, x + rightX - upX, y + rightY - upY, z + rightZ - upZ)
                    .setUv(1f, 0f).setColor(color);
            drawn++;
        }
        if (drawn > 0) drawBuiltMesh(buffer, SWEEP_PARTICLE_PIPE, nowSeconds());
    }

    private static void drawLightning(PoseStack poseStack, SweepAttackInstance inst,
                                      float lightningAlpha, Config config) {
        if (lightningAlpha < 0.004f || inst.boltCount == 0) return;
        Camera camera = mc.getEntityRenderDispatcher().camera;
        float cameraX = (float) camera.position().x;
        float cameraY = (float) camera.position().y;
        float cameraZ = (float) camera.position().z;
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int segmentsDrawn = 0;

        for (int bolt = 0; bolt < inst.boltCount; bolt++) {
            for (int segment = 0; segment < inst.boltSegments[bolt] - 1; segment++) {
                float x0 = inst.boltX[bolt][segment];
                float y0 = inst.boltY[bolt][segment];
                float z0 = inst.boltZ[bolt][segment];
                float x1 = inst.boltX[bolt][segment + 1];
                float y1 = inst.boltY[bolt][segment + 1];
                float z1 = inst.boltZ[bolt][segment + 1];
                float dx = x1 - x0;
                float dy = y1 - y0;
                float dz = z1 - z0;
                float toCameraX = cameraX - (x0 + x1) * 0.5f;
                float toCameraY = cameraY - (y0 + y1) * 0.5f;
                float toCameraZ = cameraZ - (z0 + z1) * 0.5f;
                float normalX = dy * toCameraZ - dz * toCameraY;
                float normalY = dz * toCameraX - dx * toCameraZ;
                float normalZ = dx * toCameraY - dy * toCameraX;
                float normalLength = (float) Math.sqrt(
                        normalX * normalX + normalY * normalY + normalZ * normalZ);
                if (normalLength < 0.0001f) continue;
                float width = config.lightningWidth()
                        * (0.7f + 0.5f * pseudo(bolt * 11 + segment, inst.seed));
                normalX = normalX / normalLength * width;
                normalY = normalY / normalLength * width;
                normalZ = normalZ / normalLength * width;
                int color = packColor(pseudo(bolt, inst.seed), 1f, 1f,
                        lightningAlpha * config.intensity());

                buffer.addVertex(matrix, x0 - normalX - cameraX, y0 - normalY - cameraY,
                                z0 - normalZ - cameraZ)
                        .setUv(0f, -1f).setColor(color);
                buffer.addVertex(matrix, x0 + normalX - cameraX, y0 + normalY - cameraY,
                                z0 + normalZ - cameraZ)
                        .setUv(0f, 1f).setColor(color);
                buffer.addVertex(matrix, x1 + normalX - cameraX, y1 + normalY - cameraY,
                                z1 + normalZ - cameraZ)
                        .setUv(1f, 1f).setColor(color);
                buffer.addVertex(matrix, x1 - normalX - cameraX, y1 - normalY - cameraY,
                                z1 - normalZ - cameraZ)
                        .setUv(1f, -1f).setColor(color);
                segmentsDrawn++;
            }
        }
        if (segmentsDrawn > 0) drawBuiltMesh(buffer, SWEEP_PARTICLE_PIPE, nowSeconds());
    }

    private static void drawPhotonRings(PoseStack poseStack, SweepAttackInstance inst,
                                        float progress, float alpha, Config config) {
        if (progress < 0.003f || alpha < 0.004f || config.ringCount() <= 0) return;
        Camera camera = mc.getEntityRenderDispatcher().camera;
        float cameraX = (float) camera.position().x;
        float cameraY = (float) camera.position().y;
        float cameraZ = (float) camera.position().z;
        Matrix4f matrix = poseStack.last().pose();

        for (int ring = 0; ring < Math.min(config.ringCount(), 5); ring++) {
            float delayedProgress = clamp01(progress - ring * 0.09f);
            if (delayedProgress <= 0.002f) continue;
            float radius = config.radius() * config.ringScale() * delayedProgress
                    * (1f + ring * 0.13f);
            float quadSize = radius * 1.34f + 0.08f;
            float y = (float) inst.y - 0.12f + ring * 0.025f;
            int color = packColor(ring / 5f, 1f, 0f,
                    alpha * config.intensity() * (1f - ring * 0.11f));
            BufferBuilder buffer = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float x = (float) inst.x - cameraX;
            float z = (float) inst.z - cameraZ;
            buffer.addVertex(matrix, x - quadSize, y - cameraY, z - quadSize)
                    .setUv(0f, 0f).setColor(color);
            buffer.addVertex(matrix, x - quadSize, y - cameraY, z + quadSize)
                    .setUv(0f, 1f).setColor(color);
            buffer.addVertex(matrix, x + quadSize, y - cameraY, z + quadSize)
                    .setUv(1f, 1f).setColor(color);
            buffer.addVertex(matrix, x + quadSize, y - cameraY, z - quadSize)
                    .setUv(1f, 0f).setColor(color);
            drawBuiltMesh(buffer, SWEEP_RING_PIPE, nowSeconds());
        }
    }

    private static void drawCoreBurst(PoseStack poseStack, SweepAttackInstance inst,
                                      float progress, float alpha, Config config) {
        if (alpha < 0.004f) return;
        Camera camera = mc.getEntityRenderDispatcher().camera;
        float cameraX = (float) camera.position().x;
        float cameraY = (float) camera.position().y;
        float cameraZ = (float) camera.position().z;
        Matrix4f matrix = poseStack.last().pose();
        float size = config.radius() * (0.32f + progress * 0.72f);
        float rightX = CAM_RIGHT.x * size;
        float rightY = CAM_RIGHT.y * size;
        float rightZ = CAM_RIGHT.z * size;
        float upX = CAM_UP.x * size;
        float upY = CAM_UP.y * size;
        float upZ = CAM_UP.z * size;
        float x = (float) inst.x + inst.dirX * config.radius() * 0.56f - cameraX;
        float y = (float) inst.y + config.verticalLift() * 0.5f - cameraY;
        float z = (float) inst.z + inst.dirZ * config.radius() * 0.56f - cameraZ;
        int color = packColor(progress, 1f, 1f, alpha * config.intensity());
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix, x - rightX - upX, y - rightY - upY, z - rightZ - upZ)
                .setUv(0f, 0f).setColor(color);
        buffer.addVertex(matrix, x - rightX + upX, y - rightY + upY, z - rightZ + upZ)
                .setUv(0f, 1f).setColor(color);
        buffer.addVertex(matrix, x + rightX + upX, y + rightY + upY, z + rightZ + upZ)
                .setUv(1f, 1f).setColor(color);
        buffer.addVertex(matrix, x + rightX - upX, y + rightY - upY, z + rightZ - upZ)
                .setUv(1f, 0f).setColor(color);
        drawBuiltMesh(buffer, SWEEP_RING_PIPE, nowSeconds());
    }

    private static void drawBuiltMesh(BufferBuilder buffer, RenderPipeline pipeline, float time) {
        MeshData mesh = buffer.buildOrThrow();
        if (mesh.drawState().vertexCount() == 0) {
            mesh.close();
            return;
        }
        drawMesh(mesh, pipeline, time);
    }

    private static void drawMesh(MeshData mesh, RenderPipeline pipeline, float time) {
        try {
            GpuBuffer vertices = pipeline.getVertexFormat()
                    .uploadImmediateVertexBuffer(mesh.vertexBuffer());
            GpuBuffer indices;
            VertexFormat.IndexType indexType;
            if (mesh.indexBuffer() == null) {
                var autoIndices = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
                indices = autoIndices.getBuffer(mesh.drawState().indexCount());
                indexType = autoIndices.type();
            } else {
                indices = pipeline.getVertexFormat()
                        .uploadImmediateIndexBuffer(mesh.indexBuffer());
                indexType = mesh.drawState().indexType();
            }

            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                    new Matrix4f(), new Vector4f(1f, 1f, 1f, 1f),
                    new Vector3f(time, 0f, 0f), new Matrix4f());
            RenderTarget target = mc.getMainRenderTarget();
            GpuTextureView colorTexture = RenderSystem.outputColorTextureOverride != null
                    ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
            GpuTextureView depthTexture = target.useDepth
                    ? (RenderSystem.outputDepthTextureOverride != null
                        ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView())
                    : null;
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "Sweeping Attack VFX", colorTexture, OptionalInt.empty(),
                    depthTexture, OptionalDouble.empty())) {
                pass.setPipeline(pipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setUniform("SweepUniforms", uniforms);
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indices, indexType);
                pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1);
            }
        } finally {
            mesh.close();
        }
    }

    public static void processPost(float distortion, float chromatic, float flash,
                                   float vignette, float tintR, float tintG, float tintB) {
        if (distortion <= 0.001f && chromatic <= 0.001f
                && flash <= 0.001f && vignette <= 0.001f) return;
        ensureInit();
        RenderTarget target = mc.getMainRenderTarget();
        if (target.getColorTexture() == null || target.getColorTextureView() == null) return;
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        if (sceneCopy == null) sceneCopy = new TextureTarget("SweepScene", width, height, false);
        if (sceneCopy.width != width || sceneCopy.height != height) sceneCopy.resize(width, height);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(postUniforms, false, true)) {
            Std140Builder builder = Std140Builder.intoBuffer(view.data());
            builder.putVec4(width, height, nowSeconds(), 0f);
            builder.putVec4(distortion, chromatic, flash, vignette);
            builder.putVec4(tintR, tintG, tintB, 0f);
        }
        encoder.copyTextureToTexture(target.getColorTexture(), sceneCopy.getColorTexture(),
                0, 0, 0, 0, 0, width, height);
        try (RenderPass pass = encoder.createRenderPass(
                () -> "Sweep Attack Post FX",
                target.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(SWEEP_POST_PIPE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("SweepPostUniforms", postUniforms);
            pass.bindTexture("SceneSampler", sceneCopy.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(0, 3);
        }
    }

    public static void destroy() {
        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
        if (postUniforms != null) {
            postUniforms.close();
            postUniforms = null;
        }
        sceneCopy = null;
    }

    private static float positiveAngleRange(float start, float end) {
        float range = end - start;
        return range < 0f ? range + (float) (Math.PI * 2.0) : range;
    }

    private static float pseudo(int index, float seed) {
        double value = Math.sin(index * 12.9898 + seed * 78.233) * 43758.5453;
        return (float) (value - Math.floor(value));
    }

    private static float nowSeconds() {
        return System.currentTimeMillis() / 1000f;
    }

    private static int packColor(float red, float green, float blue, float alpha) {
        int r = Math.round(clamp01(red) * 255f);
        int g = Math.round(clamp01(green) * 255f);
        int b = Math.round(clamp01(blue) * 255f);
        int a = Math.round(clamp01(alpha) * 255f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}

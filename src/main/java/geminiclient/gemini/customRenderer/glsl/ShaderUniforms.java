package geminiclient.gemini.customRenderer.glsl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Utility for managing std140 uniform buffers shared across
 * the Hypernova Kill Effect rendering pipeline.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Create once
 * GpuBuffer timeBuf = ShaderUniforms.create("MyUniforms", ShaderUniforms.LAYOUT_VEC4);
 *
 * // Write per-frame
 * ShaderUniforms.writeVec4(timeBuf, encoder, time, 0, 0, 0);
 *
 * // Bind in render pass
 * renderPass.setUniform("MyUniforms", timeBuf);
 * }</pre>
 */
public final class ShaderUniforms {

    private ShaderUniforms() {}

    // ── Standard layouts ───────────────────────────────────────────

    /** Single vec4: 16 bytes. */
    public static final int LAYOUT_VEC4 = new Std140SizeCalculator()
            .putVec4().get();

    /** Two vec4: 32 bytes. */
    public static final int LAYOUT_VEC4X2 = new Std140SizeCalculator()
            .putVec4().putVec4().get();

    /** vec4 + vec4 + vec4: 48 bytes. */
    public static final int LAYOUT_VEC4X3 = new Std140SizeCalculator()
            .putVec4().putVec4().putVec4().get();

    /** vec4 + vec4 + vec4 + vec4: 64 bytes. */
    public static final int LAYOUT_VEC4X4 = new Std140SizeCalculator()
            .putVec4().putVec4().putVec4().putVec4().get();

    /** vec3 + vec4 + vec4 + vec4: CustomBlurRenderer-style. */
    public static final int LAYOUT_BLUR = new Std140SizeCalculator()
            .putVec3().putVec4().putVec4().putVec4().get();

    /** vec4 (resolution+params) + vec4 (time+extras): post-processing. */
    public static final int LAYOUT_POST = new Std140SizeCalculator()
            .putVec4().putVec4().get();

    // ── Buffer creation ────────────────────────────────────────────

    /**
     * Create a mapped-write uniform buffer.
     *
     * @param label debug label for the GPU buffer
     * @param size  byte size (use one of the LAYOUT_ constants)
     */
    public static GpuBuffer create(String label, int size) {
        return RenderSystem.getDevice().createBuffer(
                () -> label,
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
                size);
    }

    // ── Writing ────────────────────────────────────────────────────

    /** Write a single vec4 into the uniform buffer. */
    public static void writeVec4(GpuBuffer buffer, float x, float y, float z, float w) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(buffer, false, true)) {
            Std140Builder.intoBuffer(view.data()).putVec4(x, y, z, w);
        }
    }

    /** Write two vec4s into the uniform buffer. */
    public static void writeVec4x2(GpuBuffer buffer,
                                    float x0, float y0, float z0, float w0,
                                    float x1, float y1, float z1, float w1) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(buffer, false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec4(x0, y0, z0, w0)
                    .putVec4(x1, y1, z1, w1);
        }
    }

    // ── Resolution helpers ─────────────────────────────────────────

    /** Write framebuffer resolution + time into a post-processing uniform buffer. */
    public static void writePostUniforms(GpuBuffer buffer, int fbWidth, int fbHeight,
                                          float param1, float param2, float time) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(buffer, false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec4(fbWidth, fbHeight, param1, param2)
                    .putVec4(time, 0f, 0f, 0f);
        }
    }

    // ── Cleanup ────────────────────────────────────────────────────

    /** Release a uniform buffer. */
    public static void destroy(GpuBuffer buffer) {
        if (buffer != null) {
            buffer.close();
        }
    }
}

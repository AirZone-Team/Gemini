package geminiclient.gemini.customRenderer.glsl;

import geminiclient.gemini.customRenderer.GeminiRenderPipelines;

import com.mojang.blaze3d.PrimitiveTopology;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * GPU material system for modern GUI rendering.
 *
 * <p>Provides a {@link RenderPipeline} with the {@code panel_material.fsh}
 * fragment shader that renders GUI panels with:
 * <ul>
 *   <li>Vertical luminance gradient</li>
 *   <li>Top-edge specular highlight (VisionOS / Arc style)</li>
 *   <li>Fresnel edge glow</li>
 *   <li>Sub-pixel noise grain</li>
 *   <li>SDF rounded corners with adaptive AA</li>
 *   <li>Mouse proximity glow</li>
 *   <li>Enabled-state background lift + accent bloom</li>
 * </ul>
 *
 * <p>The pipeline is registered but direct rendering through
 * {@code GuiElementRenderState} requires per-element UBO support
 * not yet exposed by the API. For now, use
 * {@link geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer}
 * for rounded-rect fills.</p>
 */
public final class MaterialRenderer {

    // ── UBO layout sizes ────────────────────────────────

    private static final int FRAME_UBO_SIZE = new Std140SizeCalculator()
            .putVec4()  // u_mouse.xy + u_time + pad
            .get();

    private static final int ELEMENT_UBO_SIZE = new Std140SizeCalculator()
            .putVec4()  // u_pos.xy + u_size.xy
            .putVec4()  // u_radius + u_enabled + pad + pad
            .putVec4()  // u_accent
            .get();

    // ── Pipeline ────────────────────────────────────────

    private static RenderPipeline pipeline;
    private static GpuBuffer frameUniforms;
    private static GpuBuffer elementUniforms;

    // ── Modern color palette ────────────────────────────

    /** Linear purple accent */
    public static final int ACCENT_PURPLE = new Color(139, 92, 246).getRGB();   // #8B5CF6
    /** VisionOS blue accent */
    public static final int ACCENT_BLUE   = new Color(110, 168, 255).getRGB();  // #6EA8FF
    /** Cyan accent */
    public static final int ACCENT_CYAN   = new Color(6, 182, 212).getRGB();    // #06B6D4
    /** Indigo accent */
    public static final int ACCENT_INDIGO = new Color(124, 140, 255).getRGB();  // #7C8CFF

    // ── Shadow colors ───────────────────────────────────

    /** Large soft ambient shadow */
    public static final int AMBIENT_SHADOW = new Color(0, 0, 0, 18).getRGB();
    /** Tight contact shadow */
    public static final int CONTACT_SHADOW = new Color(0, 0, 0, 40).getRGB();

    private MaterialRenderer() {}

    // ── Pipeline init ───────────────────────────────────

    private static void ensurePipeline() {
        if (pipeline == null) {
            pipeline = RenderPipeline.builder(
                            GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(getIdentifier("pipeline/panel_material"))
                    .withVertexShader(getIdentifier("core/panel_material"))
                    .withFragmentShader(getIdentifier("core/panel_material"))
                    .withBindGroupLayout(GeminiRenderPipelines.uniforms("FrameData", "ElementData"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withCull(false)
                    .build();
        }
        if (frameUniforms == null) {
            frameUniforms = RenderSystem.getDevice().createBuffer(
                    () -> "GeminiMaterialFrameUBO",
                    GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
                    FRAME_UBO_SIZE);
        }
        if (elementUniforms == null) {
            elementUniforms = RenderSystem.getDevice().createBuffer(
                    () -> "GeminiMaterialElementUBO",
                    GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
                    ELEMENT_UBO_SIZE);
        }
    }

    // ── Multi-layer shadow ──────────────────────────────

    /**
     * Draw a modern multi-layer shadow beneath a panel.
     *
     * <p>Layers: ambient (large, very soft) + contact (tight, grounds the element).
     */
    public static void drawPanelShadow(GuiGraphicsExtractor gui,
                                        int x, int y, int w, int h) {
        GlowRenderer.drawDropShadow(gui, x, y, w, h,
                2, 22, AMBIENT_SHADOW);
        GlowRenderer.drawDropShadow(gui, x, y, w, h,
                1, 8, CONTACT_SHADOW);
    }

    // ── Pipeline registration ───────────────────────────

    public static void registerPipeline(java.util.function.Consumer<RenderPipeline> registry) {
        ensurePipeline();
        registry.accept(pipeline);
    }
}

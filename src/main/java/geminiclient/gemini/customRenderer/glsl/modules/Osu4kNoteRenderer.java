package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import geminiclient.gemini.customRenderer.GeminiRenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * GPU glow bar for OSU4k notes.
 *
 * <p>Renders a soft luminous quad behind each falling note using the Slang
 * {@code osu4k_note} shader — a Gaussian-falloff glow whose intensity peaks at
 * the note's leading edge. The solid note body itself is drawn on top by the
 * CPU renderer.</p>
 */
public final class Osu4kNoteRenderer {

    public static final RenderPipeline NOTE_GLOW_PIPELINE =
            RenderPipeline.builder(GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(getIdentifier("pipeline/osu4k_note"))
                    .withVertexShader(getIdentifier("core/osu4k_note"))
                    .withFragmentShader(getIdentifier("core/osu4k_note"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .build();

    private static final TextureSetup NO_TEXTURE = TextureSetup.noTexture();
    private static final Matrix3x2f IDENTITY = new Matrix3x2f();

    private Osu4kNoteRenderer() {}

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(NOTE_GLOW_PIPELINE);
    }

    /**
     * Draws a glowing quad expanded by {@code spread} px around a note bar.
     *
     * @param glowAlpha 0..1 intensity of the glow (color alpha is multiplied by it)
     */
    public static void drawNoteGlow(GuiGraphicsExtractor gui, int x, int y, int w, int h,
                                    int spread, float glowAlpha, int color) {
        if (w <= 0 || h <= 0 || spread <= 0) {
            return;
        }
        int a = (int) (((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, glowAlpha)));
        if (a == 0) {
            return;
        }
        int tint = (a << 24) | (color & 0xFFFFFF);

        float qx0 = x - spread;
        float qy0 = y - spread;
        float qx1 = x + w + spread;
        float qy1 = y + h + spread;

        float u0 = (qx0 - x) / (float) w;
        float v0 = (qy0 - y) / (float) h;
        float u1 = (qx1 - x) / (float) w;
        float v1 = (qy1 - y) / (float) h;

        ScreenRectangle scissor = gui.peekScissorStack();
        gui.submitGuiElementRenderState(new NoteGlowState(
                qx0, qy0, qx1, qy1, u0, v0, u1, v1, tint, scissor));
    }

    private static final class NoteGlowState implements GuiElementRenderState {
        private final float x0, y0, x1, y1;
        private final float u0, v0, u1, v1;
        private final int color;
        @Nullable private final ScreenRectangle scissor;
        @Nullable private final ScreenRectangle bounds;

        NoteGlowState(float x0, float y0, float x1, float y1,
                      float u0, float v0, float u1, float v1,
                      int color, @Nullable ScreenRectangle scissor) {
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
            this.u0 = u0; this.v0 = v0; this.u1 = u1; this.v1 = v1;
            this.color = color;
            this.scissor = scissor;

            int ix = (int) Math.floor(x0);
            int iy = (int) Math.floor(y0);
            int iw = (int) Math.ceil(x1) - ix;
            int ih = (int) Math.ceil(y1) - iy;
            ScreenRectangle b = new ScreenRectangle(ix, iy, Math.max(1, iw), Math.max(1, ih));
            this.bounds = scissor != null ? scissor.intersection(b) : b;
        }

        @Override
        public void buildVertices(@NonNull VertexConsumer vc) {
            vc.addVertexWith2DPose(IDENTITY, x0, y0).setUv(u0, v0).setColor(color);
            vc.addVertexWith2DPose(IDENTITY, x0, y1).setUv(u0, v1).setColor(color);
            vc.addVertexWith2DPose(IDENTITY, x1, y1).setUv(u1, v1).setColor(color);
            vc.addVertexWith2DPose(IDENTITY, x1, y0).setUv(u1, v0).setColor(color);
        }

        @Override public @NonNull RenderPipeline pipeline() { return NOTE_GLOW_PIPELINE; }
        @Override public @NonNull TextureSetup textureSetup() { return NO_TEXTURE; }
        @Override @Nullable public ScreenRectangle scissorArea() { return scissor; }
        @Override @Nullable public ScreenRectangle bounds() { return bounds; }
    }
}

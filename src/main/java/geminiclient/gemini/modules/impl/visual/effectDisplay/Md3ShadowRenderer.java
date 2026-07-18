package geminiclient.gemini.modules.impl.visual.effectDisplay;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * MD3 elevation shadow renderer — GLSL companion to the Material
 * EffectDisplay cards.
 * <p>
 * Uses {@code md3_elevation.fsh}: an exact rounded-box SDF in pixel space
 * with a Gaussian penumbra. This complements {@code GlowRenderer}'s
 * rectangular UV-distance glow, which cannot follow corner radii of
 * 12 px+ (MD3 "medium" shape scale) without squared-off corner artifacts.
 * <p>
 * Per-element parameters (width / height / radius / strength) are encoded
 * into the vertex colour — the same convention as
 * {@code TargetDisplayRenderer#encodeBgColor} — because the GUI render-state
 * API does not expose per-element uniform buffers.
 */
public final class Md3ShadowRenderer {

    private Md3ShadowRenderer() {}

    // ── Encoding limits (must match md3_elevation.fsh) ────────────
    private static final float MAX_DIMENSION = 512f;
    private static final float MAX_RADIUS    = 64f;

    // ── MD3 elevation level-1 shadow tuning ───────────────────────
    /**
     * Penumbra extent in GUI pixels. With the shader's sigma = 5 px the
     * Gaussian tail at 14 px (2.8σ) is ≈ 2 % opacity — an imperceptible
     * cutoff at the quad boundary.
     */
    private static final int SPREAD = 14;

    /** Key-light drop distance for MD3 elevation level 1 (GUI px). */
    public static final int OFFSET_Y = 2;

    /** Peak opacity of the umbra — mirrors Mellow's 0.16 shadow alpha. */
    public static final float MAX_STRENGTH = 0.16f;

    // ── Pipeline ──────────────────────────────────────────────────

    public static final RenderPipeline MD3_ELEVATION_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/md3_elevation"))
            .withVertexShader(getIdentifier("core/md3_elevation"))
            .withFragmentShader(getIdentifier("core/md3_elevation"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withCull(false)
            .build();

    private static final TextureSetup NO_TEXTURE = TextureSetup.noTexture();

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(MD3_ELEVATION_PIPELINE);
    }

    // ── Public draw ───────────────────────────────────────────────

    /**
     * Draws an MD3 elevation shadow under a rounded card.
     *
     * @param x, y     card position (before the key-light Y offset)
     * @param w, h     card size in GUI pixels
     * @param radius   card corner radius in GUI pixels
     * @param strength peak opacity multiplier (0–1), see {@link #MAX_STRENGTH}
     */
    public static void drawElevationShadow(GuiGraphicsExtractor gui,
                                           int x, int y, int w, int h,
                                           int radius, float strength) {
        if (w <= 0 || h <= 0 || strength <= 0f) return;

        // Key-light offset: the shadow sits slightly below the card.
        int ey = y + OFFSET_Y;

        // Quad expanded by SPREAD beyond the card on every side.
        float qx0 = x - SPREAD;
        float qy0 = ey - SPREAD;
        float qx1 = x + w + SPREAD;
        float qy1 = ey + h + SPREAD;

        // UV in pixel space relative to the card origin:
        // [0,w] x [0,h] is the card, outside is the penumbra region.
        float u0 = -SPREAD, v0 = -SPREAD;
        float u1 = w + SPREAD, v1 = h + SPREAD;

        ScreenRectangle scissor = gui.peekScissorStack();
        gui.submitGuiElementRenderState(new ElevationShadowState(
                new Matrix3x2f(gui.pose()),
                qx0, qy0, qx1, qy1,
                u0, v0, u1, v1,
                encode(w, h, radius, strength),
                scissor));
    }

    // ── Encoding ──────────────────────────────────────────────────

    /** Packs (width, height, radius, strength) into an ARGB vertex colour. */
    static int encode(float width, float height, float radius, float strength) {
        int ir = (int) (Math.min(width,  MAX_DIMENSION) / MAX_DIMENSION * 255f) & 0xFF;
        int ig = (int) (Math.min(height, MAX_DIMENSION) / MAX_DIMENSION * 255f) & 0xFF;
        int ib = (int) (Math.min(radius, MAX_RADIUS)    / MAX_RADIUS    * 255f) & 0xFF;
        int ia = (int) (Math.min(strength, 1f) * 255f) & 0xFF;
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    // ── Render state ──────────────────────────────────────────────

    private record ElevationShadowState(
            Matrix3x2f pose,
            float x0, float y0, float x1, float y1,
            float u0, float v0, float u1, float v1,
            int encodedColor,
            @Nullable ScreenRectangle scissor
    ) implements GuiElementRenderState {

        @Override
        public void buildVertices(@NonNull VertexConsumer vc) {
            vc.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(encodedColor);
            vc.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(encodedColor);
            vc.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(encodedColor);
            vc.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(encodedColor);
        }

        @Override
        public @NonNull RenderPipeline pipeline() { return MD3_ELEVATION_PIPELINE; }
        @Override
        public @NonNull TextureSetup textureSetup() { return NO_TEXTURE; }
        @Override
        public @Nullable ScreenRectangle scissorArea() { return scissor; }

        @Override
        public @Nullable ScreenRectangle bounds() {
            int ix = (int) Math.floor(x0), iy = (int) Math.floor(y0);
            int iw = (int) Math.ceil(x1) - ix, ih = (int) Math.ceil(y1) - iy;
            ScreenRectangle b = new ScreenRectangle(ix, iy, Math.max(1, iw), Math.max(1, ih));
            return scissor != null ? scissor.intersection(b) : b;
        }
    }
}

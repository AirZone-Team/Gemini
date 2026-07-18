package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * GLSL-accelerated TargetDisplay renderer.
 * <p>
 * Draws a rounded rectangle background via a custom fragment shader (SDF-based),
 * then composites the target player's face, name, and an animated wave (liquid)
 * health bar on top. All text uses the bundled Google Sans font through
 * {@link CustomFontRenderer} (vanilla font fallback if the TTF fails to load).
 * Fully supports fade-in/out alpha animations.
 */
public final class TargetDisplayRenderer {

    private TargetDisplayRenderer() {}

    // ── Constants ────────────────────────────────────────────────

    /** Total display dimensions. */
    public static final int DISPLAY_WIDTH  = 170;
    public static final int DISPLAY_HEIGHT = 36;

    /** Corner radius for the background rounded rect. */
    public static final int CORNER_RADIUS = 3;

    /** Layout padding & head size. */
    private static final int PADDING      = 4;
    private static final int HEAD_SIZE    = 28;
    /** Equal spacing between name ↔ HP text ↔ health bar. */
    private static final int SPACING      = 2;

    /** Health bar dimensions. */
    private static final int HEALTH_BAR_WIDTH  = 130;
    private static final int HEALTH_BAR_HEIGHT = 6;

    /** Max element dimension for vertex-colour encoding (see fragment shader). */
    private static final float MAX_DIMENSION = 512f;
    private static final float MAX_RADIUS    = 64f;

    // ── Pipeline ─────────────────────────────────────────────────

    public static final RenderPipeline TARGET_BG_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/target_display"))
            .withVertexShader(getIdentifier("core/target_display"))
            .withFragmentShader(getIdentifier("core/target_display"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(new BlendFunction(
                    com.mojang.blaze3d.platform.SourceFactor.SRC_ALPHA,
                    com.mojang.blaze3d.platform.DestFactor.ONE_MINUS_SRC_ALPHA,
                    com.mojang.blaze3d.platform.SourceFactor.ONE,
                    com.mojang.blaze3d.platform.DestFactor.ZERO)))
            .withCull(false)
            .build();

    // ── Registration ─────────────────────────────────────────────

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(TARGET_BG_PIPELINE);
    }

    // ── Google Sans font (replaces the vanilla font) ─────────────

    private static final Identifier GOOGLE_SANS_FONT = getIdentifier("font/googlesans-regular.ttf");
    private static final float TEXT_SIZE = 8f;
    private static final float NAME_SIZE = 9f;

    private static volatile CustomFontRenderer.GlyphFont googleSans;
    private static volatile CustomFontRenderer.GlyphFont googleSansBold;
    private static volatile boolean googleSansFailed;

    /** Regular Google Sans, or {@code null} if the bundled TTF failed to load. */
    public static CustomFontRenderer.@Nullable GlyphFont googleSans() {
        if (googleSansFailed) return null;
        CustomFontRenderer.GlyphFont f = googleSans;
        if (f == null) {
            try {
                f = googleSans = CustomFontRenderer.loadFont(GOOGLE_SANS_FONT, TEXT_SIZE);
            } catch (Exception e) {
                googleSansFailed = true;
                return null;
            }
        }
        return f;
    }

    /** Bold Google Sans for emphasis text such as the target name. */
    public static CustomFontRenderer.@Nullable GlyphFont googleSansBold() {
        if (googleSansFailed) return null;
        CustomFontRenderer.GlyphFont f = googleSansBold;
        if (f == null) {
            try {
                f = googleSansBold = CustomFontRenderer.loadFont(
                        GOOGLE_SANS_FONT, NAME_SIZE, java.awt.Font.BOLD);
            } catch (Exception e) {
                googleSansFailed = true;
                return null;
            }
        }
        return f;
    }

    /** Draws text with Google Sans, falling back to the vanilla font if unavailable. */
    public static void drawText(GuiGraphicsExtractor gui, String text, float x, float y, int color) {
        if (text == null || text.isEmpty() || (color >>> 24) == 0) return;
        CustomFontRenderer.GlyphFont f = googleSans();
        if (f != null) {
            CustomFontRenderer.drawString(gui, f, text, x, y, color);
        } else {
            gui.text(mc.font, text, (int) x, (int) y, color, false);
        }
    }

    /** Bold variant of {@link #drawText} used for the target name. */
    public static void drawBoldText(GuiGraphicsExtractor gui, String text, float x, float y, int color) {
        if (text == null || text.isEmpty() || (color >>> 24) == 0) return;
        CustomFontRenderer.GlyphFont f = googleSansBold();
        if (f != null) {
            CustomFontRenderer.drawString(gui, f, text, x, y, color);
        } else {
            gui.text(mc.font, Component.literal(text).withStyle(s -> s.withBold(true)),
                    (int) x, (int) y, color, false);
        }
    }

    public static float textWidth(String text) {
        CustomFontRenderer.GlyphFont f = googleSans();
        return f != null ? CustomFontRenderer.stringWidth(f, text) : mc.font.width(text);
    }

    public static float textLineHeight() {
        CustomFontRenderer.GlyphFont f = googleSans();
        return f != null ? f.lineHeight : mc.font.lineHeight;
    }

    // ── Background render state ──────────────────────────────────

    private record BgRenderState(
            RenderPipeline pipeline,
            Matrix3x2f pose,
            float x0, float y0, float x1, float y1,
            int encodedColor,
            @Nullable ScreenRectangle scissor
    ) implements GuiElementRenderState {

        @Override
        public void buildVertices(@NonNull VertexConsumer vc) {
            vc.addVertexWith2DPose(pose, x0, y0).setUv(0f, 0f).setColor(encodedColor);
            vc.addVertexWith2DPose(pose, x0, y1).setUv(0f, 1f).setColor(encodedColor);
            vc.addVertexWith2DPose(pose, x1, y1).setUv(1f, 1f).setColor(encodedColor);
            vc.addVertexWith2DPose(pose, x1, y0).setUv(1f, 0f).setColor(encodedColor);
        }

        @Override
        public @NonNull TextureSetup textureSetup() { return TextureSetup.noTexture(); }
        @Override @Nullable public ScreenRectangle scissorArea() { return scissor; }

        @Override
        @Nullable
        public ScreenRectangle bounds() {
            int ix = (int) Math.floor(x0), iy = (int) Math.floor(y0);
            int iw = (int) Math.ceil(x1) - ix, ih = (int) Math.ceil(y1) - iy;
            ScreenRectangle b = new ScreenRectangle(ix, iy, iw, ih);
            return scissor != null ? scissor.intersection(b) : b;
        }
    }

    // ── Low-level background draw ────────────────────────────────

    static int encodeBgColor(float width, float height, float radius, float alpha) {
        int ir = (int) (width  / MAX_DIMENSION * 255f) & 0xFF;
        int ig = (int) (height / MAX_DIMENSION * 255f) & 0xFF;
        int ib = (int) (radius / MAX_RADIUS    * 255f) & 0xFF;
        int ia = (int) (alpha * 255f) & 0xFF;
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    public static void drawBackground(GuiGraphicsExtractor gui, int x, int y, int w, int h, int radius, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0f) return;
        ScreenRectangle scissor = gui.peekScissorStack();
        int color = encodeBgColor(w, h, radius, alpha);

        gui.submitGuiElementRenderState(new BgRenderState(
                TARGET_BG_PIPELINE,
                new Matrix3x2f(gui.pose()),
                x, y, x + w, y + h,
                color, scissor
        ));
    }

    // ── Player head rendering ────────────────────────────────────

    public static void drawPlayerHead(GuiGraphicsExtractor gui, int x, int y, int size, Player player, float alpha) {
        if (size <= 0 || alpha <= 0f) return;

        Identifier skinTexture = getPlayerSkinTexture(player);
        if (skinTexture == null) return;

        AbstractTexture tex = mc.getTextureManager().getTexture(skinTexture);

        TextureSetup texSetup = TextureSetup.singleTexture(tex.getTextureView(), tex.getSampler());

        ScreenRectangle scissor = gui.peekScissorStack();
        Matrix3x2f pose = new Matrix3x2f(gui.pose());
        float x1 = x + size;
        float y1 = y + size;

        float u0 = 8f / 64f, v0 = 8f / 64f;
        float u1 = 16f / 64f, v1 = 16f / 64f;

        // 赋予头像透明度
        int color = applyAlpha(0xFFFFFFFF, alpha);

        gui.submitGuiElementRenderState(new HeadRenderState(
                RenderPipelines.GUI_TEXTURED, texSetup, pose,
                (float) x, (float) y, x1, y1, u0, v0, u1, v1, scissor, color
        ));
    }

    private record HeadRenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2f pose,
            float x0, float y0, float x1, float y1,
            float u0, float v0, float u1, float v1,
            @Nullable ScreenRectangle scissor,
            int color
    ) implements GuiElementRenderState {

        @Override
        public void buildVertices(@NonNull VertexConsumer vc) {
            vc.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(color);
            vc.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(color);
            vc.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color);
            vc.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(color);
        }

        @Override @Nullable public ScreenRectangle scissorArea() { return scissor; }

        @Override
        @Nullable
        public ScreenRectangle bounds() {
            int ix = (int) Math.floor(x0), iy = (int) Math.floor(y0);
            int iw = (int) Math.ceil(x1) - ix, ih = (int) Math.ceil(y1) - iy;
            ScreenRectangle b = new ScreenRectangle(ix, iy, iw, ih);
            return scissor != null ? scissor.intersection(b) : b;
        }
    }

    @Nullable
    public static Identifier getPlayerSkinTexture(Player player) {
        try {
            var lookup = mc.getSkinManager().createLookup(player.getGameProfile(), false);
            var skin = lookup.get();
            var texture = skin.body();
            return texture.texturePath();
        } catch (Exception e) {
            try {
                return DefaultPlayerSkin.get(player.getGameProfile()).body().texturePath();
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    // ── Wave (liquid) health bar render state ────────────────────

    /**
     * Quad list with per-vertex colours. Each consecutive group of four
     * vertices (TL, BL, BR, TR) forms one tessellation slice of the wave.
     */
    private record WaveRenderState(
            Matrix3x2f pose,
            float[] positions,
            int[] colors,
            @Nullable ScreenRectangle scissor,
            @Nullable ScreenRectangle bounds
    ) implements GuiElementRenderState {

        static WaveRenderState of(Matrix3x2f pose, float[] positions, int[] colors,
                                  @Nullable ScreenRectangle scissor) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int i = 0; i < positions.length; i += 2) {
                minX = Math.min(minX, positions[i]);
                minY = Math.min(minY, positions[i + 1]);
                maxX = Math.max(maxX, positions[i]);
                maxY = Math.max(maxY, positions[i + 1]);
            }
            int bx = (int) Math.floor(minX), by = (int) Math.floor(minY);
            ScreenRectangle b = new ScreenRectangle(
                    bx, by,
                    Math.max(1, (int) Math.ceil(maxX) - bx),
                    Math.max(1, (int) Math.ceil(maxY) - by)).transformMaxBounds(pose);
            return new WaveRenderState(pose, positions, colors, scissor,
                    scissor != null ? scissor.intersection(b) : b);
        }

        @Override
        public void buildVertices(@NonNull VertexConsumer vc) {
            for (int i = 0; i < colors.length; i++) {
                vc.addVertexWith2DPose(pose, positions[i * 2], positions[i * 2 + 1])
                        .setColor(colors[i]);
            }
        }

        @Override public @NonNull RenderPipeline pipeline() { return RenderPipelines.GUI; }
        @Override public @NonNull TextureSetup textureSetup() { return TextureSetup.noTexture(); }
        @Override @Nullable public ScreenRectangle scissorArea() { return scissor; }
        @Override @Nullable public ScreenRectangle bounds() { return bounds; }
    }

    // ── Health bar rendering (wave) ──────────────────────────────

    /** Liquid-wave fill tuning. */
    private static final float WAVE_AMPLITUDE = 1.6f;  // px — top-edge undulation height
    private static final float WAVE_LENGTH    = 16f;   // px per full sine period
    private static final float WAVE_STEP      = 2f;    // horizontal tessellation step
    private static final long  WAVE_PERIOD_MS = 1600;  // wave travel speed

    public static void drawHealthBar(GuiGraphicsExtractor gui, int x, int y, int width, int height, float healthPercent, float alpha) {
        if (width <= 0 || height <= 0 || alpha <= 0f) return;

        float hp = Math.max(0f, Math.min(1f, healthPercent));

        // Track (dark) with dynamic alpha
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, width, height, 2,
                applyAlpha(0x66000000, alpha));

        if (hp <= 0f) return;

        // Colour: green → yellow → red based on HP
        int color;
        if (hp > 0.6f) {
            color = lerpColor(0xFFFFAA00, 0xFF44DD44, (hp - 0.6f) / 0.4f);
        } else if (hp > 0.3f) {
            color = lerpColor(0xFFFF5500, 0xFFFFAA00, (hp - 0.3f) / 0.3f);
        } else {
            color = lerpColor(0xFFFF2222, 0xFFFF5500, hp / 0.3f);
        }

        int fillColor  = applyAlpha(color, alpha);
        int crestColor = applyAlpha(brighten(color, 0.35f), alpha);

        // Liquid fill: straight bottom edge, animated sine-wave surface.
        // Inset by 1px so the fill respects the track's rounded corners.
        float fillLeft  = x + 1f;
        float fillRight = x + Math.max(2f, width * hp) - 1f;
        if (fillRight <= fillLeft) fillRight = fillLeft + 1f;
        float bottom = y + height - 1f;
        float phase  = (float) ((System.currentTimeMillis() % WAVE_PERIOD_MS)
                / (double) WAVE_PERIOD_MS * Math.PI * 2.0);

        int segments = Math.max(1, (int) Math.ceil((fillRight - fillLeft) / WAVE_STEP));
        float[] pos = new float[segments * 8];
        int[]   col = new int[segments * 4];

        for (int i = 0; i < segments; i++) {
            float x0 = Math.min(fillLeft + i * WAVE_STEP, fillRight);
            float x1 = Math.min(fillLeft + (i + 1) * WAVE_STEP, fillRight);
            float t0 = waveTop(y, x0 - fillLeft, phase);
            float t1 = waveTop(y, x1 - fillLeft, phase);

            int o = i * 8;
            pos[o]     = x0; pos[o + 1] = t0;     // TL
            pos[o + 2] = x0; pos[o + 3] = bottom; // BL
            pos[o + 4] = x1; pos[o + 5] = bottom; // BR
            pos[o + 6] = x1; pos[o + 7] = t1;     // TR

            int c = i * 4;
            col[c] = crestColor; col[c + 1] = fillColor;
            col[c + 2] = fillColor; col[c + 3] = crestColor;
        }

        gui.submitGuiElementRenderState(WaveRenderState.of(
                new Matrix3x2f(gui.pose()), pos, col, gui.peekScissorStack()));
    }

    /** Y of the liquid surface at horizontal offset {@code dx}; only the top edge undulates. */
    private static float waveTop(float barTop, float dx, float phase) {
        return barTop + WAVE_AMPLITUDE
                + WAVE_AMPLITUDE * (float) Math.sin(dx / WAVE_LENGTH * Math.PI * 2.0 + phase);
    }

    /** Lerps an ARGB colour towards white; the alpha channel is preserved. */
    private static int brighten(int color, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        int r = clamp8((int) (((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * amount));
        int g = clamp8((int) (((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * amount));
        int b = clamp8((int) ((color & 0xFF) + (255 - (color & 0xFF)) * amount));
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // ── Name rendering ───────────────────────────────────────────

    public static void drawName(GuiGraphicsExtractor gui, String name, int x, int y, int color, float alpha) {
        if (name == null || name.isEmpty() || alpha <= 0f) return;
        drawText(gui, name, x, y, applyAlpha(color, alpha));
    }

    // ── Composite draw ───────────────────────────────────────────

    public static void drawTargetDisplay(
            GuiGraphicsExtractor gui,
            int x, int y,
            Player target,
            String displayName,
            float health, float maxHealth,
            float alpha
    ) {

        // 1. Rounded rectangle background (GLSL)
        // Background has alpha combined
        drawBackground(gui, x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT, CORNER_RADIUS, alpha);

        // 2. Player head — equal PADDING top and bottom
        int headX = x + PADDING;
        int headY = y + PADDING;
        drawPlayerHead(gui, headX, headY, HEAD_SIZE, target, alpha);

        // 3. Name — top-aligned with the head
        int nameX = headX + HEAD_SIZE + PADDING;
        drawName(gui, displayName, nameX, headY, 0xFFDDDDDD, alpha);

        // 4. HP text — spaced equally below the name
        float hpPercent = maxHealth > 0f ? health / maxHealth : 0f;
        String hpText = String.format("%.0f/%d", health, (int) maxHealth);
        int hpColorBase = hpPercent > 0.6f ? 0xFF88DD88 : (hpPercent > 0.3f ? 0xFFDDDD88 : 0xFFDD8888);
        float lineHeight = textLineHeight();
        float hpTextY = headY + lineHeight + SPACING;
        drawText(gui, hpText, nameX, hpTextY, applyAlpha(hpColorBase, alpha));

        // 5. Wave health bar — spaced equally below the HP text
        int hpBarY = Math.round(hpTextY + lineHeight + SPACING);
        drawHealthBar(gui, nameX, hpBarY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT, hpPercent, alpha);
    }

    public static void drawTargetDisplayFallback(
            GuiGraphicsExtractor gui,
            int x, int y,
            String displayName,
            float health, float maxHealth,
            float alpha
    ) {
        int w = DISPLAY_WIDTH;

        drawBackground(gui, x, y, w, DISPLAY_HEIGHT, CORNER_RADIUS, alpha);

        int textX = x + PADDING;
        int textY = y + PADDING;
        drawName(gui, displayName, textX, textY, 0xFFDDDDDD, alpha);

        float hpPercent = maxHealth > 0f ? health / maxHealth : 0f;
        String hpText = String.format("%.0f/%d", health, (int) maxHealth);
        int hpColorBase = hpPercent > 0.6f ? 0xFF88DD88 : (hpPercent > 0.3f ? 0xFFDDDD88 : 0xFFDD8888);
        float lineHeight = textLineHeight();
        float hpTextY = textY + lineHeight + SPACING;
        drawText(gui, hpText, textX, hpTextY, applyAlpha(hpColorBase, alpha));

        int hpBarY = Math.round(hpTextY + lineHeight + SPACING);
        drawHealthBar(gui, textX, hpBarY, w - PADDING * 2, HEALTH_BAR_HEIGHT, hpPercent, alpha);
    }

    // ── Utility ──────────────────────────────────────────────────

    /** Utility to inject a new alpha multiplier into an existing ARGB color */
    public static int applyAlpha(int color, float alpha) {
        int originalAlpha = (color >>> 24) & 0xFF;
        int newAlpha = (int) (originalAlpha * alpha);
        newAlpha = clamp8(newAlpha);
        return (newAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static int lerpColor(int colorA, int colorB, float t) {
        t = Math.max(0f, Math.min(1f, t));
        float a = (colorA >>> 24) / 255f;
        float r = ((colorA >> 16) & 0xFF) / 255f;
        float g = ((colorA >> 8) & 0xFF) / 255f;
        float b = (colorA & 0xFF) / 255f;
        float aB = (colorB >>> 24) / 255f;
        float rB = ((colorB >> 16) & 0xFF) / 255f;
        float gB = ((colorB >> 8) & 0xFF) / 255f;
        float bB = (colorB & 0xFF) / 255f;
        return (clamp8((int) ((a + (aB - a) * t) * 255f)) << 24)
                | (clamp8((int) ((r + (rB - r) * t) * 255f)) << 16)
                | (clamp8((int) ((g + (gB - g) * t) * 255f)) << 8)
                | clamp8((int) ((b + (bB - b) * t) * 255f));
    }

    private static int clamp8(int v) { return Math.max(0, Math.min(255, v)); }
}
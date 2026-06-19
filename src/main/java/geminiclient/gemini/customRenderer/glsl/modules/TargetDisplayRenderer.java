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
 * then composites the target player's face, name, and health bar on top.
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
    private static Identifier getPlayerSkinTexture(Player player) {
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

    // ── Health bar rendering ─────────────────────────────────────

    public static void drawHealthBar(GuiGraphicsExtractor gui, int x, int y, int width, int height, float healthPercent, float alpha) {
        if (width <= 0 || height <= 0 || alpha <= 0f) return;

        float hp = Math.max(0f, Math.min(1f, healthPercent));

        // Background (dark) with dynamic alpha
        int bgColor = applyAlpha(0x66000000, alpha);
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, width, height, 2, bgColor);

        // Filled portion
        if (hp > 0f) {
            int fillWidth = Math.max(1, Math.round(width * hp));

            // Colour: green → yellow → red based on HP
            int color;
            if (hp > 0.6f) {
                float t = (hp - 0.6f) / 0.4f;
                color = lerpColor(0xFFFFAA00, 0xFF44DD44, t);
            } else if (hp > 0.3f) {
                float t = (hp - 0.3f) / 0.3f;
                color = lerpColor(0xFFFF5500, 0xFFFFAA00, t);
            } else {
                float t = hp / 0.3f;
                color = lerpColor(0xFFFF2222, 0xFFFF5500, t);
            }

            int fillColor = applyAlpha(color, alpha);
            CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, fillWidth, height, 2, fillColor);
        }
    }

    // ── Name rendering ───────────────────────────────────────────

    public static void drawName(GuiGraphicsExtractor gui, String name, int x, int y, int color, float alpha) {
        if (name == null || name.isEmpty() || alpha <= 0f) return;
        CustomFontRenderer.drawString(gui, mc.font, name, x, y, applyAlpha(color, alpha));
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
        int hpTextY = headY + mc.font.lineHeight + SPACING;
        int hpTextColor = applyAlpha(hpColorBase, alpha);
        CustomFontRenderer.drawString(gui, mc.font, hpText, nameX, hpTextY, hpTextColor);

        // 5. Health bar — spaced equally below the HP text
        int hpBarY = hpTextY + mc.font.lineHeight + SPACING;
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
        int hpTextY = textY + mc.font.lineHeight + SPACING;
        int hpTextColor = applyAlpha(hpColorBase, alpha);
        CustomFontRenderer.drawString(gui, mc.font, hpText, textX, hpTextY, hpTextColor);

        int hpBarY = hpTextY + mc.font.lineHeight + SPACING;
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
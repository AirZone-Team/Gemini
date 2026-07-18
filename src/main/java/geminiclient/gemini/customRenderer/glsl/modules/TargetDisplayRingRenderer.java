package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * GLSL-accelerated "Ring" style TargetDisplay renderer.
 * <p>
 * Light lavender theme: a circular avatar emblem with a health progress
 * ring (single SDF shader quad — thin track, thick bottom-centred arc),
 * Name / HP / Ping text lines (Google Sans via {@link TargetDisplayRenderer})
 * and three decorative status icons (bell, gear, square) on the right.
 */
public final class TargetDisplayRingRenderer {

    private TargetDisplayRingRenderer() {}

    // ── Constants ────────────────────────────────────────────────

    /** Total display dimensions. */
    public static final int DISPLAY_WIDTH  = 172;
    public static final int DISPLAY_HEIGHT = 56;

    /** Corner radius for the background rounded rect. */
    public static final int CORNER_RADIUS = 9;

    /** Emblem (avatar + ring) layout. */
    private static final int PADDING         = 6;
    private static final int EMBLEM_SIZE     = 44;
    private static final float RING_THICKNESS = 4.5f;

    /** Text layout. */
    private static final int TEXT_X   = PADDING + EMBLEM_SIZE + 8; // 58
    private static final int NAME_Y   = 9;
    private static final int SUB_Y    = 31;
    private static final int PING_COL = TEXT_X + 48;

    /** Decorative icon column. */
    private static final int ICON_CENTER_X = DISPLAY_WIDTH - 10;
    private static final int ICON_BELL_Y   = 13;
    private static final int ICON_GEAR_Y   = 28;
    private static final int ICON_SQUARE_Y = 43;

    /** Palette (light lavender theme). */
    private static final int BG_COLOR       = 0xECEDF9F6; // card fill
    private static final int BG_BORDER      = 0xFFD5D0E8; // card border
    private static final int NAME_COLOR     = 0xFF322E45; // dark lavender ink
    private static final int LABEL_COLOR    = 0xFF7A7490; // gray-purple labels
    private static final int VALUE_COLOR    = 0xFF4A4560; // dark values
    private static final int ICON_COLOR     = 0xFFC6C1DC; // light gray-lavender

    /** Max element dimension for vertex-colour encoding (see fragment shader). */
    private static final float MAX_DIMENSION = 512f;
    private static final float MAX_THICKNESS = 64f;

    // ── Pipeline ─────────────────────────────────────────────────

    public static final RenderPipeline TARGET_EMBLEM_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/target_emblem"))
            .withVertexShader(getIdentifier("core/target_emblem"))
            .withFragmentShader(getIdentifier("core/target_emblem"))
            .withSampler("Sampler0")
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
        registry.accept(TARGET_EMBLEM_PIPELINE);
    }

    // ── Emblem render state ──────────────────────────────────────

    private record EmblemRenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
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
        public @NonNull TextureSetup textureSetup() { return textureSetup; }
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

    static int encodeEmblemColor(float size, float thickness, float progress, float alpha) {
        int ir = (int) (size      / MAX_DIMENSION * 255f) & 0xFF;
        int ig = (int) (thickness / MAX_THICKNESS * 255f) & 0xFF;
        int ib = (int) (Math.max(0f, Math.min(1f, progress)) * 255f) & 0xFF;
        int ia = (int) (alpha * 255f) & 0xFF;
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    // ── Low-level emblem draw ────────────────────────────────────

    public static void drawEmblem(GuiGraphicsExtractor gui, int x, int y, int size,
                                  float thickness, float healthPercent,
                                  LivingEntity target, float alpha) {
        if (size <= 0 || alpha <= 0f) return;

        Identifier skinTexture = resolveSkinTexture(target);
        if (skinTexture == null) return;

        AbstractTexture tex = mc.getTextureManager().getTexture(skinTexture);
        TextureSetup texSetup = TextureSetup.singleTexture(tex.getTextureView(), tex.getSampler());

        gui.submitGuiElementRenderState(new EmblemRenderState(
                TARGET_EMBLEM_PIPELINE, texSetup,
                new Matrix3x2f(gui.pose()),
                x, y, x + size, y + size,
                encodeEmblemColor(size, thickness, healthPercent, alpha),
                gui.peekScissorStack()
        ));
    }

    @Nullable
    private static Identifier resolveSkinTexture(LivingEntity target) {
        if (target instanceof Player player) {
            Identifier skin = TargetDisplayRenderer.getPlayerSkinTexture(player);
            if (skin != null) return skin;
        }
        // 非玩家实体（或皮肤解析失败）使用默认皮肤作为头像
        try {
            return DefaultPlayerSkin.get(target.getUUID()).body().texturePath();
        } catch (Exception ignored) {
            return null;
        }
    }

    // ── CPU shape render state (icons) ───────────────────────────

    /**
     * Triangle-fan render state: vertices[0..1] is the fan centre,
     * the remaining pairs form the perimeter. Emitted as degenerate-quad
     * triangles (centre, centre, p[i+1], p[i]) matching the project's
     * existing corner-arc convention.
     */
    private record FanRenderState(
            Matrix3x2f pose,
            float[] vertices,
            int color,
            @Nullable ScreenRectangle scissor,
            @Nullable ScreenRectangle bounds
    ) implements GuiElementRenderState {

        static FanRenderState of(Matrix3x2f pose, float[] vertices, int color,
                                 @Nullable ScreenRectangle scissor) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int i = 0; i < vertices.length; i += 2) {
                minX = Math.min(minX, vertices[i]);
                minY = Math.min(minY, vertices[i + 1]);
                maxX = Math.max(maxX, vertices[i]);
                maxY = Math.max(maxY, vertices[i + 1]);
            }
            int bx = (int) Math.floor(minX), by = (int) Math.floor(minY);
            ScreenRectangle b = new ScreenRectangle(
                    bx, by,
                    Math.max(1, (int) Math.ceil(maxX) - bx),
                    Math.max(1, (int) Math.ceil(maxY) - by)).transformMaxBounds(pose);
            return new FanRenderState(pose, vertices, color, scissor,
                    scissor != null ? scissor.intersection(b) : b);
        }

        @Override
        public void buildVertices(@NonNull VertexConsumer vc) {
            float cx = vertices[0], cy = vertices[1];
            int perimeterCount = vertices.length / 2 - 1;
            for (int i = 0; i < perimeterCount; i++) {
                int cur = 2 + i * 2;
                int nxt = 2 + ((i + 1) % perimeterCount) * 2;
                vc.addVertexWith2DPose(pose, cx, cy).setColor(color);
                vc.addVertexWith2DPose(pose, cx, cy).setColor(color);
                vc.addVertexWith2DPose(pose, vertices[nxt], vertices[nxt + 1]).setColor(color);
                vc.addVertexWith2DPose(pose, vertices[cur], vertices[cur + 1]).setColor(color);
            }
        }

        @Override public @NonNull RenderPipeline pipeline() { return RenderPipelines.GUI; }
        @Override public @NonNull TextureSetup textureSetup() { return TextureSetup.noTexture(); }
        @Override @Nullable public ScreenRectangle scissorArea() { return scissor; }
        @Override @Nullable public ScreenRectangle bounds() { return bounds; }
    }

    private static void submitFan(GuiGraphicsExtractor gui, float[] vertices, int color) {
        if ((color >>> 24) == 0) return;
        gui.submitGuiElementRenderState(FanRenderState.of(
                new Matrix3x2f(gui.pose()), vertices, color, gui.peekScissorStack()));
    }

    /** Filled circle as a triangle fan. */
    private static void drawCircle(GuiGraphicsExtractor gui, float cx, float cy, float r, int color) {
        drawArcFan(gui, cx, cy, r, 0f, (float) (Math.PI * 2.0), 14, color);
    }

    /** Filled arc sector (startAngle/sweep in radians, y-down screen space). */
    private static void drawArcFan(GuiGraphicsExtractor gui, float cx, float cy, float r,
                                   float startAngle, float sweep, int segments, int color) {
        float[] v = new float[2 + (segments + 1) * 2];
        v[0] = cx;
        v[1] = cy;
        for (int i = 0; i <= segments; i++) {
            float a = startAngle + sweep * i / segments;
            v[2 + i * 2]     = cx + (float) Math.cos(a) * r;
            v[2 + i * 2 + 1] = cy + (float) Math.sin(a) * r;
        }
        submitFan(gui, v, color);
    }

    /** Arbitrary triangle. */
    private static void drawTriangle(GuiGraphicsExtractor gui,
                                     float ax, float ay, float bx, float by,
                                     float cx, float cy, int color) {
        submitFan(gui, new float[]{ax, ay, bx, by, cx, cy}, color);
    }

    // ── Icon shapes ──────────────────────────────────────────────

    /** Bell: top knob, dome, flared skirt, clapper. */
    private static void drawBell(GuiGraphicsExtractor gui, float cx, float cy, float r, int color) {
        drawCircle(gui, cx, cy - r * 0.92f, r * 0.24f, color);                       // knob
        drawArcFan(gui, cx, cy + r * 0.05f, r * 0.78f,
                (float) Math.PI, (float) Math.PI, 10, color);                        // dome
        drawTriangle(gui,                                                            // skirt (left half)
                cx - r * 0.78f, cy + r * 0.05f,
                cx + r * 0.78f, cy + r * 0.05f,
                cx + r * 1.02f, cy + r * 0.78f, color);
        drawTriangle(gui,                                                            // skirt (right half)
                cx - r * 0.78f, cy + r * 0.05f,
                cx + r * 1.02f, cy + r * 0.78f,
                cx - r * 1.02f, cy + r * 0.78f, color);
        drawCircle(gui, cx, cy + r * 0.98f, r * 0.26f, color);                       // clapper
    }

    /** Gear: N-tooth starburst polygon. */
    private static void drawGear(GuiGraphicsExtractor gui, float cx, float cy, float r, int color) {
        int teeth = 8;
        float inner = r * 0.62f;
        float[] v = new float[2 + teeth * 2 * 2];
        v[0] = cx;
        v[1] = cy;
        for (int i = 0; i < teeth * 2; i++) {
            float a = (float) (i * Math.PI / teeth);
            float rad = (i % 2 == 0) ? r : inner;
            v[2 + i * 2]     = cx + (float) Math.cos(a) * rad;
            v[2 + i * 2 + 1] = cy + (float) Math.sin(a) * rad;
        }
        submitFan(gui, v, color);
    }

    // ── Background ───────────────────────────────────────────────

    public static void drawBackground(GuiGraphicsExtractor gui, int x, int y, float alpha) {
        if (alpha <= 0f) return;
        CustomRoundedRectRenderer.drawRoundedBorderedRect(gui, x, y,
                DISPLAY_WIDTH, DISPLAY_HEIGHT, CORNER_RADIUS,
                TargetDisplayRenderer.applyAlpha(BG_COLOR, alpha),
                TargetDisplayRenderer.applyAlpha(BG_BORDER, alpha), 1);
    }

    // ── Composite draw ───────────────────────────────────────────

    public static void drawRingDisplay(
            GuiGraphicsExtractor gui,
            int x, int y,
            LivingEntity target,
            String displayName,
            float health, float maxHealth,
            int ping,
            float alpha
    ) {
        // 1. Light card background
        drawBackground(gui, x, y, alpha);

        // 2. Avatar emblem with health progress ring (GLSL)
        float hpPercent = maxHealth > 0f ? Math.max(0f, Math.min(1f, health / maxHealth)) : 0f;
        int emblemX = x + PADDING;
        int emblemY = y + PADDING;
        drawEmblem(gui, emblemX, emblemY, EMBLEM_SIZE, RING_THICKNESS, hpPercent, target, alpha);

        // 3. Name (bold Google Sans, dark ink)
        int nameX = x + TEXT_X;
        int nameColor = TargetDisplayRenderer.applyAlpha(NAME_COLOR, alpha);
        TargetDisplayRenderer.drawBoldText(gui, displayName, nameX, y + NAME_Y, nameColor);

        // 4. HP line: gray label + health-coloured value
        int labelColor = TargetDisplayRenderer.applyAlpha(LABEL_COLOR, alpha);
        int subY = y + SUB_Y;
        TargetDisplayRenderer.drawText(gui, "HP: ", nameX, subY, labelColor);
        float hpLabelW = TargetDisplayRenderer.textWidth("HP: ");
        int hpColorBase = hpPercent > 0.6f ? 0xFF3FB950 : (hpPercent > 0.3f ? 0xFFD29922 : 0xFFF85149);
        String hpValue = String.format("%.1f", health);
        TargetDisplayRenderer.drawText(gui, hpValue, nameX + hpLabelW, subY,
                TargetDisplayRenderer.applyAlpha(hpColorBase, alpha));

        // 5. Ping line: gray label + dark value
        int pingX = x + PING_COL;
        TargetDisplayRenderer.drawText(gui, "Ping: ", pingX, subY, labelColor);
        float pingLabelW = TargetDisplayRenderer.textWidth("Ping: ");
        String pingValue = ping >= 0 ? ping + "ms" : "--";
        TargetDisplayRenderer.drawText(gui, pingValue, pingX + pingLabelW, subY,
                TargetDisplayRenderer.applyAlpha(VALUE_COLOR, alpha));

        // 6. Decorative status icons (bell / gear / square)
        int iconColor = TargetDisplayRenderer.applyAlpha(ICON_COLOR, alpha);
        int iconCx = x + ICON_CENTER_X;
        drawBell(gui, iconCx, y + ICON_BELL_Y, 5f, iconColor);
        drawGear(gui, iconCx, y + ICON_GEAR_Y, 4.8f, iconColor);
        CustomRoundedRectRenderer.drawRoundedRect(gui,
                iconCx - 4, y + ICON_SQUARE_Y - 4, 8, 8, 2, iconColor);
    }
}

package geminiclient.gemini.modules.impl.visual.notice;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.GlowRenderer;
import geminiclient.gemini.customRenderer.glsl.SdfUIRenderer;
import geminiclient.gemini.modules.impl.visual.Notification;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import static geminiclient.gemini.base.MinecraftInstance.mc;

/**
 * "Mellow" 通知卡片渲染器 —— 浅色粉彩设计（参考设计稿还原）：
 * <ul>
 *   <li>近白色圆角卡片 + GLSL 柔和投影（{@link GlowRenderer} / glow_rect 着色器）</li>
 *   <li>左侧淡紫色圆形图标底盘，深色 PNG 图标居中</li>
 *   <li>双行文本：深色粗体标题 + 紫色消息（Google Sans，MSDF 渲染管线）</li>
 *   <li>右侧 Material 3 圆形波浪进度条：浅色整圈轨道 + 垂直渐变波浪进度带，
 *       按通知生命周期自 12 点方向顺时针扫描（{@link SdfUIRenderer} 波浪环
 *       SDF，fwidth 抗锯齿）</li>
 * </ul>
 * 生命周期（滑入回弹 / 滑出 / 透明度）由 {@link ModuleNotification} 统一驱动，
 * 本类只负责卡片的几何与绘制。颜色、圆角、阴影、进度环均由
 * {@link Notification} 模块的配置项提供，不做硬编码。
 */
public final class MellowNotificationRenderer {

    private MellowNotificationRenderer() {}

    // ========================
    //  布局常量（GUI 像素）
    // ========================

    public  static final int CARD_HEIGHT     = 38;
    private static final int PAD_LEFT        = 10;   // 卡片左缘 → 图标圆盘
    private static final int CIRCLE_SIZE     = 22;   // 图标圆盘直径
    private static final int ICON_SIZE       = 12;   // 图标渲染尺寸
    private static final int ICON_TEX_SIZE   = 24;   // PNG 原始尺寸
    private static final int TEXT_GAP        = 8;    // 圆盘 → 文本
    private static final int TEXT_RIGHT_PAD  = 6;    // 文本 → 进度环
    private static final int MIN_TEXT_WIDTH  = 72;   // 短文本时的最小文本区宽度
    private static final float LINE_SPACING  = 1.5f; // 标题 ↔ 消息行距

    // ========================
    //  右侧 Material 3 圆形波浪进度条
    // ========================

    // ---- 几何定义（GUI px，y 轴向下） ----
    private static final int RING_MID_RADIUS = 8;   // 进度带中线半径（标称内径 6.5 / 外径 9.5）
    private static final int RING_BAND_THICK = 3;   // 进度带厚度
    private static final int TRACK_THICK     = 2;   // 轨道线宽（细于进度带，波谷处透出）
    // 波浪中线 ±1.5px 径向偏移 → 波峰外凸至 11 / 波谷内收至 5（包围盒半径）
    private static final int RING_OUTER      = 11;  // = RING_MID_RADIUS + RING_BAND_THICK/2 + 振幅
    private static final int RING_RIGHT_PAD  = 8;   // 环外缘 → 卡片右缘
    // 起始角度：12 点方向；扫描方向：顺时针；两端圆角帽（由着色器 SDF 钳制给出）

    // ---- 波浪动画参数（振幅 / 频率见 SdfUIRenderer.WAVE_AMPLITUDE / WAVE_COUNT） ----
    /** 相位速度：2.4 s 推进一个完整相位周期（0..2π），波峰随相位顺时针漂移（与填充同向）。 */
    private static final long WAVE_PERIOD_MS = 2400;

    // ========================
    //  投影调参
    // ========================

    private static final int   SHADOW_SPREAD      = 6;
    private static final int   SHADOW_OFFSET_Y    = 2;
    private static final float SHADOW_MAX_ALPHA   = 0.16f;

    // ========================
    //  Google Sans 字体（与 TargetDisplayRenderer 共享 FONT_CACHE）
    // ========================

    private static final Identifier GOOGLE_SANS =
            Identifier.fromNamespaceAndPath("gemini", "font/googlesans-regular.ttf");
    private static final float TITLE_SIZE   = 9f;
    private static final float MESSAGE_SIZE = 8f;

    private static volatile CustomFontRenderer.GlyphFont titleFont;
    private static volatile CustomFontRenderer.GlyphFont messageFont;
    private static volatile boolean fontLoadFailed;

    /** 粗体 Google Sans（标题行），加载失败时回退 {@code null}。 */
    private static CustomFontRenderer.@Nullable GlyphFont titleFont() {
        if (fontLoadFailed) return null;
        CustomFontRenderer.GlyphFont f = titleFont;
        if (f == null) {
            try {
                f = titleFont = CustomFontRenderer.loadFont(
                        GOOGLE_SANS, TITLE_SIZE, java.awt.Font.BOLD);
            } catch (Exception e) {
                fontLoadFailed = true;
                return null;
            }
        }
        return f;
    }

    /** 常规 Google Sans（消息行），加载失败时回退 {@code null}。 */
    private static CustomFontRenderer.@Nullable GlyphFont messageFont() {
        if (fontLoadFailed) return null;
        CustomFontRenderer.GlyphFont f = messageFont;
        if (f == null) {
            try {
                f = messageFont = CustomFontRenderer.loadFont(GOOGLE_SANS, MESSAGE_SIZE);
            } catch (Exception e) {
                fontLoadFailed = true;
                return null;
            }
        }
        return f;
    }

    // ========================
    //  测量
    // ========================

    public static float titleWidth(String text) {
        CustomFontRenderer.GlyphFont f = titleFont();
        return f != null ? CustomFontRenderer.stringWidth(f, text) : mc.font.width(text);
    }

    public static float messageWidth(String text) {
        CustomFontRenderer.GlyphFont f = messageFont();
        return f != null ? CustomFontRenderer.stringWidth(f, text) : mc.font.width(text);
    }

    private static float titleLineHeight() {
        CustomFontRenderer.GlyphFont f = titleFont();
        return f != null ? f.lineHeight : mc.font.lineHeight;
    }

    private static float messageLineHeight() {
        CustomFontRenderer.GlyphFont f = messageFont();
        return f != null ? f.lineHeight : mc.font.lineHeight;
    }

    /** 给定标题 / 消息文本的完整卡片宽度。 */
    public static float cardWidth(String title, String message) {
        float textW = Math.max(titleWidth(title), messageWidth(message));
        textW = Math.max(textW, MIN_TEXT_WIDTH);
        return PAD_LEFT + CIRCLE_SIZE + TEXT_GAP + textW + TEXT_RIGHT_PAD
                + RING_OUTER * 2 + RING_RIGHT_PAD;
    }

    /** 给定通知的完整卡片宽度。 */
    public static float cardWidth(ModuleNotification n) {
        return cardWidth(n.getTitle(), n.getMessage());
    }

    // ========================
    //  颜色工具
    // ========================

    private static int scaleAlpha(int argb, float mul) {
        int a = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 0xFF) * mul)));
        return (argb & 0xFFFFFF) | (a << 24);
    }

    // ========================
    //  卡片渲染
    // ========================

    /**
     * 渲染单张 Mellow 卡片。
     *
     * @param gui     GUI graphics handle
     * @param n       通知数据（生命周期由它驱动）
     * @param style   Notification 模块（提供颜色 / 圆角 / 阴影 / 进度环配置）
     * @param targetX 布局目标 X（已考虑左右对齐）
     * @param targetY 布局目标 Y（卡片顶部）
     */
    public static void render(GuiGraphicsExtractor gui, ModuleNotification n,
                              Notification style, float targetX, float targetY) {
        render(gui, n, style, targetX, targetY, cardWidth(n));
    }

    /**
     * 以指定宽度渲染卡片 —— 通知栈传入统一宽度（参考图中各卡同宽），
     * 宽度不足时回退到该通知自身所需宽度，避免文本溢出。
     */
    public static void render(GuiGraphicsExtractor gui, ModuleNotification n,
                              Notification style, float targetX, float targetY,
                              float stackWidth) {
        if (n.isExpired()) return;

        long elapsed   = n.timeElapsed();
        long remaining = n.remainingTime();
        float alpha    = n.getAlphaFactor(elapsed, remaining);
        if (alpha <= 0.004f) return;

        float width  = Math.max(stackWidth, cardWidth(n));
        float x      = n.slideX(targetX, width, elapsed, remaining) + n.getRenderXOffset();
        int   ix     = (int) x;
        int   iy     = (int) targetY;
        int   iw     = (int) Math.ceil(width);
        int   radius = Math.min(style.cardRadius.getValue(), CARD_HEIGHT / 2);

        // ---- 1. GLSL 柔和投影（glow_rect 着色器） ----
        if (style.cardShadow.enabled) {
            int shadowA = (int) (SHADOW_MAX_ALPHA * alpha * 255);
            if (shadowA > 2) {
                GlowRenderer.drawDropShadowRoundedRect(gui, ix, iy, iw, CARD_HEIGHT,
                        radius, 0, SHADOW_OFFSET_Y, SHADOW_SPREAD, shadowA << 24);
            }
        }

        // ---- 2. 卡片主体 ----
        int cardColor = scaleAlpha(style.cardColor.getColor(), alpha);
        CustomRoundedRectRenderer.drawRoundedRect(gui, ix, iy, iw, CARD_HEIGHT, radius, cardColor);

        // ---- 3. 图标圆盘 + 等级图标 ----
        int iconBg   = scaleAlpha(style.iconBgColor.getColor(), alpha);
        int iconTint = scaleAlpha(style.iconColor.getColor(), alpha);
        int circleX  = ix + PAD_LEFT;
        int circleY  = iy + (CARD_HEIGHT - CIRCLE_SIZE) / 2;
        CustomRoundedRectRenderer.drawRoundedRect(gui, circleX, circleY,
                CIRCLE_SIZE, CIRCLE_SIZE, CIRCLE_SIZE / 2, iconBg);
        blitIcon(gui, n.getLevel().getTexture(),
                circleX + (CIRCLE_SIZE - ICON_SIZE) / 2,
                circleY + (CIRCLE_SIZE - ICON_SIZE) / 2,
                ICON_SIZE, iconTint);

        // ---- 4. 标题 + 消息（Google Sans，MSDF 管线） ----
        float textX  = ix + PAD_LEFT + CIRCLE_SIZE + TEXT_GAP;
        float blockH = titleLineHeight() + LINE_SPACING + messageLineHeight();
        float titleY = iy + (CARD_HEIGHT - blockH) / 2f;
        drawText(gui, true, n.getTitle(), textX, titleY,
                scaleAlpha(style.titleColor.getColor(), alpha));
        drawText(gui, false, n.getMessage(), textX,
                titleY + titleLineHeight() + LINE_SPACING,
                scaleAlpha(style.messageColor.getColor(), alpha));

        // ---- 5. 右侧 Material 3 圆形波浪进度条（生命周期进度） ----
        if (style.progressRing.enabled) {
            float ringCx = ix + iw - RING_RIGHT_PAD - RING_OUTER;
            float ringCy = iy + CARD_HEIGHT / 2f;
            drawWavyProgress(gui, ringCx, ringCy, n.lifeProgress(),
                    scaleAlpha(style.ringTrackColor.getColor(), alpha),
                    scaleAlpha(style.ringWaveTopColor.getColor(), alpha),
                    scaleAlpha(style.ringWaveBottomColor.getColor(), alpha));
        }
    }

    /**
     * M3E 圆形波浪进度条：平坦的浅色整圈轨道（细线宽）+ 垂直渐变的波浪进度带。
     * 进度带自 12 点方向起顺时针按生命周期进度扫描；波浪相位按
     * {@link #WAVE_PERIOD_MS} 周期推进，波峰缓慢顺时针漂移。
     */
    private static void drawWavyProgress(GuiGraphicsExtractor gui, float cx, float cy,
                                         float progress, int trackColor,
                                         int waveTopColor, int waveBottomColor) {
        CustomRoundedRectRenderer.drawRing(gui, cx, cy, RING_MID_RADIUS, TRACK_THICK, trackColor);
        float phase01 = (System.currentTimeMillis() % WAVE_PERIOD_MS) / (float) WAVE_PERIOD_MS;
        CustomRoundedRectRenderer.drawWavyRing(gui, cx, cy, RING_MID_RADIUS, RING_BAND_THICK,
                progress, phase01, waveTopColor, waveBottomColor);
    }

    /** 以 pose 缩放把 24px PNG 图标绘制为任意尺寸。 */
    private static void blitIcon(GuiGraphicsExtractor gui, Identifier texture,
                                 int x, int y, int size, int argb) {
        if ((argb >>> 24) == 0) return;
        var pose = gui.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        float scale = (float) size / ICON_TEX_SIZE;
        pose.scale(scale, scale);
        gui.blit(RenderPipelines.GUI_TEXTURED, texture,
                0, 0, 0, 0, ICON_TEX_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE, argb);
        pose.popMatrix();
    }

    /** Google Sans 文本绘制，字体不可用时回退原版字体（标题加粗）。 */
    private static void drawText(GuiGraphicsExtractor gui, boolean title, String text,
                                 float x, float y, int argb) {
        if (text == null || text.isEmpty() || (argb >>> 24) == 0) return;
        CustomFontRenderer.GlyphFont f = title ? titleFont() : messageFont();
        if (f != null) {
            CustomFontRenderer.drawString(gui, f, text, x, y, argb);
        } else if (title) {
            gui.text(mc.font, Component.literal(text).withStyle(s -> s.withBold(true)),
                    (int) x, (int) y, argb, false);
        } else {
            gui.text(mc.font, text, (int) x, (int) y, argb, false);
        }
    }
}

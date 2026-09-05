package geminiclient.gemini.base;

import geminiclient.gemini.customRenderer.cpu.CustomRectRenderer;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomBlurRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer.GlyphFont;
import geminiclient.gemini.customRenderer.glsl.SdfUIRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 主菜单 / AltManager 共享的"单色磨砂"设计语言。
 *
 * <p>无彩色体系：大面积只靠白→灰的透明度分层与深色磨砂玻璃，白色即强调色
 * （活跃指示、键盘焦点、主按钮）。错误/警告保留语义色，但不参与主色调。</p>
 */
public final class MenuUI {

    // ========================
    //  单色色板 (ARGB)
    // ========================
    public static final int TEXT_HIGH  = 0xFFEDEDED; // 主文字
    public static final int TEXT_BODY  = 0xFF9C9C9C; // 次级文字
    public static final int TEXT_FAINT = 0xFF5E5E5E; // 三级 / 提示
    public static final int TEXT_GHOST = 0xFF454545; // 角标
    public static final int WHITE      = 0xFFFFFFFF;

    /** 主按钮（白底）上的深色文字。 */
    public static final int ON_PRIMARY = 0xFF161616;

    // ========================
    //  磨砂玻璃
    // ========================
    public static final int GLASS_TOP    = 0x551A1A1A; // 面板顶部
    public static final int GLASS_BOTTOM = 0x82101010; // 面板底部
    public static final int GLASS_OUTLINE = 0x28FFFFFF; // 1px 白描边

    /** 交互填充（白）：悬停 5%、选中/激活 9%。 */
    public static final int HOVER_FILL    = 0x0DFFFFFF;
    public static final int SELECTED_FILL = 0x17FFFFFF;

    // ========================
    //  语义色（仅错误/警告，不参与主色调）
    // ========================
    public static final int ERROR = 0xFFFF8080;
    public static final int WARN  = 0xFFFFC87A;

    private MenuUI() {
    }

    // ========================
    //  玻璃面板
    // ========================

    /**
     * 磨砂玻璃面板：SDF 柔和阴影 + 区域模糊 + 深色垂直渐变 + 1px 白描边。
     *
     * @param alphaScale  入场/淡出整体透明度缩放（0~1）
     * @param blurStrength 区域模糊半径（px），面板越大建议越大
     */
    public static void glassPanel(GuiGraphicsExtractor gui, float x, float y, float w, float h,
                                  float radius, float alphaScale, float blurStrength) {
        if (w <= 0f || h <= 0f || alphaScale <= 0.01f) {
            return;
        }
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.round(w);
        int ih = Math.round(h);
        int ir = Math.round(radius);
        SdfUIRenderer.drawShadow(gui, ix, iy, iw, ih, ir, 0, 5, 16, scaleAlpha(0x52000000, alphaScale));
        CustomBlurRenderer.render(x, y, w, h, radius, scaleAlpha(0x3A101012, alphaScale), blurStrength);
        CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, ix, iy, iw, ih, ir,
                scaleAlpha(GLASS_TOP, alphaScale), scaleAlpha(GLASS_BOTTOM, alphaScale));
        CustomRoundedRectRenderer.drawRoundedOutline(gui, ix, iy, iw, ih, ir,
                scaleAlpha(GLASS_OUTLINE, alphaScale), 1);
    }

    /** 只填充不模糊的玻璃面板（叠在已模糊背景上时用，省一次模糊 pass）。 */
    public static void glassFill(GuiGraphicsExtractor gui, float x, float y, float w, float h,
                                 float radius, float alphaScale) {
        if (w <= 0f || h <= 0f || alphaScale <= 0.01f) {
            return;
        }
        CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, Math.round(x), Math.round(y),
                Math.round(w), Math.round(h), Math.round(radius),
                scaleAlpha(GLASS_TOP, alphaScale), scaleAlpha(GLASS_BOTTOM, alphaScale));
        CustomRoundedRectRenderer.drawRoundedOutline(gui, Math.round(x), Math.round(y),
                Math.round(w), Math.round(h), Math.round(radius),
                scaleAlpha(GLASS_OUTLINE, alphaScale), 1);
    }

    /** 顶部/底部可读性渐变（黑，向下/向上淡出）。 */
    public static void edgeScrim(GuiGraphicsExtractor gui, int x, int y, int w, int h, int color, boolean fromTop) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
            return;
        }
        int transparent = color & 0x00FFFFFF;
        if (fromTop) {
            CustomRectRenderer.drawRectVertGrad(gui, x, y, w, h, color, transparent);
        } else {
            CustomRectRenderer.drawRectVertGrad(gui, x, y, w, h, transparent, color);
        }
    }

    // ========================
    //  按钮
    // ========================

    /** 主按钮：白底胶囊 + 深色文字，hover 提亮。 */
    public static void primaryButton(GuiGraphicsExtractor gui, GlyphFont font, String label,
                                     float x, float y, float w, float h, float hover, float alphaScale) {
        if (alphaScale <= 0.01f) {
            return;
        }
        int fill = lerpColor(scaleAlpha(0xFFE8E8E8, alphaScale), scaleAlpha(0xFFFFFFFF, alphaScale), hover);
        CustomRoundedRectRenderer.drawRoundedRect(gui, Math.round(x), Math.round(y),
                Math.round(w), Math.round(h), Math.round(h / 2f), fill);
        drawCentered(gui, font, label, x + w / 2f, y + (h - font.lineHeight) / 2f,
                scaleAlpha(ON_PRIMARY, alphaScale));
    }

    /** 幽灵按钮：白色细描边胶囊 + 白色文字，hover 微填充。 */
    public static void ghostButton(GuiGraphicsExtractor gui, GlyphFont font, String label,
                                   float x, float y, float w, float h, float hover, float alphaScale) {
        if (alphaScale <= 0.01f) {
            return;
        }
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.round(w);
        int ih = Math.round(h);
        int ir = Math.round(h / 2f);
        CustomRoundedRectRenderer.drawRoundedRect(gui, ix, iy, iw, ih, ir,
                scaleAlpha(HOVER_FILL, alphaScale * (0.4f + hover * 0.6f)));
        CustomRoundedRectRenderer.drawRoundedOutline(gui, ix, iy, iw, ih, ir,
                scaleAlpha(lerpColor(0x33FFFFFF, 0x66FFFFFF, hover), alphaScale), 1);
        drawCentered(gui, font, label, x + w / 2f, y + (h - font.lineHeight) / 2f,
                lerpColor(scaleAlpha(TEXT_BODY, alphaScale), scaleAlpha(TEXT_HIGH, alphaScale), hover));
    }

    /** 圆形幽灵图标按钮（主菜单右上角工具钮）。 */
    public static void ghostIconButton(GuiGraphicsExtractor gui, GlyphFont font, String icon,
                                       float cx, float cy, int diameter, float hover,
                                       boolean enabled, float alphaScale) {
        if (alphaScale <= 0.01f) {
            return;
        }
        float r = diameter / 2f;
        float x = cx - r;
        float y = cy - r;
        CustomRoundedRectRenderer.drawCircle(gui, cx, cy, diameter,
                scaleAlpha(HOVER_FILL, alphaScale * (0.5f + hover * 0.8f)));
        CustomRoundedRectRenderer.drawRing(gui, cx, cy, Math.round(r - 0.5f), 1,
                scaleAlpha(lerpColor(0x24FFFFFF, 0x52FFFFFF, hover), alphaScale));
        int iconColor = !enabled
                ? scaleAlpha(TEXT_FAINT, alphaScale)
                : lerpColor(scaleAlpha(TEXT_BODY, alphaScale), scaleAlpha(TEXT_HIGH, alphaScale), hover);
        drawCentered(gui, font, icon, cx, cy - font.lineHeight / 2f, iconColor);
    }

    // ========================
    //  文字
    // ========================

    public static void drawCentered(GuiGraphicsExtractor gui, GlyphFont font, String text,
                                    float centerX, float y, int color) {
        CustomFontRenderer.drawString(gui, font, text,
                centerX - CustomFontRenderer.stringWidth(font, text) / 2f, y, color);
    }

    // ========================
    //  命中测试
    // ========================

    public static boolean inRect(double mx, double my, double x, double y, double w, double h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // ========================
    //  缓动 / 颜色工具
    // ========================

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static float easeOutCubic(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u;
    }

    public static int lerpColor(int a, int b, float t) {
        float tp = Math.clamp(t, 0f, 1f);
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * tp) << 24)
                | (Math.round(ar + (br - ar) * tp) << 16)
                | (Math.round(ag + (bg - ag) * tp) << 8)
                | Math.round(ab + (bb - ab) * tp);
    }

    public static int scaleAlpha(int argb, float scale) {
        int a = Math.round((argb >>> 24) * clamp01(scale));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public static String ellipsize(GlyphFont font, String text, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0f) {
            return "";
        }
        if (CustomFontRenderer.stringWidth(font, text) <= maxWidth) {
            return text;
        }
        String suffix = "…";
        if (CustomFontRenderer.stringWidth(font, suffix) > maxWidth) {
            return "";
        }
        int end = text.length();
        while (end > 0) {
            end = text.offsetByCodePoints(end, -1);
            String candidate = text.substring(0, end) + suffix;
            if (CustomFontRenderer.stringWidth(font, candidate) <= maxWidth) {
                return candidate;
            }
        }
        return suffix;
    }

    public static String dots(long nowMs) {
        return ".".repeat((int) ((nowMs / 400) % 4));
    }
}

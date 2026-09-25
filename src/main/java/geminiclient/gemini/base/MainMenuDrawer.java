package geminiclient.gemini.base;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer.GlyphFont;
import geminiclient.gemini.customRenderer.glsl.GlowRenderer;
import geminiclient.gemini.customRenderer.glsl.SdfUIRenderer;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Anim;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3RenderUtils;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.List;

import static geminiclient.gemini.base.MenuUI.clamp01;
import static geminiclient.gemini.base.MenuUI.lerpColor;
import static geminiclient.gemini.base.MenuUI.scaleAlpha;

/**
 * 主菜单左侧 MD3 侧边抽屉（Navigation Drawer 形态）。
 *
 * <p>贴左缘全高、右缘两角 {@link Md3Theme#R_EXTRA_LARGE} 大圆角。收起时
 * 整体滑出屏幕左侧、仅在左缘露出一个右指圆角三角形把手（SDF 绘制，
 * {@link SdfUIRenderer#drawRoundedTriangle}）；鼠标靠近左缘后抽屉向外
 * 伸出（{@link Md3Anim} Material standard 缓动），屏幕背景的模糊 + 变暗
 * 由宿主 Screen 按 {@link #drawerT()} 同步推进，形成层次。</p>
 *
 * <p>抽屉内容自上而下：GEMINI 大标题 + 副标题、分隔线、菜单行（24px
 * Material Symbols 图标 + 文字，悬停为 8% 状态层、键盘焦点为
 * secondary-container 胶囊）、底部工具行（壁纸开关 / 壁纸选择器 /
 * 语言切换）与 Github · Discord 链接、版本号。配色全部取自
 * {@link Md3Theme}（baseline light scheme）。</p>
 */
public final class MainMenuDrawer {

    /** 一行菜单：i18n 标签 + 24px Material Symbols 图标 + 点击动作。 */
    public record Item(String label, Identifier icon, boolean destructive, Runnable action) {}

    /** 工具钮行为：由宿主 Screen 注入（语言切换会触发主菜单重建）。 */
    public interface Callbacks {
        void onToggleBackground();
        boolean backgroundToggleAvailable();
        boolean backgroundEnabled();
        void onOpenWallpaperSelector();
        void onToggleLanguage();
        String version();
    }

    // ========================
    //  Metrics（紧凑档）
    // ========================
    private static final int CORNER = Md3Theme.R_LARGE;         // 16
    private static final int PAD = 12;                           // 内容左右内边距
    private static final int ROW_GAP = 2;
    private static final int ICON_SIZE = 20;
    private static final int UTILITY_DIAMETER = 28;
    private static final int UTILITY_GAP = 6;
    private static final float EXPAND_ZONE = 64f;                // 鼠标进入左缘此距离即展开
    private static final float COLLAPSE_MARGIN = 48f;            // 越过抽屉右缘此距离才收起（滞回）
    private static final int HANDLE_SIZE = 14;

    // 右缘投影配色，与 Md3Theme.elevation3 同配方
    private static final int SHADOW_A = new java.awt.Color(0x21, 0x00, 0x5D, 26).getRGB();
    private static final int SHADOW_B = new java.awt.Color(0, 0, 0, 24).getRGB();

    // ========================
    //  State
    // ========================
    private final List<Item> items;
    private final Callbacks callbacks;
    private final GlyphFont titleFont;
    private final GlyphFont subtitleFont;
    private final GlyphFont rowFont;
    private final GlyphFont smallFont;

    private final Md3Anim slide;
    private final float[] rowHover;
    private float nearT;      // 收起态把手"即将被拉开"的预告进度
    private float bgHover;
    private float gearHover;
    private float langHover;
    private float githubHover;
    private float discordHover;

    private int focusedIndex = -1;

    // 最近一帧的屏幕尺寸（layout()/handleClick 复用）
    private int screenW;
    private int screenH;

    public MainMenuDrawer(List<Item> items, Callbacks callbacks,
                          GlyphFont titleFont, GlyphFont subtitleFont,
                          GlyphFont rowFont, GlyphFont smallFont) {
        this.items = items;
        this.callbacks = callbacks;
        this.titleFont = titleFont;
        this.subtitleFont = subtitleFont;
        this.rowFont = rowFont;
        this.smallFont = smallFont;
        this.slide = new Md3Anim(Md3Anim.DURATION_MEDIUM);
        this.rowHover = new float[items.size()];
    }

    // ========================
    //  Public interaction API
    // ========================

    /** 每帧驱动：接近区判定（滞回）+ 把手预告插值。 */
    public void update(double mx, double my, float dt) {
        float t = drawerT();
        if (mx <= EXPAND_ZONE) {
            slide.setTarget(1f);
        } else if (mx > drawerWidth() + COLLAPSE_MARGIN) {
            slide.setTarget(0f);
        }
        // 中间保留区：维持当前目标不变

        float nearTarget = (t < 0.5f && mx <= EXPAND_ZONE && my >= 0 && my <= screenH) ? 1f : 0f;
        nearT += (nearTarget - nearT) * Math.min(1f, dt * 12f);
    }

    /** 展开动画进度 0..1（Md3Anim 已缓动），宿主据此推进背景模糊/变暗。 */
    public float drawerT() {
        return clamp01(slide.getValue());
    }

    public boolean isExpanded() {
        return slide.getTarget() >= 0.5f;
    }

    public void expand() {
        slide.setTarget(1f);
    }

    public void collapse() {
        slide.setTarget(0f);
    }

    public int focusedIndex() {
        return focusedIndex;
    }

    public void moveFocus(int delta) {
        int n = items.size();
        if (n == 0) return;
        if (focusedIndex < 0) focusedIndex = delta > 0 ? 0 : n - 1;
        else focusedIndex = (focusedIndex + delta + n) % n;
        expand();
    }

    public void activateFocused() {
        if (focusedIndex >= 0 && focusedIndex < items.size()) {
            items.get(focusedIndex).action().run();
        }
    }

    /** 处理一次点击，true 表示已消费。 */
    public boolean handleClick(double mx, double my) {
        float t = drawerT();
        // 收起/半开态：左缘接近区点击一律视为拉开把手
        if (t < 0.6f) {
            if (mx <= EXPAND_ZONE + 40f) {
                expand();
                return true;
            }
            return false;
        }

        Layout layout = layout();
        float drawerX = -layout.drawerW() * (1f - t);

        for (int i = 0; i < items.size(); i++) {
            if (inRect(mx, my, drawerX + PAD, layout.rowY(i), layout.rowW(), layout.rowH)) {
                focusedIndex = i;
                items.get(i).action().run();
                return true;
            }
        }
        if (inCircle(mx, my, drawerX + layout.bgCx(), layout.utilCy())) {
            callbacks.onToggleBackground();
            return true;
        }
        if (inCircle(mx, my, drawerX + layout.gearCx(), layout.utilCy())) {
            callbacks.onOpenWallpaperSelector();
            return true;
        }
        if (inCircle(mx, my, drawerX + layout.langCx(), layout.utilCy())) {
            callbacks.onToggleLanguage();
            return true;
        }
        if (layout.showLinks()) {
            float linkH = smallFont == null ? 14 : smallFont.lineHeight + 8;
            if (inRect(mx, my, drawerX + PAD - 4, layout.linksY() - 4, layout.githubW() + 8, linkH)) {
                return true;   // 占位链接：与原主菜单一致，暂不 openUri
            }
            if (inRect(mx, my, drawerX + layout.discordX() - 4, layout.linksY() - 4, layout.discordW() + 8, linkH)) {
                return true;
            }
        }
        // 展开态点抽屉右侧空白 → 主动收起
        if (mx > drawerX + layout.drawerW() + 8) {
            collapse();
            return true;
        }
        return false;
    }

    // ========================
    //  Layout
    // ========================

    private record Layout(
            float drawerW, float headerY, float dividerY,
            float rowsTop, float rowH,
            float utilCy, float bgCx, float gearCx, float langCx, float divider2Y,
            boolean showLinks, float linksY,
            float githubW, float separatorW, float discordW,
            float versionX, float versionW, float titleSpacing) {

        float rowW() { return drawerW - PAD * 2f; }
        float rowY(int i) { return rowsTop + i * (rowH + ROW_GAP); }
        float discordX() { return PAD + githubW + separatorW; }
    }

    private float drawerWidth() {
        return layout().drawerW();
    }

    private Layout layout() {
        int n = Math.max(1, items.size());

        // 标题自然宽（字距上限 6px，紧凑档）
        float charSum = 0f;
        if (titleFont != null) {
            for (int i = 0; i < "GEMINI".length(); i++) {
                charSum += CustomFontRenderer.stringWidth(titleFont, "GEMINI".substring(i, i + 1));
            }
        }
        float natural = Math.max(charSum + 5 * 6f + PAD * 2f, 230f);
        if (rowFont != null) {
            float maxRow = 0f;
            for (Item item : items) {
                maxRow = Math.max(maxRow, CustomFontRenderer.stringWidth(rowFont, item.label()));
            }
            natural = Math.max(natural, PAD * 2f + 10f + ICON_SIZE + 10f + maxRow + 14f);
        }
        float drawerW = Math.min(natural, Math.max(220f, screenW * 0.62f));
        // 标题字距：抽屉装不下时压缩
        float spacing = clamp((drawerW - PAD * 2f - charSum) / 5f, 0f, 6f);

        // 纵向：header 定顶、footer 定底，行区吃中间
        float headerH = titleFont == null ? 40f : titleFont.lineHeight;
        float subtitleH = subtitleFont == null ? 14f : subtitleFont.lineHeight;
        float headerY = 20f;
        float dividerY = headerY + headerH + 2f + subtitleH + 10f;

        float utilCy = screenH - 76f;
        float divider2Y = utilCy - UTILITY_DIAMETER / 2f - 10f;
        float linksY = screenH - 26f;
        boolean showLinks = screenH >= 400 && smallFont != null;

        float rowsTop = dividerY + 10f;
        float rowsBudget = divider2Y - 10f - rowsTop;
        float rowH = clamp(rowsBudget / n - ROW_GAP, 26f, 36f);

        float step = UTILITY_DIAMETER + UTILITY_GAP;
        float bgCx = PAD + UTILITY_DIAMETER / 2f;
        float gearCx = bgCx + step;
        float langCx = gearCx + step;

        float githubW = smallFont == null ? 0f : CustomFontRenderer.stringWidth(smallFont, I18n.tr("Github"));
        float separatorW = smallFont == null ? 0f : CustomFontRenderer.stringWidth(smallFont, "   ·   ");
        float discordW = smallFont == null ? 0f : CustomFontRenderer.stringWidth(smallFont, I18n.tr("Discord"));
        String version = "v" + callbacks.version();
        float versionW = smallFont == null ? 0f : CustomFontRenderer.stringWidth(smallFont, version);
        float versionX = drawerW - PAD - versionW;

        return new Layout(drawerW, headerY, dividerY, rowsTop, rowH,
                utilCy, bgCx, gearCx, langCx, divider2Y,
                showLinks, linksY, githubW, separatorW, discordW,
                versionX, versionW, spacing);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ========================
    //  Render
    // ========================

    /**
     * 绘制抽屉与把手。应在壁纸层与全屏模糊变暗之后调用；内部按
     * drawerT 决定滑出位置与内容透明度。
     */
    public void render(GuiGraphicsExtractor gui, int screenW, int screenH,
                       double mx, double my, float dt, float elapsed, float entryAlpha) {
        this.screenW = screenW;
        this.screenH = screenH;

        float t = drawerT();
        Layout layout = layout();
        float drawerX = -layout.drawerW() * (1f - t);

        updateHoverStates(layout, drawerX, t, mx, my, dt);

        // ── 抽屉本体：左缘四角圆出屏幕，可见部分即贴边直边 + 右缘大圆角 ──
        if (t > 0.004f) {
            int bx = Math.round(drawerX - CORNER);
            int bw = Math.round(layout.drawerW() + CORNER);
            int bh = screenH + CORNER * 2;
            GlowRenderer.drawDropShadowRoundedRect(gui, bx, -CORNER, bw, bh, CORNER,
                    3, 0, 16, scaleAlpha(SHADOW_B, entryAlpha));
            GlowRenderer.drawDropShadowRoundedRect(gui, bx, -CORNER, bw, bh, CORNER,
                    2, 0, 10, scaleAlpha(SHADOW_A, entryAlpha));
            CustomRoundedRectRenderer.drawRoundedRect(gui, bx, -CORNER, bw, bh, CORNER,
                    scaleAlpha(Md3Theme.withAlpha(Md3Theme.SURFACE_CONTAINER, 0.94f), entryAlpha));
        }

        // ── 内容 ──
        if (t > 0.02f) {
            float headerA = clamp01((t - 0.15f) * 2.6f) * entryAlpha;
            drawHeader(gui, layout, drawerX, headerA);
            drawRows(gui, layout, drawerX, t, entryAlpha);
            drawFooter(gui, layout, drawerX, headerA);
        }

        drawHandle(gui, t, elapsed, entryAlpha);
    }

    private void drawHeader(GuiGraphicsExtractor gui, Layout layout, float drawerX, float alpha) {
        if (titleFont == null || alpha <= 0.01f) return;
        float x = drawerX + PAD;
        float y = layout.headerY();
        int color = ((int) (alpha * 255) << 24) | (Md3Theme.PRIMARY & 0x00FFFFFF);
        for (int i = 0; i < "GEMINI".length(); i++) {
            String ch = "GEMINI".substring(i, i + 1);
            CustomFontRenderer.drawString(gui, titleFont, ch, x, y, color);
            x += CustomFontRenderer.stringWidth(titleFont, ch) + layout.titleSpacing();
        }
        if (subtitleFont != null) {
            CustomFontRenderer.drawString(gui, subtitleFont, I18n.tr("Modern Minecraft Client"),
                    drawerX + PAD, y + titleFont.lineHeight + 2f,
                    ((int) (alpha * 220) << 24) | (Md3Theme.ON_SURFACE_VARIANT & 0x00FFFFFF));
        }
        drawDivider(gui, drawerX, layout.dividerY(), layout.drawerW(), alpha);
    }

    private void drawRows(GuiGraphicsExtractor gui, Layout layout, float drawerX,
                          float t, float entryAlpha) {
        if (rowFont == null) return;
        for (int i = 0; i < items.size(); i++) {
            // 逐项错帧淡入（与旧 Dock 的 stagger 同一语言）
            float reveal = clamp01((t - 0.2f - i * 0.05f) * 3.5f) * entryAlpha;
            if (reveal <= 0.01f) continue;

            Item item = items.get(i);
            float hp = rowHover[i];
            boolean focused = i == focusedIndex;
            float rowY = layout.rowY(i);
            int rx = Math.round(drawerX + PAD);
            int ry = Math.round(rowY);
            int rw = Math.round(layout.rowW());
            int rh = Math.round(layout.rowH);

            // 状态层：焦点 = secondary-container 实底胶囊；悬停 = 8% on-surface
            if (focused) {
                CustomRoundedRectRenderer.drawRoundedRect(gui, rx, ry, rw, rh, rh / 2,
                        scaleAlpha(Md3Theme.SECONDARY_CONTAINER, reveal));
            } else if (hp > 0.01f) {
                CustomRoundedRectRenderer.drawRoundedRect(gui, rx, ry, rw, rh, rh / 2,
                        scaleAlpha(Md3Theme.hoverState(Md3Theme.ON_SURFACE), reveal * hp));
            }

            int baseText = item.destructive() ? Md3Theme.ERROR : Md3Theme.ON_SURFACE_VARIANT;
            int activeText = item.destructive() ? Md3Theme.ERROR : Md3Theme.ON_SURFACE;
            int iconColor;
            int textColor;
            if (focused) {
                iconColor = scaleAlpha(item.destructive() ? Md3Theme.ERROR : Md3Theme.PRIMARY, reveal);
                textColor = scaleAlpha(item.destructive() ? Md3Theme.ERROR : Md3Theme.ON_SECONDARY_CONTAINER, reveal);
            } else {
                iconColor = scaleAlpha(lerpColor(baseText, activeText, hp), reveal);
                textColor = scaleAlpha(lerpColor(baseText, activeText, hp), reveal);
            }

            int iconCx = Math.round(drawerX + PAD + 10 + ICON_SIZE / 2f);
            int iconCy = Math.round(rowY + layout.rowH / 2f);
            Md3RenderUtils.drawTextureIcon(gui, item.icon(), iconCx, iconCy, ICON_SIZE, iconColor);

            float textX = drawerX + PAD + 10 + ICON_SIZE + 10f;
            float textY = rowY + (layout.rowH - rowFont.lineHeight) / 2f;
            CustomFontRenderer.drawString(gui, rowFont, item.label(), textX, textY, textColor);
        }
    }

    private void drawFooter(GuiGraphicsExtractor gui, Layout layout, float drawerX, float alpha) {
        if (smallFont == null || alpha <= 0.01f) return;

        drawDivider(gui, drawerX, layout.divider2Y(), layout.drawerW(), alpha);

        boolean bgAvailable = callbacks.backgroundToggleAvailable();
        utilityButton(gui, "BG", drawerX + layout.bgCx(), layout.utilCy(), bgHover, bgAvailable, alpha);
        if (bgAvailable && callbacks.backgroundEnabled()) {
            CustomRoundedRectRenderer.drawCircle(gui,
                    drawerX + layout.bgCx() + UTILITY_DIAMETER / 2f - 4f,
                    layout.utilCy() - UTILITY_DIAMETER / 2f + 4f,
                    3, scaleAlpha(Md3Theme.PRIMARY, alpha));
        }
        utilityButton(gui, "⚙", drawerX + layout.gearCx(), layout.utilCy(), gearHover, true, alpha);
        utilityButton(gui, I18n.getLanguage().label(), drawerX + layout.langCx(),
                layout.utilCy(), langHover, true, alpha);

        if (!layout.showLinks()) return;

        int faint = ((int) (alpha * 255) << 24) | (Md3Theme.ON_SURFACE_VARIANT & 0x00FFFFFF);
        int high = ((int) (alpha * 255) << 24) | (Md3Theme.ON_SURFACE & 0x00FFFFFF);
        int ghost = ((int) (alpha * 140) << 24) | (Md3Theme.OUTLINE & 0x00FFFFFF);
        float x = drawerX + PAD;
        CustomFontRenderer.drawString(gui, smallFont, I18n.tr("Github"), x, layout.linksY(),
                lerpColor(faint, high, githubHover));
        float sepX = x + layout.githubW();
        CustomFontRenderer.drawString(gui, smallFont, "   ·   ", sepX, layout.linksY(), ghost);
        CustomFontRenderer.drawString(gui, smallFont, I18n.tr("Discord"),
                sepX + layout.separatorW(), layout.linksY(), lerpColor(faint, high, discordHover));
        CustomFontRenderer.drawString(gui, smallFont, "v" + callbacks.version(),
                drawerX + layout.versionX(), layout.linksY(), ghost);
    }

    /** MD3 圆形幽灵工具钮：1px 描边圆 + 文字字形，8% 状态层悬停。 */
    private void utilityButton(GuiGraphicsExtractor gui, String glyph, float cx, float cy,
                               float hover, boolean enabled, float alpha) {
        if (smallFont == null) return;
        if (enabled) {
            if (hover > 0.01f) {
                CustomRoundedRectRenderer.drawCircle(gui, cx, cy, UTILITY_DIAMETER,
                        scaleAlpha(Md3Theme.hoverState(Md3Theme.ON_SURFACE), alpha * hover));
            }
            CustomRoundedRectRenderer.drawRing(gui, cx, cy, UTILITY_DIAMETER / 2 - 1, 1,
                    scaleAlpha(lerpColor(Md3Theme.OUTLINE_VARIANT, Md3Theme.OUTLINE, hover), alpha));
        }
        int color = enabled
                ? scaleAlpha(lerpColor(Md3Theme.ON_SURFACE_VARIANT, Md3Theme.ON_SURFACE, hover), alpha)
                : scaleAlpha(Md3Theme.disabledContent(Md3Theme.ON_SURFACE_VARIANT), alpha);
        MenuUI.drawCentered(gui, smallFont, glyph, cx, cy - smallFont.lineHeight / 2f, color);
    }

    private void drawHandle(GuiGraphicsExtractor gui, float t, float elapsed, float entryAlpha) {
        // 与加载画面交接错开入场；抽屉伸出前 1/4 行程内淡出（被抽屉盖住）
        float intro = MenuUI.easeOutCubic(clamp01((elapsed - 0.35f) * 2.5f)) * entryAlpha;
        float fade = clamp01(1f - t * 4f);
        float alpha = intro * fade;
        if (alpha <= 0.02f) return;

        int size = HANDLE_SIZE + Math.round(nearT * 2f);
        float cx = size / 2f + nearT * 1.5f + t * 24f;
        float cy = screenH / 2f - nearT * 1f;
        int color = lerpColor(
                scaleAlpha(Md3Theme.PRIMARY_CONTAINER, alpha),
                scaleAlpha(Md3Theme.PRIMARY, alpha),
                Math.max(nearT, t));
        SdfUIRenderer.drawRoundedTriangle(gui, cx, cy, size, 35, color);
    }

    private static void drawDivider(GuiGraphicsExtractor gui, float drawerX, float y,
                                    float drawerW, float alpha) {
        if (alpha <= 0.01f) return;
        int x1 = Math.round(drawerX + PAD);
        int x2 = Math.round(drawerX + drawerW - PAD);
        if (x2 <= x1) return;
        gui.fill(x1, Math.round(y), x2 + 1, Math.round(y) + 1,
                Md3Theme.withAlpha(Md3Theme.OUTLINE_VARIANT, alpha));
    }

    // ========================
    //  Hover state machine
    // ========================

    private void updateHoverStates(Layout layout, float drawerX, float t,
                                   double mx, double my, float dt) {
        float k = Math.min(1f, dt * 12f);
        boolean open = t >= 0.6f;
        for (int i = 0; i < items.size(); i++) {
            boolean over = open && inRect(mx, my, drawerX + PAD, layout.rowY(i),
                    layout.rowW(), layout.rowH);
            rowHover[i] += ((over || i == focusedIndex ? 1f : 0f) - rowHover[i]) * k;
        }
        bgHover += ((open && inCircle(mx, my, drawerX + layout.bgCx(), layout.utilCy()) ? 1f : 0f) - bgHover) * k;
        gearHover += ((open && inCircle(mx, my, drawerX + layout.gearCx(), layout.utilCy()) ? 1f : 0f) - gearHover) * k;
        langHover += ((open && inCircle(mx, my, drawerX + layout.langCx(), layout.utilCy()) ? 1f : 0f) - langHover) * k;

        boolean links = open && layout.showLinks();
        float linkH = smallFont == null ? 14 : smallFont.lineHeight + 8;
        githubHover += ((links && inRect(mx, my, drawerX + PAD - 4, layout.linksY() - 4,
                layout.githubW() + 8, linkH) ? 1f : 0f) - githubHover) * k;
        discordHover += ((links && inRect(mx, my, drawerX + layout.discordX() - 4, layout.linksY() - 4,
                layout.discordW() + 8, linkH) ? 1f : 0f) - discordHover) * k;
    }

    private static boolean inRect(double mx, double my, double x, double y, double w, double h) {
        return MenuUI.inRect(mx, my, x, y, w, h);
    }

    private static boolean inCircle(double mx, double my, double cx, double cy) {
        double dx = mx - cx, dy = my - cy;
        double r = UTILITY_DIAMETER / 2.0 + 3.0;
        return dx * dx + dy * dy <= r * r;
    }
}

package geminiclient.gemini.base.alt;

import com.mojang.blaze3d.platform.InputConstants;
import geminiclient.gemini.customRenderer.cpu.CustomRectRenderer;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomBlurRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer.GlyphFont;
import geminiclient.gemini.customRenderer.glsl.InfiniteGridRenderer;
import geminiclient.gemini.customRenderer.glsl.SdfUIRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Alt Manager —— 与主菜单同一套玻璃质感视觉语言的账号管理界面。
 *
 * <p>GLSL 透视网格上叠加区域模糊、渐变玻璃面板、账号状态胶囊及滑动/
 * 聚焦动效。模态框使用更高层级的磨砂材质与 SDF 柔和阴影，遮罩淡入时
 * 整体上浮。</p>
 *
 * <p>功能：账号列表（名称 / 类型 / 应用状态）、Microsoft 登录（自动 +
 * 手动两种模式）、离线账号、删除、应用为当前会话账号。</p>
 */
public class AltManagerScreen extends Screen {

    // ========================
    // Layout Constants
    // ========================
    private static final float CONTENT_MAX_W = 1280f;
    private static final float CONTENT_MIN_PAD = 56f;
    private static final float TITLE_Y = 34f;
    private static final float SUBTITLE_Y = 78f;
    private static final float LIST_TOP = 104f;
    private static final float ROW_H = 42f;
    private static final float ACTIONS_Y_FROM_BOTTOM = 84f;
    private static final float STATUS_Y_FROM_BOTTOM = 110f;
    private static final float FOOTER_BOTTOM_PAD = 30f;

    // ========================
    // Color Constants (ARGB)
    // ========================
    private static final int ACCENT          = 0xFF89DDFF;
    private static final int TEXT_IDLE       = 0xFF9A9A9A;
    private static final int TEXT_HOVER      = 0xFFEAF6FF;
    private static final int TITLE_COLOR     = 0xFFFFFFFF;
    private static final int TITLE_GRADIENT  = 0xFFA8DCFF;
    private static final int SUBTITLE_COLOR  = 0xFF7A7A7A;
    private static final int HINT_COLOR      = 0xFF4A4A4A;
    private static final int VERSION_COLOR   = 0xFF666666;
    private static final int ERROR_COLOR     = 0xFFFF8080;
    private static final int WARN_COLOR      = 0xFFFFC87A;
    private static final int PANEL_FILL      = 0xFA121216;
    private static final int PLACEHOLDER     = 0xFF5A5A5A;
    private static final int GLASS_TOP       = 0x9C17202A;
    private static final int GLASS_BOTTOM    = 0xC20A0E15;
    private static final int GLASS_OUTLINE   = 0x5A8CC9DD;
    private static final int ROW_HOVER_FILL  = 0xA21A2B39;
    private static final int ROW_SELECTED    = 0xB3213442;

    private static final String MOD_VERSION = "0.1.0";

    // ========================
    // Fonts
    // ========================
    private static final Identifier FONT_BOLD =
            Identifier.fromNamespaceAndPath("gemini", "font/sourcehansanssc-bold.ttf");
    private static final Identifier FONT_MEDIUM =
            Identifier.fromNamespaceAndPath("gemini", "font/sourcehansanssc-medium.ttf");
    private static final Identifier FONT_LIGHT =
            Identifier.fromNamespaceAndPath("gemini", "font/sourcehansanssc-light.ttf");

    private static GlyphFont titleFont;      // bold 24
    private static GlyphFont bodyFont;       // light 12 — 副标题 / 正文 / 状态
    private static GlyphFont nameFont;       // medium 14 — 列表账号名
    private static GlyphFont tinyFont;       // light 10 — 列表次级信息
    private static GlyphFont linkFont;       // medium 12 — 排版链接
    private static GlyphFont hintFont;       // light 11 — 底部提示 / 角标
    private static GlyphFont modalTitleFont; // bold 17
    private static GlyphFont fieldFont;      // medium 13 — 输入框

    private static void ensureFontsLoaded() {
        if (titleFont == null)      titleFont = CustomFontRenderer.loadFont(FONT_BOLD, 24f);
        if (bodyFont == null)       bodyFont = CustomFontRenderer.loadFont(FONT_LIGHT, 12f);
        if (nameFont == null)       nameFont = CustomFontRenderer.loadFont(FONT_MEDIUM, 14f);
        if (tinyFont == null)       tinyFont = CustomFontRenderer.loadFont(FONT_LIGHT, 10f);
        if (linkFont == null)       linkFont = CustomFontRenderer.loadFont(FONT_MEDIUM, 12f);
        if (hintFont == null)       hintFont = CustomFontRenderer.loadFont(FONT_LIGHT, 11f);
        if (modalTitleFont == null) modalTitleFont = CustomFontRenderer.loadFont(FONT_BOLD, 17f);
        if (fieldFont == null)      fieldFont = CustomFontRenderer.loadFont(FONT_MEDIUM, 13f);
    }

    // ========================
    // Animation Constants
    // ========================
    private static final float HOVER_SPEED      = 12f;
    private static final float ENTRY_FADE_SPEED = 4f;
    private static final float ENTRY_STAGGER    = 0.05f;
    private static final float TITLE_STAGGER    = 0.045f;
    private static final float ENTRY_SLIDE_PX   = 16f;
    private static final float HOVER_SLIDE_PX   = 5f;
    private static final float DIM_FACTOR       = 0.30f;
    private static final float MODAL_SPEED      = 10f;
    private static final float SCROLL_SPEED     = 14f;
    private static final float TITLE_SPACING_PX = 9f;

    // ========================
    // Inner Types
    // ========================

    private record Link(String label, int idleColor, boolean enabled, Runnable action) {}

    /** 每个链接位点持有一组悬停进度值。 */
    private static final class HoverSet {
        private float[] values = new float[0];

        /** 只增不缩，保留已有进度。 */
        void ensure(int n) {
            if (values.length < n) values = java.util.Arrays.copyOf(values, n);
        }

        float get(int i) {
            return i < values.length ? values[i] : 0f;
        }

        void update(int i, boolean hovered, float dt) {
            ensure(i + 1);
            float target = hovered ? 1f : 0f;
            values[i] += (target - values[i]) * dt * HOVER_SPEED;
        }

        void reset() {
            values = new float[0];
        }
    }

    /** 模态框面板矩形。 */
    private record PanelRect(float x, float y, float w, float h) {}

    // ========================
    // State
    // ========================

    private final Screen parent;
    private final List<AltAccount> accounts;
    private int selected = -1;
    private int hoveredRow = -1;
    private float[] rowHover = new float[0];
    private float anyHover;
    private float entryAlpha;
    private long screenOpenTime;
    private long lastFrameMs;

    // 滚动
    private float scrollOffset;
    private float targetScroll;

    // 模态框
    private Modal modal;
    private Modal closingModal; // 淡出期间继续渲染（不可交互）
    private float modalAlpha;

    // 操作链接
    private final HoverSet actionHover = new HoverSet();

    // 状态消息（底部瞬时提示）
    private String statusMessage;
    private int statusColor;
    private long statusSetMs;

    // Microsoft「应用」时的令牌刷新
    private CompletableFuture<AltAccount> applyingFuture;
    private AltAccount applyingTarget;
    private volatile String applyingStatus = "";

    // 双击检测
    private long lastClickMs;
    private int lastClickRow = -1;

    public AltManagerScreen(Screen parent) {
        super(Component.literal("Alt Manager"));
        this.parent = parent;
        this.accounts = AltManager.load();
        // 预选上次使用的账号
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).isActive()) {
                selected = i;
                break;
            }
        }
        screenOpenTime = System.currentTimeMillis();
        lastFrameMs = screenOpenTime;
    }

    @Override
    protected void init() {
        lastFrameMs = System.currentTimeMillis();
    }

    // ========================
    // Main render
    // ========================

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        ensureFontsLoaded();
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastFrameMs) / 1000f, 0.1f);
        lastFrameMs = now;
        float elapsed = (now - screenOpenTime) / 1000f;

        entryAlpha += (1f - entryAlpha) * dt * ENTRY_FADE_SPEED;
        if (entryAlpha > 0.99f) entryAlpha = 1f;

        // ── 1. GLSL 背景 ─────────────────────────────
        InfiniteGridRenderer.render(elapsed);

        // ── 2. 动画与异步任务轮询 ─────────────────────
        boolean interactive = modal == null;
        updateRowHover(mouseX, mouseY, dt, interactive);
        scrollOffset += (targetScroll - scrollOffset) * dt * SCROLL_SPEED;
        modalAlpha += (((modal != null) ? 1f : 0f) - modalAlpha) * dt * MODAL_SPEED;
        if (modal == null && closingModal != null && modalAlpha < 0.02f) {
            closingModal = null;
        }
        pollApplying();

        // ── 3. 磨砂工作区 ───────────────────────────
        drawWorkspaceChrome(gui, elapsed);

        // ── 4. 头部 ─────────────────────────────────
        drawHeader(gui, elapsed);

        // ── 5. 账号列表 ─────────────────────────────
        if (accounts.isEmpty()) {
            drawEmptyState(gui, elapsed);
        } else {
            drawList(gui, mouseX, mouseY, elapsed, interactive);
        }

        // ── 6. 操作链接 / 状态消息 / 页脚 ─────────────
        drawActions(gui, mouseX, mouseY, dt, elapsed, interactive);
        drawStatus(gui, now);
        drawFooter(gui, elapsed);

        // ── 7. 模态框（最后提交，压在最上层）─────────
        if (modal != null) {
            modal.extractWithChrome(gui, mouseX, mouseY, dt, now);
        } else if (closingModal != null && modalAlpha > 0.01f) {
            closingModal.extractWithChrome(gui, -1, -1, dt, now);
        }
    }

    private void drawWorkspaceChrome(GuiGraphicsExtractor gui, float elapsed) {
        float reveal = easeOutCubic(clamp01((elapsed - 0.15f) * 2.8f)) * entryAlpha;
        if (reveal <= 0.01f) return;

        int x = Math.round(contentLeft() - 16f);
        int y = Math.round(LIST_TOP - 10f + (1f - reveal) * 8f);
        int w = Math.round(contentWidth() + 32f);
        int h = Math.max(72, Math.round(listViewportH() + 18f));
        int r = 14;

        SdfUIRenderer.drawShadow(gui, x, y, w, h, r,
                0, 7, 22, scaleAlpha(0x72000000, reveal));
        CustomBlurRenderer.render(x, y, w, h, r,
                scaleAlpha(0x54101820, reveal), 8f);
        CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, x, y, w, h, r,
                scaleAlpha(GLASS_TOP, reveal), scaleAlpha(GLASS_BOTTOM, reveal));
        CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, w, h, r,
                scaleAlpha(GLASS_OUTLINE, reveal), 1);

        SdfUIRenderer.drawCircle(gui, contentRight() + 34f, 54f, 118,
                scaleAlpha(0x1489DDFF, reveal));
    }

    // ========================
    // 头部
    // ========================

    private void drawHeader(GuiGraphicsExtractor gui, float elapsed) {
        // 标题：逐字揭示 + 白→青渐变（与主菜单一致）
        String title = "ALT MANAGER";
        float left = contentLeft();
        float cx = left;
        int letterIndex = 0;
        int letterCount = title.replace(" ", "").length();

        for (int i = 0; i < title.length();) {
            int cp = title.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            float chW = CustomFontRenderer.stringWidth(titleFont, ch);
            if (!ch.isBlank()) {
                float reveal = easeOutCubic(clamp01((elapsed - 0.05f - letterIndex * TITLE_STAGGER) * 3f));
                int alpha = (int) (entryAlpha * reveal * 255);
                if (alpha > 0) {
                    float t = letterCount <= 1 ? 0f : (float) letterIndex / (letterCount - 1);
                    int base = lerpColor(TITLE_COLOR, TITLE_GRADIENT, t);
                    CustomFontRenderer.drawString(gui, titleFont, ch, cx, TITLE_Y,
                            (alpha << 24) | (base & 0x00FFFFFF));
                }
                letterIndex++;
            }
            cx += chW + TITLE_SPACING_PX;
            i += Character.charCount(cp);
        }

        // 强调线：从左侧画出
        float titleW = computeSpacedWidth(titleFont, title, TITLE_SPACING_PX);
        float progress = easeOutCubic(clamp01((elapsed - 0.35f) * 2.4f));
        if (progress > 0.01f) {
            int a = (int) (entryAlpha * progress * 255);
            CustomRectRenderer.drawRect(gui, Math.round(left), (int) (TITLE_Y + 30f),
                    (int) (titleW * 0.28f * progress), 1, (a << 24) | (ACCENT & 0x00FFFFFF));
        }

        // 副标题：账号数 + 当前会话
        float reveal = easeOutCubic(clamp01((elapsed - 0.45f) * 2.5f));
        int subAlpha = (int) (entryAlpha * reveal * 255);
        if (subAlpha > 0) {
            String sub = "共 " + accounts.size() + " 个账号    ·    当前会话："
                    + AltManager.currentSessionName();
            CustomFontRenderer.drawString(gui, bodyFont, sub, left, SUBTITLE_Y,
                    (subAlpha << 24) | (SUBTITLE_COLOR & 0x00FFFFFF));
        }
    }

    // ========================
    // 账号列表
    // ========================

    private float listViewportH() {
        return this.height - STATUS_Y_FROM_BOTTOM - 8f - LIST_TOP;
    }

    private float contentWidth() {
        float available = Math.max(260f, this.width - CONTENT_MIN_PAD * 2f);
        return Math.max(260f, Math.min(available, CONTENT_MAX_W));
    }

    private float contentLeft() {
        return (this.width - contentWidth()) / 2f;
    }

    private float contentRight() {
        return contentLeft() + contentWidth();
    }

    private float listLeft() {
        return contentLeft();
    }

    private float listRight() {
        return contentRight();
    }

    private float maxScroll() {
        return Math.max(0f, accounts.size() * ROW_H - listViewportH());
    }

    private void updateRowHover(int mouseX, int mouseY, float dt, boolean interactive) {
        if (rowHover.length != accounts.size()) rowHover = new float[accounts.size()];
        hoveredRow = interactive ? rowAt(mouseX, mouseY) : -1;
        for (int i = 0; i < accounts.size(); i++) {
            boolean over = i == hoveredRow;
            rowHover[i] += ((over ? 1f : 0f) - rowHover[i]) * dt * HOVER_SPEED;
        }
        float anyTarget = hoveredRow >= 0 ? 1f : 0f;
        anyHover += (anyTarget - anyHover) * dt * HOVER_SPEED;
    }

    private int rowAt(double mx, double my) {
        float viewH = listViewportH();
        if (mx < listLeft() - 12 || mx > listRight()) return -1;
        if (my < LIST_TOP || my > LIST_TOP + viewH) return -1;
        int row = (int) ((my - LIST_TOP + scrollOffset) / ROW_H);
        return row >= 0 && row < accounts.size() ? row : -1;
    }

    private void drawList(GuiGraphicsExtractor gui, int mouseX, int mouseY, float elapsed, boolean interactive) {
        float viewH = listViewportH();
        gui.enableScissor(0, (int) LIST_TOP - 2, this.width, (int) (LIST_TOP + viewH) + 2);

        for (int i = 0; i < accounts.size(); i++) {
            AltAccount acc = accounts.get(i);
            float rowY = LIST_TOP + i * ROW_H - scrollOffset;
            if (rowY + ROW_H < LIST_TOP - 4 || rowY > LIST_TOP + viewH + 4) continue;

            float reveal = easeOutCubic(clamp01((elapsed - 0.30f - i * ENTRY_STAGGER) * 3f));
            if (reveal <= 0.01f) continue;
            float slideIn = (1f - reveal) * -ENTRY_SLIDE_PX;

            float hp = interactive ? rowHover[i] : 0f;
            boolean isSelected = i == selected;

            float dim = 1f - DIM_FACTOR * anyHover * (1f - hp);
            int alpha = (int) (entryAlpha * reveal * dim * 255);
            if (alpha <= 0) continue;

            float left = listLeft();
            int cardX = Math.round(left - 8f);
            int cardY = Math.round(rowY + 3f);
            int cardW = Math.max(40, Math.round(listRight() - cardX - 8f));
            int cardH = Math.round(ROW_H - 6f);
            float cardStrength = Math.max(hp, isSelected ? 0.68f : 0f);
            if (cardStrength > 0.01f) {
                int fill = isSelected ? ROW_SELECTED : ROW_HOVER_FILL;
                CustomRoundedRectRenderer.drawRoundedRectHorizGrad(gui,
                        cardX, cardY, cardW, cardH, 8,
                        scaleAlpha(fill, reveal * cardStrength),
                        scaleAlpha(0x5414202A, reveal * cardStrength));
                CustomRoundedRectRenderer.drawRoundedOutline(gui,
                        cardX, cardY, cardW, cardH, 8,
                        scaleAlpha(isSelected ? ACCENT : GLASS_OUTLINE,
                                reveal * cardStrength * (isSelected ? 0.55f : 0.7f)), 1);
            }

            // 指示条：悬停生长；选中常驻
            float lineH = ROW_H - 14f;
            float barTarget = Math.max(hp, isSelected ? 0.9f : 0f);
            if (barTarget > 0.01f) {
                float barH = lineH * barTarget;
                float barY = rowY + (ROW_H - barH) / 2f - 1f;
                int barAlpha = (int) (alpha * barTarget);
                CustomRectRenderer.drawRect(gui, Math.round(left - 12f + slideIn), (int) barY,
                        2, (int) barH, (barAlpha << 24) | (ACCENT & 0x00FFFFFF));
            }

            float textX = left + 16f + slideIn + hp * HOVER_SLIDE_PX;

            // 账号名
            int nameColor = lerpColor(
                    (alpha << 24) | ((isSelected ? TEXT_HOVER : TEXT_IDLE) & 0x00FFFFFF),
                    (alpha << 24) | (TEXT_HOVER & 0x00FFFFFF), hp);
            CustomFontRenderer.drawString(gui, nameFont, acc.getName(), textX, rowY + 5f, nameColor);

            // 次级信息：类型 · 短 UUID
            String sub = acc.typeLabel() + "  ·  " + acc.shortUuid();
            int subAlpha = (int) (alpha * 0.75f);
            CustomFontRenderer.drawString(gui, tinyFont, sub, textX, rowY + 24f,
                    (subAlpha << 24) | (SUBTITLE_COLOR & 0x00FFFFFF));

            // 右侧：应用状态
            drawRowStatus(gui, acc, rowY, alpha);
        }

        gui.disableScissor();
        drawScrollbar(gui, viewH);
    }

    private void drawRowStatus(GuiGraphicsExtractor gui, AltAccount acc, float rowY, int rowAlpha) {
        String text;
        int color;
        if (AltManager.isCurrent(acc)) {
            text = "使用中";
            color = ACCENT;
        } else if (acc.isActive()) {
            text = "上次使用";
            color = VERSION_COLOR;
        } else {
            return;
        }
        float w = CustomFontRenderer.stringWidth(hintFont, text);
        float x = listRight() - w;
        float y = rowY + (ROW_H - hintFont.lineHeight) / 2f - 1f;
        int a = (int) (rowAlpha * 0.95f);
        int chipX = Math.round(x - 17f);
        int chipY = Math.round(y - 5f);
        int chipW = Math.round(w + 25f);
        int chipH = Math.round(hintFont.lineHeight + 10f);
        CustomRoundedRectRenderer.drawRoundedRect(gui, chipX, chipY, chipW, chipH,
                chipH / 2, scaleAlpha(color, a / 255f * 0.14f));
        CustomRoundedRectRenderer.drawRoundedOutline(gui, chipX, chipY, chipW, chipH,
                chipH / 2, scaleAlpha(color, a / 255f * 0.48f), 1);
        CustomFontRenderer.drawString(gui, hintFont, text, x, y, (a << 24) | (color & 0x00FFFFFF));
        if (AltManager.isCurrent(acc)) {
            float sqY = y + (hintFont.lineHeight - 3f) / 2f;
            CustomRectRenderer.drawRect(gui, (int) (x - 8), (int) sqY, 3, 3,
                    (a << 24) | (ACCENT & 0x00FFFFFF));
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor gui, float viewH) {
        float max = maxScroll();
        if (max <= 0.5f) return;
        float contentH = accounts.size() * ROW_H;
        float barH = Math.max(24f, viewH * viewH / contentH);
        float barY = LIST_TOP + (viewH - barH) * (scrollOffset / max);
        int alpha = (int) (entryAlpha * 0.35f * 255);
        CustomRectRenderer.drawRect(gui, Math.round(listRight() + 12f), (int) barY, 2, (int) barH,
                (alpha << 24) | (ACCENT & 0x00FFFFFF));
    }

    private void drawEmptyState(GuiGraphicsExtractor gui, float elapsed) {
        float reveal = easeOutCubic(clamp01((elapsed - 0.4f) * 2f));
        int alpha = (int) (entryAlpha * reveal * 255);
        if (alpha <= 0) return;
        float cy = LIST_TOP + listViewportH() / 2f - 20f;
        String line1 = "列表为空";
        String line2 = "使用下方链接添加 Microsoft 或离线账号";
        float w1 = CustomFontRenderer.stringWidth(nameFont, line1);
        float w2 = CustomFontRenderer.stringWidth(bodyFont, line2);
        float centerX = contentLeft() + contentWidth() / 2f;
        int a1 = (int) (alpha * 0.8f);
        int a2 = (int) (alpha * 0.55f);
        CustomFontRenderer.drawString(gui, nameFont, line1, centerX - w1 / 2f, cy,
                (a1 << 24) | (TEXT_IDLE & 0x00FFFFFF));
        CustomFontRenderer.drawString(gui, bodyFont, line2, centerX - w2 / 2f, cy + 20f,
                (a2 << 24) | (SUBTITLE_COLOR & 0x00FFFFFF));
    }

    // ========================
    // 操作链接 / 状态 / 页脚
    // ========================

    private Link[] buildActionLinks() {
        boolean busy = applyingFuture != null && !applyingFuture.isDone();
        boolean hasSel = selected >= 0 && selected < accounts.size() && !busy;
        return new Link[]{
                new Link("+ Microsoft 账号", TEXT_IDLE, !busy,
                        () -> openModal(new MicrosoftModal())),
                new Link("+ 离线账号", TEXT_IDLE, !busy,
                        () -> openModal(new OfflineModal())),
                new Link("应用", TEXT_IDLE, hasSel, this::applySelected),
                new Link("删除", TEXT_IDLE, hasSel,
                        () -> openModal(new DeleteModal(accounts.get(selected)))),
        };
    }

    private void drawActions(GuiGraphicsExtractor gui, int mouseX, int mouseY, float dt,
                             float elapsed, boolean interactive) {
        float reveal = easeOutCubic(clamp01((elapsed - 0.6f) * 3f));
        int alpha = (int) (entryAlpha * reveal * 255);
        if (alpha <= 0) return;

        Link[] links = buildActionLinks();
        float y = this.height - ACTIONS_Y_FROM_BOTTOM;
        float left = contentLeft();

        float trayW = Math.min(contentWidth() + 24f, 520f);
        int trayX = Math.round(left - 12f);
        int trayY = Math.round(y - 12f);
        int trayH = Math.round(linkFont.lineHeight + 24f);
        CustomBlurRenderer.render(trayX, trayY, trayW, trayH, 10,
                scaleAlpha(0x48101820, reveal), 5f);
        CustomRoundedRectRenderer.drawRoundedRect(gui, trayX, trayY,
                Math.round(trayW), trayH, 10, scaleAlpha(0x7A101720, reveal));
        CustomRoundedRectRenderer.drawRoundedOutline(gui, trayX, trayY,
                Math.round(trayW), trayH, 10, scaleAlpha(GLASS_OUTLINE, reveal * 0.75f), 1);

        drawHLinks(gui, links, actionHover, interactive ? mouseX : -1, interactive ? mouseY : -1,
                dt, left, y, alpha);
    }

    private void drawStatus(GuiGraphicsExtractor gui, long now) {
        boolean busy = applyingFuture != null && !applyingFuture.isDone();
        String msg;
        int color;
        float alphaScale;
        if (busy) {
            msg = applyingStatus + dots(now);
            color = ACCENT;
            alphaScale = 1f;
        } else {
            if (statusMessage == null) return;
            float age = (now - statusSetMs) / 1000f;
            if (age > 3.2f) {
                statusMessage = null;
                return;
            }
            alphaScale = age < 2.6f ? 1f : 1f - (age - 2.6f) / 0.6f;
            msg = statusMessage;
            color = statusColor;
        }
        int alpha = (int) (entryAlpha * alphaScale * 255);
        if (alpha <= 0) return;
        CustomFontRenderer.drawString(gui, bodyFont, msg, contentLeft(),
                this.height - STATUS_Y_FROM_BOTTOM, (alpha << 24) | (color & 0x00FFFFFF));
    }

    private void drawFooter(GuiGraphicsExtractor gui, float elapsed) {
        float reveal = easeOutCubic(clamp01((elapsed - 0.7f) * 3f));
        int alpha = (int) (entryAlpha * reveal * 255);
        if (alpha <= 0) return;
        float y = this.height - FOOTER_BOTTOM_PAD;

        String hints = "↑↓  选择    Enter  应用    Delete  删除    Esc  返回";
        CustomFontRenderer.drawString(gui, hintFont, hints, contentLeft(), y,
                (alpha << 24) | (HINT_COLOR & 0x00FFFFFF));

        String line1 = "Gemini Client";
        String line2 = "v" + MOD_VERSION;
        float w1 = CustomFontRenderer.stringWidth(hintFont, line1);
        float w2 = CustomFontRenderer.stringWidth(hintFont, line2);
        float x1 = listRight() - w1;
        float x2 = listRight() - w2;
        float y2 = y;
        float y1 = y2 - 11f - 3f;
        int color = (alpha << 24) | (VERSION_COLOR & 0x00FFFFFF);
        float sqY = y1 + (11f - 3f) / 2f;
        CustomRectRenderer.drawRect(gui, (int) (x1 - 9), (int) sqY, 3, 3,
                (alpha << 24) | (ACCENT & 0x00FFFFFF));
        CustomFontRenderer.drawString(gui, hintFont, line1, x1, y1, color);
        CustomFontRenderer.drawString(gui, hintFont, line2, x2, y2, color);
    }

    // ========================
    // 链接绘制（水平排版链接：悬停下划线）
    // ========================

    private void drawHLinks(GuiGraphicsExtractor gui, Link[] links, HoverSet hover,
                            int mouseX, int mouseY, float dt, float x, float y, int baseAlpha) {
        hover.ensure(links.length);
        String sep = "    ·    ";
        float sepW = CustomFontRenderer.stringWidth(linkFont, sep);
        float cx = x;
        for (int i = 0; i < links.length; i++) {
            Link link = links[i];
            float w = CustomFontRenderer.stringWidth(linkFont, link.label());
            boolean over = link.enabled()
                    && mouseX >= cx - 7 && mouseX <= cx + w + 7
                    && mouseY >= y - 5 && mouseY <= y + linkFont.lineHeight + 5;
            hover.update(i, over, dt);
            float hp = hover.get(i);

            int idle = link.enabled() ? link.idleColor() : HINT_COLOR;
            int a = baseAlpha * (idle >>> 24) / 255;
            int pillX = Math.round(cx - 7f);
            int pillY = Math.round(y - 5f);
            int pillW = Math.round(w + 14f);
            int pillH = Math.round(linkFont.lineHeight + 10f);
            float enabledBase = link.enabled() ? 0.16f : 0.06f;
            CustomRoundedRectRenderer.drawRoundedRect(gui, pillX, pillY, pillW, pillH,
                    6, scaleAlpha(0xFF24323D, baseAlpha / 255f * (enabledBase + hp * 0.34f)));
            CustomRoundedRectRenderer.drawRoundedOutline(gui, pillX, pillY, pillW, pillH,
                    6, scaleAlpha(hp > 0.01f ? ACCENT : GLASS_OUTLINE,
                            baseAlpha / 255f * (0.35f + hp * 0.55f)), 1);
            int idleC = (a << 24) | (idle & 0x00FFFFFF);
            int hoverC = (a << 24) | (TEXT_HOVER & 0x00FFFFFF);
            CustomFontRenderer.drawString(gui, linkFont, link.label(), cx, y,
                    lerpColor(idleC, hoverC, hp));

            if (hp > 0.01f) {
                int ulA = (int) (a * hp);
                float ulW = w * hp;
                float ulX = cx + (w - ulW) / 2f;
                CustomRectRenderer.drawRect(gui, (int) ulX, (int) (y + linkFont.lineHeight + 2f),
                        (int) ulW, 1, (ulA << 24) | (ACCENT & 0x00FFFFFF));
            }
            cx += w + sepW;
        }
    }

    /** 返回水平链接布局的命中盒（与 drawHLinks 布局完全一致）。 */
    private float[][] layoutHLinks(Link[] links, float x, float y) {
        float sepW = CustomFontRenderer.stringWidth(linkFont, "    ·    ");
        float[][] bounds = new float[links.length][4];
        float cx = x;
        for (int i = 0; i < links.length; i++) {
            float w = CustomFontRenderer.stringWidth(linkFont, links[i].label());
            bounds[i] = new float[]{cx - 7, y - 5, w + 14, linkFont.lineHeight + 10};
            cx += w + sepW;
        }
        return bounds;
    }

    private int hLinkAt(Link[] links, float x, float y, double mx, double my) {
        float[][] bounds = layoutHLinks(links, x, y);
        for (int i = 0; i < bounds.length; i++) {
            float[] b = bounds[i];
            if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) return i;
        }
        return -1;
    }

    // ========================
    // 账号操作
    // ========================

    private void applySelected() {
        if (selected < 0 || selected >= accounts.size()) return;
        if (applyingFuture != null && !applyingFuture.isDone()) {
            showStatus("正在应用账号，请稍候…", WARN_COLOR);
            return;
        }
        AltAccount acc = accounts.get(selected);
        if (acc.getType() == AltAccount.Type.OFFLINE) {
            AltManager.apply(acc, accounts);
            showStatus("已应用离线账号：" + acc.getName(), ACCENT);
            return;
        }
        // Microsoft：优先用 refreshToken 静默刷新，失败则回退本地令牌
        if (acc.getRefreshToken().isEmpty()) {
            AltManager.apply(acc, accounts);
            showStatus("已应用 Microsoft 账号：" + acc.getName() + "（本地令牌）", ACCENT);
            return;
        }
        applyingTarget = acc;
        applyingStatus = "正在刷新 " + acc.getName() + " 的令牌…";
        applyingFuture = MicrosoftAuthService.refresh(acc.getRefreshToken(),
                        s -> applyingStatus = s)
                .thenApply(result -> {
                    AltManager.updateFromAuth(acc, result);
                    return acc;
                });
    }

    private void pollApplying() {
        if (applyingFuture == null || !applyingFuture.isDone()) return;
        AltAccount target = applyingTarget;
        applyingTarget = null;
        try {
            AltAccount acc = applyingFuture.get();
            AltManager.apply(acc, accounts);
            showStatus("已应用 Microsoft 账号：" + acc.getName() + "（令牌已刷新）", ACCENT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fallbackApply(target, "刷新被中断");
        } catch (ExecutionException e) {
            fallbackApply(target, extractMessage(e.getCause()));
        } catch (Exception e) {
            fallbackApply(target, "未知错误");
        }
        applyingFuture = null;
    }

    /** 刷新失败时回退：仍用本地保存的令牌应用账号。 */
    private void fallbackApply(AltAccount target, String reason) {
        if (target != null && accounts.contains(target)) {
            AltManager.apply(target, accounts);
            showStatus("刷新失败（" + reason + "），已使用本地令牌应用", WARN_COLOR);
        } else {
            showStatus("刷新失败：" + reason, ERROR_COLOR);
        }
    }

    private void addMicrosoftAccount(MicrosoftAuthService.AuthResult result) {
        UUID uuid = AltAccount.parseUuid(result.uuid());
        // 同 UUID 已存在 → 更新令牌而不是添加重复条目
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).getUuid().equals(uuid)) {
                AltManager.updateFromAuth(accounts.get(i), result);
                AltManager.save(accounts);
                selected = i;
                ensureRowVisible(i);
                showStatus("已更新 Microsoft 账号：" + result.name(), ACCENT);
                return;
            }
        }
        AltAccount acc = new AltAccount(AltAccount.Type.MICROSOFT,
                result.name(), uuid, result.accessToken(), result.refreshToken());
        accounts.add(acc);
        AltManager.save(accounts);
        selected = accounts.size() - 1;
        ensureRowVisible(selected);
        showStatus("已添加 Microsoft 账号：" + result.name(), ACCENT);
    }

    private void addOfflineAccount(String name) {
        AltAccount acc = AltAccount.offline(name);
        accounts.add(acc);
        AltManager.save(accounts);
        selected = accounts.size() - 1;
        ensureRowVisible(selected);
        showStatus("已添加离线账号：" + name, ACCENT);
    }

    private boolean hasDuplicateName(String name) {
        for (AltAccount a : accounts) {
            if (a.getName().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private void showStatus(String msg, int color) {
        statusMessage = msg;
        statusColor = color;
        statusSetMs = System.currentTimeMillis();
    }

    private void ensureRowVisible(int i) {
        float rowTop = i * ROW_H;
        float rowBottom = rowTop + ROW_H;
        float viewH = listViewportH();
        if (rowTop < targetScroll) targetScroll = rowTop;
        else if (rowBottom > targetScroll + viewH) targetScroll = rowBottom - viewH;
        clampScroll();
    }

    private void clampScroll() {
        float max = maxScroll();
        if (targetScroll < 0) targetScroll = 0;
        if (targetScroll > max) targetScroll = max;
    }

    // ========================
    // Input
    // ========================

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent mouse, boolean idk) {
        if (mouse.button() != 0) return super.mouseClicked(mouse, idk);

        if (modal != null) {
            modal.mouseClicked(mouse.x(), mouse.y());
            return true;
        }
        if (closingModal != null) return true; // 淡出期间吞掉点击

        // 操作链接
        Link[] links = buildActionLinks();
        float linkY = this.height - ACTIONS_Y_FROM_BOTTOM;
        int linkIdx = hLinkAt(links, contentLeft(), linkY, mouse.x(), mouse.y());
        if (linkIdx >= 0 && links[linkIdx].enabled()) {
            links[linkIdx].action().run();
            return true;
        }

        // 列表行：单击选中，双击应用
        int row = rowAt(mouse.x(), mouse.y());
        if (row >= 0) {
            long now = System.currentTimeMillis();
            if (row == lastClickRow && now - lastClickMs < 350) {
                selected = row;
                applySelected();
                lastClickRow = -1;
            } else {
                selected = row;
                lastClickRow = row;
                lastClickMs = now;
            }
            return true;
        }
        return super.mouseClicked(mouse, idk);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (modal != null) {
            return modal.keyPressed(event);
        }
        if (closingModal != null) {
            if (event.isEscape()) return super.keyPressed(event); // 淡出期间允许 Esc 直接退出
            return true;
        }

        if (event.isDown() || event.isUp()) {
            int dir = event.isDown() ? 1 : -1;
            if (accounts.isEmpty()) return true;
            if (selected < 0) selected = 0;
            else selected = (selected + dir + accounts.size()) % accounts.size();
            ensureRowVisible(selected);
            return true;
        }
        if (event.isConfirmation()) {
            applySelected();
            return true;
        }
        if (event.key() == InputConstants.KEY_DELETE && selected >= 0 && selected < accounts.size()) {
            openModal(new DeleteModal(accounts.get(selected)));
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (modal != null) {
            return modal.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (modal == null && closingModal == null) {
            targetScroll -= (float) scrollY * 30f;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (modal != null) {
            closeModal();
            return;
        }
        MicrosoftAuthService.cancelActive();
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ========================
    // 模态框框架
    // ========================

    private void openModal(Modal m) {
        if (modal != null) modal.onClose();
        modal = m;
        closingModal = null;
    }

    private void closeModal() {
        if (modal == null) return;
        modal.onClose();
        closingModal = modal;
        modal = null;
    }

    /** 模态框基类：面板由屏幕统一绘制（阴影/圆角/标题），内容子类负责。 */
    private abstract class Modal {
        abstract String title();
        abstract float panelHeight();
        abstract void extractContent(GuiGraphicsExtractor gui, PanelRect p,
                                     int mouseX, int mouseY, float dt, long now, float alphaScale);
        abstract void mouseClicked(double mx, double my);
        abstract boolean keyPressed(KeyEvent event);

        boolean charTyped(CharacterEvent event) {
            return false;
        }

        void onClose() {}

        PanelRect panel() {
            float w = 430f;
            float h = panelHeight();
            float x = (AltManagerScreen.this.width - w) / 2f;
            float y = (AltManagerScreen.this.height - h) / 2f
                    + (1f - easeOutCubic(modalAlpha)) * 12f;
            return new PanelRect(x, y, w, h);
        }

        void extractWithChrome(GuiGraphicsExtractor gui, int mouseX, int mouseY, float dt, long now) {
            float a = modalAlpha;
            // 遮罩
            int overlayA = (int) (a * 150);
            CustomRectRenderer.drawRect(gui, 0, 0, AltManagerScreen.this.width,
                    AltManagerScreen.this.height, overlayA << 24);

            PanelRect p = panel();

            // 区域模糊 + 阴影 + 分层渐变面板
            CustomBlurRenderer.render(p.x(), p.y(), p.w(), p.h(), 14,
                    scaleAlpha(0xA0141922, a), 10f);
            int shadowA = (int) (a * 0x60);
            SdfUIRenderer.drawShadow(gui, (int) p.x(), (int) p.y(), (int) p.w(), (int) p.h(),
                    14, 0, 8, 24, (shadowA << 24));
            CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui,
                    (int) p.x(), (int) p.y(), (int) p.w(), (int) p.h(), 14,
                    scaleAlpha(0xEE19212B, a), scaleAlpha(PANEL_FILL, a));
            SdfUIRenderer.drawOutline(gui, (int) p.x(), (int) p.y(), (int) p.w(), (int) p.h(),
                    14, scaleAlpha(0xFF3B5261, a), 1);

            // 标题 + 强调线
            CustomFontRenderer.drawString(gui, modalTitleFont, title(),
                    p.x() + 26, p.y() + 20, scaleAlpha(TITLE_COLOR, a));
            CustomRectRenderer.drawRect(gui, (int) (p.x() + 26), (int) (p.y() + 46),
                    26, 1, scaleAlpha(ACCENT, a));

            extractContent(gui, p, mouseX, mouseY, dt, now, a);
        }
    }

    // ========================
    // 文本输入框
    // ========================

    private static final class TextField {
        private final StringBuilder text = new StringBuilder();
        private final java.util.function.IntPredicate filter;
        private int caret;
        private int maxLength = 64;
        private float scrollX;
        private String placeholder = "";

        TextField(java.util.function.IntPredicate filter) {
            this.filter = filter;
        }

        TextField maxLength(int n) {
            maxLength = n;
            return this;
        }

        TextField placeholder(String s) {
            placeholder = s;
            return this;
        }

        String text() {
            return text.toString();
        }

        void charTyped(int cp) {
            if (text.length() >= maxLength) return;
            if (cp < 32 || cp == 127 || !filter.test(cp)) return;
            text.insert(caret, new String(Character.toChars(cp)));
            caret += Character.charCount(cp);
        }

        boolean keyPressed(KeyEvent event, net.minecraft.client.Minecraft mc) {
            if (event.isLeft() && caret > 0) {
                caret--;
                return true;
            }
            if (event.isRight() && caret < text.length()) {
                caret++;
                return true;
            }
            if (event.key() == InputConstants.KEY_HOME) {
                caret = 0;
                return true;
            }
            if (event.key() == InputConstants.KEY_END) {
                caret = text.length();
                return true;
            }
            if (event.key() == InputConstants.KEY_BACKSPACE && caret > 0) {
                text.deleteCharAt(caret - 1);
                caret--;
                return true;
            }
            if (event.key() == InputConstants.KEY_DELETE && caret < text.length()) {
                text.deleteCharAt(caret);
                return true;
            }
            if (event.key() == InputConstants.KEY_V && event.hasControlDownWithQuirk()) {
                String clip = mc.keyboardHandler.getClipboard();
                if (clip != null) {
                    for (int i = 0; i < clip.length() && text.length() < maxLength; i++) {
                        char c = clip.charAt(i);
                        if (c >= 32 && c != 127 && filter.test(c)) {
                            text.insert(caret++, c);
                        }
                    }
                }
                return true;
            }
            return false;
        }

        void render(GuiGraphicsExtractor gui, GlyphFont font, float x, float y, float width,
                    long nowMs, float alphaScale) {
            // 让光标保持可见
            float caretX = CustomFontRenderer.stringWidth(font, text.substring(0, caret));
            if (caretX - scrollX > width - 4) scrollX = caretX - (width - 4);
            if (caretX - scrollX < 0) scrollX = caretX;

            int fieldX = Math.round(x - 10f);
            int fieldY = Math.round(y - 8f);
            int fieldW = Math.round(width + 20f);
            int fieldH = Math.round(font.lineHeight + 16f);
            CustomRoundedRectRenderer.drawRoundedRect(gui, fieldX, fieldY, fieldW, fieldH,
                    8, scaleAlpha(0x8A090D13, alphaScale));
            CustomRoundedRectRenderer.drawRoundedOutline(gui, fieldX, fieldY, fieldW, fieldH,
                    8, scaleAlpha(0xA0507183, alphaScale), 1);

            gui.enableScissor((int) x - 2, (int) y - 4,
                    (int) (x + width) + 2, (int) (y + font.lineHeight) + 4);
            if (text.length() == 0) {
                CustomFontRenderer.drawString(gui, font, placeholder, x, y,
                        scaleAlpha(PLACEHOLDER, alphaScale));
            } else {
                CustomFontRenderer.drawString(gui, font, text.toString(),
                        x - scrollX, y, scaleAlpha(TEXT_HOVER, alphaScale));
            }
            // 光标（550ms 闪烁）
            if ((nowMs / 550) % 2 == 0) {
                int cx = (int) (x + caretX - scrollX);
                CustomRectRenderer.drawRect(gui, cx, (int) y - 1, 1, (int) font.lineHeight,
                        scaleAlpha(ACCENT, alphaScale));
            }
            gui.disableScissor();
        }
    }

    // ========================
    // Microsoft 登录模态框
    // ========================

    private enum MsState {CHOICE, WAITING, MANUAL, PROGRESS, ERROR}

    private class MicrosoftModal extends Modal {
        private MsState state = MsState.CHOICE;
        private volatile String statusText = "";
        private CompletableFuture<MicrosoftAuthService.AuthResult> future;
        private long waitStartMs;
        private String errorText = "";
        private final HoverSet vHover = new HoverSet();
        private final HoverSet hHover = new HoverSet();
        private final TextField urlField = new TextField(cp -> cp >= 32 && cp != 127)
                .maxLength(512)
                .placeholder("粘贴重定向 URL 或授权码…");

        @Override
        String title() {
            return "Microsoft 登录";
        }

        @Override
        float panelHeight() {
            return switch (state) {
                case CHOICE -> 224f;
                case WAITING -> 196f;
                case MANUAL -> 246f;
                case PROGRESS -> 172f;
                case ERROR -> 150f + errorLines().size() * 16f + 46f;
            };
        }

        private List<String> errorLines() {
            List<String> lines = new ArrayList<>();
            for (String s : errorText.split("\n")) {
                if (!s.isBlank()) lines.add(s.trim());
            }
            if (lines.isEmpty()) lines.add("未知错误");
            return lines;
        }

        // ---- 内容绘制 ----

        @Override
        void extractContent(GuiGraphicsExtractor gui, PanelRect p,
                            int mouseX, int mouseY, float dt, long now, float alphaScale) {
            pollFuture();
            switch (state) {
                case CHOICE -> extractChoice(gui, p, mouseX, mouseY, dt, alphaScale);
                case WAITING -> extractWaiting(gui, p, mouseX, mouseY, dt, now, alphaScale);
                case MANUAL -> extractManual(gui, p, mouseX, mouseY, dt, now, alphaScale);
                case PROGRESS -> extractProgress(gui, p, mouseX, mouseY, dt, now, alphaScale);
                case ERROR -> extractError(gui, p, mouseX, mouseY, dt, alphaScale);
            }
        }

        private void extractChoice(GuiGraphicsExtractor gui, PanelRect p,
                                   int mouseX, int mouseY, float dt, float a) {
            drawBodyLines(gui, p, a, 58f,
                    "自动模式：打开浏览器并在本地接收登录回调（推荐）。",
                    "手动模式：自行完成登录后粘贴浏览器重定向链接。");
            String[] items = {"自动登录（推荐）", "手动粘贴链接", "取消"};
            vHover.ensure(items.length);
            float y = p.y() + 108f;
            for (int i = 0; i < items.length; i++) {
                float itemY = y + i * 26f;
                boolean over = mouseX >= p.x() + 20 && mouseX <= p.x() + p.w() - 20
                        && mouseY >= itemY - 4 && mouseY <= itemY + linkFont.lineHeight + 4;
                vHover.update(i, over, dt);
                float hp = vHover.get(i);
                if (hp > 0.01f) {
                    float barH = linkFont.lineHeight * hp;
                    CustomRectRenderer.drawRect(gui, (int) (p.x() + 26),
                            (int) (itemY + (linkFont.lineHeight - barH) / 2f), 2, (int) barH,
                            scaleAlpha(ACCENT, a * hp));
                }
                int color = lerpColor(scaleAlpha(TEXT_IDLE, a), scaleAlpha(TEXT_HOVER, a), hp);
                CustomFontRenderer.drawString(gui, linkFont, items[i],
                        p.x() + 34 + hp * HOVER_SLIDE_PX, itemY, color);
            }
        }

        private void extractWaiting(GuiGraphicsExtractor gui, PanelRect p,
                                    int mouseX, int mouseY, float dt, long now, float a) {
            // 收到浏览器回调后（状态文本更新为令牌交换），在渲染线程切换到进度视图
            if (statusText.contains("收到回调")) {
                setState(MsState.PROGRESS);
                return;
            }
            String status = statusText.isEmpty() ? "等待浏览器回调" : statusText;
            CustomFontRenderer.drawString(gui, bodyFont, status + dots(now),
                    p.x() + 26, p.y() + 62, scaleAlpha(TEXT_HOVER, a));

            long remain = Math.max(0, 300 - (now - waitStartMs) / 1000);
            String countdown = String.format("剩余 %d:%02d", remain / 60, remain % 60);
            CustomFontRenderer.drawString(gui, bodyFont, countdown,
                    p.x() + 26, p.y() + 82, scaleAlpha(SUBTITLE_COLOR, a));

            CustomFontRenderer.drawString(gui, bodyFont, "页面没有打开？可以改用手动模式自行复制链接。",
                    p.x() + 26, p.y() + 102, scaleAlpha(SUBTITLE_COLOR, a));

            Link[] links = waitingLinks();
            drawHLinks(gui, links, hHover, mouseX, mouseY, dt,
                    p.x() + 26, p.y() + 140, (int) (a * 255));
        }

        private void extractManual(GuiGraphicsExtractor gui, PanelRect p,
                                   int mouseX, int mouseY, float dt, long now, float a) {
            drawBodyLines(gui, p, a, 58f,
                    "在浏览器中完成登录后，地址栏会跳转到 localhost 链接",
                    "（页面可能无法打开）——复制完整链接粘贴到下方：");

            float fieldY = p.y() + 100f;
            urlField.render(gui, fieldFont, p.x() + 26, fieldY, p.w() - 52, now, a);
            // 输入框强调下划线
            CustomRectRenderer.drawRect(gui, (int) (p.x() + 26),
                    (int) (fieldY + fieldFont.lineHeight + 5f),
                    (int) (p.w() - 52), 1, scaleAlpha(ACCENT, a));

            Link[] links = manualLinks();
            drawHLinks(gui, links, hHover, mouseX, mouseY, dt,
                    p.x() + 26, p.y() + 168, (int) (a * 255));
        }

        private void extractProgress(GuiGraphicsExtractor gui, PanelRect p,
                                     int mouseX, int mouseY, float dt, long now, float a) {
            String status = statusText.isEmpty() ? "正在与微软服务器通信" : statusText;
            CustomFontRenderer.drawString(gui, bodyFont, status + dots(now),
                    p.x() + 26, p.y() + 66, scaleAlpha(TEXT_HOVER, a));
            CustomFontRenderer.drawString(gui, bodyFont, "请稍候，正在完成令牌交换…",
                    p.x() + 26, p.y() + 88, scaleAlpha(SUBTITLE_COLOR, a));

            Link[] links = {new Link("取消", TEXT_IDLE, true, this::cancelLogin)};
            drawHLinks(gui, links, hHover, mouseX, mouseY, dt,
                    p.x() + 26, p.y() + 122, (int) (a * 255));
        }

        private void extractError(GuiGraphicsExtractor gui, PanelRect p,
                                  int mouseX, int mouseY, float dt, float a) {
            List<String> lines = errorLines();
            float y = p.y() + 58f;
            for (String line : lines) {
                CustomFontRenderer.drawString(gui, bodyFont, line, p.x() + 26, y,
                        scaleAlpha(ERROR_COLOR, a));
                y += 16f;
            }
            Link[] links = {
                    new Link("返回", TEXT_IDLE, true, () -> setState(MsState.CHOICE)),
                    new Link("关闭", TEXT_IDLE, true, AltManagerScreen.this::closeModal),
            };
            drawHLinks(gui, links, hHover, mouseX, mouseY, dt,
                    p.x() + 26, y + 14, (int) (a * 255));
        }

        private void drawBodyLines(GuiGraphicsExtractor gui, PanelRect p, float a,
                                   float offsetY, String... lines) {
            float y = p.y() + offsetY;
            for (String line : lines) {
                CustomFontRenderer.drawString(gui, bodyFont, line, p.x() + 26, y,
                        scaleAlpha(SUBTITLE_COLOR, a));
                y += 16f;
            }
        }

        // ---- 链接定义 ----

        private Link[] waitingLinks() {
            return new Link[]{
                    new Link("改用手动模式", TEXT_IDLE, true, () -> {
                        cancelLogin();
                        setState(MsState.MANUAL);
                    }),
                    new Link("取消", TEXT_IDLE, true, this::cancelLogin),
            };
        }

        private Link[] manualLinks() {
            boolean hasInput = !urlField.text().isBlank();
            return new Link[]{
                    new Link("打开授权页面", TEXT_IDLE, true, () -> {
                        try {
                            MicrosoftAuthService.openAuthorizationPage();
                        } catch (MicrosoftAuthService.AuthException e) {
                            fail(e.getMessage());
                        }
                    }),
                    new Link("开始登录", TEXT_IDLE, hasInput, this::startManualLogin),
                    new Link("返回", TEXT_IDLE, true, () -> setState(MsState.CHOICE)),
            };
        }

        // ---- 行为 ----

        private void setState(MsState s) {
            state = s;
            hHover.reset();
            vHover.reset();
            if (s == MsState.WAITING) waitStartMs = System.currentTimeMillis();
        }

        private void startAutoLogin() {
            setState(MsState.WAITING);
            statusText = "";
            // 状态文本由登录线程写入（volatile）；状态切换在渲染线程完成
            future = MicrosoftAuthService.loginAuto(s -> statusText = s);
        }

        private void startManualLogin() {
            String input = urlField.text();
            setState(MsState.PROGRESS);
            statusText = "";
            future = MicrosoftAuthService.loginManual(input, s -> statusText = s);
        }

        private void cancelLogin() {
            MicrosoftAuthService.cancelActive();
            if (future != null) {
                future = null;
            }
            setState(MsState.CHOICE);
        }

        private void fail(String message) {
            errorText = message == null || message.isBlank() ? "未知错误" : message;
            setState(MsState.ERROR);
        }

        private void pollFuture() {
            if (future == null || !future.isDone()) return;
            CompletableFuture<MicrosoftAuthService.AuthResult> f = future;
            future = null;
            try {
                MicrosoftAuthService.AuthResult result = f.get();
                addMicrosoftAccount(result);
                closeModal();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                setState(MsState.CHOICE);
            } catch (ExecutionException e) {
                String msg = extractMessage(e.getCause());
                if (msg.contains("已取消")) {
                    setState(MsState.CHOICE);
                } else {
                    fail(msg);
                }
            }
        }

        // ---- 输入路由 ----

        @Override
        void mouseClicked(double mx, double my) {
            PanelRect p = panel();
            switch (state) {
                case CHOICE -> {
                    String[] items = {"自动登录（推荐）", "手动粘贴链接", "取消"};
                    float y = p.y() + 108f;
                    for (int i = 0; i < items.length; i++) {
                        float itemY = y + i * 26f;
                        if (mx >= p.x() + 20 && mx <= p.x() + p.w() - 20
                                && my >= itemY - 4 && my <= itemY + linkFont.lineHeight + 4) {
                            if (i == 0) startAutoLogin();
                            else if (i == 1) setState(MsState.MANUAL);
                            else closeModal();
                            return;
                        }
                    }
                }
                case WAITING -> {
                    Link[] links = waitingLinks();
                    int idx = hLinkAt(links, p.x() + 26, p.y() + 140, mx, my);
                    if (idx >= 0 && links[idx].enabled()) links[idx].action().run();
                }
                case MANUAL -> {
                    Link[] links = manualLinks();
                    int idx = hLinkAt(links, p.x() + 26, p.y() + 168, mx, my);
                    if (idx >= 0 && links[idx].enabled()) links[idx].action().run();
                }
                case PROGRESS -> {
                    Link[] links = {new Link("取消", TEXT_IDLE, true, this::cancelLogin)};
                    int idx = hLinkAt(links, p.x() + 26, p.y() + 122, mx, my);
                    if (idx >= 0) links[idx].action().run();
                }
                case ERROR -> {
                    List<String> lines = errorLines();
                    float y = p.y() + 58f + lines.size() * 16f + 14f;
                    Link[] links = {
                            new Link("返回", TEXT_IDLE, true, () -> setState(MsState.CHOICE)),
                            new Link("关闭", TEXT_IDLE, true, AltManagerScreen.this::closeModal),
                    };
                    int idx = hLinkAt(links, p.x() + 26, y, mx, my);
                    if (idx >= 0) links[idx].action().run();
                }
            }
        }

        @Override
        boolean keyPressed(KeyEvent event) {
            if (event.isEscape()) {
                closeModal();
                return true;
            }
            switch (state) {
                case CHOICE -> {
                    if (event.isConfirmation()) {
                        startAutoLogin();
                        return true;
                    }
                }
                case MANUAL -> {
                    if (event.isConfirmation()) {
                        if (!urlField.text().isBlank()) startManualLogin();
                        return true;
                    }
                    if (urlField.keyPressed(event, minecraft)) return true;
                }
                case WAITING, PROGRESS -> {
                    if (event.isConfirmation()) return true; // 吞掉回车
                }
                case ERROR -> {
                    if (event.isConfirmation()) {
                        setState(MsState.CHOICE);
                        return true;
                    }
                }
            }
            return true; // 模态框打开时吞掉所有按键
        }

        @Override
        boolean charTyped(CharacterEvent event) {
            if (state == MsState.MANUAL) {
                urlField.charTyped(event.codepoint());
            }
            return true;
        }

        @Override
        void onClose() {
            if (future != null && !future.isDone()) {
                MicrosoftAuthService.cancelActive();
            }
        }
    }

    // ========================
    // 离线账号模态框
    // ========================

    private class OfflineModal extends Modal {
        private final TextField nameField = new TextField(
                cp -> Character.isLetterOrDigit(cp) || cp == '_')
                .maxLength(16)
                .placeholder("用户名（3-16 位字母、数字、下划线）");
        private final HoverSet hHover = new HoverSet();
        private String errorText = "";

        @Override
        String title() {
            return "添加离线账号";
        }

        @Override
        float panelHeight() {
            return 226f;
        }

        private Link[] links() {
            return new Link[]{
                    new Link("添加", TEXT_IDLE, true, this::submit),
                    new Link("取消", TEXT_IDLE, true, AltManagerScreen.this::closeModal),
            };
        }

        private void submit() {
            String name = nameField.text().trim();
            if (name.isEmpty()) {
                errorText = "请输入用户名";
                return;
            }
            if (name.length() < 3) {
                errorText = "用户名至少 3 个字符";
                return;
            }
            if (hasDuplicateName(name)) {
                errorText = "已存在同名账号";
                return;
            }
            addOfflineAccount(name);
            closeModal();
        }

        @Override
        void extractContent(GuiGraphicsExtractor gui, PanelRect p,
                            int mouseX, int mouseY, float dt, long now, float a) {
            CustomFontRenderer.drawString(gui, bodyFont,
                    "离线账号无需联网验证，仅适用于离线模式服务器。",
                    p.x() + 26, p.y() + 58, scaleAlpha(SUBTITLE_COLOR, a));

            float fieldY = p.y() + 88f;
            nameField.render(gui, fieldFont, p.x() + 26, fieldY, p.w() - 52, now, a);
            CustomRectRenderer.drawRect(gui, (int) (p.x() + 26),
                    (int) (fieldY + fieldFont.lineHeight + 5f),
                    (int) (p.w() - 52), 1, scaleAlpha(ACCENT, a));

            if (!errorText.isEmpty()) {
                CustomFontRenderer.drawString(gui, bodyFont, errorText,
                        p.x() + 26, p.y() + 128, scaleAlpha(ERROR_COLOR, a));
            }

            drawHLinks(gui, links(), hHover, mouseX, mouseY, dt,
                    p.x() + 26, p.y() + 160, (int) (a * 255));
        }

        @Override
        void mouseClicked(double mx, double my) {
            PanelRect p = panel();
            int idx = hLinkAt(links(), p.x() + 26, p.y() + 160, mx, my);
            if (idx >= 0 && links()[idx].enabled()) links()[idx].action().run();
        }

        @Override
        boolean keyPressed(KeyEvent event) {
            if (event.isEscape()) {
                closeModal();
                return true;
            }
            if (event.isConfirmation()) {
                submit();
                return true;
            }
            nameField.keyPressed(event, minecraft);
            return true;
        }

        @Override
        boolean charTyped(CharacterEvent event) {
            nameField.charTyped(event.codepoint());
            return true;
        }
    }

    // ========================
    // 删除确认模态框
    // ========================

    private class DeleteModal extends Modal {
        private final AltAccount target;
        private final HoverSet hHover = new HoverSet();

        DeleteModal(AltAccount target) {
            this.target = target;
        }

        @Override
        String title() {
            return "删除账号";
        }

        @Override
        float panelHeight() {
            return 196f;
        }

        private Link[] links() {
            return new Link[]{
                    new Link("删除", ERROR_COLOR, true, () -> {
                        int idx = accounts.indexOf(target);
                        AltManager.remove(target, accounts);
                        if (selected >= accounts.size()) selected = accounts.size() - 1;
                        if (idx >= 0 && selected > idx) selected--;
                        showStatus("已删除账号：" + target.getName(), ACCENT);
                        closeModal();
                    }),
                    new Link("取消", TEXT_IDLE, true, AltManagerScreen.this::closeModal),
            };
        }

        @Override
        void extractContent(GuiGraphicsExtractor gui, PanelRect p,
                            int mouseX, int mouseY, float dt, long now, float a) {
            CustomFontRenderer.drawString(gui, bodyFont,
                    "确定删除账号 " + target.getName() + "（" + target.typeLabel() + "）？",
                    p.x() + 26, p.y() + 62, scaleAlpha(TEXT_HOVER, a));
            CustomFontRenderer.drawString(gui, bodyFont, "此操作不可撤销。",
                    p.x() + 26, p.y() + 80, scaleAlpha(SUBTITLE_COLOR, a));

            drawHLinks(gui, links(), hHover, mouseX, mouseY, dt,
                    p.x() + 26, p.y() + 130, (int) (a * 255));
        }

        @Override
        void mouseClicked(double mx, double my) {
            PanelRect p = panel();
            int idx = hLinkAt(links(), p.x() + 26, p.y() + 130, mx, my);
            if (idx >= 0 && links()[idx].enabled()) links()[idx].action().run();
        }

        @Override
        boolean keyPressed(KeyEvent event) {
            if (event.isEscape()) {
                closeModal();
                return true;
            }
            if (event.isConfirmation()) {
                links()[0].action().run();
                return true;
            }
            return true;
        }
    }

    // ========================
    // 杂项工具
    // ========================

    private static String dots(long nowMs) {
        return ".".repeat((int) ((nowMs / 400) % 4));
    }

    private static String extractMessage(Throwable t) {
        if (t == null) return "未知错误";
        if (t instanceof MicrosoftAuthService.AuthException ae) return ae.getMessage();
        String msg = t.getMessage();
        return msg == null || msg.isBlank() ? "未知错误（" + t.getClass().getSimpleName() + "）" : msg;
    }

    private static int scaleAlpha(int argb, float scale) {
        int a = (int) ((argb >>> 24) * clamp01(scale));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static float computeSpacedWidth(GlyphFont font, String text, float spacing) {
        if (font == null) return 0;
        float w = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            w += CustomFontRenderer.stringWidth(font, ch) + spacing;
            i += Character.charCount(cp);
        }
        if (w > 0) w -= spacing;
        return w;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float easeOutCubic(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u;
    }

    private static int lerpColor(int a, int b, float t) {
        float tp = Math.clamp(t, 0f, 1f);
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * tp) << 24)
                | (Math.round(ar + (br - ar) * tp) << 16)
                | (Math.round(ag + (bg - ag) * tp) << 8)
                | Math.round(ab + (bb - ab) * tp);
    }
}

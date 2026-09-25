package geminiclient.gemini.base.alt;

import com.mojang.blaze3d.platform.InputConstants;
import geminiclient.gemini.base.I18n;
import geminiclient.gemini.base.MenuBackdrop;
import geminiclient.gemini.base.MenuUI;
import geminiclient.gemini.customRenderer.cpu.CustomRectRenderer;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomBlurRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer.GlyphFont;
import geminiclient.gemini.customRenderer.glsl.InfiniteGridRenderer;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3RenderUtils;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Theme;
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
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static geminiclient.gemini.base.MenuUI.clamp01;
import static geminiclient.gemini.base.MenuUI.dots;
import static geminiclient.gemini.base.MenuUI.drawCentered;
import static geminiclient.gemini.base.MenuUI.easeOutCubic;
import static geminiclient.gemini.base.MenuUI.ellipsize;
import static geminiclient.gemini.base.MenuUI.lerpColor;
import static geminiclient.gemini.base.MenuUI.scaleAlpha;

/**
 * Alt Manager —— 双栏 MD3 账号管理（Material Design 3，与主菜单同源）。
 *
 * <p>与主菜单共享同一张壁纸背景（{@link MenuBackdrop}），叠一层 MD3 幕层
 * （径向模糊 + 变暗，参数与主菜单抽屉一致）；上面浮两层 MD3 表面面板：左侧
 * 账号列表（{@code surface-container-low}）+ 右侧详情（{@code surface-container}），
 * 窄窗口自动折叠回单栏。全部配色取自 {@link Md3Theme}：{@code primary} 是
 * 唯一强调色（主按钮、选中指示条、键盘焦点），选中行为 {@code secondary-container}
 * 胶囊，悬停为 8% {@code on-surface} 状态层，模态框是 MD3 dialog、输入框是
 * MD3 outlined text field。</p>
 *
 * <p>功能：账号列表（名称 / 类型 / 应用状态）、Microsoft 登录（自动 +
 * 手动两种模式）、离线账号、删除、应用为当前会话账号。</p>
 */
public class AltManagerScreen extends Screen {

    // ========================
    // MD3 色板（角色名 → Md3Theme token）
    // ========================
    // 主菜单用同一套 token：这里只保留本界面用到的角色别名，避免出现一次性颜色。
    private static final int TEXT_HIGH       = Md3Theme.ON_SURFACE;
    private static final int TEXT_BODY       = Md3Theme.ON_SURFACE_VARIANT;
    private static final int TEXT_FAINT      = Md3Theme.OUTLINE;
    private static final int HOVER_FILL      = Md3Theme.hoverState(Md3Theme.ON_SURFACE);
    private static final int SELECTED_FILL   = Md3Theme.SECONDARY_CONTAINER;
    private static final int PRIMARY         = Md3Theme.PRIMARY;
    private static final int ON_PRIMARY      = Md3Theme.ON_PRIMARY;
    private static final int ACCENT_CONTAINER = Md3Theme.SECONDARY_CONTAINER;
    private static final int ON_ACCENT       = Md3Theme.ON_SECONDARY_CONTAINER;
    private static final int AVATAR_FILL     = Md3Theme.PRIMARY_CONTAINER;
    private static final int ON_AVATAR       = Md3Theme.ON_PRIMARY_CONTAINER;
    private static final int LIST_SURFACE    = Md3Theme.SURFACE_CONTAINER_LOW;
    private static final int DETAIL_SURFACE  = Md3Theme.SURFACE_CONTAINER;
    private static final int DIALOG_SURFACE  = Md3Theme.SURFACE_CONTAINER_HIGH;
    private static final int FIELD_SURFACE   = Md3Theme.SURFACE_CONTAINER_LOWEST;
    private static final int DIVIDER         = Md3Theme.OUTLINE_VARIANT;
    private static final int SURFACE_OUTLINE = Md3Theme.withAlpha(Md3Theme.ON_SURFACE, 0.08f);
    private static final int ERROR           = Md3Theme.ERROR;
    /** MD3 没有独立 warning 角色，回退态用 tertiary 与错误区分。 */
    private static final int WARN            = Md3Theme.TERTIARY;
    private static final int ERROR_OUTLINE   = Md3Theme.withAlpha(Md3Theme.ERROR, 0.45f);

    /** 幕层：与主菜单抽屉同时长同量级，保证两屏之间观感连续。 */
    private static final float SCRIM_ALPHA = 0.40f;
    private static final float BLUR_STRENGTH = 12f;

    /** 行首类型图标，与主菜单抽屉的行图标同一套 Material Symbols 贴图集。 */
    private static final Identifier ICON_MICROSOFT = Md3RenderUtils.menuIcon("groups");
    private static final Identifier ICON_OFFLINE = Md3RenderUtils.clickGuiIcon("person");
    private static final int ROW_ICON_SIZE = 20;

    // ========================
    // Layout Constants
    // ========================
    private static final float CONTENT_MAX_W = 1200f;
    private static final float CONTENT_MIN_PAD = 56f;
    private static final float PAGE_PAD_X = 28f;
    private static final float PAGE_PAD_TOP = 22f;
    private static final float PAGE_PAD_BOTTOM = 14f;
    private static final float ROW_H = 52f;
    private static final float LIST_INSET = 10f;   // 列表面板内边距
    private static final float ADD_BTN_H = 32f;
    private static final float ACTION_BTN_H = 36f;

    // ========================
    // Fonts
    // ========================
    // 全部面取自 MiSans-Bold：资源中唯一含完整中文字形的 MiSans 文件（Source
    // Han Sans 子集不含 CJK，无法渲染中文，已弃用）。
    private static final Identifier FONT_REGULAR =
            Identifier.fromNamespaceAndPath("gemini", "font/misans-bold.ttf");

    private static GlyphFont titleFont;      // 24 — 页面标题
    private static GlyphFont detailFont;     // 18 — 详情账号名
    private static GlyphFont bodyFont;       // 12 — 副标题 / 正文 / 状态
    private static GlyphFont nameFont;       // 14 — 列表账号名
    private static GlyphFont tinyFont;       // 10 — 列表次级信息
    private static GlyphFont linkFont;       // 12 — 按钮 / 链接
    private static GlyphFont hintFont;       // 11 — 底部提示 / 角标
    private static GlyphFont modalTitleFont; // 17
    private static GlyphFont fieldFont;      // 13 — 输入框

    private static void ensureFontsLoaded() {
        if (titleFont == null)      titleFont = CustomFontRenderer.loadFont(FONT_REGULAR, 24f);
        if (detailFont == null)     detailFont = CustomFontRenderer.loadFont(FONT_REGULAR, 18f);
        if (bodyFont == null)       bodyFont = CustomFontRenderer.loadFont(FONT_REGULAR, 12f);
        if (nameFont == null)       nameFont = CustomFontRenderer.loadFont(FONT_REGULAR, 14f);
        if (tinyFont == null)       tinyFont = CustomFontRenderer.loadFont(FONT_REGULAR, 10f);
        if (linkFont == null)       linkFont = CustomFontRenderer.loadFont(FONT_REGULAR, 12f);
        if (hintFont == null)       hintFont = CustomFontRenderer.loadFont(FONT_REGULAR, 11f);
        if (modalTitleFont == null) modalTitleFont = CustomFontRenderer.loadFont(FONT_REGULAR, 17f);
        if (fieldFont == null)      fieldFont = CustomFontRenderer.loadFont(FONT_REGULAR, 13f);
    }

    /**
     * 预热 AltManager 字体，由 {@code UiShaderWarmup} 在加载界面调用。调用方
     * 需随后执行 {@code CustomFontRenderer.flushPendingGlyphs()}。
     *
     * <p>仅预热 ASCII 与常用符号：中文文案留到首次绘制时才生成（三角化成本随
     * 字形复杂度变化，全量预热会拖慢加载界面）。缺失字形有 vanilla 字体兜底。</p>
     */
    public static void warmup() {
        try {
            ensureFontsLoaded();
            String warmupText = "1234567890.-+×▶←→·";
            GlyphFont[] faces = { titleFont, detailFont, bodyFont, nameFont, tinyFont,
                    linkFont, hintFont, modalTitleFont, fieldFont };
            for (GlyphFont face : faces) {
                if (face == null) {
                    continue;
                }
                for (int cp = 0x20; cp <= 0x7E; cp++) {
                    face.getGlyphBlocking(cp);
                }
                for (int i = 0; i < warmupText.length(); ) {
                    int cp = warmupText.codePointAt(i);
                    face.getGlyphBlocking(cp);
                    i += Character.charCount(cp);
                }
            }
        } catch (Throwable t) {
            // 预热失败不影响运行：字形仍会惰性三角化。
        }
    }

    // ========================
    // Animation Constants
    // ========================
    private static final float HOVER_SPEED      = 12f;
    private static final float ENTRY_FADE_SPEED = 4f;
    private static final float ENTRY_STAGGER    = 0.05f;
    private static final float ENTRY_SLIDE_PX   = 14f;
    private static final float MODAL_SPEED      = 10f;
    private static final float SCROLL_SPEED     = 14f;
    private static final float PANES_RISE_PX    = 12f;

    // ========================
    // Inner Types
    // ========================

    private record Link(String label, int idleColor, boolean enabled, Runnable action) {}

    /** 每个链接位点持有一组悬停进度值。 */
    private static final class HoverSet {
        private float[] values = new float[0];

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

    /** 单一权威屏幕布局，绘制和命中测试都从这里取坐标。 */
    private record Layout(float contentLeft, float contentRight, float contentWidth,
                          float titleY, float subtitleY,
                          float addMsX, float addOffX, float addBtnY,
                          float addMsW, float addOffW,
                          boolean twoPane,
                          float panesTop, float panesH,
                          float listX, float listW,
                          float detailX, float detailW,
                          float statusY, float hintsY) {

        float listTop() {
            return panesTop + LIST_INSET;
        }

        float listViewH() {
            return panesH - LIST_INSET * 2f;
        }

        float actionRowY() {
            return panesTop + panesH + 14f;
        }
    }

    private record LinkBounds(int index, float x, float y, float w, float h, float textX, float textY) {}

    /** 已测量、可换行的链接布局；绘制和命中测试共享同一组边界。 */
    private record LinkLayout(List<LinkBounds> bounds, float width, float height) {}

    // ========================
    // State
    // ========================

    private final Screen parent;
    private final List<AltAccount> accounts;
    private int selected = -1;
    private int hoveredRow = -1;
    private float[] rowHover = new float[0];
    private float entryAlpha;
    /** 本帧面板上浮量。绘制与命中测试共用，否则入场动画期间点击会落在控件上方。 */
    private float paneRise;
    private long screenOpenTime;
    private long lastFrameMs;

    // 滚动
    private float scrollOffset;
    private float targetScroll;
    private int layoutWidth = -1;
    private int layoutHeight = -1;
    private float layoutViewportH = -1f;

    // 模态框
    private Modal modal;
    private Modal closingModal; // 淡出期间继续渲染（不可交互）
    private float modalAlpha;

    // 头部「添加」按钮悬停
    private final HoverSet addHover = new HoverSet();
    // 详情 / 紧凑操作按钮悬停（应用、删除）
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
        // 引用计数：主菜单 ↔ AltManager 互切时壁纸资源保持存活
        MenuBackdrop.acquire();
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
        Layout l = screenLayout();

        entryAlpha += (1f - entryAlpha) * dt * ENTRY_FADE_SPEED;
        if (entryAlpha > 0.99f) entryAlpha = 1f;

        // ── 1. 背景：与主菜单共享的壁纸；无壁纸时回退 GLSL 网格 ──
        MenuBackdrop.render(gui, this.width, this.height, mouseX, mouseY);
        if (!MenuBackdrop.isActive()) {
            InfiniteGridRenderer.render(elapsed);
        }
        // MD3 幕层：壁纸做一次性全屏模糊 + 变暗，浅色表面面板靠它保证可读
        CustomBlurRenderer.render(0, 0, this.width, this.height, 0,
                Md3Theme.withAlpha(0xFF000000, SCRIM_ALPHA * entryAlpha),
                BLUR_STRENGTH * entryAlpha);

        // ── 2. 动画与异步任务轮询 ─────────────────────
        boolean interactive = modal == null;
        updateRowHover(mouseX, mouseY, dt, interactive);
        scrollOffset += (targetScroll - scrollOffset) * dt * SCROLL_SPEED;
        modalAlpha += (((modal != null) ? 1f : 0f) - modalAlpha) * dt * MODAL_SPEED;
        if (modal == null && closingModal != null && modalAlpha < 0.02f) {
            closingModal = null;
        }
        pollApplying();

        float rise = (1f - easeOutCubic(clamp01((elapsed - 0.1f) * 2.2f))) * PANES_RISE_PX;
        paneRise = rise;

        // ── 3. MD3 页面表面：浅色 surface 承载标题与页脚，面板浮在它上面 ──
        drawPageSurface(gui, l, elapsed, rise);

        // ── 4. 头部 ─────────────────────────────────
        drawHeader(gui, l, elapsed, interactive, mouseX, mouseY, dt);

        // ── 5. 列表面板 ─────────────────────────────
        if (accounts.isEmpty()) {
            drawListPanel(gui, l, elapsed, rise);
            drawEmptyState(gui, l, elapsed, rise);
        } else {
            drawList(gui, l, mouseX, mouseY, elapsed, interactive, rise);
        }

        // ── 6. 详情面板 / 紧凑操作行 ─────────────────
        if (l.twoPane()) {
            drawDetailPane(gui, l, elapsed, rise, interactive, mouseX, mouseY, dt);
        } else {
            drawCompactActions(gui, l, elapsed, rise, interactive, mouseX, mouseY, dt);
        }

        // ── 7. 状态消息 / 页脚提示 ───────────────────
        drawStatus(gui, l, now);
        drawHints(gui, l, elapsed);

        // ── 8. 模态框（最后提交，压在最上层）─────────
        if (modal != null) {
            modal.extractWithChrome(gui, mouseX, mouseY, dt, now);
        } else if (closingModal != null && modalAlpha > 0.01f) {
            closingModal.extractWithChrome(gui, -1, -1, dt, now);
        }
    }

    // ========================
    // 布局
    // ========================

    private Layout screenLayout() {
        float pad = Math.max(12f, Math.min(CONTENT_MIN_PAD, this.width * 0.06f));
        float contentW = Math.max(1f, Math.min(CONTENT_MAX_W, this.width - pad * 2f));
        float left = (this.width - contentW) / 2f;
        boolean twoPane = this.width >= 700 && this.height >= 400;

        float titleY = 30f;
        float subtitleY = titleY + 36f;

        // 右上「添加」按钮
        float addMsW = (linkFont == null ? 90f : CustomFontRenderer.stringWidth(linkFont, I18n.tr("+ Microsoft 账号"))) + 30f;
        float addOffW = (linkFont == null ? 70f : CustomFontRenderer.stringWidth(linkFont, I18n.tr("+ 离线账号"))) + 30f;
        float addOffX = left + contentW - addOffW;
        float addMsX = addOffX - addMsW - 10f;
        float addBtnY = titleY - 4f;

        float panesTop = subtitleY + 20f;
        float bottomReserve = twoPane ? 62f : 108f;
        float panesH = Math.max(130f, this.height - bottomReserve - panesTop);

        float detailW = twoPane ? Math.clamp(contentW * 0.36f, 250f, 380f) : 0f;
        float listW = twoPane ? Math.max(1f, contentW - detailW - 14f) : contentW;
        float detailX = left + listW + 14f;

        float statusY = this.height - 30f;
        float hintsW = hintFont == null ? 0f
                : CustomFontRenderer.stringWidth(hintFont, I18n.tr("↑↓  选择    Enter  应用    Delete  删除    Esc  返回"));
        float hintsY = this.height - 30f;

        Layout result = new Layout(left, left + contentW, contentW, titleY, subtitleY,
                addMsX, addOffX, addBtnY, addMsW, addOffW,
                twoPane, panesTop, panesH, left, listW, detailX, detailW,
                statusY, hintsY);

        if (layoutWidth != this.width || layoutHeight != this.height
                || Math.abs(layoutViewportH - result.listViewH()) > 0.01f) {
            layoutWidth = this.width;
            layoutHeight = this.height;
            layoutViewportH = result.listViewH();
            float max = Math.max(0f, accounts.size() * ROW_H - result.listViewH());
            targetScroll = Math.clamp(targetScroll, 0f, max);
            scrollOffset = Math.clamp(scrollOffset, 0f, max);
        }
        return result;
    }

    private float maxScroll() {
        return Math.max(0f, accounts.size() * ROW_H - screenLayout().listViewH());
    }

    /** MD3 页面表面：整块浅色 surface，标题与页脚文字直接印在它上面。 */
    private void drawPageSurface(GuiGraphicsExtractor gui, Layout l, float elapsed, float rise) {
        float reveal = easeOutCubic(clamp01(elapsed * 3.2f)) * entryAlpha;
        float padX = Math.min(PAGE_PAD_X, Math.max(8f, l.contentLeft() - 6f));
        float x = l.contentLeft() - padX;
        float y = l.titleY() - PAGE_PAD_TOP + rise * 0.4f;
        surface(gui, x, y, l.contentWidth() + padX * 2f,
                Math.max(1f, this.height - y - PAGE_PAD_BOTTOM),
                Md3Theme.R_EXTRA_LARGE, Md3Theme.withAlpha(Md3Theme.SURFACE, 0.95f), reveal);
    }

    /** 列表卡片：surface-container-low + 投影。空态也画，避免布局跳动。 */
    private void drawListPanel(GuiGraphicsExtractor gui, Layout l, float elapsed, float rise) {
        float reveal = easeOutCubic(clamp01((elapsed - 0.16f) * 2.4f)) * entryAlpha;
        surface(gui, l.listX(), l.panesTop() + rise, l.listW(), l.panesH(),
                Md3Theme.R_LARGE, LIST_SURFACE, reveal);
    }

    // ========================
    // 头部
    // ========================

    private void drawHeader(GuiGraphicsExtractor gui, Layout l, float elapsed,
                            boolean interactive, int mouseX, int mouseY, float dt) {
        float reveal = easeOutCubic(clamp01((elapsed - 0.05f) * 2.5f)) * entryAlpha;
        if (reveal <= 0.01f) return;
        int alpha = (int) (reveal * 255);

        // 标题
        CustomFontRenderer.drawString(gui, titleFont, I18n.tr("Alt Manager"),
                l.contentLeft(), l.titleY(), (alpha << 24) | (TEXT_HIGH & 0x00FFFFFF));

        // 副标题：账号数 + 当前会话
        float subReveal = easeOutCubic(clamp01((elapsed - 0.15f) * 2.5f));
        int subAlpha = (int) (alpha * subReveal);
        if (subAlpha > 0) {
            String sub = I18n.trf("AltManagerSubtitle", accounts.size(),
                    AltManager.currentSessionName());
            sub = ellipsize(bodyFont, sub, l.contentWidth());
            CustomFontRenderer.drawString(gui, bodyFont, sub, l.contentLeft(), l.subtitleY(),
                    (subAlpha << 24) | (TEXT_BODY & 0x00FFFFFF));
        }

        // 右上「添加」按钮：0 = tonal（次要主操作），1 = outlined（中性操作）
        boolean busy = applyingFuture != null && !applyingFuture.isDone();
        addHover.ensure(2);
        float[][] addRects = {
                {l.addMsX(), l.addBtnY(), l.addMsW(), ADD_BTN_H},
                {l.addOffX(), l.addBtnY(), l.addOffW(), ADD_BTN_H},
        };
        String[] addLabels = {I18n.tr("+ Microsoft 账号"), I18n.tr("+ 离线账号")};
        float scale = alpha / 255f * (busy ? 0.38f : 1f);
        for (int i = 0; i < 2; i++) {
            float[] r = addRects[i];
            boolean over = interactive && !busy
                    && MenuUI.inRect(mouseX, mouseY, r[0], r[1], r[2], r[3]);
            addHover.update(i, over, dt);
            if (i == 0) {
                filledButton(gui, linkFont, addLabels[i], r[0], r[1], r[2], r[3],
                        ACCENT_CONTAINER, ON_ACCENT, addHover.get(i), scale);
            } else {
                outlinedButton(gui, linkFont, addLabels[i], r[0], r[1], r[2], r[3],
                        PRIMARY, DIVIDER, addHover.get(i), scale);
            }
        }
    }

    // ========================
    // 账号列表
    // ========================

    private void updateRowHover(int mouseX, int mouseY, float dt, boolean interactive) {
        if (rowHover.length != accounts.size()) rowHover = new float[accounts.size()];
        hoveredRow = interactive ? rowAt(mouseX, mouseY) : -1;
        for (int i = 0; i < accounts.size(); i++) {
            boolean over = i == hoveredRow;
            rowHover[i] += ((over ? 1f : 0f) - rowHover[i]) * dt * HOVER_SPEED;
        }
    }

    private int rowAt(double mx, double my) {
        Layout l = screenLayout();
        double y = my - paneRise;   // 行随面板一起上浮，命中测试要跟着偏移
        if (mx < l.listX() || mx > l.listX() + l.listW()) return -1;
        if (y < l.listTop() || y > l.listTop() + l.listViewH()) return -1;
        int row = (int) ((y - l.listTop() + scrollOffset) / ROW_H);
        return row >= 0 && row < accounts.size() ? row : -1;
    }

    private void drawList(GuiGraphicsExtractor gui, Layout l, int mouseX, int mouseY,
                          float elapsed, boolean interactive, float rise) {
        float viewH = l.listViewH();
        if (viewH <= 0f || this.width <= 0) return;

        drawListPanel(gui, l, elapsed, rise);

        float clipTop = l.listTop() + rise;
        gui.enableScissor(Math.max(0, (int) l.listX()), Math.max(0, (int) clipTop),
                Math.min(this.width, (int) (l.listX() + l.listW())),
                Math.min(this.height, (int) (clipTop + viewH)));

        float rowX = l.listX() + LIST_INSET + 6f;
        float rowW = l.listW() - (LIST_INSET + 6f) * 2f;

        for (int i = 0; i < accounts.size(); i++) {
            AltAccount acc = accounts.get(i);
            float rowY = l.listTop() + i * ROW_H - scrollOffset;
            if (rowY + ROW_H < l.listTop() - 4 || rowY > l.listTop() + viewH + 4) continue;

            float reveal = easeOutCubic(clamp01((elapsed - 0.2f - i * ENTRY_STAGGER) * 3f));
            if (reveal <= 0.01f) continue;
            float slideIn = (1f - reveal) * -ENTRY_SLIDE_PX;

            float hp = interactive ? rowHover[i] : 0f;
            boolean isSelected = i == selected;

            int alpha = (int) (entryAlpha * reveal * 255);
            if (alpha <= 0) continue;

            float rx = rowX + slideIn;
            float ry = rowY + rise;
            int pillX = Math.round(rx);
            int pillY = Math.round(ry + 4);
            int pillW = Math.round(rowW);
            int pillH = Math.round(ROW_H - 8);

            // 状态层：选中为 secondary-container 胶囊，悬停为 8% on-surface
            if (isSelected) {
                CustomRoundedRectRenderer.drawRoundedRect(gui, pillX, pillY, pillW, pillH,
                        pillH / 2, scaleAlpha(SELECTED_FILL, reveal));
            } else if (hp > 0.01f) {
                CustomRoundedRectRenderer.drawRoundedRect(gui, pillX, pillY, pillW, pillH,
                        pillH / 2, scaleAlpha(HOVER_FILL, reveal * hp));
            }
            // MD3 活跃指示条：primary 圆头短条，贴在胶囊左缘
            if (isSelected) {
                CustomRoundedRectRenderer.drawRoundedRect(gui, pillX, Math.round(ry + ROW_H / 2f - 9),
                        3, 18, 1, scaleAlpha(PRIMARY, reveal));
            }

            // 行首类型图标
            int iconCy = Math.round(ry + ROW_H / 2f);
            Md3RenderUtils.drawTextureIcon(gui, typeIcon(acc),
                    Math.round(rx + 14 + ROW_ICON_SIZE / 2f), iconCy, ROW_ICON_SIZE,
                    scaleAlpha(isSelected ? PRIMARY
                            : lerpColor(TEXT_BODY, TEXT_HIGH, hp), reveal));

            float textX = rx + 14 + ROW_ICON_SIZE + 12f + hp * 2f;
            float textMaxW = Math.max(30f, rowW - 14f - ROW_ICON_SIZE - 12f - 110f);

            // 账号名
            int nameColor = isSelected ? ON_ACCENT
                    : lerpColor(scaleAlpha(TEXT_BODY, alpha / 255f),
                            scaleAlpha(TEXT_HIGH, alpha / 255f), Math.max(hp, 0f));
            CustomFontRenderer.drawString(gui, nameFont,
                    ellipsize(nameFont, acc.getName(), textMaxW), textX, ry + 8f, nameColor);

            // 次级信息：类型 · 短 UUID
            String sub = acc.typeLabel() + "  ·  " + acc.shortUuid();
            sub = ellipsize(tinyFont, sub, textMaxW);
            CustomFontRenderer.drawString(gui, tinyFont, sub, textX, ry + 27f,
                    (int) (alpha * 0.8f) << 24 | (TEXT_FAINT & 0x00FFFFFF));

            // 右侧状态
            drawRowStatus(gui, acc, rx, ry, rowW, alpha, reveal);
        }

        gui.disableScissor();
        drawScrollbar(gui, l);
    }

    private void drawRowStatus(GuiGraphicsExtractor gui, AltAccount acc, float rowX, float rowY,
                               float rowW, int rowAlpha, float reveal) {
        boolean current = AltManager.isCurrent(acc);
        boolean lastUsed = !current && acc.isActive();
        if (!current && !lastUsed) return;

        String text = I18n.tr(current ? "使用中" : "上次使用");
        float a = rowAlpha / 255f * reveal;

        if (current) {
            // MD3 assist chip：描边胶囊 + primary 勾选，任何行状态（含选中）下都读得清
            float textW = CustomFontRenderer.stringWidth(hintFont, text);
            float chipW = textW + 30f;
            float chipH = hintFont.lineHeight + 10f;
            float chipX = rowX + rowW - chipW - 6f;
            float chipY = rowY + (ROW_H - chipH) / 2f;
            CustomRoundedRectRenderer.drawRoundedOutline(gui, Math.round(chipX), Math.round(chipY),
                    Math.round(chipW), Math.round(chipH), Math.round(chipH / 2f),
                    scaleAlpha(DIVIDER, a), 1);
            int checkSize = Math.max(9, Math.round(chipH - 8));
            Md3RenderUtils.drawCheck(gui, Math.round(chipX + 8),
                    Math.round(chipY + (chipH - checkSize) / 2f), checkSize, scaleAlpha(PRIMARY, a));
            CustomFontRenderer.drawString(gui, hintFont, text, chipX + 21f,
                    chipY + (chipH - hintFont.lineHeight) / 2f, scaleAlpha(TEXT_BODY, a));
        } else {
            float textW = CustomFontRenderer.stringWidth(hintFont, text);
            CustomFontRenderer.drawString(gui, hintFont, text, rowX + rowW - textW - 16f,
                    rowY + (ROW_H - hintFont.lineHeight) / 2f, scaleAlpha(TEXT_FAINT, a * 0.9f));
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor gui, Layout l) {
        float viewH = l.listViewH();
        if (viewH <= 0f) return;
        float max = maxScroll();
        if (max <= 0.5f) return;
        float contentH = accounts.size() * ROW_H;
        float barH = Math.min(viewH, Math.max(Math.min(24f, viewH), viewH * viewH / contentH));
        float barY = l.listTop() + (viewH - barH) * (scrollOffset / max);
        float barX = l.listX() + l.listW() - 8f;
        CustomRoundedRectRenderer.drawRoundedRect(gui, Math.round(barX), Math.round(barY),
                3, Math.round(barH), 1, scaleAlpha(TEXT_FAINT, entryAlpha * 0.6f));
    }

    private void drawEmptyState(GuiGraphicsExtractor gui, Layout l, float elapsed, float rise) {
        float reveal = easeOutCubic(clamp01((elapsed - 0.3f) * 2f)) * entryAlpha;
        if (reveal <= 0.01f) return;
        float cy = l.listTop() + l.listViewH() / 2f - 14f + rise;
        float centerX = l.listX() + l.listW() / 2f;

        String line1 = I18n.tr("列表为空");
        String line2 = I18n.tr("点击右上角按钮添加 Microsoft 或离线账号");
        drawCentered(gui, nameFont, line1, centerX, cy - 8f, scaleAlpha(TEXT_BODY, reveal));
        drawCentered(gui, bodyFont, line2, centerX, cy + 14f, scaleAlpha(TEXT_FAINT, reveal * 0.9f));
    }

    // ========================
    // 详情面板（双栏）
    // ========================

    private void drawDetailPane(GuiGraphicsExtractor gui, Layout l, float elapsed, float rise,
                                boolean interactive, int mouseX, int mouseY, float dt) {
        float reveal = easeOutCubic(clamp01((elapsed - 0.22f) * 2.4f)) * entryAlpha;
        if (reveal <= 0.01f) return;

        float px = l.detailX();
        float py = l.panesTop() + rise;
        float pw = l.detailW();
        float ph = l.panesH();
        surface(gui, px, py, pw, ph, Md3Theme.R_LARGE, DETAIL_SURFACE, reveal);

        boolean busy = applyingFuture != null && !applyingFuture.isDone();
        boolean hasSel = selected >= 0 && selected < accounts.size();
        AltAccount acc = hasSel ? accounts.get(selected) : null;

        if (!hasSel || acc == null) {
            drawCentered(gui, bodyFont, I18n.tr("未选择账号"),
                    px + pw / 2f, py + ph / 2f - bodyFont.lineHeight / 2f,
                    scaleAlpha(TEXT_FAINT, reveal));
            return;
        }

        float pad = 22f;
        int alpha = (int) (reveal * 255);

        // 头像：首字母圆形（primary-container 底）
        float avatarD = 52f;
        float acx = px + pad + avatarD / 2f;
        float acy = py + pad + avatarD / 2f;
        String name = acc.getName();
        String initial = name.isEmpty() ? "?"
                : new String(Character.toChars(name.codePointAt(0))).toUpperCase(Locale.ROOT);
        CustomRoundedRectRenderer.drawCircle(gui, acx, acy, Math.round(avatarD),
                scaleAlpha(AVATAR_FILL, reveal));
        drawCentered(gui, titleFont, initial, acx, acy - titleFont.lineHeight / 2f,
                scaleAlpha(ON_AVATAR, reveal));

        // 账号名 + 类型
        float nameX = acx + avatarD / 2f + 14f;
        float nameMaxW = px + pw - pad - nameX;
        CustomFontRenderer.drawString(gui, detailFont, ellipsize(detailFont, name, Math.max(30f, nameMaxW)),
                nameX, acy - detailFont.lineHeight - 3f, scaleAlpha(TEXT_HIGH, alpha / 255f));
        CustomFontRenderer.drawString(gui, tinyFont, acc.typeLabel(),
                nameX, acy + 2f, scaleAlpha(TEXT_BODY, alpha / 255f));

        // 分隔线
        float dividerY = py + pad + avatarD + 16f;
        CustomRectRenderer.drawRect(gui, Math.round(px + pad), Math.round(dividerY),
                Math.round(pw - pad * 2), 1, scaleAlpha(DIVIDER, reveal));

        // 字段（UUID / 状态）
        if (ph >= 250f) {
            float fieldX = px + pad;
            float uuidLabelY = dividerY + 16f;
            CustomFontRenderer.drawString(gui, tinyFont, "UUID", fieldX, uuidLabelY,
                    scaleAlpha(TEXT_FAINT, reveal));
            CustomFontRenderer.drawString(gui, bodyFont,
                    ellipsize(bodyFont, String.valueOf(acc.getUuid()), Math.max(30f, pw - pad * 2f)),
                    fieldX, uuidLabelY + 13f, scaleAlpha(TEXT_HIGH, alpha / 255f));

            float statusLabelY = uuidLabelY + 38f;
            CustomFontRenderer.drawString(gui, tinyFont, I18n.tr("状态"), fieldX, statusLabelY,
                    scaleAlpha(TEXT_FAINT, reveal));
            int statusColor = AltManager.isCurrent(acc) ? TEXT_HIGH
                    : acc.isActive() ? TEXT_BODY : TEXT_FAINT;
            String statusText = I18n.tr(AltManager.isCurrent(acc) ? "使用中"
                    : acc.isActive() ? "上次使用" : "未使用");
            CustomFontRenderer.drawString(gui, bodyFont, statusText,
                    fieldX, statusLabelY + 13f, scaleAlpha(statusColor, alpha / 255f));
        }

        // 底部按钮：应用（filled primary）/ 删除（outlined, error 语义色）
        float btnW = (pw - pad * 2f - 10f) / 2f;
        float btnY = py + ph - pad - ACTION_BTN_H;
        actionHover.ensure(2);
        boolean enabled = hasSel && !busy;

        boolean overApply = interactive && enabled
                && MenuUI.inRect(mouseX, mouseY, px + pad, btnY, btnW, ACTION_BTN_H);
        actionHover.update(0, overApply, dt);
        float btnAlpha = enabled ? alpha / 255f : alpha / 255f * 0.38f;
        filledButton(gui, linkFont, I18n.tr("应用"), px + pad, btnY, btnW, ACTION_BTN_H,
                PRIMARY, ON_PRIMARY, actionHover.get(0), btnAlpha);

        float delX = px + pad + btnW + 10f;
        boolean overDelete = interactive && enabled
                && MenuUI.inRect(mouseX, mouseY, delX, btnY, btnW, ACTION_BTN_H);
        actionHover.update(1, overDelete, dt);
        outlinedButton(gui, linkFont, I18n.tr("删除"), delX, btnY, btnW, ACTION_BTN_H,
                ERROR, ERROR_OUTLINE, actionHover.get(1), btnAlpha);
    }

    // ========================
    // 紧凑模式操作行（单栏折叠）
    // ========================

    private void drawCompactActions(GuiGraphicsExtractor gui, Layout l, float elapsed, float rise,
                                    boolean interactive, int mouseX, int mouseY, float dt) {
        float reveal = easeOutCubic(clamp01((elapsed - 0.3f) * 2.4f)) * entryAlpha;
        if (reveal <= 0.01f) return;

        boolean busy = applyingFuture != null && !applyingFuture.isDone();
        boolean hasSel = selected >= 0 && selected < accounts.size();
        boolean enabled = hasSel && !busy;

        float btnW = 110f;
        float delW = 90f;
        float btnY = l.actionRowY() + rise;
        float delX = l.contentRight() - delW;
        float applyX = delX - btnW - 10f;
        int alpha = (int) (reveal * 255);

        actionHover.ensure(2);
        float btnAlpha = enabled ? alpha / 255f : alpha / 255f * 0.38f;
        boolean overApply = interactive && enabled
                && MenuUI.inRect(mouseX, mouseY, applyX, btnY, btnW, ACTION_BTN_H);
        actionHover.update(0, overApply, dt);
        filledButton(gui, linkFont, I18n.tr("应用"), applyX, btnY, btnW, ACTION_BTN_H,
                PRIMARY, ON_PRIMARY, actionHover.get(0), btnAlpha);

        boolean overDelete = interactive && enabled
                && MenuUI.inRect(mouseX, mouseY, delX, btnY, delW, ACTION_BTN_H);
        actionHover.update(1, overDelete, dt);
        outlinedButton(gui, linkFont, I18n.tr("删除"), delX, btnY, delW, ACTION_BTN_H,
                ERROR, ERROR_OUTLINE, actionHover.get(1), btnAlpha);
    }

    // ========================
    // 状态 / 提示
    // ========================

    private void drawStatus(GuiGraphicsExtractor gui, Layout l, long now) {
        boolean busy = applyingFuture != null && !applyingFuture.isDone();
        String msg;
        int color;
        float alphaScale;
        if (busy) {
            msg = applyingStatus + dots(now);
            color = TEXT_HIGH;
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
        float alpha = entryAlpha * alphaScale;
        if (alpha <= 0) return;
        CustomFontRenderer.drawString(gui, bodyFont, ellipsize(bodyFont, msg, l.contentWidth() - 8f),
                l.contentLeft(), l.statusY(), scaleAlpha(color, alpha));
    }

    private void drawHints(GuiGraphicsExtractor gui, Layout l, float elapsed) {
        float reveal = easeOutCubic(clamp01((elapsed - 0.45f) * 2.5f)) * entryAlpha;
        if (reveal <= 0.01f) return;
        if (this.height < 250) return;

        String hints = I18n.tr("↑↓  选择    Enter  应用    Delete  删除    Esc  返回");
        float hintsW = CustomFontRenderer.stringWidth(hintFont, hints);
        float x = Math.max(l.contentLeft(), l.contentRight() - hintsW);
        CustomFontRenderer.drawString(gui, hintFont, hints, x, l.hintsY(),
                scaleAlpha(TEXT_FAINT, reveal));
    }

    // ========================
    // 账号操作（后端调用保持不变）
    // ========================

    private void applySelected() {
        if (selected < 0 || selected >= accounts.size()) return;
        if (applyingFuture != null && !applyingFuture.isDone()) {
            showStatus(I18n.tr("正在应用账号，请稍候…"), WARN);
            return;
        }
        AltAccount acc = accounts.get(selected);
        if (acc.getType() == AltAccount.Type.OFFLINE) {
            AltManager.apply(acc, accounts);
            showStatus(I18n.trf("已应用离线账号：%s", acc.getName()), TEXT_HIGH);
            return;
        }
        // Microsoft：优先用 refreshToken 静默刷新，失败则回退本地令牌
        if (acc.getRefreshToken().isEmpty()) {
            AltManager.apply(acc, accounts);
            showStatus(I18n.trf("已应用 Microsoft 账号：%s（本地令牌）", acc.getName()), TEXT_HIGH);
            return;
        }
        applyingTarget = acc;
        applyingStatus = I18n.trf("正在刷新 %s 的令牌…", acc.getName());
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
            showStatus(I18n.trf("已应用 Microsoft 账号：%s（令牌已刷新）", acc.getName()), TEXT_HIGH);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fallbackApply(target, I18n.tr("刷新被中断"));
        } catch (ExecutionException e) {
            fallbackApply(target, extractMessage(e.getCause()));
        } catch (Exception e) {
            fallbackApply(target, I18n.tr("未知错误"));
        }
        applyingFuture = null;
    }

    /** 刷新失败时回退：仍用本地保存的令牌应用账号。 */
    private void fallbackApply(AltAccount target, String reason) {
        if (target != null && accounts.contains(target)) {
            AltManager.apply(target, accounts);
            showStatus(I18n.trf("刷新失败（%s），已使用本地令牌应用", reason), WARN);
        } else {
            showStatus(I18n.trf("刷新失败：%s", reason), ERROR);
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
                showStatus(I18n.trf("已更新 Microsoft 账号：%s", result.name()), TEXT_HIGH);
                return;
            }
        }
        AltAccount acc = new AltAccount(AltAccount.Type.MICROSOFT,
                result.name(), uuid, result.accessToken(), result.refreshToken());
        accounts.add(acc);
        AltManager.save(accounts);
        selected = accounts.size() - 1;
        ensureRowVisible(selected);
        showStatus(I18n.trf("已添加 Microsoft 账号：%s", result.name()), TEXT_HIGH);
    }

    private void addOfflineAccount(String name) {
        AltAccount acc = AltAccount.offline(name);
        accounts.add(acc);
        AltManager.save(accounts);
        selected = accounts.size() - 1;
        ensureRowVisible(selected);
        showStatus(I18n.trf("已添加离线账号：%s", name), TEXT_HIGH);
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
        float viewH = screenLayout().listViewH();
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
    public boolean mouseClicked(@NotNull MouseButtonEvent mouse, boolean doubleClick) {
        // 26.3 起鼠标键号改为 1 基（InputConstants.MOUSE_BUTTON_LEFT == 1），不能再与 0 比较，
        // 否则左键会在第一行就被判成"非左键"而转给 super，整屏自定义命中测试全部失效。
        if (mouse.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouse, doubleClick);
        }

        if (modal != null) {
            modal.mouseClicked(mouse.x(), mouse.y());
            return true;
        }
        if (closingModal != null) return true; // 淡出期间吞掉点击

        Layout l = screenLayout();
        boolean busy = applyingFuture != null && !applyingFuture.isDone();
        boolean hasSel = selected >= 0 && selected < accounts.size();

        // 头部「添加」按钮
        if (!busy) {
            if (MenuUI.inRect(mouse.x(), mouse.y(), l.addMsX(), l.addBtnY(), l.addMsW(), ADD_BTN_H)) {
                openModal(new MicrosoftModal());
                return true;
            }
            if (MenuUI.inRect(mouse.x(), mouse.y(), l.addOffX(), l.addBtnY(), l.addOffW(), ADD_BTN_H)) {
                openModal(new OfflineModal());
                return true;
            }
        }

        // 详情面板 / 紧凑操作行按钮
        if (hasSel && !busy) {
            float[] btn = actionButtonRect(l, 0);
            float[] del = actionButtonRect(l, 1);
            if (MenuUI.inRect(mouse.x(), mouse.y(), btn[0], btn[1], btn[2], btn[3])) {
                applySelected();
                return true;
            }
            if (MenuUI.inRect(mouse.x(), mouse.y(), del[0], del[1], del[2], del[3])) {
                openModal(new DeleteModal(accounts.get(selected)));
                return true;
            }
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
        return super.mouseClicked(mouse, doubleClick);
    }

    /**
     * 操作按钮矩形：0 = 应用（主），1 = 删除（幽灵）。
     * 双栏在详情面板底部；单栏在列表下方的操作行。
     */
    private float[] actionButtonRect(Layout l, int index) {
        if (l.twoPane()) {
            float pad = 22f;
            float btnW = (l.detailW() - pad * 2f - 10f) / 2f;
            float btnY = l.panesTop() + paneRise + l.panesH() - pad - ACTION_BTN_H;
            float x = index == 0 ? l.detailX() + pad : l.detailX() + pad + btnW + 10f;
            return new float[]{x, btnY, btnW, ACTION_BTN_H};
        }
        float btnW = index == 0 ? 110f : 90f;
        float delX = l.contentRight() - 90f;
        float x = index == 0 ? delX - 110f - 10f : delX;
        return new float[]{x, l.actionRowY() + paneRise, btnW, ACTION_BTN_H};
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
    public void removed() {
        super.removed();
        MenuBackdrop.release();
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

    /** 模态框基类：面板由屏幕统一绘制（磨砂玻璃 + 标题 + 分隔线），内容子类负责。 */
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

        float inset(PanelRect p) {
            return Math.clamp(p.w() * 0.06f, 14f, 26f);
        }

        float contentY(PanelRect p, float normalOffset) {
            float desired = panelHeight();
            if (p.h() >= desired - 0.5f || normalOffset <= 48f) return p.y() + normalOffset;
            float usable = Math.max(1f, p.h() - 58f);
            float desiredUsable = Math.max(1f, desired - 58f);
            return p.y() + 48f + (normalOffset - 48f) * usable / desiredUsable;
        }

        PanelRect panel() {
            float margin = 12f;
            float w = Math.max(1f, Math.min(430f, AltManagerScreen.this.width - margin * 2f));
            float h = Math.max(1f, Math.min(panelHeight(), AltManagerScreen.this.height - margin * 2f));
            float x = (AltManagerScreen.this.width - w) / 2f;
            float y = (AltManagerScreen.this.height - h) / 2f
                    + (1f - easeOutCubic(modalAlpha)) * Math.min(12f, margin);
            return new PanelRect(x, y, w, h);
        }

        void extractWithChrome(GuiGraphicsExtractor gui, int mouseX, int mouseY, float dt, long now) {
            float a = modalAlpha;
            // MD3 dialog 幕层
            CustomRectRenderer.drawRect(gui, 0, 0, AltManagerScreen.this.width,
                    AltManagerScreen.this.height, Md3Theme.withAlpha(0xFF000000, SCRIM_ALPHA * a));

            PanelRect p = panel();

            // dialog 表面：surface-container-high + elevation3
            surface(gui, p.x(), p.y(), p.w(), p.h(), Md3Theme.R_EXTRA_LARGE, DIALOG_SURFACE, a);

            // 标题 + 分隔线
            float inset = inset(p);
            String panelTitle = ellipsize(modalTitleFont, title(), Math.max(1f, p.w() - inset * 2f));
            CustomFontRenderer.drawString(gui, modalTitleFont, panelTitle,
                    p.x() + inset, p.y() + 20, scaleAlpha(TEXT_HIGH, a));
            CustomRectRenderer.drawRect(gui, Math.round(p.x() + inset), Math.round(p.y() + 48),
                    Math.round(p.w() - inset * 2f), 1, scaleAlpha(DIVIDER, a));

            gui.enableScissor(Math.max(0, (int) p.x()), Math.max(0, (int) (p.y() + Math.min(52f, p.h()))),
                    Math.min(AltManagerScreen.this.width, (int) (p.x() + p.w())),
                    Math.min(AltManagerScreen.this.height, (int) (p.y() + p.h())));
            extractContent(gui, p, mouseX, mouseY, dt, now, a);
            gui.disableScissor();
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
            // MD3 outlined text field：实底 + 1px primary 描边（弹层内始终处于焦点态）
            CustomRoundedRectRenderer.drawRoundedRect(gui, fieldX, fieldY, fieldW, fieldH,
                    Md3Theme.R_SMALL, scaleAlpha(FIELD_SURFACE, alphaScale));
            CustomRoundedRectRenderer.drawRoundedOutline(gui, fieldX, fieldY, fieldW, fieldH,
                    Md3Theme.R_SMALL, scaleAlpha(PRIMARY, alphaScale * 0.7f), 1);

            gui.enableScissor((int) x - 2, (int) y - 4,
                    (int) (x + width) + 2, (int) (y + font.lineHeight) + 4);
            if (text.length() == 0) {
                CustomFontRenderer.drawString(gui, font, placeholder, x, y,
                        scaleAlpha(TEXT_FAINT, alphaScale));
            } else {
                CustomFontRenderer.drawString(gui, font, text.toString(),
                        x - scrollX, y, scaleAlpha(TEXT_HIGH, alphaScale));
            }
            // 光标（550ms 闪烁）
            if ((nowMs / 550) % 2 == 0) {
                int cx = (int) (x + caretX - scrollX);
                CustomRectRenderer.drawRect(gui, cx, (int) y - 1, 2, (int) font.lineHeight,
                        scaleAlpha(PRIMARY, alphaScale));
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
                .placeholder(I18n.tr("粘贴重定向 URL 或授权码…"));

        @Override
        String title() {
            return I18n.tr("Microsoft 登录");
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
            if (lines.isEmpty()) lines.add(I18n.tr("未知错误"));
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
            drawBodyLines(gui, p, a, 66f,
                    I18n.tr("自动模式：打开浏览器并在本地接收登录回调（推荐）。"),
                    I18n.tr("手动模式：自行完成登录后粘贴浏览器重定向链接。"));
            String[] items = {I18n.tr("自动登录（推荐）"), I18n.tr("手动粘贴链接"), I18n.tr("取消")};
            vHover.ensure(items.length);
            for (int i = 0; i < items.length; i++) {
                float itemY = contentY(p, 118f + i * 28f);
                boolean over = mouseX >= p.x() + 20 && mouseX <= p.x() + p.w() - 20
                        && mouseY >= itemY - 5 && mouseY <= itemY + linkFont.lineHeight + 5;
                vHover.update(i, over, dt);
                float hp = vHover.get(i);
                if (hp > 0.01f) {
                    int itemH = Math.round(linkFont.lineHeight + 10);
                    CustomRoundedRectRenderer.drawRoundedRect(gui,
                            Math.round(p.x() + inset(p) - 10), Math.round(itemY - 5),
                            Math.round(p.w() - (inset(p) - 10) * 2f), itemH, itemH / 2,
                            scaleAlpha(HOVER_FILL, a * hp));
                }
                int color = lerpColor(scaleAlpha(TEXT_BODY, a), scaleAlpha(TEXT_HIGH, a), hp);
                CustomFontRenderer.drawString(gui, linkFont, items[i],
                        p.x() + inset(p) + 2f, itemY, color);
            }
        }

        private void extractWaiting(GuiGraphicsExtractor gui, PanelRect p,
                                    int mouseX, int mouseY, float dt, long now, float a) {
            // 收到浏览器回调后（状态文本更新为令牌交换），在渲染线程切换到进度视图
            if (statusText.contains("收到回调")) {
                setState(MsState.PROGRESS);
                return;
            }
            String status = statusText.isEmpty() ? I18n.tr("等待浏览器回调") : statusText;
            status = ellipsize(bodyFont, status, Math.max(1f, p.w() - inset(p) * 2f - 18f));
            CustomFontRenderer.drawString(gui, bodyFont, status + dots(now),
                    p.x() + inset(p), contentY(p, 66f), scaleAlpha(TEXT_HIGH, a));

            long remain = Math.max(0, 300 - (now - waitStartMs) / 1000);
            String countdown = String.format(Locale.ROOT, I18n.tr("剩余 %d:%02d"), remain / 60, remain % 60);
            CustomFontRenderer.drawString(gui, bodyFont, countdown,
                    p.x() + inset(p), contentY(p, 86f), scaleAlpha(TEXT_BODY, a));

            CustomFontRenderer.drawString(gui, bodyFont, I18n.tr("页面没有打开？可以改用手动模式自行复制链接。"),
                    p.x() + inset(p), contentY(p, 106f), scaleAlpha(TEXT_FAINT, a));

            Link[] links = waitingLinks();
            drawHLinks(gui, links, hHover, mouseX, mouseY, dt,
                    p.x() + inset(p), contentY(p, 142f), (int) (a * 255));
        }

        private void extractManual(GuiGraphicsExtractor gui, PanelRect p,
                                   int mouseX, int mouseY, float dt, long now, float a) {
            drawBodyLines(gui, p, a, 64f,
                    I18n.tr("在浏览器中完成登录后，地址栏会跳转到 localhost 链接"),
                    I18n.tr("（页面可能无法打开）——复制完整链接粘贴到下方："));

            float fieldY = contentY(p, 104f);
            urlField.render(gui, fieldFont, p.x() + inset(p), fieldY, Math.max(1f, p.w() - inset(p) * 2f), now, a);

            Link[] links = manualLinks();
            drawHLinks(gui, links, hHover, mouseX, mouseY, dt,
                    p.x() + inset(p), contentY(p, 170f), (int) (a * 255));
        }

        private void extractProgress(GuiGraphicsExtractor gui, PanelRect p,
                                     int mouseX, int mouseY, float dt, long now, float a) {
            String status = statusText.isEmpty() ? I18n.tr("正在与微软服务器通信") : statusText;
            status = ellipsize(bodyFont, status, Math.max(1f, p.w() - inset(p) * 2f - 18f));
            CustomFontRenderer.drawString(gui, bodyFont, status + dots(now),
                    p.x() + inset(p), contentY(p, 70f), scaleAlpha(TEXT_HIGH, a));
            CustomFontRenderer.drawString(gui, bodyFont, I18n.tr("请稍候，正在完成令牌交换…"),
                    p.x() + inset(p), contentY(p, 92f), scaleAlpha(TEXT_FAINT, a));

            Link[] links = {new Link(I18n.tr("取消"), PRIMARY, true, this::cancelLogin)};
            drawHLinks(gui, links, hHover, mouseX, mouseY, dt,
                    p.x() + inset(p), contentY(p, 126f), (int) (a * 255));
        }

        private void extractError(GuiGraphicsExtractor gui, PanelRect p,
                                  int mouseX, int mouseY, float dt, float a) {
            List<String> lines = errorLines();
            float y = contentY(p, 64f);
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                CustomFontRenderer.drawString(gui, bodyFont, ellipsize(bodyFont, lines.get(lineIndex),
                        Math.max(1f, p.w() - inset(p) * 2f)), p.x() + inset(p), y,
                        scaleAlpha(ERROR, a));
                y = contentY(p, 64f + (lineIndex + 1) * 16f);
            }
            Link[] links = {
                    new Link(I18n.tr("返回"), PRIMARY, true, () -> setState(MsState.CHOICE)),
                    new Link(I18n.tr("关闭"), PRIMARY, true, AltManagerScreen.this::closeModal),
            };
            drawHLinks(gui, links, hHover, mouseX, mouseY, dt,
                    p.x() + inset(p), contentY(p, 64f + lines.size() * 16f + 12f),
                    (int) (a * 255));
        }

        private void drawBodyLines(GuiGraphicsExtractor gui, PanelRect p, float a,
                                   float offsetY, String... lines) {
            for (int i = 0; i < lines.length; i++) {
                float y = contentY(p, offsetY + i * 16f);
                String line = ellipsize(bodyFont, lines[i], Math.max(1f, p.w() - inset(p) * 2f));
                CustomFontRenderer.drawString(gui, bodyFont, line, p.x() + inset(p), y,
                        scaleAlpha(TEXT_BODY, a));
            }
        }

        // ---- 链接定义 ----

        private Link[] waitingLinks() {
            return new Link[]{
                    new Link(I18n.tr("改用手动模式"), PRIMARY, true, () -> {
                        cancelLogin();
                        setState(MsState.MANUAL);
                    }),
                    new Link(I18n.tr("取消"), PRIMARY, true, this::cancelLogin),
            };
        }

        private Link[] manualLinks() {
            boolean hasInput = !urlField.text().isBlank();
            return new Link[]{
                    new Link(I18n.tr("打开授权页面"), PRIMARY, true, () -> {
                        try {
                            MicrosoftAuthService.openAuthorizationPage();
                        } catch (MicrosoftAuthService.AuthException e) {
                            fail(e.getMessage());
                        }
                    }),
                    new Link(I18n.tr("开始登录"), PRIMARY, hasInput, this::startManualLogin),
                    new Link(I18n.tr("返回"), PRIMARY, true, () -> setState(MsState.CHOICE)),
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
            errorText = message == null || message.isBlank() ? I18n.tr("未知错误") : message;
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
                    String[] items = {I18n.tr("自动登录（推荐）"), I18n.tr("手动粘贴链接"), I18n.tr("取消")};
                    for (int i = 0; i < items.length; i++) {
                        float itemY = contentY(p, 118f + i * 28f);
                        if (mx >= p.x() + 20 && mx <= p.x() + p.w() - 20
                                && my >= itemY - 5 && my <= itemY + linkFont.lineHeight + 5) {
                            if (i == 0) startAutoLogin();
                            else if (i == 1) setState(MsState.MANUAL);
                            else closeModal();
                            return;
                        }
                    }
                }
                case WAITING -> {
                    Link[] links = waitingLinks();
                    int idx = hLinkAt(links, p.x() + inset(p), contentY(p, 142f), mx, my);
                    if (idx >= 0 && links[idx].enabled()) links[idx].action().run();
                }
                case MANUAL -> {
                    Link[] links = manualLinks();
                    int idx = hLinkAt(links, p.x() + inset(p), contentY(p, 170f), mx, my);
                    if (idx >= 0 && links[idx].enabled()) links[idx].action().run();
                }
                case PROGRESS -> {
                    Link[] links = {new Link(I18n.tr("取消"), PRIMARY, true, this::cancelLogin)};
                    int idx = hLinkAt(links, p.x() + inset(p), contentY(p, 126f), mx, my);
                    if (idx >= 0) links[idx].action().run();
                }
                case ERROR -> {
                    List<String> lines = errorLines();
                    float y = contentY(p, 64f + lines.size() * 16f + 12f);
                    Link[] links = {
                            new Link(I18n.tr("返回"), PRIMARY, true, () -> setState(MsState.CHOICE)),
                            new Link(I18n.tr("关闭"), PRIMARY, true, AltManagerScreen.this::closeModal),
                    };
                    int idx = hLinkAt(links, p.x() + inset(p), y, mx, my);
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
                .placeholder(I18n.tr("用户名（3-16 位字母、数字、下划线）"));
        private final HoverSet hHover = new HoverSet();
        private String errorText = "";

        @Override
        String title() {
            return I18n.tr("添加离线账号");
        }

        @Override
        float panelHeight() {
            return 220f;
        }

        private Link[] links() {
            return new Link[]{
                    new Link(I18n.tr("添加"), PRIMARY, true, this::submit),
                    new Link(I18n.tr("取消"), PRIMARY, true, AltManagerScreen.this::closeModal),
            };
        }

        private void submit() {
            String name = nameField.text().trim();
            if (name.isEmpty()) {
                errorText = I18n.tr("请输入用户名");
                return;
            }
            if (name.length() < 3) {
                errorText = I18n.tr("用户名至少 3 个字符");
                return;
            }
            if (hasDuplicateName(name)) {
                errorText = I18n.tr("已存在同名账号");
                return;
            }
            addOfflineAccount(name);
            closeModal();
        }

        @Override
        void extractContent(GuiGraphicsExtractor gui, PanelRect p,
                            int mouseX, int mouseY, float dt, long now, float a) {
            CustomFontRenderer.drawString(gui, bodyFont,
                    I18n.tr("离线账号无需联网验证，仅适用于离线模式服务器。"),
                    p.x() + inset(p), contentY(p, 64f), scaleAlpha(TEXT_BODY, a));

            float fieldY = contentY(p, 96f);
            nameField.render(gui, fieldFont, p.x() + inset(p), fieldY, Math.max(1f, p.w() - inset(p) * 2f), now, a);

            if (!errorText.isEmpty()) {
                CustomFontRenderer.drawString(gui, bodyFont, errorText,
                        p.x() + inset(p), contentY(p, 132f), scaleAlpha(ERROR, a));
            }

            drawHLinks(gui, links(), hHover, mouseX, mouseY, dt,
                    p.x() + inset(p), contentY(p, 158f), (int) (a * 255));
        }

        @Override
        void mouseClicked(double mx, double my) {
            PanelRect p = panel();
            int idx = hLinkAt(links(), p.x() + inset(p), contentY(p, 158f), mx, my);
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
            return I18n.tr("删除账号");
        }

        @Override
        float panelHeight() {
            return 190f;
        }

        private Link[] links() {
            return new Link[]{
                    new Link(I18n.tr("删除"), ERROR, true, () -> {
                        int idx = accounts.indexOf(target);
                        AltManager.remove(target, accounts);
                        if (selected >= accounts.size()) selected = accounts.size() - 1;
                        if (idx >= 0 && selected > idx) selected--;
                        showStatus(I18n.trf("已删除账号：%s", target.getName()), TEXT_HIGH);
                        closeModal();
                    }),
                    new Link(I18n.tr("取消"), PRIMARY, true, AltManagerScreen.this::closeModal),
            };
        }

        @Override
        void extractContent(GuiGraphicsExtractor gui, PanelRect p,
                            int mouseX, int mouseY, float dt, long now, float a) {
            CustomFontRenderer.drawString(gui, bodyFont,
                    I18n.trf("确定删除账号 %s（%s）？", target.getName(), target.typeLabel()),
                    p.x() + inset(p), contentY(p, 68f), scaleAlpha(TEXT_HIGH, a));
            CustomFontRenderer.drawString(gui, bodyFont, I18n.tr("此操作不可撤销。"),
                    p.x() + inset(p), contentY(p, 86f), scaleAlpha(TEXT_BODY, a));

            drawHLinks(gui, links(), hHover, mouseX, mouseY, dt,
                    p.x() + inset(p), contentY(p, 128f), (int) (a * 255));
        }

        @Override
        void mouseClicked(double mx, double my) {
            PanelRect p = panel();
            int idx = hLinkAt(links(), p.x() + inset(p), contentY(p, 128f), mx, my);
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
    // 链接绘制（悬停胶囊；水平排版）
    // ========================

    private LinkLayout layoutLinks(Link[] links, float x, float y, float maxWidth) {
        float gap = CustomFontRenderer.stringWidth(linkFont, "    ") + 10f;
        float lineStep = linkFont.lineHeight + 16f;
        float cx = x;
        float cy = y;
        float right = x;
        List<LinkBounds> bounds = new ArrayList<>(links.length);
        for (int i = 0; i < links.length; i++) {
            float textW = CustomFontRenderer.stringWidth(linkFont, links[i].label());
            float pillW = textW + 20f;
            if (cx > x && cx + pillW > x + Math.max(1f, maxWidth)) {
                cx = x;
                cy += lineStep;
            }
            bounds.add(new LinkBounds(i, cx - 10f, cy - 6f, pillW,
                    linkFont.lineHeight + 12f, cx, cy));
            right = Math.max(right, cx + textW);
            cx += pillW + gap;
        }
        return new LinkLayout(bounds, Math.max(0f, right - x),
                bounds.isEmpty() ? 0f : cy - y + linkFont.lineHeight + 12f);
    }

    private void drawHLinks(GuiGraphicsExtractor gui, Link[] links, HoverSet hover,
                            int mouseX, int mouseY, float dt, float x, float y, int baseAlpha) {
        drawLinks(gui, links, hover, mouseX, mouseY, dt,
                layoutLinks(links, x, y, linkMaxWidth(x)), baseAlpha);
    }

    private void drawLinks(GuiGraphicsExtractor gui, Link[] links, HoverSet hover,
                           int mouseX, int mouseY, float dt, LinkLayout layout, int baseAlpha) {
        hover.ensure(links.length);
        for (LinkBounds b : layout.bounds()) {
            Link link = links[b.index()];
            boolean over = link.enabled() && mouseX >= b.x() && mouseX <= b.x() + b.w()
                    && mouseY >= b.y() && mouseY <= b.y() + b.h();
            hover.update(b.index(), over, dt);
            float a = baseAlpha / 255f;
            textButton(gui, linkFont, link.label(), b.x(), b.y(), b.w(), b.h(),
                    link.enabled() ? link.idleColor() : Md3Theme.disabledContent(TEXT_BODY),
                    link.enabled() ? hover.get(b.index()) : 0f, a);
        }
    }

    private int hLinkAt(Link[] links, float x, float y, double mx, double my) {
        return linkAt(layoutLinks(links, x, y, linkMaxWidth(x)), mx, my);
    }

    private float linkMaxWidth(float x) {
        Modal active = modal != null ? modal : closingModal;
        if (active != null) {
            PanelRect p = active.panel();
            if (x >= p.x() && x <= p.x() + p.w()) return Math.max(1f, p.x() + p.w() - active.inset(p) - x);
        }
        return Math.max(1f, AltManagerScreen.this.width - x - 12f);
    }

    private int linkAt(LinkLayout layout, double mx, double my) {
        for (LinkBounds b : layout.bounds()) {
            if (mx >= b.x() && mx <= b.x() + b.w() && my >= b.y() && my <= b.y() + b.h()) {
                return b.index();
            }
        }
        return -1;
    }

    // ========================
    // MD3 控件基元（token 全部取自 Md3Theme，与主菜单同源）
    // ========================

    /** MD3 表面：elevation1 投影 + 实底圆角 + 1px 发丝描边，不做磨砂模糊。 */
    private static void surface(GuiGraphicsExtractor gui, float x, float y, float w, float h,
                                int radius, int fill, float alpha) {
        if (w <= 0f || h <= 0f || alpha <= 0.01f) return;
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.round(w);
        int ih = Math.round(h);
        Md3Theme.elevation1(gui, ix, iy, iw, ih, radius);
        CustomRoundedRectRenderer.drawRoundedRect(gui, ix, iy, iw, ih, radius, scaleAlpha(fill, alpha));
        CustomRoundedRectRenderer.drawRoundedOutline(gui, ix, iy, iw, ih, radius,
                scaleAlpha(SURFACE_OUTLINE, alpha), 1);
    }

    /** MD3 filled / tonal 按钮：容器实底 + on-container 文字，悬停叠 8% 状态层。 */
    private static void filledButton(GuiGraphicsExtractor gui, GlyphFont font, String label,
                                     float x, float y, float w, float h, int container,
                                     int content, float hover, float alpha) {
        if (alpha <= 0.01f) return;
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.round(w);
        int ih = Math.round(h);
        CustomRoundedRectRenderer.drawRoundedRect(gui, ix, iy, iw, ih, ih / 2,
                scaleAlpha(container, alpha));
        stateLayer(gui, ix, iy, iw, ih, content, hover, alpha);
        drawCentered(gui, font, label, x + w / 2f, y + (h - font.lineHeight) / 2f,
                scaleAlpha(content, alpha));
    }

    /** MD3 outlined 按钮：透明底 + 1px 描边 + 彩色文字，用于中性/危险操作。 */
    private static void outlinedButton(GuiGraphicsExtractor gui, GlyphFont font, String label,
                                       float x, float y, float w, float h, int content,
                                       int outline, float hover, float alpha) {
        if (alpha <= 0.01f) return;
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.round(w);
        int ih = Math.round(h);
        stateLayer(gui, ix, iy, iw, ih, content, hover, alpha);
        CustomRoundedRectRenderer.drawRoundedOutline(gui, ix, iy, iw, ih, ih / 2,
                scaleAlpha(outline, alpha), 1);
        drawCentered(gui, font, label, x + w / 2f, y + (h - font.lineHeight) / 2f,
                scaleAlpha(content, alpha));
    }

    /** MD3 text 按钮：仅悬停时浮出胶囊状态层，用于对话框动作与链接。 */
    private static void textButton(GuiGraphicsExtractor gui, GlyphFont font, String label,
                                   float x, float y, float w, float h, int content,
                                   float hover, float alpha) {
        if (alpha <= 0.01f) return;
        stateLayer(gui, Math.round(x), Math.round(y), Math.round(w), Math.round(h),
                content, hover, alpha);
        drawCentered(gui, font, label, x + w / 2f, y + (h - font.lineHeight) / 2f,
                scaleAlpha(content, alpha));
    }

    private static void stateLayer(GuiGraphicsExtractor gui, int x, int y, int w, int h,
                                   int content, float hover, float alpha) {
        if (hover <= 0.01f || alpha <= 0.01f) return;
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, w, h, h / 2,
                scaleAlpha(Md3Theme.withAlpha(content, 0.08f), alpha * hover));
    }

    private static Identifier typeIcon(AltAccount acc) {
        return acc.getType() == AltAccount.Type.MICROSOFT ? ICON_MICROSOFT : ICON_OFFLINE;
    }

    // ========================
    // 杂项工具
    // ========================

    private static String extractMessage(Throwable t) {
        if (t == null) return I18n.tr("未知错误");
        if (t instanceof MicrosoftAuthService.AuthException ae) return ae.getMessage();
        String msg = t.getMessage();
        return msg == null || msg.isBlank()
                ? I18n.trf("未知错误（%s）", t.getClass().getSimpleName()) : msg;
    }
}

package geminiclient.gemini.base;

import com.mojang.blaze3d.platform.InputConstants;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.alt.AltManagerScreen;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer.GlyphFont;
import geminiclient.gemini.customRenderer.glsl.InfiniteGridRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static geminiclient.gemini.base.MenuUI.*;

/**
 * 主菜单 —— 底部 Dock 磨砂构图（单色设计语言）。
 *
 * <p>壁纸优先：清晰的壁纸/视频背景占据全部视野，仅顶部/底部各一条极淡的
 * 压暗渐变保证文字可读。标题居上，五个菜单项收进底部一条全圆角磨砂
 * Dock（区域模糊 + 深色渐变 + 白色细描边），悬停时项文字提亮并轻微上浮，
 * 没有多余的卡片与装饰。右上角三个圆形幽灵按钮负责壁纸开关、壁纸选择
 * 与语言切换。窄窗口下 Dock 自动退化为居中竖排磨砂卡。</p>
 */
public class MainMenuScreen extends Screen {

    // ========================
    // Color Constants (ARGB)
    // ========================
    private static final int SCRIM_TOP = 0x4A000000;
    private static final int SCRIM_BOTTOM = 0x40000000;

    // ========================
    // Fonts
    // ========================
    // 全部面取自 MiSans-Bold：这是资源中唯一一个既含完整中文字形、又带
    // 标题磅值的 MiSans 文件。Source Han Sans 子集文件不含 CJK，无法渲染
    // 中文，因此不再使用。
    private static final Identifier FONT_REGULAR =
            Identifier.fromNamespaceAndPath("gemini", "font/misans-bold.ttf");

    private static final float TITLE_FONT_SIZE   = 40f;
    private static final float SUBTITLE_FONT_SIZE = 13f;
    private static final float DOCK_FONT_SIZE     = 15f;
    private static final float SMALL_FONT_SIZE    = 11f;

    private static final float TITLE_SPACING_PX = 12f;

    private static GlyphFont titleFont;
    private static GlyphFont subtitleFont;
    private static GlyphFont dockFont;
    private static GlyphFont smallFont;

    private static void ensureFontsLoaded() {
        if (titleFont == null)
            titleFont = CustomFontRenderer.loadFont(FONT_REGULAR, TITLE_FONT_SIZE);
        if (subtitleFont == null)
            subtitleFont = CustomFontRenderer.loadFont(FONT_REGULAR, SUBTITLE_FONT_SIZE);
        if (dockFont == null)
            dockFont = CustomFontRenderer.loadFont(FONT_REGULAR, DOCK_FONT_SIZE);
        if (smallFont == null)
            smallFont = CustomFontRenderer.loadFont(FONT_REGULAR, SMALL_FONT_SIZE);
    }

    /**
     * 预热主菜单字体，由 {@code UiShaderWarmup} 在加载界面调用，避免首次进入
     * 主菜单时渲染线程现场生成 MSDF。调用方需随后执行
     * {@code CustomFontRenderer.flushAllPages()} 上传图集页。
     *
     * <p>预热范围刻意受限：全部面预热 ASCII；展示中文的面额外预热主菜单
     * 可见的中文（约 20 余字）。MSDF 生成成本约 17ms/字形（96px em）。</p>
     */
    public static void warmup() {
        try {
            ensureFontsLoaded();
            String warmupCjk = I18n.warmupText();
            warmupAsciiAnd(titleFont, "GEMINI");
            warmupAsciiAnd(subtitleFont, warmupCjk);
            warmupAsciiAnd(dockFont, warmupCjk + "↑↓←→");
            warmupAsciiAnd(smallFont, warmupCjk + "GithubDiscord·EN中v0.1.0BG");
        } catch (Throwable t) {
            // 预热失败不影响运行：字形仍会惰性栅格化。
        }
    }

    private static void warmupAsciiAnd(GlyphFont face, String extraText) {
        if (face == null) {
            return;
        }
        for (int cp = 0x20; cp <= 0x7E; cp++) {
            face.getGlyphBlocking(cp);
        }
        for (int i = 0; i < extraText.length(); ) {
            int cp = extraText.codePointAt(i);
            face.getGlyphBlocking(cp);
            i += Character.charCount(cp);
        }
    }

    // ========================
    // Animation Constants
    // ========================
    private static final float HOVER_SPEED      = 12f;  // exponential lerp speed
    private static final float ENTRY_FADE_SPEED = 4f;
    private static final float ITEM_STAGGER     = 0.05f; // dock items, seconds
    private static final float DOCK_RISE_PX     = 18f;   // dock slide-up distance
    private static final float HOVER_RISE_PX    = 2f;    // item lift on hover

    private static final int   UTILITY_SIZE     = 32;
    private static final int   UTILITY_GAP      = 6;
    private static final float DOCK_HEIGHT      = 56f;
    private static final float DOCK_ITEM_PAD    = 18f;
    private static final float DOCK_ITEM_GAP    = 2f;
    private static final float COMPACT_ROW_H    = 42f;
    private static final float COMPACT_CARD_PAD = 14f;

    private static final String MOD_VERSION = "0.1.0";

    // TODO: replace with your real links
    private static final String GITHUB_URL  = "https://github.com/";
    private static final String DISCORD_URL = "https://discord.com/";

    // ========================
    // Inner Types
    // ========================

    private record MenuItem(String label, Runnable action) {}

    /**
     * 单一权威屏幕布局，绘制与命中测试共享。
     */
    private record Layout(
            float titleY, float subtitleY,
            boolean compact,
            float dockX, float dockY, float dockW, float dockH, float[] itemW,
            float cardX, float cardY, float cardW, float cardH,
            float utilCy, float bgCx, float gearCx, float langCx,
            boolean showCorners, float navY, float navX,
            float githubW, float separatorW, float discordW,
            float versionX, float versionW) {

        float itemX(int index) {
            float x = dockX + DOCK_ITEM_PAD;
            for (int i = 0; i < index; i++) {
                x += itemW[i] + DOCK_ITEM_GAP;
            }
            return x;
        }

        float cardRowY(int index) {
            return cardY + COMPACT_CARD_PAD + index * COMPACT_ROW_H;
        }

        float discordX() {
            return navX + githubW + separatorW;
        }
    }

    // ========================
    // State
    // ========================

    private final List<MenuItem> menuItems = new ArrayList<>();
    private float[] hoverProgress;
    private float entryAlpha;        // global fade-in: 0 → 1
    private int focusedIndex = -1;
    private int hoveredIndex = -1;

    // Corner link hover
    private float githubHover;
    private float discordHover;

    // Utility button hover
    private float bgToggleHover;
    private float bgCycleHover;
    private float bgLangHover;

    // Mouse position (wallpaper parallax + particles)
    private float mouseX;
    private float mouseY;

    // Particle system for custom background
    private ParticleSystem particleSystem;

    private long screenOpenTime;
    private long lastFrameMs;
    private boolean firstInit = true;

    // ========================
    // Constructor
    // ========================

    public MainMenuScreen() {
        super(Component.literal("Gemini Main Menu"));
        // 引用计数：主菜单 ↔ AltManager 互切时壁纸资源保持存活
        MenuBackdrop.acquire();
    }

    // ========================
    // Lifecycle: init
    // ========================

    @Override
    protected void init() {
        FileSystem fileSystem = fs();
        if (fileSystem != null) {
            fileSystem.refreshBackgrounds();
        }

        // Initialize particle system
        if (particleSystem == null) {
            particleSystem = new ParticleSystem(this.width, this.height);
        } else {
            particleSystem.resize(this.width, this.height);
        }

        menuItems.clear();
        menuItems.add(new MenuItem(I18n.tr("Singleplayer"),
                () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this))));
        menuItems.add(new MenuItem(I18n.tr("Multiplayer"),
                () -> this.minecraft.gui.setScreen(new JoinMultiplayerScreen(this))));
        menuItems.add(new MenuItem(I18n.tr("Settings"),
                () -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false))));
        menuItems.add(new MenuItem(I18n.tr("Alt Manager"),
                () -> this.minecraft.gui.setScreen(new AltManagerScreen(this))));
        menuItems.add(new MenuItem(I18n.tr("Exit"),
                this.minecraft::stop));

        if (hoverProgress == null || hoverProgress.length != menuItems.size()) {
            hoverProgress = new float[menuItems.size()];
        }

        if (firstInit) {
            screenOpenTime = System.currentTimeMillis();
            firstInit = false;
            entryAlpha = 0f;
        }
        lastFrameMs = System.currentTimeMillis();
    }

    private static FileSystem fs() {
        return Gemini.fileSystem;
    }

    /**
     * Reloads the custom background texture.
     * Called when a new wallpaper is selected from the selector screen.
     */
    public void reloadCustomBackground() {
        MenuBackdrop.reload();
    }

    // ========================
    // Main render
    // ========================

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastFrameMs) / 1000f, 0.1f);
        lastFrameMs = now;
        float elapsed = (now - screenOpenTime) / 1000f;

        this.mouseX = mouseX;
        this.mouseY = mouseY;

        FileSystem fileSystem = fs();

        // Particle system follows the mouse only while the wallpaper is up
        boolean wallpaperActive = MenuBackdrop.isActive();
        if (particleSystem != null && wallpaperActive) {
            particleSystem.updateMousePosition(mouseX, mouseY);
            particleSystem.update(dt);
        }

        entryAlpha += (1f - entryAlpha) * dt * ENTRY_FADE_SPEED;
        if (entryAlpha > 0.99f) entryAlpha = 1f;

        Layout layout = layout();

        // ── 1. Background: wallpaper (or GLSL grid fallback) ──
        MenuBackdrop.render(gui, this.width, this.height, mouseX, mouseY);
        if (!wallpaperActive && fileSystem != null && fileSystem.isParticlesEnabled()) {
            InfiniteGridRenderer.render(elapsed);
        }
        if (particleSystem != null && wallpaperActive) {
            particleSystem.render(gui, partialTicks);
        }

        // ── 2. Readability scrims ─────────────────────────
        edgeScrim(gui, 0, 0, this.width, 110, scaleAlpha(SCRIM_TOP, entryAlpha), true);
        edgeScrim(gui, 0, this.height - 150, this.width, 150, scaleAlpha(SCRIM_BOTTOM, entryAlpha), false);

        // ── 3. Hover animations ───────────────────────────
        updateHovers(layout, mouseX, mouseY, dt);

        // ── 4. Title + subtitle ───────────────────────────
        drawTitle(gui, layout, elapsed);
        drawSubtitle(gui, layout, elapsed);

        // ── 5. Menu (dock / compact card) ─────────────────
        if (layout.compact()) {
            drawCompactCard(gui, layout, elapsed);
        } else {
            drawDock(gui, layout, elapsed);
        }

        // ── 6. Corner links + version ─────────────────────
        drawCorners(gui, layout, elapsed);

        // ── 7. Utility buttons ────────────────────────────
        drawUtilityButtons(gui, layout, elapsed);

        // ── 8. Boot star mark（加载画面交接的左上角品牌标记） ──
        GeminiLoadingOverlay.drawMenuMark(gui);
    }

    // ========================
    // Layout
    // ========================

    private Layout layout() {
        ensureFontsLoaded();
        int n = menuItems.size();

        // ── Utility buttons (top right) ──
        float pad = Math.max(16f, this.width * 0.03f);
        float bgCx = this.width - pad - UTILITY_SIZE / 2f;
        float gearCx = bgCx - UTILITY_SIZE - UTILITY_GAP;
        float langCx = gearCx - UTILITY_SIZE - UTILITY_GAP;
        float utilCy = Math.max(20f, pad * 0.6f) + UTILITY_SIZE / 2f;

        // ── Title / subtitle (top center) ──
        float titleY = Math.max(46f, this.height * 0.14f);
        float subtitleY = titleY + TITLE_FONT_SIZE * 0.9f + 18f;

        // ── Dock (bottom center) ──
        float[] itemW = new float[n];
        float total = DOCK_ITEM_PAD * 2f + DOCK_ITEM_GAP * (n - 1);
        for (int i = 0; i < n; i++) {
            itemW[i] = (dockFont == null ? DOCK_FONT_SIZE * 6f
                    : CustomFontRenderer.stringWidth(dockFont, menuItems.get(i).label())) + 36f;
            total += itemW[i];
        }

        boolean compact = total > this.width - 24f;
        float dockX = 0, dockY = 0, dockW = 0;
        float cardX = 0, cardY = 0, cardW = 0, cardH = 0;

        if (!compact) {
            dockW = total;
            dockX = (this.width - dockW) / 2f;
            dockY = this.height - Math.max(26f, this.height * 0.075f) - DOCK_HEIGHT;
        } else {
            float maxLabelW = 0f;
            if (dockFont != null) {
                for (MenuItem item : menuItems) {
                    maxLabelW = Math.max(maxLabelW, CustomFontRenderer.stringWidth(dockFont, item.label()));
                }
            }
            cardW = Math.max(140f, maxLabelW + 64f);
            cardH = n * COMPACT_ROW_H + COMPACT_CARD_PAD * 2f;
            cardX = (this.width - cardW) / 2f;
            cardY = Math.max(subtitleY + 40f, (this.height - cardH) / 2f);
        }

        // ── Corner links + version ──
        boolean showCorners = this.width >= 720 && this.height >= 300;
        float githubW = smallFont == null ? 0f : CustomFontRenderer.stringWidth(smallFont, I18n.tr("Github"));
        float separatorW = smallFont == null ? 0f : CustomFontRenderer.stringWidth(smallFont, "   ·   ");
        float discordW = smallFont == null ? 0f : CustomFontRenderer.stringWidth(smallFont, I18n.tr("Discord"));
        String version = "v" + MOD_VERSION;
        float versionW = smallFont == null ? 0f : CustomFontRenderer.stringWidth(smallFont, version);
        float navY = this.height - 26f;
        float navX = 18f;
        float versionX = this.width - 18f - versionW;

        return new Layout(titleY, subtitleY, compact,
                dockX, dockY, dockW, DOCK_HEIGHT, itemW,
                cardX, cardY, cardW, cardH,
                utilCy, bgCx, gearCx, langCx,
                showCorners, navY, navX,
                githubW, separatorW, discordW,
                versionX, versionW);
    }

    // ========================
    // Hover updates
    // ========================

    private void updateHovers(Layout layout, double mx, double my, float dt) {
        hoveredIndex = -1;
        int n = menuItems.size();
        for (int i = 0; i < n; i++) {
            boolean over = isItemHover(layout, mx, my, i);
            if (over) hoveredIndex = i;
            // 键盘焦点与鼠标悬停同一视觉语言
            float target = over || i == focusedIndex ? 1f : 0f;
            hoverProgress[i] += (target - hoverProgress[i]) * dt * HOVER_SPEED;
        }

        boolean overGithub = layout.showCorners()
                && inRect(mx, my, layout.navX() - 4, layout.navY() - 4,
                        layout.githubW() + 8, smallFont == null ? 0 : smallFont.lineHeight + 8);
        boolean overDiscord = layout.showCorners()
                && inRect(mx, my, layout.discordX() - 4, layout.navY() - 4,
                        layout.discordW() + 8, smallFont == null ? 0 : smallFont.lineHeight + 8);
        githubHover += ((overGithub ? 1f : 0f) - githubHover) * dt * HOVER_SPEED;
        discordHover += ((overDiscord ? 1f : 0f) - discordHover) * dt * HOVER_SPEED;

        bgToggleHover += ((isUtilHover(layout, mx, my, layout.bgCx()) ? 1f : 0f) - bgToggleHover) * dt * HOVER_SPEED;
        bgCycleHover += ((isUtilHover(layout, mx, my, layout.gearCx()) ? 1f : 0f) - bgCycleHover) * dt * HOVER_SPEED;
        bgLangHover += ((isUtilHover(layout, mx, my, layout.langCx()) ? 1f : 0f) - bgLangHover) * dt * HOVER_SPEED;
    }

    // ========================
    // Title + subtitle
    // ========================

    private void drawTitle(GuiGraphicsExtractor gui, Layout layout, float elapsed) {
        ensureFontsLoaded();
        if (titleFont == null) return;

        float reveal = easeOutCubic(clamp01((elapsed - 0.08f) * 2f)) * entryAlpha;
        if (reveal <= 0.01f) return;

        String title = "GEMINI";
        float spacing = TITLE_SPACING_PX;
        float totalW = computeSpacedWidth(titleFont, title, spacing);
        float cx = (this.width - totalW) / 2f;
        // 从上方轻微沉降入场
        float y = layout.titleY() - (1f - reveal) * 10f;
        int alpha = (int) (reveal * 255);

        for (int i = 0; i < title.length(); i++) {
            String ch = title.substring(i, i + 1);
            float chW = CustomFontRenderer.stringWidth(titleFont, ch);
            CustomFontRenderer.drawString(gui, titleFont, ch, cx, y, (alpha << 24) | (TEXT_HIGH & 0x00FFFFFF));
            cx += chW + spacing;
        }
    }

    private void drawSubtitle(GuiGraphicsExtractor gui, Layout layout, float elapsed) {
        if (subtitleFont == null) return;
        float reveal = easeOutCubic(clamp01((elapsed - 0.22f) * 2f)) * entryAlpha;
        if (reveal <= 0.01f) return;

        int alpha = (int) (reveal * 220);
        drawCentered(gui, subtitleFont, I18n.tr("Modern Minecraft Client"),
                this.width / 2f, layout.subtitleY(), (alpha << 24) | (TEXT_BODY & 0x00FFFFFF));
    }

    // ========================
    // Dock menu
    // ========================

    private void drawDock(GuiGraphicsExtractor gui, Layout layout, float elapsed) {
        ensureFontsLoaded();
        if (dockFont == null) return;

        float reveal = easeOutCubic(clamp01((elapsed - 0.3f) * 2.2f)) * entryAlpha;
        if (reveal <= 0.01f) return;

        float dockY = layout.dockY() + (1f - reveal) * DOCK_RISE_PX;

        glassPanel(gui, layout.dockX(), dockY, layout.dockW(), layout.dockH(),
                layout.dockH() / 2f, reveal, 10f);

        for (int i = 0; i < menuItems.size(); i++) {
            float itemReveal = easeOutCubic(clamp01((elapsed - 0.38f - i * ITEM_STAGGER) * 2.5f));
            if (itemReveal <= 0.01f) continue;

            float hp = hoverProgress[i];
            float itemX = layout.itemX(i);
            float itemW = layout.itemW()[i];

            // 悬停 / 焦点胶囊
            if (hp > 0.01f) {
                CustomRoundedRectRenderer.drawRoundedRect(gui,
                        Math.round(itemX + 4), Math.round(dockY + 6),
                        Math.round(itemW - 8), Math.round(layout.dockH() - 12),
                        Math.round((layout.dockH() - 12) / 2f),
                        scaleAlpha(lerpColor(HOVER_FILL, SELECTED_FILL, hp), reveal * hp));
            }

            // 文字：居中 + 悬停轻微上浮
            String label = menuItems.get(i).label();
            float labelW = CustomFontRenderer.stringWidth(dockFont, label);
            float textX = itemX + (itemW - labelW) / 2f;
            float textY = dockY + (layout.dockH() - dockFont.lineHeight) / 2f - hp * HOVER_RISE_PX;
            int alpha = (int) (reveal * itemReveal * 255);
            int color = lerpColor((alpha << 24) | (TEXT_BODY & 0x00FFFFFF),
                    (alpha << 24) | (TEXT_HIGH & 0x00FFFFFF), hp);
            CustomFontRenderer.drawString(gui, dockFont, label, textX, textY, color);
        }
    }

    /** 窄窗口降级：居中竖排磨砂卡。 */
    private void drawCompactCard(GuiGraphicsExtractor gui, Layout layout, float elapsed) {
        ensureFontsLoaded();
        if (dockFont == null) return;

        float reveal = easeOutCubic(clamp01((elapsed - 0.3f) * 2.2f)) * entryAlpha;
        if (reveal <= 0.01f) return;

        float cardY = layout.cardY() + (1f - reveal) * DOCK_RISE_PX;
        glassPanel(gui, layout.cardX(), cardY, layout.cardW(), layout.cardH(),
                16f, reveal, 8f);

        for (int i = 0; i < menuItems.size(); i++) {
            float itemReveal = easeOutCubic(clamp01((elapsed - 0.38f - i * ITEM_STAGGER) * 2.5f));
            if (itemReveal <= 0.01f) continue;

            float hp = hoverProgress[i];
            float rowX = layout.cardX() + COMPACT_CARD_PAD;
            float rowW = layout.cardW() - COMPACT_CARD_PAD * 2f;
            float rowY = layout.cardRowY(i) + (1f - reveal) * DOCK_RISE_PX;

            if (hp > 0.01f) {
                CustomRoundedRectRenderer.drawRoundedRect(gui,
                        Math.round(rowX), Math.round(rowY + 2),
                        Math.round(rowW), Math.round(COMPACT_ROW_H - 4), 12,
                        scaleAlpha(lerpColor(HOVER_FILL, SELECTED_FILL, hp), reveal * hp));
            }

            String label = menuItems.get(i).label();
            float textY = rowY + (COMPACT_ROW_H - dockFont.lineHeight) / 2f;
            int alpha = (int) (reveal * itemReveal * 255);
            int color = lerpColor((alpha << 24) | (TEXT_BODY & 0x00FFFFFF),
                    (alpha << 24) | (TEXT_HIGH & 0x00FFFFFF), hp);
            drawCentered(gui, dockFont, label, rowX + rowW / 2f, textY, color);
        }
    }

    // ========================
    // Corner links + version
    // ========================

    private void drawCorners(GuiGraphicsExtractor gui, Layout layout, float elapsed) {
        ensureFontsLoaded();
        if (smallFont == null || !layout.showCorners()) return;

        float reveal = easeOutCubic(clamp01((elapsed - 0.55f) * 2.5f)) * entryAlpha;
        if (reveal <= 0.01f) return;
        int alpha = (int) (reveal * 255);

        // Github · Discord（左下）
        float x = layout.navX();
        int githubColor = lerpColor((alpha << 24) | (TEXT_FAINT & 0x00FFFFFF),
                (alpha << 24) | (TEXT_HIGH & 0x00FFFFFF), githubHover);
        CustomFontRenderer.drawString(gui, smallFont, I18n.tr("Github"), x, layout.navY(), githubColor);
        float sepX = x + layout.githubW();
        CustomFontRenderer.drawString(gui, smallFont, "   ·   ", sepX, layout.navY(),
                (alpha << 24) | (TEXT_GHOST & 0x00FFFFFF));
        float discordX = sepX + layout.separatorW();
        int discordColor = lerpColor((alpha << 24) | (TEXT_FAINT & 0x00FFFFFF),
                (alpha << 24) | (TEXT_HIGH & 0x00FFFFFF), discordHover);
        CustomFontRenderer.drawString(gui, smallFont, I18n.tr("Discord"), discordX, layout.navY(), discordColor);

        // 版本（右下）
        CustomFontRenderer.drawString(gui, smallFont, "v" + MOD_VERSION, layout.versionX(), layout.navY(),
                (alpha << 24) | (TEXT_GHOST & 0x00FFFFFF));
    }

    // ========================
    // Utility buttons (top right)
    // ========================

    private void drawUtilityButtons(GuiGraphicsExtractor gui, Layout layout, float elapsed) {
        ensureFontsLoaded();
        if (smallFont == null) return;

        float reveal = easeOutCubic(clamp01((elapsed - 0.45f) * 2.5f)) * entryAlpha;
        if (reveal <= 0.01f) return;

        FileSystem fileSystem = fs();
        boolean fileExists = fileSystem != null && fileSystem.customBackgroundFileExists();
        boolean bgEnabled = fileSystem != null && fileSystem.isCustomBackgroundEnabled();

        // BG 开关：启用时右上角常亮状态点
        ghostIconButton(gui, smallFont, "BG", layout.bgCx(), layout.utilCy(), UTILITY_SIZE,
                bgToggleHover, fileExists, reveal);
        if (fileExists && bgEnabled) {
            CustomRoundedRectRenderer.drawCircle(gui,
                    layout.bgCx() + UTILITY_SIZE / 2f - 5f, layout.utilCy() - UTILITY_SIZE / 2f + 5f,
                    4, scaleAlpha(TEXT_HIGH, reveal));
        }

        // 壁纸选择器
        ghostIconButton(gui, smallFont, "⚙", layout.gearCx(), layout.utilCy(), UTILITY_SIZE,
                bgCycleHover, true, reveal);

        // 语言切换
        ghostIconButton(gui, smallFont, I18n.getLanguage().label(), layout.langCx(), layout.utilCy(),
                UTILITY_SIZE, bgLangHover, true, reveal);
    }

    // ========================
    // Input
    // ========================

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent mouse, boolean idk) {
        Layout layout = layout();
        double mx = mouse.x();
        double my = mouse.y();
        FileSystem fileSystem = fs();

        // BG 开关
        if (isUtilHover(layout, mx, my, layout.bgCx())) {
            if (fileSystem != null && fileSystem.customBackgroundFileExists()) {
                fileSystem.toggleCustomBackground();
                if (fileSystem.isCustomBackgroundEnabled()) {
                    // 下一次渲染按当前壁纸重建
                    MenuBackdrop.reload();
                } else {
                    // 关闭后停掉视频解码线程，避免空转
                    MenuBackdrop.releaseAll();
                }
            }
            return true;
        }

        // 壁纸选择器
        if (isUtilHover(layout, mx, my, layout.gearCx())) {
            if (fileSystem != null) {
                this.minecraft.gui.setScreen(new BackgroundSelectorScreen(this));
            }
            return true;
        }

        // 语言切换
        if (isUtilHover(layout, mx, my, layout.langCx())) {
            if (fileSystem != null) {
                fileSystem.toggleLanguage();
            }
            firstInit = true;
            init();
            return true;
        }

        // 角落链接
        if (layout.showCorners()) {
            float linkH = smallFont == null ? 12 : smallFont.lineHeight + 8;
            if (inRect(mx, my, layout.navX() - 4, layout.navY() - 4, layout.githubW() + 8, linkH)) {
//            Util.getPlatform().openUri(GITHUB_URL);
                return true;
            }
            if (inRect(mx, my, layout.discordX() - 4, layout.navY() - 4, layout.discordW() + 8, linkH)) {
//            Util.getPlatform().openUri(DISCORD_URL);
                return true;
            }
        }

        // 菜单项
        for (int i = 0; i < menuItems.size(); i++) {
            if (isItemHover(layout, mx, my, i)) {
                focusedIndex = i;
                menuItems.get(i).action.run();
                return true;
            }
        }
        return super.mouseClicked(mouse, idk);
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        if (MenuBackdrop.importWallpapers(files)) {
            MenuBackdrop.reload();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        int n = menuItems.size();

        // 键盘导航：四方向均可移动 Dock 焦点
        if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_RIGHT
                || key == InputConstants.KEY_UP || key == InputConstants.KEY_LEFT) {
            int dir = (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_RIGHT) ? 1 : -1;
            if (focusedIndex < 0) focusedIndex = dir > 0 ? 0 : n - 1;
            else focusedIndex = (focusedIndex + dir + n) % n;
            return true;
        }
        // Enter / Numpad Enter
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            if (focusedIndex >= 0 && focusedIndex < n) {
                menuItems.get(focusedIndex).action.run();
                return true;
            }
        }
        // 直达键: S, M, O, A, E
        if (n == 5) {
            if (key == InputConstants.KEY_S) { menuItems.get(0).action.run(); return true; }
            if (key == InputConstants.KEY_M) { menuItems.get(1).action.run(); return true; }
            if (key == InputConstants.KEY_O) { menuItems.get(2).action.run(); return true; }
            if (key == InputConstants.KEY_A) { menuItems.get(3).action.run(); return true; }
            if (key == InputConstants.KEY_E) { menuItems.get(4).action.run(); return true; }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        // 引用计数归零（进入世界 / 打开其他界面）时才真正释放视频解码器；
        // 主菜单 ↔ AltManager 互切时资源保持存活。
        MenuBackdrop.release();
    }

    // ========================
    // Hit testing
    // ========================

    private boolean isItemHover(Layout layout, double mx, double my, int index) {
        if (dockFont == null) return false;
        if (layout.compact()) {
            return inRect(mx, my, layout.cardX(), layout.cardRowY(index),
                    layout.cardW(), COMPACT_ROW_H);
        }
        return inRect(mx, my, layout.itemX(index), layout.dockY(),
                layout.itemW()[index], layout.dockH());
    }

    private boolean isUtilHover(Layout layout, double mx, double my, float cx) {
        float r = UTILITY_SIZE / 2f + 2f;
        return inRect(mx, my, cx - r, layout.utilCy() - r, r * 2, r * 2);
    }

    // ========================
    // Text helpers
    // ========================

    private static float computeSpacedWidth(GlyphFont font, String text, float spacing) {
        if (font == null) return 0;
        float w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += CustomFontRenderer.stringWidth(font, text.substring(i, i + 1)) + spacing;
        }
        if (w > 0) w -= spacing;
        return w;
    }
}

package geminiclient.gemini.base;

import com.mojang.blaze3d.platform.InputConstants;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.MainMenuDrawer.Callbacks;
import geminiclient.gemini.base.MainMenuDrawer.Item;
import geminiclient.gemini.base.alt.AltManagerScreen;
import geminiclient.gemini.customRenderer.glsl.CustomBlurRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer.GlyphFont;
import geminiclient.gemini.customRenderer.glsl.InfiniteGridRenderer;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3RenderUtils;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Theme;
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

/**
 * 主菜单 —— 左侧 MD3 侧边抽屉构图。
 *
 * <p>壁纸优先：清晰的壁纸/视频背景占据全部视野。导航收进一个 MD3
 * Navigation Drawer 形态的左侧全高抽屉（{@link MainMenuDrawer}）：GEMINI
 * 大标题、五个菜单项、工具按钮与链接全部住在抽屉里。收起时抽屉整体
 * 滑出屏幕左侧，仅在左缘中部露出一个右指圆角三角形把手；鼠标靠近
 * 左缘后抽屉向外伸出，同时全屏背景做径向模糊 + 变暗（一次性经
 * {@link CustomBlurRenderer} 完成）形成层次。配色与圆角、状态层遵循
 * MD3 token（{@link Md3Theme}，与 ClickGui 同源）。</p>
 */
public class MainMenuScreen extends Screen {

    // ========================
    // Fonts
    // ========================
    // 全部面取自 MiSans-Bold：这是资源中唯一一个既含完整中文字形、又带
    // 标题磅值的 MiSans 文件。Source Han Sans 子集文件不含 CJK，无法渲染
    // 中文，因此不再使用。
    private static final Identifier FONT_REGULAR =
            Identifier.fromNamespaceAndPath("gemini", "font/misans-bold.ttf");

    private static final float TITLE_FONT_SIZE   = 32f;
    private static final float SUBTITLE_FONT_SIZE = 12f;
    private static final float ROW_FONT_SIZE      = 14f;
    private static final float SMALL_FONT_SIZE    = 10f;

    static GlyphFont titleFont;
    static GlyphFont subtitleFont;
    static GlyphFont rowFont;
    static GlyphFont smallFont;

    static void ensureFontsLoaded() {
        if (titleFont == null)
            titleFont = CustomFontRenderer.loadFont(FONT_REGULAR, TITLE_FONT_SIZE);
        if (subtitleFont == null)
            subtitleFont = CustomFontRenderer.loadFont(FONT_REGULAR, SUBTITLE_FONT_SIZE);
        if (rowFont == null)
            rowFont = CustomFontRenderer.loadFont(FONT_REGULAR, ROW_FONT_SIZE);
        if (smallFont == null)
            smallFont = CustomFontRenderer.loadFont(FONT_REGULAR, SMALL_FONT_SIZE);
    }

    /**
     * 预热主菜单字体，由 {@code UiShaderWarmup} 在加载界面调用，避免首次进入
     * 主菜单时字形才刚排队生成、当帧只能由 vanilla 字体兜底。调用方需随后执行
     * {@code CustomFontRenderer.flushPendingGlyphs()} 应用完成的三角化结果。
     *
     * <p>预热范围刻意受限：全部面预热 ASCII；展示中文的面额外预热主菜单
     * 可见的中文（约 20 余字）。三角化成本随字形笔画复杂度变化，全量预热
     * 会拖慢加载界面。</p>
     */
    public static void warmup() {
        try {
            ensureFontsLoaded();
            String warmupCjk = I18n.warmupText();
            warmupAsciiAnd(titleFont, "GEMINI");
            warmupAsciiAnd(subtitleFont, warmupCjk);
            warmupAsciiAnd(rowFont, warmupCjk + "↑↓←→");
            warmupAsciiAnd(smallFont, warmupCjk + "GithubDiscord·EN中v0.1.0BG");
        } catch (Throwable t) {
            // 预热失败不影响运行：字形仍会惰性三角化。
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
    // Constants
    // ========================
    private static final String MOD_VERSION = "0.1.0";

    /** 背景模糊 / 变暗的峰值（drawerT = 1 时），与 ClickGui 幕层同量级。 */
    private static final float SCRIM_ALPHA = 0.45f;
    private static final float BLUR_STRENGTH = 14f;

    // ========================
    // State
    // ========================
    private final List<Item> menuItems = new ArrayList<>();
    private MainMenuDrawer drawer;

    // Global fade-in: 0 → 1
    private float entryAlpha;

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

        ensureFontsLoaded();
        menuItems.clear();
        menuItems.add(new Item(I18n.tr("Singleplayer"),
                Md3RenderUtils.clickGuiIcon("person"), false,
                () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this))));
        menuItems.add(new Item(I18n.tr("Multiplayer"),
                Md3RenderUtils.menuIcon("groups"), false,
                () -> this.minecraft.gui.setScreen(new JoinMultiplayerScreen(this))));
        menuItems.add(new Item(I18n.tr("Settings"),
                Md3RenderUtils.menuIcon("settings"), false,
                () -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options))));
        menuItems.add(new Item(I18n.tr("Alt Manager"),
                Md3RenderUtils.clickGuiIcon("accessible_forward"), false,
                () -> this.minecraft.gui.setScreen(new AltManagerScreen(this))));
        menuItems.add(new Item(I18n.tr("Exit"),
                Md3RenderUtils.menuIcon("exit_to_app"), true,
                this.minecraft::stop));

        this.drawer = new MainMenuDrawer(menuItems, buildCallbacks(),
                titleFont, subtitleFont, rowFont, smallFont);

        if (firstInit) {
            screenOpenTime = System.currentTimeMillis();
            firstInit = false;
            entryAlpha = 0f;
        }
        lastFrameMs = System.currentTimeMillis();
    }

    private Callbacks buildCallbacks() {
        return new Callbacks() {
            @Override
            public void onToggleBackground() {
                toggleBackground();
            }

            @Override
            public boolean backgroundToggleAvailable() {
                FileSystem fs = fs();
                return fs != null && fs.customBackgroundFileExists();
            }

            @Override
            public boolean backgroundEnabled() {
                FileSystem fs = fs();
                return fs != null && fs.isCustomBackgroundEnabled();
            }

            @Override
            public void onOpenWallpaperSelector() {
                if (fs() != null) {
                    MainMenuScreen.this.minecraft.gui.setScreen(new BackgroundSelectorScreen(MainMenuScreen.this));
                }
            }

            @Override
            public void onToggleLanguage() {
                FileSystem fs = fs();
                if (fs != null) {
                    fs.toggleLanguage();
                }
                // 标签需要按新语言重建
                firstInit = true;
                init();
            }

            @Override
            public String version() {
                return MOD_VERSION;
            }
        };
    }

    private void toggleBackground() {
        FileSystem fileSystem = fs();
        if (fileSystem == null || !fileSystem.customBackgroundFileExists()) {
            return;
        }
        fileSystem.toggleCustomBackground();
        if (fileSystem.isCustomBackgroundEnabled()) {
            // 下一次渲染按当前壁纸重建
            MenuBackdrop.reload();
        } else {
            // 关闭后停掉视频解码线程，避免空转
            MenuBackdrop.releaseAll();
        }
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

        FileSystem fileSystem = fs();

        // Particle system follows the mouse only while the wallpaper is up
        boolean wallpaperActive = MenuBackdrop.isActive();
        if (particleSystem != null && wallpaperActive) {
            particleSystem.updateMousePosition(mouseX, mouseY);
            particleSystem.update(dt);
        }

        entryAlpha += (1f - entryAlpha) * dt * 4f;
        if (entryAlpha > 0.99f) entryAlpha = 1f;

        // ── 1. Background: wallpaper (or GLSL grid fallback) ──
        MenuBackdrop.render(gui, this.width, this.height, mouseX, mouseY);
        if (!wallpaperActive && fileSystem != null && fileSystem.isParticlesEnabled()) {
            InfiniteGridRenderer.render(elapsed);
        }
        if (particleSystem != null && wallpaperActive) {
            particleSystem.render(gui, partialTicks);
        }

        // ── 2. Drawer state + background layering (blur + dim scale with slide) ──
        drawer.update(mouseX, mouseY, dt);
        float t = drawer.drawerT();
        if (t > 0.015f) {
            CustomBlurRenderer.render(0, 0, this.width, this.height, 0,
                    Md3Theme.withAlpha(0xFF000000, SCRIM_ALPHA * t), BLUR_STRENGTH * t);
        }

        // ── 3. Boot star mark（加载画面交接的左上角品牌标记），抽屉展开时盖在其上 ──
        GeminiLoadingOverlay.drawMenuMark(gui);

        // ── 4. Drawer（含左缘三角形把手） ──
        drawer.render(gui, this.width, this.height, mouseX, mouseY, dt, elapsed, entryAlpha);
    }

    // ========================
    // Input
    // ========================

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent mouse, boolean idk) {
        if (drawer.handleClick(mouse.x(), mouse.y())) {
            return true;
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

        // ESC：展开态先收起抽屉，避免直接关闭根界面
        if (key == InputConstants.KEY_ESCAPE && drawer.isExpanded()) {
            drawer.collapse();
            return true;
        }
        // 键盘导航：四方向均可移动抽屉焦点（收起时自动展开）
        if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_RIGHT
                || key == InputConstants.KEY_UP || key == InputConstants.KEY_LEFT) {
            int dir = (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_RIGHT) ? 1 : -1;
            drawer.moveFocus(dir);
            return true;
        }
        // Enter / Numpad Enter
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            if (drawer.focusedIndex() >= 0 && drawer.focusedIndex() < n && drawer.isExpanded()) {
                drawer.activateFocused();
                return true;
            }
        }
        // 直达键: S, M, O, A, E
        if (n == 5) {
            if (key == InputConstants.KEY_S) { menuItems.get(0).action().run(); return true; }
            if (key == InputConstants.KEY_M) { menuItems.get(1).action().run(); return true; }
            if (key == InputConstants.KEY_O) { menuItems.get(2).action().run(); return true; }
            if (key == InputConstants.KEY_A) { menuItems.get(3).action().run(); return true; }
            if (key == InputConstants.KEY_E) { menuItems.get(4).action().run(); return true; }
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
}

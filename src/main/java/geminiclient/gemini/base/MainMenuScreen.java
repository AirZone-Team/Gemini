package geminiclient.gemini.base;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import geminiclient.gemini.base.alt.AltManagerScreen;
import geminiclient.gemini.customRenderer.cpu.CustomRectRenderer;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomBlurRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer.GlyphFont;
import geminiclient.gemini.customRenderer.glsl.InfiniteGridRenderer;
import geminiclient.gemini.customRenderer.glsl.SdfUIRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Layered glass main menu built around the fullscreen perspective grid.
 *
 * <p>Typography remains the visual anchor while frosted navigation surfaces,
 * soft accent blooms, rounded focus cards and restrained shadows add depth.
 * All surfaces share the same cyan-tinted dark material and staggered motion.</p>
 */
public class MainMenuScreen extends Screen {

    // ========================
    // Layout Constants
    // ========================
    private static final int MENU_SPACING = 32;
    private static final int NAV_RIGHT_PAD = 40;
    private static final int FOOTER_RIGHT_PAD = 40;
    private static final int FOOTER_BOTTOM_PAD = 30;

    // ========================
    // Color Constants (ARGB)
    // ========================
    private static final int ACCENT          = 0xFF89DDFF; // soft cyan accent
    private static final int TEXT_IDLE       = 0xFF9A9A9A;
    private static final int TEXT_HOVER      = 0xFFEAF6FF; // accent-tinted white
    private static final int TITLE_COLOR     = 0xFFFFFFFF;
    private static final int TITLE_GRADIENT  = 0xFFA8DCFF; // title right edge
    private static final int SUBTITLE_COLOR  = 0xFF7A7A7A;
    private static final int NAV_IDLE        = 0xFFAAAAAA;
    private static final int NAV_HOVER       = 0xFFFFFFFF;
    private static final int VERSION_COLOR   = 0xFF666666;
    private static final int SEPARATOR_COLOR = 0xFF444444;
    private static final int HINT_COLOR      = 0xFF4A4A4A;
    private static final int GLASS_TOP       = 0xA0161D28;
    private static final int GLASS_BOTTOM    = 0xC00A0E15;
    private static final int GLASS_OUTLINE   = 0x5A9CC9DD;
    private static final int ROW_FILL        = 0xB01B2936;
    private static final int ROW_OUTLINE     = 0x6689DDFF;

    private static final int MENU_PANEL_W = 320;
    private static final int MENU_PANEL_RADIUS = 14;
    private static final int MIN_EDGE_PAD = 8;
    private static final int NORMAL_EDGE_PAD = 24;
    private static final int MIN_MENU_SPACING = 20;

    // ========================
    // Fonts
    // ========================
    private static final Identifier FONT_BOLD =
            Identifier.fromNamespaceAndPath("gemini", "font/sourcehansanssc-bold.ttf");
    private static final Identifier FONT_MEDIUM =
            Identifier.fromNamespaceAndPath("gemini", "font/sourcehansanssc-medium.ttf");
    private static final Identifier FONT_LIGHT =
            Identifier.fromNamespaceAndPath("gemini", "font/sourcehansanssc-light.ttf");

    private static final float TITLE_FONT_SIZE     = 48f;
    private static final float SUBTITLE_FONT_SIZE  = 16f;
    private static final float MENU_FONT_SIZE      = 14f;
    private static final float NAV_FONT_SIZE       = 12f;
    private static final float VERSION_FONT_SIZE   = 11f;

    private static final float TITLE_SPACING_PX = 14f;

    private static GlyphFont titleFont;
    private static GlyphFont subtitleFont;
    private static GlyphFont menuFont;
    private static GlyphFont navFont;
    private static GlyphFont versionFont;

    private static void ensureFontsLoaded() {
        if (titleFont == null)
            titleFont = CustomFontRenderer.loadFont(FONT_BOLD, TITLE_FONT_SIZE);
        if (subtitleFont == null)
            subtitleFont = CustomFontRenderer.loadFont(FONT_LIGHT, SUBTITLE_FONT_SIZE);
        if (menuFont == null)
            menuFont = CustomFontRenderer.loadFont(FONT_MEDIUM, MENU_FONT_SIZE);
        if (navFont == null)
            navFont = CustomFontRenderer.loadFont(FONT_MEDIUM, NAV_FONT_SIZE);
        if (versionFont == null)
            versionFont = CustomFontRenderer.loadFont(FONT_LIGHT, VERSION_FONT_SIZE);
    }

    // ========================
    // Animation Constants
    // ========================
    private static final float HOVER_SPEED      = 12f;  // exponential lerp speed
    private static final float UNDERLINE_SPEED  = 12f;
    private static final float ENTRY_FADE_SPEED = 4f;
    private static final float ENTRY_STAGGER    = 0.09f; // per menu item, seconds
    private static final float TITLE_STAGGER    = 0.06f; // per title letter, seconds
    private static final float ENTRY_SLIDE_PX   = 18f;   // cascade-in distance
    private static final float HOVER_SLIDE_PX   = 6f;    // text slide on hover
    private static final float DIM_FACTOR       = 0.35f; // idle dim while another item hovered

    private static final String MOD_VERSION = "0.1.0";

    // TODO: replace with your real links
    private static final String GITHUB_URL  = "https://github.com/";
    private static final String DISCORD_URL = "https://discord.com/";

    // ========================
    // Custom Background
    // ========================
    private static BackgroundConfig backgroundConfig;
    private static Identifier customBackgroundTexture;
    private static boolean customBackgroundLoadFailed = false;

    // ========================
    // Inner Types
    // ========================

    private record MenuItem(String label, Runnable action) {}

    private record Layout(
            String titleText,
            float titleX, float titleY, float titleWidth, float titleSpacing,
            float accentY, float subtitleX, float subtitleY,
            int panelX, int panelY, int panelW, int panelH,
            float menuStartY, float menuSpacing, int rowX, int rowW, int rowH,
            float navStartX, float navY, float githubW, float separatorW, float discordW,
            int navSurfaceX, int navSurfaceY, int navSurfaceW, int navSurfaceH,
            float footerNameX, float footerVersionX, float footerNameY, float footerVersionY,
            float hintsX, boolean showSubtitle, boolean showAtmosphere, boolean showNavigation,
            boolean showFooter, boolean showHints,
            int bgToggleX, int bgToggleY, int bgToggleW, int bgToggleH,
            int bgCycleX, int bgCycleY, int bgCycleW, int bgCycleH) {

        float menuY(int index) {
            return menuStartY + index * menuSpacing;
        }

        float discordX() {
            return navStartX + githubW + separatorW;
        }
    }

    // ========================
    // State
    // ========================

    private final List<MenuItem> menuItems = new ArrayList<>();
    private float[] hoverProgress;
    private float entryAlpha;        // global fade-in: 0 → 1
    private float anyHoverProgress;  // 1 while any menu item is hovered/focused
    private int focusedIndex = -1;
    private int hoveredIndex = -1;

    // Navigation hover
    private float githubHover;
    private float discordHover;
    private float githubUnderline;
    private float discordUnderline;

    // Background toggle hover
    private float bgToggleHover;
    private float bgCycleHover;

    // Mouse parallax effect
    private float mouseX = 0;
    private float mouseY = 0;

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
    }

    // ========================
    // Lifecycle: init
    // ========================

    @Override
    protected void init() {
        // Initialize background config
        if (backgroundConfig == null) {
            backgroundConfig = new BackgroundConfig();
        }

        // Initialize particle system
        if (particleSystem == null) {
            particleSystem = new ParticleSystem(this.width, this.height);
        } else {
            particleSystem.resize(this.width, this.height);
        }

        menuItems.clear();
        menuItems.add(new MenuItem("Singleplayer",
                () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this))));
        menuItems.add(new MenuItem("Multiplayer",
                () -> this.minecraft.gui.setScreen(new JoinMultiplayerScreen(this))));
        menuItems.add(new MenuItem("Settings",
                () -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false))));
        menuItems.add(new MenuItem("Alt Manager",
                () -> this.minecraft.gui.setScreen(new AltManagerScreen(this))));
        menuItems.add(new MenuItem("Exit",
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

    // ========================
    // Main render
    // ========================

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastFrameMs) / 1000f, 0.1f);
        lastFrameMs = now;
        float elapsed = (now - screenOpenTime) / 1000f;

        // Update mouse position for parallax
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        // Update particle system
        if (particleSystem != null && backgroundConfig != null && backgroundConfig.isCustomBackgroundEnabled()) {
            particleSystem.updateMousePosition(mouseX, mouseY);
            particleSystem.update(dt);
        }

        // Entry fade-in
        entryAlpha += (1f - entryAlpha) * dt * ENTRY_FADE_SPEED;
        if (entryAlpha > 0.99f) entryAlpha = 1f;

        Layout layout = layout();

        // ── 1. GLSL Background ─────────────────────────────
        renderBackground(gui, elapsed);

        // ── 1.5. Particle System (only with custom background) ─────
        if (particleSystem != null && backgroundConfig != null && backgroundConfig.isCustomBackgroundEnabled()) {
            particleSystem.render(gui, partialTicks);
        }

        // ── 2. Update hover animations ─────────────────────
        updateMenuHover(layout, mouseX, mouseY, dt);
        updateNavHover(layout, mouseX, mouseY, dt);
        updateBgToggleHover(layout, mouseX, mouseY, dt);
        updateBgCycleHover(layout, mouseX, mouseY, dt);

        // ── 3. Frosted surfaces + ambient lighting ─────────
        drawAtmosphere(gui, layout, elapsed);
        drawGlassSurfaces(gui, layout, elapsed);

        // ── 4. Title ───────────────────────────────────────
        drawTitle(gui, layout, elapsed);

        // ── 5. Accent rule + Subtitle ──────────────────────
        drawAccentRule(gui, layout, elapsed);
        drawSubtitle(gui, layout, elapsed);

        // ── 6. Menu items ──────────────────────────────────
        drawMenuItems(gui, layout, elapsed);

        // ── 7. Navigation ──────────────────────────────────
        drawNavigation(gui, layout, elapsed);

        // ── 8. Background Toggle Button ────────────────────
        drawBackgroundToggle(gui, layout, elapsed);

        // ── 8.5. Background Cycle Button ───────────────────
        drawBackgroundCycle(gui, layout, elapsed);

        // ── 9. Footer ──────────────────────────────────────
        drawFooter(gui, layout, elapsed);
    }

    private Layout layout() {
        ensureFontsLoaded();

        float githubW = navFont == null ? 0f : CustomFontRenderer.stringWidth(navFont, "Github");
        float separatorW = navFont == null ? 0f : CustomFontRenderer.stringWidth(navFont, "   ·   ");
        float discordW = navFont == null ? 0f : CustomFontRenderer.stringWidth(navFont, "Discord");
        float navW = githubW + separatorW + discordW;
        float navRight = Math.min(NAV_RIGHT_PAD, Math.max(MIN_EDGE_PAD, this.width * 0.08f));
        float navStartX = Math.max(MIN_EDGE_PAD, this.width - navRight - navW);

        int edgePad = Math.min(NORMAL_EDGE_PAD, Math.max(MIN_EDGE_PAD, this.width / 12));
        int panelW = Math.max(1, Math.min(MENU_PANEL_W, this.width - edgePad * 2));
        int panelX = Math.max(0, (this.width - panelW) / 2);

        boolean showSubtitle = this.height >= 340;
        boolean showAtmosphere = this.height >= 260;
        boolean showNavigation = this.height >= 210 && navW <= this.width - MIN_EDGE_PAD * 2f;
        boolean showFooter = this.height >= 420;
        boolean showHints = this.height >= 480 && panelW >= 210;

        String titleText = "G E M I N I";
        float rawTitleWidth = titleFont == null ? 0f
                : computeSpacedWidth(titleFont, titleText, TITLE_SPACING_PX);
        int titleGaps = titleText.codePointCount(0, titleText.length()) - 1;
        float glyphWidth = Math.max(0f, rawTitleWidth - titleGaps * TITLE_SPACING_PX);
        if (glyphWidth > this.width - MIN_EDGE_PAD * 2f) {
            titleText = "GEMINI";
            rawTitleWidth = titleFont == null ? 0f
                    : computeSpacedWidth(titleFont, titleText, TITLE_SPACING_PX);
            titleGaps = titleText.codePointCount(0, titleText.length()) - 1;
            glyphWidth = Math.max(0f, rawTitleWidth - titleGaps * TITLE_SPACING_PX);
        }
        float titleSpacing = titleGaps <= 0 ? TITLE_SPACING_PX
                : Math.clamp((this.width - MIN_EDGE_PAD * 2f - glyphWidth) / titleGaps, 1f, TITLE_SPACING_PX);
        float titleWidth = titleFont == null ? 0f
                : computeSpacedWidth(titleFont, titleText, titleSpacing);

        float titleY = Math.max(44f, this.height * 0.25f);
        float panelBottom = this.height - (showFooter ? 64f : MIN_EDGE_PAD);
        float compactTitleGap = showSubtitle ? 50f : 22f;
        float desiredMenuY = Math.max(this.height * 0.43f, titleY + TITLE_FONT_SIZE + 98f);
        float availableSpacing = (panelBottom - (desiredMenuY - 15f) - 18f) / menuItems.size();

        if (availableSpacing < MIN_MENU_SPACING) {
            titleY = Math.max(34f, Math.min(titleY,
                    panelBottom - TITLE_FONT_SIZE - compactTitleGap
                            - menuItems.size() * MIN_MENU_SPACING - 3f));
            desiredMenuY = titleY + TITLE_FONT_SIZE + compactTitleGap;
            availableSpacing = (panelBottom - (desiredMenuY - 15f) - 18f) / menuItems.size();
        }

        float menuSpacing = Math.clamp(availableSpacing, 16f, MENU_SPACING);
        float menuStartY = desiredMenuY;
        int panelY = Math.round(menuStartY - 15f);
        int panelH = Math.round(menuItems.size() * menuSpacing + 18f);
        if (panelY + panelH > this.height - MIN_EDGE_PAD) {
            panelY = Math.max(0, this.height - MIN_EDGE_PAD - panelH);
            menuStartY = panelY + 15f;
        }

        int rowX = panelX + Math.min(12, Math.max(4, panelW / 20));
        int rowW = Math.max(1, panelW - (rowX - panelX) * 2);
        int rowH = Math.round((menuFont == null ? MENU_FONT_SIZE : menuFont.lineHeight) + 14f);

        String subtitle = "Modern Minecraft Client";
        float subtitleW = subtitleFont == null ? 0f : CustomFontRenderer.stringWidth(subtitleFont, subtitle);
        float subtitleY = titleY + TITLE_FONT_SIZE + 22f;

        int navSurfaceX = Math.max(0, Math.round(navStartX - 13f));
        int navSurfaceW = Math.max(1, this.width - navSurfaceX - Math.round(navRight) + 13);

        String line1 = "Gemini Client";
        String line2 = "v" + MOD_VERSION;
        float w1 = versionFont == null ? 0f : CustomFontRenderer.stringWidth(versionFont, line1);
        float w2 = versionFont == null ? 0f : CustomFontRenderer.stringWidth(versionFont, line2);
        float footerRight = Math.min(FOOTER_RIGHT_PAD, Math.max(MIN_EDGE_PAD, this.width * 0.08f));
        float footerVersionY = this.height - Math.min(FOOTER_BOTTOM_PAD, Math.max(12, this.height / 12));
        float footerNameY = footerVersionY - VERSION_FONT_SIZE - 4f;
        String hints = "↑↓  Select    Enter  Open";
        float hintsW = versionFont == null ? 0f : CustomFontRenderer.stringWidth(versionFont, hints);

        // Background toggle button (right side, below navigation)
        int bgToggleW = 28;
        int bgToggleH = 28;
        int bgToggleX = this.width - (int) Math.min(NAV_RIGHT_PAD, Math.max(MIN_EDGE_PAD, this.width * 0.08f)) - bgToggleW;
        int navSurfaceY = 10;
        int navSurfaceH = 31;
        int bgToggleY = showNavigation ? navSurfaceY + navSurfaceH + 12 : 52;

        // Background cycle button (left of toggle button)
        int bgCycleW = 28;
        int bgCycleH = 28;
        int bgCycleX = bgToggleX - bgCycleW - 6; // 6px gap
        int bgCycleY = bgToggleY;

        return new Layout(
                titleText, (this.width - titleWidth) / 2f, titleY, titleWidth, titleSpacing,
                titleY + TITLE_FONT_SIZE + 10f,
                (this.width - subtitleW) / 2f, subtitleY,
                panelX, panelY, panelW, panelH,
                menuStartY, menuSpacing, rowX, rowW, rowH,
                navStartX, 20f, githubW, separatorW, discordW,
                navSurfaceX, 10, navSurfaceW, 31,
                this.width - footerRight - w1, this.width - footerRight - w2,
                footerNameY, footerVersionY,
                panelX + (panelW - hintsW) / 2f,
                showSubtitle, showAtmosphere, showNavigation, showFooter, showHints,
                bgToggleX, bgToggleY, bgToggleW, bgToggleH,
                bgCycleX, bgCycleY, bgCycleW, bgCycleH);
    }

    private void drawAtmosphere(GuiGraphicsExtractor gui, Layout layout, float elapsed) {
        if (!layout.showAtmosphere) return;
        // Don't show atmosphere when custom background is active
        if (backgroundConfig != null && backgroundConfig.isCustomBackgroundEnabled()
                && backgroundConfig.customBackgroundFileExists()) {
            return;
        }

        float reveal = easeOutCubic(clamp01(elapsed * 1.6f)) * entryAlpha;
        if (reveal <= 0.01f) return;

        float pulse = 0.88f + 0.12f * (float) Math.sin(elapsed * 0.8f);
        SdfUIRenderer.drawCircle(gui, this.width - 88f, 92f, 196,
                scaleAlpha(0x1889DDFF, reveal * pulse));
        SdfUIRenderer.drawCircle(gui, 52f, this.height - 72f, 148,
                scaleAlpha(0x105C7CFF, reveal));
    }

    private void drawGlassSurfaces(GuiGraphicsExtractor gui, Layout layout, float elapsed) {
        float reveal = easeOutCubic(clamp01((elapsed - 0.18f) * 2.8f)) * entryAlpha;
        if (reveal <= 0.01f) return;

        int panelX = layout.panelX;
        int panelY = Math.round(layout.panelY + (1f - reveal) * 10f);
        int panelW = layout.panelW;
        int panelH = layout.panelH;

        SdfUIRenderer.drawShadow(gui, panelX, panelY, panelW, panelH,
                MENU_PANEL_RADIUS, 0, 8, 24, scaleAlpha(0x78000000, reveal));
        CustomBlurRenderer.render(panelX, panelY, panelW, panelH,
                MENU_PANEL_RADIUS, scaleAlpha(0x52101922, reveal), 8f);
        CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, panelX, panelY, panelW, panelH,
                MENU_PANEL_RADIUS, scaleAlpha(GLASS_TOP, reveal), scaleAlpha(GLASS_BOTTOM, reveal));
        CustomRoundedRectRenderer.drawRoundedOutline(gui, panelX, panelY, panelW, panelH,
                MENU_PANEL_RADIUS, scaleAlpha(GLASS_OUTLINE, reveal), 1);

        CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui,
                panelX + 1, panelY + 18, 2, Math.max(8, panelH - 36), 1,
                scaleAlpha(ACCENT, reveal * 0.85f), scaleAlpha(0x0089DDFF, reveal));

        if (!layout.showNavigation) return;
        int navY = layout.navSurfaceY;
        int navH = layout.navSurfaceH;
        SdfUIRenderer.drawShadow(gui, layout.navSurfaceX, navY, layout.navSurfaceW, navH,
                10, 0, 4, 12, scaleAlpha(0x50000000, reveal));
        CustomBlurRenderer.render(layout.navSurfaceX, navY, layout.navSurfaceW, navH, 10,
                scaleAlpha(0x42101820, reveal), 6f);
        CustomRoundedRectRenderer.drawRoundedRect(gui, layout.navSurfaceX, navY,
                layout.navSurfaceW, navH, 10, scaleAlpha(0x7210151D, reveal));
        CustomRoundedRectRenderer.drawRoundedOutline(gui, layout.navSurfaceX, navY,
                layout.navSurfaceW, navH, 10, scaleAlpha(0x3EFFFFFF, reveal), 1);
    }

    // ========================
    // Hover updates (exponential lerp)
    // ========================

    private void updateMenuHover(Layout layout, int mouseX, int mouseY, float dt) {
        hoveredIndex = -1;
        for (int i = 0; i < menuItems.size(); i++) {
            boolean mouseOver = isMenuHover(layout, mouseX, mouseY, i);
            boolean over = mouseOver || i == focusedIndex;
            if (mouseOver) hoveredIndex = i;
            float target = over ? 1f : 0f;
            hoverProgress[i] += (target - hoverProgress[i]) * dt * HOVER_SPEED;
        }
        float anyTarget = hoveredIndex >= 0 || focusedIndex >= 0 ? 1f : 0f;
        anyHoverProgress += (anyTarget - anyHoverProgress) * dt * HOVER_SPEED;
    }

    private void updateNavHover(Layout layout, int mouseX, int mouseY, float dt) {
        boolean overGithub = isNavGithubHover(layout, mouseX, mouseY);
        boolean overDiscord = isNavDiscordHover(layout, mouseX, mouseY);

        githubHover += ((overGithub ? 1f : 0f) - githubHover) * dt * HOVER_SPEED;
        discordHover += ((overDiscord ? 1f : 0f) - discordHover) * dt * HOVER_SPEED;

        githubUnderline += ((overGithub ? 1f : 0f) - githubUnderline) * dt * UNDERLINE_SPEED;
        discordUnderline += ((overDiscord ? 1f : 0f) - discordUnderline) * dt * UNDERLINE_SPEED;
    }

    private void updateBgToggleHover(Layout layout, int mouseX, int mouseY, float dt) {
        boolean overToggle = isBgToggleHover(layout, mouseX, mouseY);
        bgToggleHover += ((overToggle ? 1f : 0f) - bgToggleHover) * dt * HOVER_SPEED;
    }

    private void updateBgCycleHover(Layout layout, int mouseX, int mouseY, float dt) {
        boolean overCycle = isBgCycleHover(layout, mouseX, mouseY);
        bgCycleHover += ((overCycle ? 1f : 0f) - bgCycleHover) * dt * HOVER_SPEED;
    }

    // ========================
    // Title: "G E M I N I"
    // Letters reveal one by one, white → soft cyan gradient.
    // ========================

    private void drawTitle(GuiGraphicsExtractor gui, Layout layout, float elapsed) {
        ensureFontsLoaded();
        if (titleFont == null) return;

        String title = layout.titleText;
        float titleX = layout.titleX;
        float titleY = layout.titleY;

        int letterIndex = 0;
        float cx = titleX;
        int letterCount = title.replace(" ", "").length();

        for (int i = 0; i < title.length();) {
            int cp = title.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            float chW = CustomFontRenderer.stringWidth(titleFont, ch);

            if (!ch.isBlank()) {
                // Per-letter staggered reveal
                float reveal = clamp01((elapsed - 0.10f - letterIndex * TITLE_STAGGER) * 3f);
                reveal = easeOutCubic(reveal);
                int alpha = (int) (entryAlpha * reveal * 255);
                if (alpha > 0) {
                    // Gradient across the word: white → accent-tinted
                    float t = letterCount <= 1 ? 0f : (float) letterIndex / (letterCount - 1);
                    int base = lerpColor(TITLE_COLOR, TITLE_GRADIENT, t);
                    int color = (alpha << 24) | (base & 0x00FFFFFF);
                    CustomFontRenderer.drawString(gui, titleFont, ch, cx, titleY, color);
                }
                letterIndex++;
            }

            cx += chW + layout.titleSpacing;
            i += Character.charCount(cp);
        }
    }

    // ========================
    // Accent rule under the title — draws itself outward from center
    // ========================

    private void drawAccentRule(GuiGraphicsExtractor gui, Layout layout, float elapsed) {
        if (!layout.showSubtitle) return;
        float progress = easeOutCubic(clamp01((elapsed - 0.45f) * 2.2f));
        if (progress <= 0.01f) return;

        float lineW = layout.titleWidth * 0.32f * progress;
        float x = (this.width - lineW) / 2f;
        float y = layout.accentY;

        int alpha = (int) (entryAlpha * progress * 255);
        int color = (alpha << 24) | (ACCENT & 0x00FFFFFF);
        CustomRectRenderer.drawRect(gui, (int) x, (int) y, (int) lineW, 1, color);
    }

    // ========================
    // Subtitle: "Modern Minecraft Client"
    // ========================

    private void drawSubtitle(GuiGraphicsExtractor gui, Layout layout, float elapsed) {
        if (!layout.showSubtitle) return;
        ensureFontsLoaded();
        if (subtitleFont == null) return;

        String subtitle = "Modern Minecraft Client";
        float subX = layout.subtitleX;
        float subY = layout.subtitleY;

        float reveal = easeOutCubic(clamp01((elapsed - 0.55f) * 2.5f));
        int alpha = (int) (entryAlpha * reveal * 255);
        if (alpha <= 0) return;
        int color = (alpha << 24) | (SUBTITLE_COLOR & 0x00FFFFFF);

        CustomFontRenderer.drawString(gui, subtitleFont, subtitle, subX, subY, color);
    }

    // ========================
    // Menu items — staggered cascade-in, accent indicator bar,
    // hover slide, idle items dim while another is hovered.
    // ========================

    private void drawMenuItems(GuiGraphicsExtractor gui, Layout layout, float elapsed) {
        ensureFontsLoaded();
        if (menuFont == null) return;

        for (int i = 0; i < menuItems.size(); i++) {
            MenuItem item = menuItems.get(i);

            // Per-item staggered reveal
            float reveal = easeOutCubic(clamp01((elapsed - 0.35f - i * ENTRY_STAGGER) * 3f));
            if (reveal <= 0.01f) continue;

            float slideIn = (1f - reveal) * -ENTRY_SLIDE_PX;
            float y = layout.menuY(i);
            float hp = hoverProgress[i];

            // Dim idle items while another is hovered
            float dim = 1f - DIM_FACTOR * anyHoverProgress * (1f - hp);
            int alpha = (int) (entryAlpha * reveal * dim * 255);
            if (alpha <= 0) continue;

            int panelX = layout.panelX;
            int rowX = layout.rowX;
            int rowY = Math.round(y - 7f);
            int rowW = layout.rowW;
            int rowH = layout.rowH;
            float active = Math.max(hp, i == focusedIndex ? 0.75f : 0f);
            if (active > 0.01f) {
                SdfUIRenderer.drawShadow(gui, rowX, rowY, rowW, rowH,
                        8, 0, 3, 10, scaleAlpha(0x48000000, reveal * active));
                CustomRoundedRectRenderer.drawRoundedRectHorizGrad(gui,
                        rowX, rowY, rowW, rowH, 8,
                        scaleAlpha(ROW_FILL, reveal * active),
                        scaleAlpha(0x5415222D, reveal * active));
                CustomRoundedRectRenderer.drawRoundedOutline(gui,
                        rowX, rowY, rowW, rowH, 8,
                        scaleAlpha(ROW_OUTLINE, reveal * active), 1);
            }

            // Accent indicator bar: grows vertically on hover
            if (hp > 0.01f) {
                float lineH = menuFont.lineHeight;
                float barH = lineH * hp;
                float barY = y + (lineH - barH) / 2f;
                int barAlpha = (int) (alpha * hp);
                int barColor = (barAlpha << 24) | (ACCENT & 0x00FFFFFF);
                CustomRoundedRectRenderer.drawRoundedRect(gui,
                        panelX + 17 + (int) slideIn, (int) barY,
                        3, Math.max(2, (int) barH), 1, barColor);
            }

            // Label text: slides right on hover
            int idleColor = (alpha << 24) | (TEXT_IDLE & 0x00FFFFFF);
            int hoverColor = (alpha << 24) | (TEXT_HOVER & 0x00FFFFFF);
            int textColor = lerpColor(idleColor, hoverColor, hp);

            float textX = Math.min(panelX + 46f, panelX + Math.max(22f, layout.panelW * 0.14f))
                    + slideIn + hp * HOVER_SLIDE_PX;
            CustomFontRenderer.drawString(gui, menuFont, item.label, textX, y, textColor);

            String shortcut = switch (i) {
                case 0 -> "S";
                case 1 -> "M";
                case 2 -> "O";
                case 3 -> "A";
                default -> "E";
            };
            float shortcutW = CustomFontRenderer.stringWidth(versionFont, shortcut);
            int shortcutColor = scaleAlpha(hp > 0.01f ? ACCENT : VERSION_COLOR,
                    entryAlpha * reveal * (0.65f + hp * 0.35f));
            CustomFontRenderer.drawString(gui, versionFont, shortcut,
                    panelX + layout.panelW - Math.min(28f, Math.max(12f, layout.panelW * 0.09f)) - shortcutW,
                    y + 1f, shortcutColor);
        }
    }

    // ========================
    // Navigation: Github | Discord
    // Accent underlines grow outward from the center; clickable.
    // ========================

    private void drawNavigation(GuiGraphicsExtractor gui, Layout layout, float elapsed) {
        if (!layout.showNavigation) return;
        ensureFontsLoaded();
        if (navFont == null) return;

        float navY = layout.navY;

        String githubText = "Github";
        String discordText = "Discord";
        String separator = "   ·   ";

        float githubW = layout.githubW;
        float separatorW = layout.separatorW;
        float discordW = layout.discordW;
        float startX = layout.navStartX;

        float reveal = easeOutCubic(clamp01((elapsed - 0.65f) * 3f));
        int alpha = (int) (entryAlpha * reveal * 255);
        if (alpha <= 0) return;

        // Github
        int githubColor = lerpColor(
                (alpha << 24) | (NAV_IDLE & 0x00FFFFFF),
                (alpha << 24) | (NAV_HOVER & 0x00FFFFFF),
                githubHover);
        CustomFontRenderer.drawString(gui, navFont, githubText, startX, navY, githubColor);

        // Github underline (center-out, accent)
        if (githubUnderline > 0.01f) {
            int ulAlpha = (int) (alpha * githubUnderline);
            int ulColor = (ulAlpha << 24) | (ACCENT & 0x00FFFFFF);
            float ulW = githubW * githubUnderline;
            float ulX = startX + (githubW - ulW) / 2f;
            float ulY = navY + NAV_FONT_SIZE + 2f;
            CustomRectRenderer.drawRect(gui, (int) ulX, (int) ulY, (int) ulW, 1, ulColor);
        }

        // Separator
        int sepColor = (alpha << 24) | (SEPARATOR_COLOR & 0x00FFFFFF);
        float sepX = startX + githubW;
        CustomFontRenderer.drawString(gui, navFont, separator, sepX, navY, sepColor);

        // Discord
        float discordX = sepX + separatorW;
        int discordColor = lerpColor(
                (alpha << 24) | (NAV_IDLE & 0x00FFFFFF),
                (alpha << 24) | (NAV_HOVER & 0x00FFFFFF),
                discordHover);
        CustomFontRenderer.drawString(gui, navFont, discordText, discordX, navY, discordColor);

        // Discord underline (center-out, accent)
        if (discordUnderline > 0.01f) {
            int ulAlpha = (int) (alpha * discordUnderline);
            int ulColor = (ulAlpha << 24) | (ACCENT & 0x00FFFFFF);
            float ulW = discordW * discordUnderline;
            float ulX = discordX + (discordW - ulW) / 2f;
            float ulY = navY + NAV_FONT_SIZE + 2f;
            CustomRectRenderer.drawRect(gui, (int) ulX, (int) ulY, (int) ulW, 1, ulColor);
        }
    }

    // ========================
    // Footer: version info (right) + shortcut hints (left)
    // ========================

    private void drawFooter(GuiGraphicsExtractor gui, Layout layout, float elapsed) {
        if (!layout.showFooter) return;
        ensureFontsLoaded();
        if (versionFont == null) return;

        float reveal = easeOutCubic(clamp01((elapsed - 0.75f) * 3f));
        int alpha = (int) (entryAlpha * reveal * 255);
        if (alpha <= 0) return;

        // ── Right: client name + version ──
        String line1 = "Gemini Client";
        String line2 = "v" + MOD_VERSION;

        float x1 = layout.footerNameX;
        float x2 = layout.footerVersionX;
        float y2 = layout.footerVersionY;
        float y1 = layout.footerNameY;

        int color = (alpha << 24) | (VERSION_COLOR & 0x00FFFFFF);

        // Small accent square before the client name
        int sqColor = (alpha << 24) | (ACCENT & 0x00FFFFFF);
        float sqY = y1 + (VERSION_FONT_SIZE - 3f) / 2f;
        CustomRectRenderer.drawRect(gui, (int) (x1 - 9), (int) sqY, 3, 3, sqColor);

        CustomFontRenderer.drawString(gui, versionFont, line1, x1, y1, color);
        CustomFontRenderer.drawString(gui, versionFont, line2, x2, y2, color);

        if (layout.showHints) {
            String hints = "↑↓  Select    Enter  Open";
            int hintColor = (int) (alpha * 0.58f) << 24 | (HINT_COLOR & 0x00FFFFFF);
            CustomFontRenderer.drawString(gui, versionFont, hints, layout.hintsX, y2, hintColor);
        }
    }

    // ========================
    // Background Rendering
    // ========================

    private void renderBackground(GuiGraphicsExtractor gui, float elapsed) {
        if (backgroundConfig != null && backgroundConfig.isCustomBackgroundEnabled()) {
            renderCustomBackground(gui);
        }
        // Render grid particles if enabled
        if (backgroundConfig != null && backgroundConfig.isParticlesEnabled()) {
            InfiniteGridRenderer.render(elapsed);
        } else if (backgroundConfig != null) {
            // Particles disabled - log once per state change
            if (elapsed < 0.1f) {
                System.out.println("[MainMenu] Particles disabled, skipping InfiniteGridRenderer");
            }
        }
    }

    private void renderCustomBackground(GuiGraphicsExtractor gui) {
        if (!backgroundConfig.customBackgroundFileExists()) {
            return;
        }

        if (customBackgroundLoadFailed) {
            return;
        }

        // Load texture if not already loaded
        if (customBackgroundTexture == null) {
            try {
                Path bgFile = backgroundConfig.getCustomBackgroundFile();
                if (!Files.exists(bgFile)) {
                    customBackgroundLoadFailed = true;
                    return;
                }

                // Load image from file using NativeImage
                // NativeImage.read() supports both PNG and JPEG formats
                NativeImage image = null;
                try (FileInputStream fis = new FileInputStream(bgFile.toFile())) {
                    image = NativeImage.read(fis);
                } catch (IOException readError) {
                    // If NativeImage fails, try using ImageIO as fallback for JPEG
                    try {
                        java.awt.image.BufferedImage bufferedImage = ImageIO.read(bgFile.toFile());
                        if (bufferedImage == null) {
                            customBackgroundLoadFailed = true;
                            return;
                        }

                        // Convert BufferedImage to NativeImage
                        int width = bufferedImage.getWidth();
                        int height = bufferedImage.getHeight();
                        image = new NativeImage(width, height, false);

                        for (int y = 0; y < height; y++) {
                            for (int x = 0; x < width; x++) {
                                int argb = bufferedImage.getRGB(x, y);
                                image.setPixel(x, y, argb);
                            }
                        }
                    } catch (Exception fallbackError) {
                        customBackgroundLoadFailed = true;
                        return;
                    }
                }

                if (image == null) {
                    customBackgroundLoadFailed = true;
                    return;
                }

                // Create dynamic texture from NativeImage
                DynamicTexture texture = new DynamicTexture(() -> "custom_background", image);
                customBackgroundTexture = Identifier.fromNamespaceAndPath("gemini", "custom_background");
                minecraft.getTextureManager().register(customBackgroundTexture, texture);

            } catch (Exception e) {
                customBackgroundLoadFailed = true;
                return;
            }
        }

        // Render the custom background with parallax effect
        try {
            // Calculate parallax offset based on mouse position
            float parallaxOffsetX = ((mouseX / (float) this.width) - 0.5f) * 40f;
            float parallaxOffsetY = ((mouseY / (float) this.height) - 0.5f) * 40f;

            // Scale image by 110% to cover parallax movement and avoid tiling
            float scale = 1.1f;
            int scaledWidth = (int) (this.width * scale);
            int scaledHeight = (int) (this.height * scale);

            // Center the scaled image and apply parallax offset
            int renderX = (int) (-(scaledWidth - this.width) / 2f + parallaxOffsetX);
            int renderY = (int) (-(scaledHeight - this.height) / 2f + parallaxOffsetY);

            // Render: blit(pipeline, texture, screenX, screenY, texU, texV, screenW, screenH, texWidth, texHeight, color)
            gui.blit(RenderPipelines.GUI_TEXTURED, customBackgroundTexture,
                    renderX, renderY, 0, 0, scaledWidth, scaledHeight, scaledWidth, scaledHeight, 0xFFFFFFFF);
        } catch (Exception e) {
            // Silently fail
        }
    }

    /**
     * Reloads the custom background texture.
     * Called when a new wallpaper is selected from the selector screen.
     */
    public void reloadCustomBackground() {
        // Clear existing texture
        if (customBackgroundTexture != null) {
            AbstractTexture texture = minecraft.getTextureManager().getTexture(customBackgroundTexture);
            if (texture != null) {
                texture.close();
            }
            minecraft.getTextureManager().release(customBackgroundTexture);
            customBackgroundTexture = null;
        }

        // Reset flags to trigger reload on next render
        customBackgroundLoadFailed = false;
    }

    // ========================
    // Background Toggle Button
    // ========================

    private void drawBackgroundToggle(GuiGraphicsExtractor gui, Layout layout, float elapsed) {
        ensureFontsLoaded();
        if (versionFont == null) return;

        float reveal = easeOutCubic(clamp01((elapsed - 0.65f) * 3f));
        int alpha = (int) (entryAlpha * reveal * 255);
        if (alpha <= 0) return;

        int x = layout.bgToggleX;
        int y = layout.bgToggleY;
        int w = layout.bgToggleW;
        int h = layout.bgToggleH;

        boolean enabled = backgroundConfig != null && backgroundConfig.isCustomBackgroundEnabled();
        boolean fileExists = backgroundConfig != null && backgroundConfig.customBackgroundFileExists();

        // Button background with hover effect
        float hoverScale = 1f + bgToggleHover * 0.08f;
        int hoverW = (int) (w * hoverScale);
        int hoverH = (int) (h * hoverScale);
        int hoverX = x - (hoverW - w) / 2;
        int hoverY = y - (hoverH - h) / 2;

        // Shadow
        SdfUIRenderer.drawShadow(gui, hoverX, hoverY, hoverW, hoverH,
                8, 0, 3, 10, scaleAlpha(0x48000000, reveal * (0.5f + bgToggleHover * 0.5f)));

        // Background
        int bgFill = enabled ? scaleAlpha(0xB01B2936, reveal) : scaleAlpha(0x80161D28, reveal);
        int bgOutline = enabled ? scaleAlpha(ROW_OUTLINE, reveal) : scaleAlpha(0x4089DDFF, reveal);
        CustomRoundedRectRenderer.drawRoundedRect(gui, hoverX, hoverY, hoverW, hoverH, 8, bgFill);
        CustomRoundedRectRenderer.drawRoundedOutline(gui, hoverX, hoverY, hoverW, hoverH, 8, bgOutline, 1);

        // Icon: "BG" text
        String iconText = "BG";
        float iconW = CustomFontRenderer.stringWidth(versionFont, iconText);
        float iconX = x + (w - iconW) / 2f;
        float iconY = y + (h - VERSION_FONT_SIZE) / 2f;

        int iconColor;
        if (!fileExists) {
            // Gray if no custom background file exists
            iconColor = scaleAlpha(VERSION_COLOR, entryAlpha * reveal * 0.6f);
        } else if (enabled) {
            // Accent cyan when enabled
            iconColor = scaleAlpha(ACCENT, entryAlpha * reveal);
        } else {
            // Normal gray when disabled but file exists
            iconColor = scaleAlpha(NAV_IDLE, entryAlpha * reveal);
        }

        CustomFontRenderer.drawString(gui, versionFont, iconText, iconX, iconY, iconColor);

        // Status indicator dot
        if (fileExists) {
            int dotSize = 4;
            int dotX = x + w - dotSize - 3;
            int dotY = y + 3;
            int dotColor = enabled
                    ? scaleAlpha(0xFF89DDFF, reveal)  // Cyan when on
                    : scaleAlpha(0xFF666666, reveal * 0.7f);  // Gray when off
            CustomRoundedRectRenderer.drawRoundedRect(gui, dotX, dotY, dotSize, dotSize, 2, dotColor);
        }
    }

    // ========================
    // Background Cycle Button
    // ========================

    private void drawBackgroundCycle(GuiGraphicsExtractor gui, Layout layout, float elapsed) {
        ensureFontsLoaded();
        if (versionFont == null) return;

        // Show when custom backgrounds exist (regardless of enabled state)
        if (backgroundConfig == null || !backgroundConfig.customBackgroundFileExists()) {
            return;
        }

        float reveal = easeOutCubic(clamp01((elapsed - 0.65f) * 3f));
        int alpha = (int) (entryAlpha * reveal * 255);
        if (alpha <= 0) return;

        int x = layout.bgCycleX;
        int y = layout.bgCycleY;
        int w = layout.bgCycleW;
        int h = layout.bgCycleH;

        // Button background with hover effect
        float hoverScale = 1f + bgCycleHover * 0.08f;
        int hoverW = (int) (w * hoverScale);
        int hoverH = (int) (h * hoverScale);
        int hoverX = x - (hoverW - w) / 2;
        int hoverY = y - (hoverH - h) / 2;

        // Shadow
        SdfUIRenderer.drawShadow(gui, hoverX, hoverY, hoverW, hoverH,
                8, 0, 3, 10, scaleAlpha(0x48000000, reveal * (0.5f + bgCycleHover * 0.5f)));

        // Background
        int bgFill = scaleAlpha(0x80161D28, reveal);
        int bgOutline = scaleAlpha(0x4089DDFF, reveal);
        CustomRoundedRectRenderer.drawRoundedRect(gui, hoverX, hoverY, hoverW, hoverH, 8, bgFill);
        CustomRoundedRectRenderer.drawRoundedOutline(gui, hoverX, hoverY, hoverW, hoverH, 8, bgOutline, 1);

        // Icon: Gear symbol for background selector
        String iconText = "⚙";
        float iconW = CustomFontRenderer.stringWidth(versionFont, iconText);
        float iconX = x + (w - iconW) / 2f;
        float iconY = y + (h - VERSION_FONT_SIZE) / 2f;

        int iconColor = scaleAlpha(NAV_IDLE, entryAlpha * reveal);
        CustomFontRenderer.drawString(gui, versionFont, iconText, iconX, iconY, iconColor);
    }

    // ========================
    // Input
    // ========================

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent mouse, boolean idk) {
        Layout layout = layout();

        // Background toggle button
        if (isBgToggleHover(layout, mouse.x(), mouse.y())) {
            if (backgroundConfig != null) {
                if (backgroundConfig.customBackgroundFileExists()) {
                    backgroundConfig.toggle();
                    // No need to reload texture on every toggle - just switch rendering
                }
            }
            return true;
        }

        // Background selector button (gear icon, opens GUI)
        if (isBgCycleHover(layout, mouse.x(), mouse.y())) {
            if (backgroundConfig != null) {
                // Open background selector screen
                this.minecraft.gui.setScreen(new BackgroundSelectorScreen(this, backgroundConfig));
            }
            return true;
        }

        // Navigation links
        if (isNavGithubHover(layout, mouse.x(), mouse.y())) {
//            Util.getPlatform().openUri(GITHUB_URL);
            return true;
        }
        if (isNavDiscordHover(layout, mouse.x(), mouse.y())) {
//            Util.getPlatform().openUri(DISCORD_URL);
            return true;
        }

        for (int i = 0; i < menuItems.size(); i++) {
            if (isMenuHover(layout, mouse.x(), mouse.y(), i)) {
                focusedIndex = i;
                menuItems.get(i).action.run();
                return true;
            }
        }
        return super.mouseClicked(mouse, idk);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        // Keyboard navigation: Up/Down arrows
        if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
            int dir = (key == InputConstants.KEY_DOWN) ? 1 : -1;
            if (focusedIndex < 0) focusedIndex = 0;
            else focusedIndex = (focusedIndex + dir + menuItems.size()) % menuItems.size();
            return true;
        }
        // Enter / Numpad Enter
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            if (focusedIndex >= 0 && focusedIndex < menuItems.size()) {
                menuItems.get(focusedIndex).action.run();
                return true;
            }
        }
        // Shortcut keys: S, M, O, A, E
        if (key == InputConstants.KEY_S) {
            menuItems.get(0).action.run();
            return true;
        }
        if (key == InputConstants.KEY_M) {
            menuItems.get(1).action.run();
            return true;
        }
        if (key == InputConstants.KEY_O) {
            menuItems.get(2).action.run();
            return true;
        }
        if (key == InputConstants.KEY_A) {
            menuItems.get(3).action.run();
            return true;
        }
        if (key == InputConstants.KEY_E) {
            menuItems.get(4).action.run();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ========================
    // Hit testing
    // ========================

    private boolean isMenuHover(Layout layout, double mx, double my, int index) {
        if (menuFont == null) return false;
        float y = layout.menuY(index);
        return mx >= layout.rowX && mx <= layout.rowX + layout.rowW
                && my >= y - 7 && my <= y - 7 + layout.rowH;
    }

    private boolean isNavGithubHover(Layout layout, double mx, double my) {
        if (!layout.showNavigation || navFont == null) return false;
        return mx >= layout.navStartX - 4 && mx <= layout.navStartX + layout.githubW + 4
                && my >= layout.navY - 4 && my <= layout.navY + navFont.lineHeight + 4;
    }

    private boolean isNavDiscordHover(Layout layout, double mx, double my) {
        if (!layout.showNavigation || navFont == null) return false;
        float discordX = layout.discordX();
        return mx >= discordX - 4 && mx <= discordX + layout.discordW + 4
                && my >= layout.navY - 4 && my <= layout.navY + navFont.lineHeight + 4;
    }

    private boolean isBgToggleHover(Layout layout, double mx, double my) {
        return mx >= layout.bgToggleX && mx <= layout.bgToggleX + layout.bgToggleW
                && my >= layout.bgToggleY && my <= layout.bgToggleY + layout.bgToggleH;
    }

    private boolean isBgCycleHover(Layout layout, double mx, double my) {
        return mx >= layout.bgCycleX && mx <= layout.bgCycleX + layout.bgCycleW
                && my >= layout.bgCycleY && my <= layout.bgCycleY + layout.bgCycleH;
    }

    // ========================
    // Text helpers
    // ========================

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

    // ========================
    // Easing / color utilities
    // ========================

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

    private static int scaleAlpha(int argb, float scale) {
        int a = Math.round((argb >>> 24) * clamp01(scale));
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}

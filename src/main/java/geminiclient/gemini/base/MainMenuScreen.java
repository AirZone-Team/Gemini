package geminiclient.gemini.base;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

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
    // Inner Types
    // ========================

    private record MenuItem(String label, Runnable action) {}

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
        menuItems.clear();
        menuItems.add(new MenuItem("Singleplayer",
                () -> this.minecraft.setScreen(new SelectWorldScreen(this))));
        menuItems.add(new MenuItem("Multiplayer",
                () -> this.minecraft.setScreen(new JoinMultiplayerScreen(this))));
        menuItems.add(new MenuItem("Settings",
                () -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options, false))));
        menuItems.add(new MenuItem("Alt Manager",
                () -> this.minecraft.setScreen(new AltManagerScreen(this))));
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

        // Entry fade-in
        entryAlpha += (1f - entryAlpha) * dt * ENTRY_FADE_SPEED;
        if (entryAlpha > 0.99f) entryAlpha = 1f;

        // ── 1. GLSL Background ─────────────────────────────
        InfiniteGridRenderer.render(elapsed);

        // ── 2. Update hover animations ─────────────────────
        updateMenuHover(mouseX, mouseY, dt);
        updateNavHover(mouseX, mouseY, dt);

        // ── 3. Frosted surfaces + ambient lighting ─────────
        drawAtmosphere(gui, elapsed);
        drawGlassSurfaces(gui, elapsed);

        // ── 4. Title ───────────────────────────────────────
        drawTitle(gui, elapsed);

        // ── 5. Accent rule + Subtitle ──────────────────────
        drawAccentRule(gui, elapsed);
        drawSubtitle(gui, elapsed);

        // ── 6. Menu items ──────────────────────────────────
        drawMenuItems(gui, mouseX, mouseY, elapsed);

        // ── 7. Navigation ──────────────────────────────────
        drawNavigation(gui, elapsed);

        // ── 8. Footer ──────────────────────────────────────
        drawFooter(gui, elapsed);
    }

    private float menuStartY() {
        return Math.max(this.height * 0.43f, this.height * 0.25f + TITLE_FONT_SIZE + 98f);
    }

    private int menuPanelX() {
        int margin = Math.max(24, Math.round(this.width * 0.08f));
        int centered = Math.round((this.width - MENU_PANEL_W) / 2f);
        return Math.max(margin, Math.min(centered, this.width - MENU_PANEL_W - margin));
    }

    private float menuPanelY() {
        return menuStartY() - 15f;
    }

    private float menuPanelH() {
        return menuItems.size() * MENU_SPACING + 18f;
    }

    private void drawAtmosphere(GuiGraphicsExtractor gui, float elapsed) {
        float reveal = easeOutCubic(clamp01(elapsed * 1.6f)) * entryAlpha;
        if (reveal <= 0.01f) return;

        float pulse = 0.88f + 0.12f * (float) Math.sin(elapsed * 0.8f);
        SdfUIRenderer.drawCircle(gui, this.width - 88f, 92f, 196,
                scaleAlpha(0x1889DDFF, reveal * pulse));
        SdfUIRenderer.drawCircle(gui, 52f, this.height - 72f, 148,
                scaleAlpha(0x105C7CFF, reveal));
    }

    private void drawGlassSurfaces(GuiGraphicsExtractor gui, float elapsed) {
        float reveal = easeOutCubic(clamp01((elapsed - 0.18f) * 2.8f)) * entryAlpha;
        if (reveal <= 0.01f) return;

        int panelX = menuPanelX();
        int panelY = Math.round(menuPanelY() + (1f - reveal) * 10f);
        int panelW = MENU_PANEL_W;
        int panelH = Math.round(menuPanelH());

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

        ensureFontsLoaded();
        if (navFont == null) return;
        float githubW = CustomFontRenderer.stringWidth(navFont, "Github");
        float navX = navStartX(githubW) - 13f;
        float navW = this.width - NAV_RIGHT_PAD - navX + 13f;
        int navY = 10;
        int navH = 31;
        SdfUIRenderer.drawShadow(gui, Math.round(navX), navY, Math.round(navW), navH,
                10, 0, 4, 12, scaleAlpha(0x50000000, reveal));
        CustomBlurRenderer.render(navX, navY, navW, navH, 10,
                scaleAlpha(0x42101820, reveal), 6f);
        CustomRoundedRectRenderer.drawRoundedRect(gui, Math.round(navX), navY,
                Math.round(navW), navH, 10, scaleAlpha(0x7210151D, reveal));
        CustomRoundedRectRenderer.drawRoundedOutline(gui, Math.round(navX), navY,
                Math.round(navW), navH, 10, scaleAlpha(0x3EFFFFFF, reveal), 1);
    }

    // ========================
    // Hover updates (exponential lerp)
    // ========================

    private void updateMenuHover(int mouseX, int mouseY, float dt) {
        hoveredIndex = -1;
        for (int i = 0; i < menuItems.size(); i++) {
            boolean over = isMenuHover(mouseX, mouseY, i) || i == focusedIndex;
            if (isMenuHover(mouseX, mouseY, i)) hoveredIndex = i;
            float target = over ? 1f : 0f;
            hoverProgress[i] += (target - hoverProgress[i]) * dt * HOVER_SPEED;
        }
        float anyTarget = hoveredIndex >= 0 || focusedIndex >= 0 ? 1f : 0f;
        anyHoverProgress += (anyTarget - anyHoverProgress) * dt * HOVER_SPEED;
    }

    private void updateNavHover(int mouseX, int mouseY, float dt) {
        boolean overGithub = isNavGithubHover(mouseX, mouseY);
        boolean overDiscord = isNavDiscordHover(mouseX, mouseY);

        githubHover += ((overGithub ? 1f : 0f) - githubHover) * dt * HOVER_SPEED;
        discordHover += ((overDiscord ? 1f : 0f) - discordHover) * dt * HOVER_SPEED;

        githubUnderline += ((overGithub ? 1f : 0f) - githubUnderline) * dt * UNDERLINE_SPEED;
        discordUnderline += ((overDiscord ? 1f : 0f) - discordUnderline) * dt * UNDERLINE_SPEED;
    }

    // ========================
    // Title: "G E M I N I"
    // Letters reveal one by one, white → soft cyan gradient.
    // ========================

    private void drawTitle(GuiGraphicsExtractor gui, float elapsed) {
        ensureFontsLoaded();
        if (titleFont == null) return;

        String title = "G E M I N I";
        float titleWidth = computeSpacedWidth(titleFont, title, TITLE_SPACING_PX);
        float titleX = (this.width - titleWidth) / 2f;
        float titleY = this.height * 0.25f;

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

            cx += chW + TITLE_SPACING_PX;
            i += Character.charCount(cp);
        }
    }

    // ========================
    // Accent rule under the title — draws itself outward from center
    // ========================

    private void drawAccentRule(GuiGraphicsExtractor gui, float elapsed) {
        ensureFontsLoaded();
        if (titleFont == null) return;

        float titleWidth = computeSpacedWidth(titleFont, "G E M I N I", TITLE_SPACING_PX);
        float progress = easeOutCubic(clamp01((elapsed - 0.45f) * 2.2f));
        if (progress <= 0.01f) return;

        float lineW = titleWidth * 0.32f * progress;
        float x = (this.width - lineW) / 2f;
        float y = this.height * 0.25f + TITLE_FONT_SIZE + 10f;

        int alpha = (int) (entryAlpha * progress * 255);
        int color = (alpha << 24) | (ACCENT & 0x00FFFFFF);
        CustomRectRenderer.drawRect(gui, (int) x, (int) y, (int) lineW, 1, color);
    }

    // ========================
    // Subtitle: "Modern Minecraft Client"
    // ========================

    private void drawSubtitle(GuiGraphicsExtractor gui, float elapsed) {
        ensureFontsLoaded();
        if (subtitleFont == null) return;

        String subtitle = "Modern Minecraft Client";
        float subWidth = CustomFontRenderer.stringWidth(subtitleFont, subtitle);
        float subX = (this.width - subWidth) / 2f;

        float titleY = this.height * 0.25f;
        float subY = titleY + TITLE_FONT_SIZE + 22f;

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

    private void drawMenuItems(GuiGraphicsExtractor gui, int mouseX, int mouseY, float elapsed) {
        ensureFontsLoaded();
        if (menuFont == null) return;

        float menuStartY = menuStartY();

        for (int i = 0; i < menuItems.size(); i++) {
            MenuItem item = menuItems.get(i);

            // Per-item staggered reveal
            float reveal = easeOutCubic(clamp01((elapsed - 0.35f - i * ENTRY_STAGGER) * 3f));
            if (reveal <= 0.01f) continue;

            float slideIn = (1f - reveal) * -ENTRY_SLIDE_PX;
            float y = menuStartY + i * MENU_SPACING;
            float hp = hoverProgress[i];

            // Dim idle items while another is hovered
            float dim = 1f - DIM_FACTOR * anyHoverProgress * (1f - hp);
            int alpha = (int) (entryAlpha * reveal * dim * 255);
            if (alpha <= 0) continue;

            int panelX = menuPanelX();
            int rowX = panelX + 12;
            int rowY = Math.round(y - 7f);
            int rowW = MENU_PANEL_W - 24;
            int rowH = Math.round(menuFont.lineHeight + 14f);
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

            float textX = panelX + 46f + slideIn + hp * HOVER_SLIDE_PX;
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
                    panelX + MENU_PANEL_W - 28f - shortcutW,
                    y + 1f, shortcutColor);
        }
    }

    // ========================
    // Navigation: Github | Discord
    // Accent underlines grow outward from the center; clickable.
    // ========================

    private void drawNavigation(GuiGraphicsExtractor gui, float elapsed) {
        ensureFontsLoaded();
        if (navFont == null) return;

        float navY = 20f;

        String githubText = "Github";
        String discordText = "Discord";
        String separator = "   ·   ";

        float githubW = CustomFontRenderer.stringWidth(navFont, githubText);
        float separatorW = CustomFontRenderer.stringWidth(navFont, separator);
        float discordW = CustomFontRenderer.stringWidth(navFont, discordText);

        float totalW = githubW + separatorW + discordW;
        float startX = this.width - NAV_RIGHT_PAD - totalW;

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

    private void drawFooter(GuiGraphicsExtractor gui, float elapsed) {
        ensureFontsLoaded();
        if (versionFont == null) return;

        float reveal = easeOutCubic(clamp01((elapsed - 0.75f) * 3f));
        int alpha = (int) (entryAlpha * reveal * 255);
        if (alpha <= 0) return;

        // ── Right: client name + version ──
        String line1 = "Gemini Client";
        String line2 = "v" + MOD_VERSION;

        float w1 = CustomFontRenderer.stringWidth(versionFont, line1);
        float w2 = CustomFontRenderer.stringWidth(versionFont, line2);

        float x1 = this.width - FOOTER_RIGHT_PAD - w1;
        float x2 = this.width - FOOTER_RIGHT_PAD - w2;
        float y2 = this.height - FOOTER_BOTTOM_PAD;
        float y1 = y2 - VERSION_FONT_SIZE - 4f;

        int color = (alpha << 24) | (VERSION_COLOR & 0x00FFFFFF);

        // Small accent square before the client name
        int sqColor = (alpha << 24) | (ACCENT & 0x00FFFFFF);
        float sqY = y1 + (VERSION_FONT_SIZE - 3f) / 2f;
        CustomRectRenderer.drawRect(gui, (int) (x1 - 9), (int) sqY, 3, 3, sqColor);

        CustomFontRenderer.drawString(gui, versionFont, line1, x1, y1, color);
        CustomFontRenderer.drawString(gui, versionFont, line2, x2, y2, color);

        String hints = "↑↓  Select    Enter  Open";
        float hintsW = CustomFontRenderer.stringWidth(versionFont, hints);
        float hintsX = menuPanelX() + (MENU_PANEL_W - hintsW) / 2f;
        int hintColor = (int) (alpha * 0.58f) << 24 | (HINT_COLOR & 0x00FFFFFF);
        CustomFontRenderer.drawString(gui, versionFont, hints, hintsX, y2, hintColor);
    }

    // ========================
    // Input
    // ========================

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent mouse, boolean idk) {
        // Navigation links
        if (isNavGithubHover(mouse.x(), mouse.y())) {
//            Util.getPlatform().openUri(GITHUB_URL);
            return true;
        }
        if (isNavDiscordHover(mouse.x(), mouse.y())) {
//            Util.getPlatform().openUri(DISCORD_URL);
            return true;
        }

        for (int i = 0; i < menuItems.size(); i++) {
            if (isMenuHover(mouse.x(), mouse.y(), i)) {
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

    private boolean isMenuHover(double mx, double my, int index) {
        if (menuFont == null) return false;
        float menuStartY = menuStartY();
        float y = menuStartY + index * MENU_SPACING;
        int panelX = menuPanelX();
        return mx >= panelX + 12 && mx <= panelX + MENU_PANEL_W - 12
                && my >= y - 7 && my <= y + menuFont.lineHeight + 7;
    }

    private boolean isNavGithubHover(double mx, double my) {
        if (navFont == null) return false;
        float githubW = CustomFontRenderer.stringWidth(navFont, "Github");
        float startX = navStartX(githubW);
        return mx >= startX - 4 && mx <= startX + githubW + 4
                && my >= 16f && my <= 20f + navFont.lineHeight + 4;
    }

    private boolean isNavDiscordHover(double mx, double my) {
        if (navFont == null) return false;
        String separator = "   ·   ";
        float githubW = CustomFontRenderer.stringWidth(navFont, "Github");
        float separatorW = CustomFontRenderer.stringWidth(navFont, separator);
        float discordW = CustomFontRenderer.stringWidth(navFont, "Discord");
        float discordX = navStartX(githubW) + githubW + separatorW;
        return mx >= discordX - 4 && mx <= discordX + discordW + 4
                && my >= 16f && my <= 20f + navFont.lineHeight + 4;
    }

    private float navStartX(float githubW) {
        float separatorW = CustomFontRenderer.stringWidth(navFont, "   ·   ");
        float discordW = CustomFontRenderer.stringWidth(navFont, "Discord");
        return this.width - NAV_RIGHT_PAD - (githubW + separatorW + discordW);
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

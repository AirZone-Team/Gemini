package geminiclient.gemini.base;

import com.mojang.blaze3d.platform.InputConstants;
import geminiclient.gemini.base.alt.AltManagerScreen;
import geminiclient.gemini.customRenderer.cpu.CustomRectRenderer;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Modernist main menu — minimalist typography-driven design.
 *
 * <p>Visual hierarchy is established purely through typography, spacing,
 * and a fullscreen GLSL perspective grid background. No particles,
 * no button backgrounds, no icons.</p>
 *
 * <p>Beautified: staggered letter-by-letter title reveal with a soft
 * gradient, an accent rule that draws itself under the title, menu items
 * that cascade in from the left, an accent indicator bar replacing the
 * dot, hover text slide, focus dimming of idle items, accent underlines
 * on the navigation (now clickable), and a richer footer.</p>
 */
public class MainMenuScreen extends Screen {

    // ========================
    // Layout Constants
    // ========================
    private static final int LEFT_MARGIN = 40;
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

        // ── 3. Title ───────────────────────────────────────
        drawTitle(gui, elapsed);

        // ── 4. Accent rule + Subtitle ──────────────────────
        drawAccentRule(gui, elapsed);
        drawSubtitle(gui, elapsed);

        // ── 5. Menu items ──────────────────────────────────
        drawMenuItems(gui, mouseX, mouseY, elapsed);

        // ── 6. Navigation ──────────────────────────────────
        drawNavigation(gui, elapsed);

        // ── 7. Footer ──────────────────────────────────────
        drawFooter(gui, elapsed);
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

        float menuStartY = this.height * 0.50f;

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

            // Accent indicator bar: grows vertically on hover
            if (hp > 0.01f) {
                float lineH = menuFont.lineHeight;
                float barH = lineH * hp;
                float barY = y + (lineH - barH) / 2f;
                int barAlpha = (int) (alpha * hp);
                int barColor = (barAlpha << 24) | (ACCENT & 0x00FFFFFF);
                CustomRectRenderer.drawRect(gui, LEFT_MARGIN - 12 + (int) slideIn, (int) barY,
                        2, (int) barH, barColor);
            }

            // Label text: slides right on hover
            int idleColor = (alpha << 24) | (TEXT_IDLE & 0x00FFFFFF);
            int hoverColor = (alpha << 24) | (TEXT_HOVER & 0x00FFFFFF);
            int textColor = lerpColor(idleColor, hoverColor, hp);

            float textX = LEFT_MARGIN + 16f + slideIn + hp * HOVER_SLIDE_PX;
            CustomFontRenderer.drawString(gui, menuFont, item.label, textX, y, textColor);
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

        // ── Left: keyboard shortcut hints ──
        String hints = "S  Singleplayer    M  Multiplayer    O  Settings    A  Alt Manager    E  Exit";
        int hintColor = (alpha << 24) | (HINT_COLOR & 0x00FFFFFF);
        CustomFontRenderer.drawString(gui, versionFont, hints, LEFT_MARGIN, y2, hintColor);
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
        float menuStartY = this.height * 0.50f;
        float y = menuStartY + index * MENU_SPACING;
        float textX = LEFT_MARGIN + 16f;
        float textW = CustomFontRenderer.stringWidth(menuFont, menuItems.get(index).label);
        // Slightly padded hitbox for a more forgiving hover target
        return mx >= LEFT_MARGIN - 12 && mx <= textX + textW + 8
                && my >= y - 4 && my <= y + menuFont.lineHeight + 4;
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
}
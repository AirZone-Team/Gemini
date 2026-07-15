package geminiclient.gemini.base;

import com.mojang.blaze3d.platform.InputConstants;
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
 * no button backgrounds, no icons, no glow effects.</p>
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
    private static final int TEXT_IDLE       = 0xFFAAAAAA;
    private static final int TEXT_HOVER      = 0xFFFFFFFF;
    private static final int DOT_COLOR       = 0xFFFFFFFF;
    private static final int TITLE_COLOR     = 0xFFFFFFFF;
    private static final int SUBTITLE_COLOR  = 0xFF7A7A7A;
    private static final int NAV_IDLE        = 0xFFAAAAAA;
    private static final int NAV_HOVER       = 0xFFFFFFFF;
    private static final int NAV_UNDERLINE   = 0xFFFFFFFF;
    private static final int VERSION_COLOR   = 0xFF666666;
    private static final int SEPARATOR_COLOR = 0xFF444444;

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
    private static final float HOVER_SPEED     = 12f;  // exponential lerp speed
    private static final float DOT_SLIDE_SPEED = 12f;
    private static final float UNDERLINE_SPEED = 12f;
    private static final float ENTRY_FADE_SPEED = 4f;

    private static final String MOD_VERSION = "0.1.0";

    // ========================
    // Inner Types
    // ========================

    private record MenuItem(String label, Runnable action) {}

    // ========================
    // State
    // ========================

    private final List<MenuItem> menuItems = new ArrayList<>();
    private float[] hoverProgress;
    private float[] dotOffset;       // ● X offset: -10 → 0
    private float entryAlpha;        // fade-in: 0 → 1
    private int focusedIndex = -1;

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
        menuItems.add(new MenuItem("Exit",
                this.minecraft::stop));

        if (hoverProgress == null || hoverProgress.length != menuItems.size()) {
            hoverProgress = new float[menuItems.size()];
            dotOffset = new float[menuItems.size()];
            for (int i = 0; i < menuItems.size(); i++) {
                dotOffset[i] = -10f;
            }
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
        drawTitle(gui);

        // ── 4. Subtitle ────────────────────────────────────
        drawSubtitle(gui);

        // ── 5. Menu items ──────────────────────────────────
        drawMenuItems(gui, mouseX, mouseY);

        // ── 6. Navigation ──────────────────────────────────
        drawNavigation(gui);

        // ── 7. Footer ──────────────────────────────────────
        drawFooter(gui);
    }

    // ========================
    // Hover updates (exponential lerp)
    // ========================

    private void updateMenuHover(int mouseX, int mouseY, float dt) {
        for (int i = 0; i < menuItems.size(); i++) {
            boolean over = isMenuHover(mouseX, mouseY, i);
            float target = over ? 1f : 0f;
            hoverProgress[i] += (target - hoverProgress[i]) * dt * HOVER_SPEED;

            // Dot slide: -10 → 0
            float dotTarget = over ? 0f : -10f;
            dotOffset[i] += (dotTarget - dotOffset[i]) * dt * DOT_SLIDE_SPEED;
        }
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
    // ========================

    private void drawTitle(GuiGraphicsExtractor gui) {
        ensureFontsLoaded();
        if (titleFont == null) return;

        String title = "G E M I N I";
        float titleWidth = computeSpacedWidth(titleFont, title, TITLE_SPACING_PX);
        float titleX = (this.width - titleWidth) / 2f;
        float titleY = this.height * 0.25f;

        int alpha = (int) (entryAlpha * 255);
        int color = (alpha << 24) | (TITLE_COLOR & 0x00FFFFFF);

        drawSpacedText(gui, titleFont, title, titleX, titleY, color, TITLE_SPACING_PX);
    }

    // ========================
    // Subtitle: "Modern Minecraft Client"
    // ========================

    private void drawSubtitle(GuiGraphicsExtractor gui) {
        ensureFontsLoaded();
        if (subtitleFont == null) return;

        String subtitle = "Modern Minecraft Client";
        float subWidth = CustomFontRenderer.stringWidth(subtitleFont, subtitle);
        float subX = (this.width - subWidth) / 2f;

        float titleY = this.height * 0.25f;
        float subY = titleY + TITLE_FONT_SIZE + 16f;

        int alpha = (int) (entryAlpha * 255);
        int color = (alpha << 24) | (SUBTITLE_COLOR & 0x00FFFFFF);

        CustomFontRenderer.drawString(gui, subtitleFont, subtitle, subX, subY, color);
    }

    // ========================
    // Menu items
    // ========================

    private void drawMenuItems(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        ensureFontsLoaded();
        if (menuFont == null) return;

        float menuStartY = this.height * 0.50f;

        for (int i = 0; i < menuItems.size(); i++) {
            MenuItem item = menuItems.get(i);
            float y = menuStartY + i * MENU_SPACING;
            float hp = hoverProgress[i];

            // Alpha with entry fade
            int alpha = (int) (entryAlpha * 255);
            if (alpha <= 0) continue;

            // Color lerp: idle → hover
            int idleColor = (alpha << 24) | (TEXT_IDLE & 0x00FFFFFF);
            int hoverColor = (alpha << 24) | (TEXT_HOVER & 0x00FFFFFF);
            int textColor = lerpColor(idleColor, hoverColor, hp);

            // ● dot
            if (hp > 0.01f) {
                int dotAlpha = (int) (alpha * hp);
                int dotColor = (dotAlpha << 24) | (DOT_COLOR & 0x00FFFFFF);
                float dotX = LEFT_MARGIN + dotOffset[i];
                CustomFontRenderer.drawString(gui, menuFont, "•", dotX, y, dotColor);
            }

            // Label text
            float textX = LEFT_MARGIN + 16f;
            CustomFontRenderer.drawString(gui, menuFont, item.label, textX, y, textColor);
        }
    }

    // ========================
    // Navigation: Github | Discord
    // ========================

    private void drawNavigation(GuiGraphicsExtractor gui) {
        ensureFontsLoaded();
        if (navFont == null) return;

        float navY = 20f;

        String githubText = "Github";
        String discordText = "Discord";
        String separator = "   |   ";

        float githubW = CustomFontRenderer.stringWidth(navFont, githubText);
        float separatorW = CustomFontRenderer.stringWidth(navFont, separator);
        float discordW = CustomFontRenderer.stringWidth(navFont, discordText);

        float totalW = githubW + separatorW + discordW;
        float startX = this.width - NAV_RIGHT_PAD - totalW;

        int alpha = (int) (entryAlpha * 255);
        if (alpha <= 0) return;

        // Github
        int githubColor = lerpColor(
                (alpha << 24) | (NAV_IDLE & 0x00FFFFFF),
                (alpha << 24) | (NAV_HOVER & 0x00FFFFFF),
                githubHover);
        CustomFontRenderer.drawString(gui, navFont, githubText, startX, navY, githubColor);

        // Github underline
        if (githubUnderline > 0.01f) {
            int ulAlpha = (int) (alpha * githubUnderline);
            int ulColor = (ulAlpha << 24) | (NAV_UNDERLINE & 0x00FFFFFF);
            float ulY = navY + NAV_FONT_SIZE + 2f;
            CustomRectRenderer.drawRect(gui, (int) startX, (int) ulY,
                    (int) (githubW * githubUnderline), 1, ulColor);
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

        // Discord underline
        if (discordUnderline > 0.01f) {
            int ulAlpha = (int) (alpha * discordUnderline);
            int ulColor = (ulAlpha << 24) | (NAV_UNDERLINE & 0x00FFFFFF);
            float ulY = navY + NAV_FONT_SIZE + 2f;
            CustomRectRenderer.drawRect(gui, (int) discordX, (int) ulY,
                    (int) (discordW * discordUnderline), 1, ulColor);
        }
    }

    // ========================
    // Footer: version info
    // ========================

    private void drawFooter(GuiGraphicsExtractor gui) {
        ensureFontsLoaded();
        if (versionFont == null) return;

        String line1 = "Gemini Client";
        String line2 = "v" + MOD_VERSION;

        float w1 = CustomFontRenderer.stringWidth(versionFont, line1);
        float w2 = CustomFontRenderer.stringWidth(versionFont, line2);

        float x1 = this.width - FOOTER_RIGHT_PAD - w1;
        float x2 = this.width - FOOTER_RIGHT_PAD - w2;
        float y2 = this.height - FOOTER_BOTTOM_PAD;
        float y1 = y2 - VERSION_FONT_SIZE - 4f;

        int alpha = (int) (entryAlpha * 255);
        if (alpha <= 0) return;

        int color = (alpha << 24) | (VERSION_COLOR & 0x00FFFFFF);

        CustomFontRenderer.drawString(gui, versionFont, line1, x1, y1, color);
        CustomFontRenderer.drawString(gui, versionFont, line2, x2, y2, color);
    }

    // ========================
    // Input
    // ========================

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent mouse, boolean idk) {
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
        // Shortcut keys: S, M, O, E
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
        if (key == InputConstants.KEY_E) {
            menuItems.get(3).action.run();
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
        return mx >= textX && mx <= textX + textW
                && my >= y && my <= y + menuFont.lineHeight;
    }

    private boolean isNavGithubHover(double mx, double my) {
        if (navFont == null) return false;
        String githubText = "Github";
        String separator = "   |   ";
        float githubW = CustomFontRenderer.stringWidth(navFont, githubText);
        float separatorW = CustomFontRenderer.stringWidth(navFont, separator);
        float discordW = CustomFontRenderer.stringWidth(navFont, "Discord");
        float totalW = githubW + separatorW + discordW;
        float startX = this.width - NAV_RIGHT_PAD - totalW;
        return mx >= startX && mx <= startX + githubW
                && my >= 20f && my <= 20f + navFont.lineHeight;
    }

    private boolean isNavDiscordHover(double mx, double my) {
        if (navFont == null) return false;
        String githubText = "Github";
        String separator = "   |   ";
        String discordText = "Discord";
        float githubW = CustomFontRenderer.stringWidth(navFont, githubText);
        float separatorW = CustomFontRenderer.stringWidth(navFont, separator);
        float discordW = CustomFontRenderer.stringWidth(navFont, discordText);
        float totalW = githubW + separatorW + discordW;
        float startX = this.width - NAV_RIGHT_PAD - totalW;
        float discordX = startX + githubW + separatorW;
        return mx >= discordX && mx <= discordX + discordW
                && my >= 20f && my <= 20f + navFont.lineHeight;
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

    private static void drawSpacedText(GuiGraphicsExtractor gui, GlyphFont font,
                                        String text, float x, float y, int color, float spacing) {
        float cx = x;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            CustomFontRenderer.drawString(gui, font, ch, cx, y, color);
            cx += CustomFontRenderer.stringWidth(font, ch) + spacing;
            i += Character.charCount(cp);
        }
    }

    // ========================
    // Color utilities
    // ========================

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

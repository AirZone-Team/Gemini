package geminiclient.gemini.base;

import com.mojang.blaze3d.platform.InputConstants;
import geminiclient.gemini.customRenderer.cpu.CustomRectRenderer;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer.GlyphFont;
import geminiclient.gemini.customRenderer.glsl.GlowRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MainMenuScreen extends Screen {

    // ========================
    // Layout Constants
    // ========================
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_RADIUS = 6;
    private static final int BUTTON_SPACING = 6;
    private static final int LEFT_MARGIN = 40;
    private static final int ICON_SIZE = 24;
    private static final int ICON_RADIUS = 2;
    private static final int ICON_LEFT_PAD = 5;
    private static final int SHORTCUT_RIGHT_PAD = 8;
    private static final int TEXT_LEFT_OFFSET = ICON_LEFT_PAD + ICON_SIZE + 5;

    // ========================
    // Color Constants (ARGB)
    // ========================
    private static final int BG_COLOR = 0xFF101010;
    private static final int BTN_FILL_IDLE = 0x80000000;
    private static final int BTN_FILL_HOVER = 0xD0404040;
    private static final int BTN_BORDER_IDLE = 0x30FFFFFF;
    private static final int BTN_BORDER_HOVER = 0x80FFFFFF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TITLE_COLOR = 0xFF64B5F6;
    private static final int TITLE_SHADOW = 0xAA000000;
    private static final float HOVER_SPEED = 0.15f;

    // New colors
    private static final int BTN_FILL_TOP_IDLE = 0x80181818;
    private static final int BTN_FILL_BOT_IDLE = 0x80080808;
    private static final int BTN_FILL_TOP_HOVER = 0xD0484848;
    private static final int BTN_FILL_BOT_HOVER = 0xD0303030;
    private static final int TEXT_SHADOW = 0xAA000000;
    private static final int SHORTCUT_COLOR = 0x50FFFFFF;
    private static final int SHINE_COLOR = 0x30FFFFFF;
    private static final int GLOW_TITLE_COLOR = 0x1864B5F6;
    private static final int SUBTITLE_COLOR = 0x80FFFFFF;
    private static final int FOCUS_PULSE_COLOR = 0x60FFFFFF;
    private static final int CLOUD_COLOR = 0x08FFFFFF;
    private static final int CLOUD_COLOR_WARM = 0x0664B5F6;
    private static final int BOTTOM_GLOW_TOP = 0x0064B5F6;
    private static final int BOTTOM_GLOW_BOT = 0x0C64B5F6;
    private static final int PARTICLE_COLOR_1 = 0x50FFFFFF;
    private static final int PARTICLE_COLOR_2 = 0x4064B5F6;
    private static final int PARTICLE_COLOR_3 = 0x40FFD54F;
    private static final int TRAIL_COLOR = 0x40FFFFFF;
    private static final int RIPPLE_COLOR_START = 0x30FFFFFF;
    private static final int RIPPLE_COLOR_END = 0x00FFFFFF;

    // Geometric background layer
    private static final int GEO_ELEMENT_COUNT = 16;
    private static final float GEO_SCROLL_SPEED = 8f;        // pixels per second
    private static final int GEO_COLOR_COOL = 0x0E64B5F6;     // subtle blue
    private static final int GEO_COLOR_WARM = 0x0AFFD54F;     // subtle amber
    private static final int GEO_COLOR_NEUTRAL = 0x0CFFFFFF;  // subtle white
    private static final int GEO_ACCENT = 0x1864B5F6;         // slightly brighter accent for caps

    // ========================
    // Custom Fonts
    // ========================
    private static final Identifier TITLE_FONT_PATH =
            Identifier.fromNamespaceAndPath("gemini", "font/sourcehansanssc-bold.ttf");
    private static final Identifier BUTTON_FONT_PATH =
            Identifier.fromNamespaceAndPath("gemini", "font/sourcehansanssc-medium.ttf");
    private static final float TITLE_FONT_SIZE = 44f;
    private static final float SUBTITLE_FONT_SIZE = 20f;
    private static final float BUTTON_FONT_SIZE = 15f;
    private static final float SHORTCUT_FONT_SIZE = 12f;
    private static final int TITLE_GRADIENT_TOP = 0xFFFFFFFF;
    private static final int TITLE_GRADIENT_BOT = 0xFF1976D2;
    private static final int TITLE_GLOW_COLOR = 0xFF42A5F5;
    private static final int TITLE_SPACING = 3;
    private static final int SUBTITLE_COLOR_ARGB = 0xB0FFFFFF;

    private static GlyphFont titleGlyphFont;
    private static GlyphFont subtitleGlyphFont;
    private static GlyphFont buttonGlyphFont;
    private static GlyphFont shortcutGlyphFont;

    private static void ensureFontsLoaded() {
        if (titleGlyphFont == null) {
            titleGlyphFont = CustomFontRenderer.loadFont(TITLE_FONT_PATH, TITLE_FONT_SIZE);
        }
        if (subtitleGlyphFont == null) {
            subtitleGlyphFont = CustomFontRenderer.loadFont(TITLE_FONT_PATH, SUBTITLE_FONT_SIZE);
        }
        if (buttonGlyphFont == null) {
            buttonGlyphFont = CustomFontRenderer.loadFont(BUTTON_FONT_PATH, BUTTON_FONT_SIZE);
        }
        if (shortcutGlyphFont == null) {
            shortcutGlyphFont = CustomFontRenderer.loadFont(BUTTON_FONT_PATH, SHORTCUT_FONT_SIZE);
        }
    }

    private static float computeSpacedWidth(GlyphFont font, String text) {
        if (font == null) return 0;
        float w = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            w += CustomFontRenderer.stringWidth(font, ch) + TITLE_SPACING;
            i += Character.charCount(cp);
        }
        if (w > 0) w -= TITLE_SPACING;
        return w;
    }

    // ========================
    // Animation Timing (seconds)
    // ========================
    private static final float ENTRY_DURATION = 0.55f;
    private static final float SHINE_PERIOD = 0.9f;
    private static final float SHINE_DURATION = 0.35f;
    private static final float CLICK_DURATION = 0.1f;
    private static final float RIPPLE_DURATION = 0.45f;
    private static final float TRAIL_LIFETIME = 0.6f;
    private static final float PARTICLE_MAX_LIFE = 4.0f;
    private static final int MAX_PARTICLES = 25;
    private static final int MAX_TRAIL_POINTS = 16;
    private static final float CLOUD_DRIFT_SPEED = 0.03f;
    private static final String SUBTITLE_TEXT = "Client";
    private static final String MOD_VERSION = "0.1.0";

    // ========================
    // Inner Types
    // ========================

    private static final int ICON_TEX_SIZE = 24;

    private record Button(String label, int y, Identifier icon, char shortcut, Runnable action) {}

    private static class Particle {
        float x, y;
        float vx, vy;
        float life;
        final float maxLife;
        final int color;
        final int size;

        Particle(float x, float y, float vx, float vy, float maxLife, int color, int size) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.maxLife = maxLife;
            this.life = maxLife;
            this.color = color;
            this.size = size;
        }
    }

    private record TrailPoint(int x, int y, long birthMs) {}

    private record Ripple(int buttonIndex, float originX, float originY, long startMs) {}

    /**
     * A geometric background element with animated height and horizontal scroll.
     * Includes depth for parallax effects.
     */
    private static class GeoElement {
        final float baseX;
        final int width;
        final float baseHeightPct;
        final int colorTop;
        final int colorBottom;
        final int capColor;
        final int shapeType;
        final float depth; // New: determines scroll speed and opacity scaling

        // Oscillation parameters
        final float freq1, freq2, freq3;
        final float phase1, phase2, phase3;
        final float amp1, amp2, amp3;

        GeoElement(float baseX, int width, float baseHeightPct,
                   int colorTop, int colorBottom, int capColor, int shapeType, float depth) {
            this.baseX = baseX;
            this.width = width;
            this.baseHeightPct = baseHeightPct;
            this.colorTop = colorTop;
            this.colorBottom = colorBottom;
            this.capColor = capColor;
            this.shapeType = shapeType;
            this.depth = depth;

            float seed = (float) (Math.random() * Math.PI * 2);
            this.freq1 = 0.4f + (float) Math.random() * 0.6f;
            this.freq2 = 0.7f + (float) Math.random() * 1.1f;
            this.freq3 = 1.3f + (float) Math.random() * 0.9f;
            this.phase1 = seed;
            this.phase2 = seed + (float) Math.random() * 3f;
            this.phase3 = seed + (float) Math.random() * 5f;
            this.amp1 = 0.08f + (float) Math.random() * 0.12f;
            this.amp2 = 0.04f + (float) Math.random() * 0.08f;
            this.amp3 = 0.02f + (float) Math.random() * 0.04f;
        }
    }

    // ========================
    // State
    // ========================

    private final List<Button> buttons = new ArrayList<>();
    private float[] hoverProgress = new float[0];
    private float[] clickScale = new float[0];
    private long[] clickStartTime = new long[0];
    private int focusedIndex = -1;

    private final List<Particle> particles = new ArrayList<>();
    private final List<TrailPoint> mouseTrail = new ArrayList<>();
    private final List<Ripple> ripples = new ArrayList<>();
    private final List<GeoElement> geoElements = new ArrayList<>();
    private boolean geoInitialized;

    private long screenOpenTime;
    private long lastFrameMs;
    private int lastMouseX;
    private int lastMouseY;
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
        buttons.clear();
        int startY = this.height / 2 - 30;

        buttons.add(new Button("Singleplayer", startY,
                Identifier.fromNamespaceAndPath("gemini", "icon/person.png"), 'S',
                () -> this.minecraft.setScreen(new SelectWorldScreen(this))));
        buttons.add(new Button("Multiplayer", startY + (BUTTON_HEIGHT + BUTTON_SPACING),
                Identifier.fromNamespaceAndPath("gemini", "icon/groups.png"), 'M',
                () -> this.minecraft.setScreen(new JoinMultiplayerScreen(this))));
        buttons.add(new Button("Settings", startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 2,
                Identifier.fromNamespaceAndPath("gemini", "icon/settings.png"), 'O',
                () -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options, false))));
        buttons.add(new Button("Quit Game", startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 3,
                Identifier.fromNamespaceAndPath("gemini", "icon/exit_to_app.png"), 'Q',
                this.minecraft::stop));

        if (hoverProgress.length != buttons.size()) {
            hoverProgress = new float[buttons.size()];
            clickScale = new float[buttons.size()];
            clickStartTime = new long[buttons.size()];
        }
        for (int i = 0; i < buttons.size(); i++) {
            if (clickStartTime[i] == 0) clickScale[i] = 1f;
        }

        if (firstInit) {
            screenOpenTime = System.currentTimeMillis();
            firstInit = false;
            for (int i = 0; i < 12; i++) {
                spawnParticle(true);
            }
        }
        lastFrameMs = System.currentTimeMillis();
    }

    // ========================
    // Lifecycle: extractRenderState (main rendering)
    // ========================

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastFrameMs) / 1000f, 0.1f);
        lastFrameMs = now;
        float elapsed = (now - screenOpenTime) / 1000f;

        // ── 1. Dynamic Background ──────────────────────────
        drawBackground(gui, elapsed, dt);

        // ── 2. Particles ───────────────────────────────────
        updateParticles(dt);
        drawParticles(gui);

        // ── 3. Mouse Trail ─────────────────────────────────
        updateMouseTrail(mouseX, mouseY, now);
        drawMouseTrail(gui, now);

        // ── 4. Hover Progress ──────────────────────────────
        for (int i = 0; i < buttons.size(); i++) {
            Button btn = buttons.get(i);
            boolean over = isMouseOver(mouseX, mouseY, btn);
            hoverProgress[i] += (over ? 1f : -1f) * HOVER_SPEED;
            hoverProgress[i] = Math.clamp(hoverProgress[i], 0f, 1f);

            // Trigger ripple on hover enter
            if (over && hoverProgress[i] < 0.1f) {
                ripples.add(new Ripple(i, mouseX, mouseY, now));
            }
        }
        // Clean old ripples
        ripples.removeIf(r -> (now - r.startMs) / 1000f > RIPPLE_DURATION);

        // ── 5. Click animations ────────────────────────────
        updateClickAnimations(now);

        // ── 6. Title ───────────────────────────────────────
        drawTitle(gui, elapsed);

        // ── 7. Buttons ─────────────────────────────────────
        for (int i = 0; i < buttons.size(); i++) {
            drawButton(gui, buttons.get(i), i, mouseX, mouseY, now);
        }
    }

    // ========================
    // Geometric Background Layer
    // ========================

    private void initGeoElements() {
        if (geoInitialized) return;
        geoInitialized = true;
        geoElements.clear();

        int[] colors = {GEO_COLOR_COOL, GEO_COLOR_WARM, GEO_COLOR_NEUTRAL};
        int[] accents = {GEO_ACCENT, 0x12FFD54F, 0x14FFFFFF};

        for (int i = 0; i < GEO_ELEMENT_COUNT; i++) {
            float baseX = (float) i / GEO_ELEMENT_COUNT;
            baseX += (float) (Math.random() - 0.5) * 0.04f;

            // Generate depth between 0.4 (far/slow) and 1.2 (near/fast)
            float depth = 0.4f + (float) Math.random() * 0.8f;

            // Width scales with depth to simulate perspective
            int width = (int) ((20 + Math.random() * 45) * depth);
            float baseHeightPct = 0.12f + (float) Math.random() * 0.45f;

            int colorTop = colors[(int) (Math.random() * colors.length)];
            // Fade depth: shapes further away are more transparent
            colorTop = multiplyAlpha(colorTop, depth * 0.8f);
            int colorBottom = (colorTop & 0x00FFFFFF); // fades to full transparent at bottom

            int capColor = accents[(int) (Math.random() * accents.length)];
            capColor = multiplyAlpha(capColor, depth);

            int shapeType = (int) (Math.random() * 4); // 0=Rect, 1=Stacked, 2=Segmented, 3=Hollow Line

            geoElements.add(new GeoElement(baseX, width, baseHeightPct,
                    colorTop, colorBottom, capColor, shapeType, depth));
        }
    }

    private void drawGeometricLayer(GuiGraphicsExtractor gui, float elapsed) {
        if (geoElements.isEmpty()) return;

        int screenH = this.height;
        int screenW = this.width;

        for (GeoElement geo : geoElements) {
            float baseX = geo.baseX * screenW;
            // Parallax scroll: Multiply base speed by element's depth
            float scrollPixels = (elapsed * GEO_SCROLL_SPEED * geo.depth);
            float x = (baseX + scrollPixels) % screenW;

            float osc = (float) (
                    Math.sin(elapsed * geo.freq1 + geo.phase1) * geo.amp1 +
                            Math.sin(elapsed * geo.freq2 + geo.phase2) * geo.amp2 +
                            Math.sin(elapsed * geo.freq3 + geo.phase3) * geo.amp3
            );
            float heightFraction = Math.clamp(geo.baseHeightPct * (1f + osc), 0.05f, 0.75f);
            int h = Math.round(screenH * heightFraction);
            int y = screenH - h;

            drawGeoElementAt(gui, x, y, geo.width, h, geo, elapsed);

            // Wrap-around
            if (x + geo.width > screenW) {
                drawGeoElementAt(gui, x - screenW, y, geo.width, h, geo, elapsed);
            }
        }
    }

    private void drawGeoElementAt(GuiGraphicsExtractor gui, float x, int y,
                                  int width, int height, GeoElement geo, float elapsed) {
        if (x + width < 0 || x > this.width || height <= 0) return;

        int ix = Math.round(x);
        // Unified elegant border radius
        int radius = Math.min(width / 3, 6);

        switch (geo.shapeType) {
            case 0 -> {
                // 1. Sleek Rounded Gradient with Subtly Glowing Border
                CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, ix, y, width, height,
                        radius, geo.colorTop, geo.colorBottom);
                // Add an ethereal outline
                CustomRoundedRectRenderer.drawRoundedOutline(gui, ix, y, width, height,
                        radius, multiplyAlpha(geo.colorTop, 1.8f), 1);
            }
            case 1 -> {
                // 2. Modern Capsule Stack (Floating Head)
                int upperH = Math.round(height * 0.25f);
                int gap = 4;
                int lowerH = height - upperH - gap;
                if (lowerH <= 0) return;

                int upperW = Math.round(width * 0.65f);
                int upperX = ix + (width - upperW) / 2;

                // Lower main body
                CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, ix, y + upperH + gap, width, lowerH,
                        radius, multiplyAlpha(geo.colorTop, 0.8f), geo.colorBottom);

                // Floating accent cap
                CustomRoundedRectRenderer.drawRoundedRect(gui, upperX, y, upperW, upperH,
                        Math.min(upperW/3, radius), geo.capColor);
            }
            case 2 -> {
                // 3. Segmented Equalizer Bars
                int segCount = 4;
                int segSpacing = 3;
                int totalSpacing = (segCount - 1) * segSpacing;
                int segH = Math.max(1, (height - totalSpacing) / segCount);

                for (int s = 0; s < segCount; s++) {
                    float segRatio = 1f - s * 0.18f; // Tapers toward the top
                    int segW = Math.max(2, Math.round(width * segRatio));
                    int segX = ix + (width - segW) / 2;
                    int segY = y + s * (segH + segSpacing);

                    float t = (float) s / segCount;
                    int segColor = lerpColor(geo.colorTop, geo.colorBottom, t);
                    int r = Math.min(segW / 3, radius);

                    CustomRoundedRectRenderer.drawRoundedRect(gui, segX, segY, segW, segH, r, segColor);
                }
            }
            case 3 -> {
                // 4. Hollow Wireframe Rect (Purely Outlined, highly modern UI touch)
                int outlineColor = multiplyAlpha(geo.colorTop, 1.2f);
                int innerFill = multiplyAlpha(geo.colorTop, 0.15f); // Very faint fill

                CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, ix, y, width, height,
                        radius, innerFill, 0x00000000);
                CustomRoundedRectRenderer.drawRoundedOutline(gui, ix, y, width, height,
                        radius, outlineColor, 2);
            }
        }
    }

    // ========================
    // Background
    // ========================

    private void drawBackground(GuiGraphicsExtractor gui, float elapsed, float dt) {
        // Base fill
        CustomRectRenderer.drawRect(gui, 0, 0, this.width, this.height, BG_COLOR);

        // Geometric layer: animated pillars with height oscillation + horizontal scroll
        initGeoElements();
        drawGeometricLayer(gui, elapsed);

        // Cloud blobs (slowly drifting semi-transparent shapes)
        drawCloudBlob(gui, this.width * 0.15f, this.height * 0.35f,
                220, 160, 60, CLOUD_COLOR, elapsed * CLOUD_DRIFT_SPEED, 0.4f);
        drawCloudBlob(gui, this.width * 0.75f, this.height * 0.55f,
                280, 140, 50, CLOUD_COLOR_WARM, elapsed * CLOUD_DRIFT_SPEED * 0.7f + 1.5f, 0.6f);
        drawCloudBlob(gui, this.width * 0.5f, this.height * 0.2f,
                350, 130, 80, CLOUD_COLOR, elapsed * CLOUD_DRIFT_SPEED * 0.5f + 3.0f, 0.35f);
        drawCloudBlob(gui, this.width * 0.85f, this.height * 0.8f,
                200, 100, 45, CLOUD_COLOR_WARM, elapsed * CLOUD_DRIFT_SPEED * 0.8f + 0.8f, 0.5f);

        // Bottom glow gradient
        int glowHeight = (int) (this.height * 0.35f);
        CustomRectRenderer.drawRectVertGrad(gui,
                0, this.height - glowHeight, this.width, glowHeight,
                BOTTOM_GLOW_TOP, BOTTOM_GLOW_BOT);

        // Subtle noise-like overlay (sparse tiny dots for texture)
        int dotSeed = (int) (elapsed * 1000) % 10000;
        for (int i = 0; i < 60; i++) {
            int dx = ((i * 7919 + dotSeed * 31) % this.width);
            int dy = ((i * 6271 + dotSeed * 17) % this.height);
            int alpha = 8 + ((i * 137 + dotSeed) % 8);
            CustomRectRenderer.drawRect(gui, dx, dy, 1, 1,
                    (alpha << 24) | 0xFFFFFF);
        }
    }

    private void drawCloudBlob(GuiGraphicsExtractor gui, float baseX, float baseY,
                                int w, int h, int radius, int color, float phase, float driftAmp) {
        float ox = (float) Math.sin(phase) * 40f * driftAmp;
        float oy = (float) Math.cos(phase * 1.3f) * 25f * driftAmp;
        int x = Math.round(baseX + ox);
        int y = Math.round(baseY + oy);
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, w, h, radius, color);
    }

    // ========================
    // Title
    // ========================

    private void drawTitle(GuiGraphicsExtractor gui, float elapsed) {
        ensureFontsLoaded();
        GlyphFont titleFont = titleGlyphFont;
        GlyphFont subFont = subtitleGlyphFont;
        if (titleFont == null) return;

        String title = "Gemini";
        float titleWidth = computeSpacedWidth(titleFont, title);
        float titleX = (this.width - titleWidth) / 2f;

        // Position title at ~14% from top of screen
        int baseTitleY = (int) (this.height * 0.14f);

        // Entry animation: slide down + fade in
        float entryT = Math.clamp(elapsed / ENTRY_DURATION, 0f, 1f);
        float entryEased = easeOutCubic(entryT);
        int titleOffsetY = (int) ((1f - entryEased) * -40f);

        int titleY = baseTitleY + titleOffsetY;

        // ── Title glow: layered soft glow behind the text ──
        int[] glowAlphas = {35, 24, 15, 8, 3};
        int[] glowOffsets = {6, 4, 2, 1, 0};

        for (int i = 0; i < glowAlphas.length; i++) {
            int ga = (int) (glowAlphas[i] * entryEased);
            if (ga <= 0) continue;
            int gc = (ga << 24) | (TITLE_GLOW_COLOR & 0x00FFFFFF);
            int dx = glowOffsets[i];

            drawSpacedGlyphText(gui, titleFont, title, titleX + dx, titleY + dx, gc);
            drawSpacedGlyphText(gui, titleFont, title, titleX - dx, titleY - dx, gc);
            drawSpacedGlyphText(gui, titleFont, title, titleX + dx, titleY - dx, gc);
            drawSpacedGlyphText(gui, titleFont, title, titleX - dx, titleY + dx, gc);
        }

        // ── Shadow ──
        int shadowAlpha = (int) (60 * entryEased);
        if (shadowAlpha > 0) {
            drawSpacedGlyphText(gui, titleFont, title,
                    titleX + 2, titleY + 2,
                    (shadowAlpha << 24) | 0x000000);
        }

        // ── Main title: vertical gradient ──
        int topColor = multiplyAlpha(TITLE_GRADIENT_TOP, entryEased);
        int botColor = multiplyAlpha(TITLE_GRADIENT_BOT, entryEased);
        drawSpacedGlyphGradientText(gui, titleFont, title,
                titleX, titleY, topColor, botColor);

        // ── Subtitle ──
        if (subFont != null) {
            String subtitle = SUBTITLE_TEXT + " v" + MOD_VERSION;
            int subAlpha = (int) (176 * entryEased);
            if (subAlpha > 0) {
                float subWidth = CustomFontRenderer.stringWidth(subFont, subtitle);
                float subX = (this.width - subWidth) / 2f;
                int subtitleY = titleY + (int) titleFont.lineHeight + 12;
                int subColor = (subAlpha << 24) | (SUBTITLE_COLOR_ARGB & 0x00FFFFFF);
                CustomFontRenderer.drawString(gui, subFont, subtitle, subX, subtitleY, subColor);
            }
        }
    }

    // ── Spaced glyph text helpers ──

    private static void drawSpacedGlyphText(GuiGraphicsExtractor gui, GlyphFont font,
                                             String text, float x, float y, int color) {
        float cx = x;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            CustomFontRenderer.drawString(gui, font, ch, cx, y, color);
            cx += CustomFontRenderer.stringWidth(font, ch) + TITLE_SPACING;
            i += Character.charCount(cp);
        }
    }

    private static void drawSpacedGlyphGradientText(GuiGraphicsExtractor gui, GlyphFont font,
                                                     String text, float x, float y,
                                                     int topColor, int botColor) {
        float cx = x;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            CustomFontRenderer.drawGradientString(gui, font, ch, cx, y, topColor, botColor);
            cx += CustomFontRenderer.stringWidth(font, ch) + TITLE_SPACING;
            i += Character.charCount(cp);
        }
    }

    // ========================
    // Button Rendering
    // ========================

    private void drawButton(GuiGraphicsExtractor gui, Button btn, int index,
                            int mouseX, int mouseY, long now) {
        float hoverP = hoverProgress[index];
        float clickP = clickScale[index];
        float entryT = Math.clamp((now - screenOpenTime) / 1000f / ENTRY_DURATION, 0f, 1f);
        float entryEased = easeOutCubic(entryT);

        // Button entry animation: staggered slide in from right
        float staggerDelay = index * 0.08f;
        float btnEntry = Math.clamp((entryT - staggerDelay) / (1f - staggerDelay * 3), 0f, 1f);
        float btnEntryEased = easeOutBack(btnEntry);
        int btnOffsetX = (int) ((1f - btnEntryEased) * 60f);

        int bx = LEFT_MARGIN + btnOffsetX;
        int by = btn.y;

        // Click scale animation
        float scale = clickP;
        int scaledW = Math.round(BUTTON_WIDTH * scale);
        int scaledH = Math.round(BUTTON_HEIGHT * scale);
        int scaledX = bx + (BUTTON_WIDTH - scaledW) / 2;
        int scaledY = by + (BUTTON_HEIGHT - scaledH) / 2;

        // Button visibility
        if (btnEntryEased <= 0.01f) return;
        int btnAlpha = (int) (btnEntryEased * 255);
        if (btnAlpha <= 0) return;

        // ── Drop shadow ──
        GlowRenderer.drawDropShadowRoundedRect(gui,
                scaledX, scaledY, scaledW, scaledH,
                BUTTON_RADIUS, 3, 3, 4,
                multiplyAlpha(0xFF000000, btnAlpha / 255f * 0.5f));

        // ── Gradient fill ──
        int fillTopIdle = multiplyAlpha(BTN_FILL_TOP_IDLE, btnAlpha / 255f);
        int fillBotIdle = multiplyAlpha(BTN_FILL_BOT_IDLE, btnAlpha / 255f);
        int fillTopHover = multiplyAlpha(BTN_FILL_TOP_HOVER, btnAlpha / 255f);
        int fillBotHover = multiplyAlpha(BTN_FILL_BOT_HOVER, btnAlpha / 255f);

        int fillTop = lerpColor(fillTopIdle, fillTopHover, hoverP);
        int fillBot = lerpColor(fillBotIdle, fillBotHover, hoverP);

        CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui,
                scaledX, scaledY, scaledW, scaledH, BUTTON_RADIUS, fillTop, fillBot);

        // ── Shine sweep ──
        if (hoverP > 0.01f) {
            drawButtonShine(gui, scaledX, scaledY, scaledW, scaledH, now, hoverP);
        }

        // ── Hover ripple ──
        for (Ripple r : ripples) {
            if (r.buttonIndex == index) {
                drawButtonRipple(gui, r, scaledX, scaledY, scaledW, scaledH, now);
            }
        }

        // ── Border ──
        int borderIdle = multiplyAlpha(BTN_BORDER_IDLE, btnAlpha / 255f);
        int borderHover = multiplyAlpha(BTN_BORDER_HOVER, btnAlpha / 255f);
        int borderColor = lerpColor(borderIdle, borderHover, hoverP);

        CustomRoundedRectRenderer.drawRoundedOutline(gui,
                scaledX, scaledY, scaledW, scaledH, BUTTON_RADIUS, borderColor, 1);

        // ── Keyboard focus pulse ──
        if (index == focusedIndex) {
            float pulse = (float) (Math.sin(now / 400.0) * 0.5 + 0.5);
            int pulseColor = multiplyAlpha(FOCUS_PULSE_COLOR, pulse);
            CustomRoundedRectRenderer.drawRoundedOutline(gui,
                    scaledX - 1, scaledY - 1, scaledW + 2, scaledH + 2,
                    BUTTON_RADIUS + 1, pulseColor, 1);
        }

        // ── Icon ──
        int iconX = scaledX + ICON_LEFT_PAD;
        int iconY = scaledY + (scaledH - ICON_SIZE) / 2;
        int iconColor = (btnAlpha << 24) | 0xFFFFFF;
        gui.blit(RenderPipelines.GUI_TEXTURED, btn.icon,
                iconX, iconY, 0, 0, ICON_SIZE, ICON_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE, iconColor);

        // ── Label ──
        float textAlpha = btnAlpha / 255f;
        int textColor = multiplyAlpha(TEXT_COLOR, textAlpha);
        int shadowColor = multiplyAlpha(TEXT_SHADOW, textAlpha);

        int textX = scaledX + TEXT_LEFT_OFFSET;
        GlyphFont bFont = buttonGlyphFont;
        if (bFont != null) {
            float textY = scaledY + (scaledH - bFont.lineHeight) / 2f;

            // Text shadow
            if ((shadowColor >>> 24) != 0) {
                CustomFontRenderer.drawString(gui, bFont, btn.label, textX + 1, textY + 1, shadowColor);
            }
            // Main text
            CustomFontRenderer.drawString(gui, bFont, btn.label, textX, textY, textColor);

            // ── Shortcut hint ──
            GlyphFont sFont = shortcutGlyphFont;
            if (sFont != null) {
                int shortcutColor = multiplyAlpha(SHORTCUT_COLOR, textAlpha);
                if ((shortcutColor >>> 24) != 0) {
                    String shortcutText = "[" + btn.shortcut + "]";
                    float sw = CustomFontRenderer.stringWidth(sFont, shortcutText);
                    float sx = scaledX + scaledW - sw - SHORTCUT_RIGHT_PAD;
                    float sy = scaledY + (scaledH - sFont.lineHeight) / 2f;
                    CustomFontRenderer.drawString(gui, sFont, shortcutText, sx, sy, shortcutColor);
                }
            }
        }
    }

    // ── Shine sweep ──

    private void drawButtonShine(GuiGraphicsExtractor gui, int bx, int by, int bw, int bh,
                                  long now, float hoverP) {
        float periodSec = SHINE_PERIOD;
        float rawT = ((now / 1000f) % periodSec) / periodSec;
        float shineT = rawT / (SHINE_DURATION / SHINE_PERIOD);
        if (shineT > 1f) return;

        float centerX = bx + shineT * bw;
        float halfW = bw * 0.22f;

        int alpha = (int) (hoverP * 35);
        int shineLeft = Math.round(centerX - halfW);
        int shineMid = Math.round(centerX);
        int shineRight = Math.round(centerX + halfW);

        gui.enableScissor(bx, by, bx + bw, by + bh);
        try {
            // Left half: fade in
            int fadeInLeft = (alpha << 24) | 0xFFFFFF;
            int fadeInRight = ((int)(alpha * 0.7f) << 24) | 0xFFFFFF;
            if (shineMid > shineLeft) {
                CustomRectRenderer.drawRect4C(gui,
                        shineLeft, by, shineMid - shineLeft, bh,
                        fadeInLeft, fadeInRight, fadeInLeft, fadeInRight);
            }
            // Right half: fade out
            int fadeOutLeft = ((int)(alpha * 0.7f) << 24) | 0xFFFFFF;
            int fadeOutRight = 0x00FFFFFF;
            if (shineRight > shineMid) {
                CustomRectRenderer.drawRect4C(gui,
                        shineMid, by, shineRight - shineMid, bh,
                        fadeOutLeft, fadeOutRight, fadeOutLeft, fadeOutRight);
            }
        } finally {
            gui.disableScissor();
        }
    }

    // ── Button ripple ──

    private void drawButtonRipple(GuiGraphicsExtractor gui, Ripple r,
                                   int bx, int by, int bw, int bh, long now) {
        float age = (now - r.startMs) / 1000f;
        if (age > RIPPLE_DURATION) return;

        float progress = age / RIPPLE_DURATION;
        float maxRadius = Math.max(bw, bh) * 1.2f;
        float currentRadius = progress * maxRadius;
        int alpha = (int) ((1f - progress) * 40);

        if (alpha <= 0) return;

        int rippleColor = (alpha << 24) | 0xFFFFFF;
        int rSize = Math.round(currentRadius * 2);
        int rX = Math.round(r.originX - currentRadius);
        int rY = Math.round(r.originY - currentRadius);

        gui.enableScissor(bx, by, bx + bw, by + bh);
        try {
            CustomRoundedRectRenderer.drawRoundedRect(gui,
                    rX, rY, rSize, rSize,
                    Math.round(currentRadius), rippleColor);
        } finally {
            gui.disableScissor();
        }
    }

    // ========================
    // Particle System
    // ========================

    private void spawnParticle(boolean randomY) {
        if (particles.size() >= MAX_PARTICLES) return;

        float x = (float) (Math.random() * this.width);
        float y = randomY ? (float) (Math.random() * this.height) : this.height + 10;
        float vx = (float) (Math.random() - 0.5) * 8f;
        float vy = -(15f + (float) Math.random() * 25f);
        float maxLife = 1.5f + (float) Math.random() * PARTICLE_MAX_LIFE;

        int[] colors = {PARTICLE_COLOR_1, PARTICLE_COLOR_2, PARTICLE_COLOR_3};
        int color = colors[(int) (Math.random() * colors.length)];

        int size = 1 + (int) (Math.random() * 3);
        float startLife = (float) Math.random() * maxLife;

        Particle p = new Particle(x, y, vx, vy, maxLife, color, size);
        p.life = startLife; // Stagger initial life for natural look
        particles.add(p);
    }

    private void updateParticles(float dt) {
        // Age and move particles
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0) {
                particles.remove(i);
                continue;
            }

            float lifeRatio = p.life / p.maxLife;
            // Sine drift for organic motion
            float drift = (float) Math.sin((p.maxLife - p.life) * 2.5f + p.x * 0.1f) * 0.3f;
            p.x += (p.vx + drift) * dt;
            p.y += p.vy * dt;

            // Wrap around edges
            if (p.y < -20) p.y = this.height + 10;
            if (p.x < -20) p.x = this.width + 10;
            if (p.x > this.width + 20) p.x = -10;
        }

        // Maintain population
        int toSpawn = MAX_PARTICLES - particles.size();
        for (int i = 0; i < toSpawn; i++) {
            spawnParticle(false);
        }
    }

    private void drawParticles(GuiGraphicsExtractor gui) {
        for (Particle p : particles) {
            float lifeRatio = Math.clamp(p.life / p.maxLife, 0f, 1f);
            // Fade in at start, fade out at end
            float fadeAlpha;
            if (lifeRatio > 0.8f) fadeAlpha = (1f - lifeRatio) / 0.2f;
            else if (lifeRatio < 0.15f) fadeAlpha = lifeRatio / 0.15f;
            else fadeAlpha = 1f;

            int alpha = (int) (((p.color >>> 24) & 0xFF) * fadeAlpha);
            if (alpha <= 0) continue;

            int color = (alpha << 24) | (p.color & 0x00FFFFFF);
            int halfSize = p.size / 2;
            int px = Math.round(p.x) - halfSize;
            int py = Math.round(p.y) - halfSize;

            if (p.size <= 1) {
                CustomRectRenderer.drawRect(gui, px, py, 1, 1, color);
            } else {
                CustomRoundedRectRenderer.drawRoundedRect(gui,
                        px, py, p.size, p.size, p.size / 2, color);
            }
        }
    }

    // ========================
    // Mouse Trail
    // ========================

    private void updateMouseTrail(int mouseX, int mouseY, long now) {
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            mouseTrail.add(new TrailPoint(mouseX, mouseY, now));
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
        // Remove old points
        float trailLifeMs = TRAIL_LIFETIME * 1000f;
        mouseTrail.removeIf(p -> (now - p.birthMs) > trailLifeMs);
        // Limit size
        while (mouseTrail.size() > MAX_TRAIL_POINTS) {
            mouseTrail.removeFirst();
        }
    }

    private void drawMouseTrail(GuiGraphicsExtractor gui, long now) {
        float trailLifeMs = TRAIL_LIFETIME * 1000f;
        for (TrailPoint tp : mouseTrail) {
            float age = (now - tp.birthMs) / trailLifeMs;
            if (age > 1f) continue;

            float fade = 1f - age;
            float size = 2f + fade * 3f;
            int alpha = (int) (40 * fade);
            if (alpha <= 0) continue;

            int color = (alpha << 24) | 0xFFFFFF;
            int s = Math.round(size);
            CustomRoundedRectRenderer.drawRoundedRect(gui,
                    tp.x - s / 2, tp.y - s / 2, s, s, s / 2, color);
        }
    }

    // ========================
    // Input
    // ========================

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent mouse, boolean idk) {
        for (int i = 0; i < buttons.size(); i++) {
            Button btn = buttons.get(i);
            if (isMouseOver(mouse.x(), mouse.y(), btn)) {
                // Trigger click animation
                clickScale[i] = 0.92f;
                clickStartTime[i] = System.currentTimeMillis();
                focusedIndex = i;
                btn.action.run();
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
            else focusedIndex = (focusedIndex + dir + buttons.size()) % buttons.size();
            return true;
        }
        // Enter / Numpad Enter
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            if (focusedIndex >= 0 && focusedIndex < buttons.size()) {
                Button btn = buttons.get(focusedIndex);
                clickScale[focusedIndex] = 0.92f;
                clickStartTime[focusedIndex] = System.currentTimeMillis();
                btn.action.run();
                return true;
            }
        }
        // Shortcut keys (match uppercase ASCII value)
        for (int i = 0; i < buttons.size(); i++) {
            Button btn = buttons.get(i);
            int shortcutKey = Character.toUpperCase(btn.shortcut);
            if (key == shortcutKey) {
                clickScale[i] = 0.92f;
                clickStartTime[i] = System.currentTimeMillis();
                focusedIndex = i;
                btn.action.run();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ========================
    // Helpers
    // ========================

    private boolean isMouseOver(double mx, double my, Button btn) {
        return mx >= LEFT_MARGIN && mx <= LEFT_MARGIN + BUTTON_WIDTH
                && my >= btn.y && my <= btn.y + BUTTON_HEIGHT;
    }

    // ── Color utilities ──

    private static int lerpColor(int a, int b, float t) {
        float tp = Math.clamp(t, 0f, 1f);
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * tp) << 24)
                | (Math.round(ar + (br - ar) * tp) << 16)
                | (Math.round(ag + (bg - ag) * tp) << 8)
                | Math.round(ab + (bb - ab) * tp);
    }

    private static int multiplyAlpha(int color, float factor) {
        float fp = Math.clamp(factor, 0f, 1f);
        int a = Math.round(((color >>> 24) & 0xFF) * fp);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    // ── Easing functions ──

    private static float easeOutCubic(float t) {
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    // ── Click animation update (called each frame) ──
    // Note: click animation is handled implicitly in drawButton via clickScale[].
    // We update clickScale here in extractRenderState before drawButton.
    private void updateClickAnimations(long now) {
        for (int i = 0; i < buttons.size(); i++) {
            if (clickScale[i] < 1f) {
                float elapsed = (now - clickStartTime[i]) / 1000f;
                float t = Math.clamp(elapsed / CLICK_DURATION, 0f, 1f);
                clickScale[i] = 0.92f + (1f - 0.92f) * easeOutCubic(t);
                if (t >= 1f) clickScale[i] = 1f;
            }
        }
    }
}

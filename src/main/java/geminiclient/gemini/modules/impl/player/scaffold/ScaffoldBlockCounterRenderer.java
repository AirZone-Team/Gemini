package geminiclient.gemini.modules.impl.player.scaffold;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.modules.impl.player.Scaffold;
import geminiclient.gemini.modules.impl.visual.effectDisplay.Md3ShadowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Material Design 3 inventory indicator used by Scaffold's optional HUD. */
public final class ScaffoldBlockCounterRenderer {

    private ScaffoldBlockCounterRenderer() {}

    private static final int MIN_CARD_WIDTH = 158;
    private static final int MAX_CARD_WIDTH = 220;
    private static final int CARD_HEIGHT = 42;
    private static final int RADIUS = 12;
    private static final int PAD = 9;
    private static final int ICON_SIZE = 24;
    private static final int TEXT_GAP = 8;
    private static final int PROGRESS_HEIGHT = 3;

    // MD3 light colour tokens, aligned with MaterialEffectRenderer.
    private static final int SURFACE = 0xFFF5F4F8;
    private static final int ON_SURFACE = 0xFF26242E;
    private static final int PRIMARY = 0xFF6750A4;
    private static final int WARNING = 0xFFF57C00;
    private static final int ERROR = 0xFFBA1A1A;

    private static final Identifier GOOGLE_SANS =
            Identifier.fromNamespaceAndPath("gemini", "font/googlesans-regular.ttf");

    private static volatile CustomFontRenderer.GlyphFont titleFont;
    private static volatile CustomFontRenderer.GlyphFont bodyFont;
    private static volatile boolean fontLoadFailed;

    public static void render(GuiGraphicsExtractor gui, Scaffold module,
                              int count, ItemStack displayStack, String displayName,
                              int lowThreshold, boolean shadow) {
        int width = cardWidth(displayName);
        int x = cardX(module, width);
        int y = module.hudY;
        drawCard(gui, x, y, width, count, displayStack, displayName,
                lowThreshold, shadow);
        Gemini.hudDragManager.registerDragRegion(
                module, x, y, width, CARD_HEIGHT);
    }

    public static void renderOutline(GuiGraphicsExtractor gui, Scaffold module,
                                     String displayName) {
        int width = cardWidth(displayName);
        int x = cardX(module, width);
        int y = module.hudY;
        CustomRoundedRectRenderer.drawRoundedOutline(
                gui, x, y, width, CARD_HEIGHT, RADIUS, 0xAAFFD700, 2);
        Gemini.hudDragManager.registerDragRegion(
                module, x, y, width, CARD_HEIGHT);
    }

    private static void drawCard(GuiGraphicsExtractor gui, int x, int y, int width,
                                 int count, ItemStack displayStack, String displayName,
                                 int lowThreshold, boolean shadow) {
        int accent = count == 0 ? ERROR : count <= lowThreshold ? WARNING : PRIMARY;
        int tonalContainer = mixRgb(accent, SURFACE, 0.78f);
        int track = mixRgb(SURFACE, 0xFF000000, 0.10f);

        if (shadow) {
            Md3ShadowRenderer.drawElevationShadow(gui, x, y, width, CARD_HEIGHT,
                    RADIUS, Md3ShadowRenderer.MAX_STRENGTH);
        }

        CustomRoundedRectRenderer.drawRoundedRect(
                gui, x, y, width, CARD_HEIGHT, RADIUS, SURFACE);
        CustomRoundedRectRenderer.drawRoundedOutline(
                gui, x, y, width, CARD_HEIGHT, RADIUS,
                mixRgb(SURFACE, 0xFF000000, 0.12f), 1);
        CustomRoundedRectRenderer.drawRoundedRect(
                gui, x + RADIUS, y + 1, width - RADIUS * 2, 1, 1, 0x66FFFFFF);

        int iconX = x + PAD;
        int iconY = y + 7;
        CustomRoundedRectRenderer.drawRoundedRect(
                gui, iconX, iconY, ICON_SIZE, ICON_SIZE, ICON_SIZE / 2, tonalContainer);

        if (displayStack != null && !displayStack.isEmpty()) {
            gui.item(displayStack, iconX + 4, iconY + 4);
        } else {
            drawFallbackBlockGlyph(gui, iconX, iconY, accent);
        }

        float textX = iconX + ICON_SIZE + TEXT_GAP;
        float titleMaxWidth = width - PAD * 2 - ICON_SIZE - TEXT_GAP;
        String title = fitText(displayName, titleMaxWidth);
        String body = count == 0 ? "0  No blocks" : count + (count <= lowThreshold ? "  Low stock" : "  Available");
        drawText(gui, true, title, textX, y + 7.5f, ON_SURFACE);
        drawText(gui, false, body, textX, y + 20.5f, accent);

        int progressX = x + PAD;
        int progressY = y + CARD_HEIGHT - 6;
        int progressWidth = width - PAD * 2;
        CustomRoundedRectRenderer.drawRoundedRect(
                gui, progressX, progressY, progressWidth, PROGRESS_HEIGHT,
                PROGRESS_HEIGHT / 2, track);

        float reference = Math.max(1.0f, lowThreshold * 4.0f);
        int fillWidth = Math.min(progressWidth, Math.round(progressWidth * (count / reference)));
        if (fillWidth > 0) {
            CustomRoundedRectRenderer.drawRoundedRect(
                    gui, progressX, progressY, fillWidth, PROGRESS_HEIGHT,
                    PROGRESS_HEIGHT / 2, accent);
        }
    }

    private static void drawFallbackBlockGlyph(GuiGraphicsExtractor gui,
                                               int iconX, int iconY, int accent) {
        int cubeSize = 10;
        int cubeX = iconX + (ICON_SIZE - cubeSize) / 2;
        int cubeY = iconY + (ICON_SIZE - cubeSize) / 2;
        CustomRoundedRectRenderer.drawRoundedRect(
                gui, cubeX, cubeY, cubeSize, cubeSize, 2, accent);
        CustomRoundedRectRenderer.drawRoundedRect(
                gui, cubeX + 2, cubeY + 2, cubeSize - 4, 2, 1,
                mixRgb(accent, 0xFFFFFFFF, 0.35f));
    }

    private static int cardWidth(String displayName) {
        String name = displayName == null || displayName.isBlank()
                ? "Building blocks" : displayName;
        int contentWidth = PAD + ICON_SIZE + TEXT_GAP
                + (int) Math.ceil(textWidth(name)) + PAD;
        return Math.max(MIN_CARD_WIDTH, Math.min(MAX_CARD_WIDTH, contentWidth));
    }

    private static String fitText(String text, float maxWidth) {
        String value = text == null || text.isBlank() ? "Building blocks" : text;
        if (textWidth(value) <= maxWidth) return value;

        String suffix = "…";
        int end = value.length();
        while (end > 0 && textWidth(value.substring(0, end) + suffix) > maxWidth) {
            end--;
        }
        return end == 0 ? suffix : value.substring(0, end) + suffix;
    }

    private static float textWidth(String text) {
        CustomFontRenderer.GlyphFont font = titleFont();
        return font != null
                ? CustomFontRenderer.stringWidth(font, text)
                : Minecraft.getInstance().font.width(text);
    }

    private static int cardX(Scaffold module, int width) {
        return Gemini.hudDragManager.isOnRightSide(module)
                ? module.hudX - width
                : module.hudX;
    }

    private static void drawText(GuiGraphicsExtractor gui, boolean title,
                                 String text, float x, float y, int color) {
        CustomFontRenderer.GlyphFont font = title ? titleFont() : bodyFont();
        if (font != null) {
            CustomFontRenderer.drawString(gui, font, text, x, y, color);
            return;
        }

        if (title) {
            gui.text(Minecraft.getInstance().font,
                    Component.literal(text).withStyle(style -> style.withBold(true)),
                    (int) x, (int) y, color, false);
        } else {
            gui.text(Minecraft.getInstance().font, text,
                    (int) x, (int) y, color, false);
        }
    }

    private static CustomFontRenderer.@Nullable GlyphFont titleFont() {
        if (fontLoadFailed) return null;
        if (titleFont == null) {
            try {
                titleFont = CustomFontRenderer.loadFont(
                        GOOGLE_SANS, 8.5f, java.awt.Font.BOLD);
            } catch (Exception ignored) {
                fontLoadFailed = true;
            }
        }
        return titleFont;
    }

    private static CustomFontRenderer.@Nullable GlyphFont bodyFont() {
        if (fontLoadFailed) return null;
        if (bodyFont == null) {
            try {
                bodyFont = CustomFontRenderer.loadFont(GOOGLE_SANS, 8.0f);
            } catch (Exception ignored) {
                fontLoadFailed = true;
            }
        }
        return bodyFont;
    }

    private static int mixRgb(int argbA, int argbB, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int r = (int) (((argbA >> 16) & 0xFF) * (1f - t) + ((argbB >> 16) & 0xFF) * t);
        int g = (int) (((argbA >> 8) & 0xFF) * (1f - t) + ((argbB >> 8) & 0xFF) * t);
        int b = (int) ((argbA & 0xFF) * (1f - t) + (argbB & 0xFF) * t);
        return (argbA & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}

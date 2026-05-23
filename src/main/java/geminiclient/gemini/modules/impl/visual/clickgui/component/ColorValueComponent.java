package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.values.impl.ColorValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class ColorValueComponent extends ValueComponent {

    private static final int BASE_BG = new Color(18, 18, 18, 230).getRGB();
    private static final int HOVER_BG = new Color(30, 30, 30, 230).getRGB();
    private static final int TEXT_COLOR = Color.WHITE.getRGB();

    private static final int[] PRESET_COLORS = {
        0xFFF5F5F5, // White
        0xFF43E096, // Green
        0xFFFF5B5B, // Red
        0xFF3B82F6, // Blue
        0xFFF59E0B, // Orange
        0xFFA855F7, // Purple
        0xFFEC4899, // Pink
        0xFF06B6D4, // Cyan
        0xFFFFD700, // Gold
        0xFF00FF00, // Lime
        0xFFFF0000, // Pure Red
        0xFF0000FF, // Pure Blue
    };

    private int presetIndex = -1;

    public ColorValueComponent(ColorValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16);
        findCurrentPreset(value.getColor());
    }

    private void findCurrentPreset(int color) {
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            if (PRESET_COLORS[i] == color) {
                presetIndex = i;
                return;
            }
        }
        presetIndex = -1;
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        ColorValue colorValue = (ColorValue) this.value;

        int bgColor = isHovered(mouseX, mouseY) ? HOVER_BG : BASE_BG;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        guiGraphics.text(mc.font, colorValue.getName(), x + 3, y + 3, TEXT_COLOR, true);

        int swatchSize = height - 6;
        int swatchX = x + width - swatchSize - 3;
        int swatchY = y + 3;

        guiGraphics.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, colorValue.getColor());

        // Hex text between name and swatch
        String hex = String.format("#%06X", colorValue.getRGB());
        int hexWidth = mc.font.width(hex);
        guiGraphics.text(mc.font, hex, swatchX - hexWidth - 4, y + 3, TEXT_COLOR, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ColorValue colorValue = (ColorValue) this.value;

        if (!isHovered(mouseX, mouseY)) return false;

        if (button == 0) {
            presetIndex = (presetIndex + 1) % PRESET_COLORS.length;
            colorValue.setColor(PRESET_COLORS[presetIndex]);
            return true;
        } else if (button == 1) {
            presetIndex = (presetIndex - 1 + PRESET_COLORS.length) % PRESET_COLORS.length;
            colorValue.setColor(PRESET_COLORS[presetIndex]);
            return true;
        }
        return false;
    }
}

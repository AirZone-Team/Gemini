package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.modules.impl.visual.clickgui.ClassicTheme;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.utils.animation.SpringAnimation;
import geminiclient.gemini.values.impl.ColorValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class ColorValueComponent extends ValueComponent {

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

    private final SpringAnimation hoverSpring = SpringAnimation.smooth();

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

        boolean hovered = isHovered(mouseX, mouseY);
        hoverSpring.setTarget(hovered ? 1.0f : 0.0f);
        hoverSpring.update(partialTicks);
        float hoverT = hoverSpring.getValue();

        ClassicTheme.drawRow(guiGraphics, x, y, width, height, hoverT);

        guiGraphics.text(mc.font, colorValue.getName(), x + 7, y + 4, ClassicTheme.TEXT, true);

        // Rounded swatch with a hairline border
        int swatchSize = height - 6;
        int swatchX = x + width - swatchSize - 6;
        int swatchY = y + 3;
        CustomRoundedRectRenderer.drawRoundedRect(guiGraphics, swatchX, swatchY,
                swatchSize, swatchSize, 3, colorValue.getColor());
        CustomRoundedRectRenderer.drawRoundedOutline(guiGraphics, swatchX, swatchY,
                swatchSize, swatchSize, 3, ClassicTheme.BORDER, 1);

        // Hex text between name and swatch
        String hex = String.format("#%06X", colorValue.getRGB());
        int hexWidth = mc.font.width(hex);
        guiGraphics.text(mc.font, hex, swatchX - hexWidth - 5, y + 4, ClassicTheme.TEXT_DIM, true);
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

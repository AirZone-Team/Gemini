package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.values.impl.BoolValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class BoolValueComponent extends ValueComponent {

    // 统一的颜色主题 - 使用黑灰色调
    private static final int ACCENT_COLOR = new Color(220, 220, 220).getRGB(); // 亮灰色作为强调色
    private static final int BASE_BG = new Color(18, 18, 18, 230).getRGB();
    private static final int HOVER_BG = new Color(30, 30, 30, 230).getRGB();
    private static final int TEXT_COLOR = Color.WHITE.getRGB();

    public BoolValueComponent(BoolValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16);
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        BoolValue boolValue = (BoolValue) this.value;

        int bgColor = isHovered(mouseX, mouseY) ? HOVER_BG : BASE_BG;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        guiGraphics.text(mc.font, boolValue.getName(), x + 3, y + 3, TEXT_COLOR, true);

        int switchWidth = 18;
        int switchHeight = height - 6;
        int switchX = x + width - switchWidth - 3;
        int switchY = y + 3;

        int trackColor = boolValue.enabled ? ACCENT_COLOR : new Color(50, 50, 50).getRGB();

        guiGraphics.fill(switchX, switchY, switchX + switchWidth, switchY + switchHeight, trackColor);

        int handleSize = switchHeight - 2;
        int handleY = switchY + 1;
        int handleX;

        if (boolValue.enabled) {
            handleX = switchX + switchWidth - handleSize - 1;
        } else {
            handleX = switchX + 1;
        }

        guiGraphics.fill(handleX, handleY, handleX + handleSize, handleY + handleSize, TEXT_COLOR);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        BoolValue boolValue = (BoolValue) this.value;

        if (isHovered(mouseX, mouseY) && button == 0) {
            boolValue.enabled = !boolValue.enabled;
            return true;
        }
        return false;
    }
}
package geminiclient.gemini.base.clickgui.component;

import geminiclient.gemini.values.impl.BoolValue;
import net.minecraft.client.gui.GuiGraphics;
import java.awt.Color;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class BoolValueComponent extends ValueComponent {

    public BoolValueComponent(BoolValue value, int x, int y, int width, int height) {
        // 使用 BoolValue 构造函数，并传入所需的默认尺寸
        super(value, x, y, width, 14);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        BoolValue boolValue = (BoolValue) this.value;

        // 1. 渲染背景
        int bgColor = new Color(20, 20, 20, 180).getRGB();
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        // 2. 渲染值名称
        int textColor = isHovered(mouseX, mouseY) ? Color.CYAN.getRGB() : Color.WHITE.getRGB();
        guiGraphics.drawString(mc.font, boolValue.getName(), x + 2, y + 3, textColor, true);

        // 3. 渲染开关/复选框
        int boxSize = height - 4; // 例如 10 像素大小
        int boxX = x + width - boxSize - 2;
        int boxY = y + 2;

        // 渲染外框
        guiGraphics.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, Color.DARK_GRAY.getRGB());

        // 渲染开关状态 (如果启用，则填充颜色)
        if (boolValue.enabled) {
            guiGraphics.fill(boxX + 1, boxY + 1, boxX + boxSize - 1, boxY + boxSize - 1, Color.GREEN.getRGB());
        } else {
            // 填充一个小空心
            guiGraphics.fill(boxX + 1, boxY + 1, boxX + boxSize - 1, boxY + boxSize - 1, new Color(50, 50, 50).getRGB());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        BoolValue boolValue = (BoolValue) this.value;

        // 如果鼠标在组件内点击，并且是左键
        if (isHovered(mouseX, mouseY) && button == 0) {
            // 切换布尔值状态
            boolValue.enabled = !boolValue.enabled;
            return true;
        }
        return false;
    }
}
package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.values.impl.BoolValue;
import net.minecraft.client.gui.GuiGraphics;
import java.awt.Color;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class BoolValueComponent extends ValueComponent {

    // 统一的颜色主题
    private static final int ACCENT_COLOR = new Color(230, 70, 180).getRGB(); // 调整: 略暗洋红色
    private static final int BASE_BG = new Color(18, 18, 18, 230).getRGB(); // 优化: 略不那么黑，透明度高
    private static final int HOVER_BG = new Color(30, 30, 30, 230).getRGB(); // 优化: 略浅的半透明黑
    private static final int TEXT_COLOR = Color.WHITE.getRGB();

    public BoolValueComponent(BoolValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        BoolValue boolValue = (BoolValue) this.value;

        // 1. 渲染背景 (悬停时有轻微变化)
        int bgColor = isHovered(mouseX, mouseY) ? HOVER_BG : BASE_BG;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        // 2. 渲染值名称
        guiGraphics.drawString(mc.font, boolValue.getName(), x + 3, y + 3, TEXT_COLOR, true);

        // 3. 渲染开关 (现代开关样式)
        int switchWidth = 18;
        int switchHeight = height - 6;
        int switchX = x + width - switchWidth - 3;
        int switchY = y + 3;

        // 轨道颜色：启用时为主题色，禁用时为深灰色
        int trackColor = boolValue.enabled ? ACCENT_COLOR : new Color(50, 50, 50).getRGB();

        // 渲染轨道
        guiGraphics.fill(switchX, switchY, switchX + switchWidth, switchY + switchHeight, trackColor);

        // 渲染滑块/手柄
        int handleSize = switchHeight - 2;
        int handleY = switchY + 1;
        int handleX;

        if (boolValue.enabled) {
            // 启用：滑块在右侧
            handleX = switchX + switchWidth - handleSize - 1;
        } else {
            // 未启用：滑块在左侧
            handleX = switchX + 1;
        }

        // 渲染手柄 (白色小方块)
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
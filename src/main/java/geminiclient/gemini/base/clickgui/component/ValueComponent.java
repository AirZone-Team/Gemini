package geminiclient.gemini.base.clickgui.component;

import geminiclient.gemini.values.ValueParent;
import net.minecraft.client.gui.GuiGraphics;

public abstract class ValueComponent {

    protected final ValueParent value;
    public int x, y, width, height;

    public ValueComponent(ValueParent value, int x, int y, int width, int height) {
        this.value = value;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public ValueParent getValue() {
        return this.value;
    }

    // 【新增方法】检查组件是否可见
    public boolean isVisible() {
        // visibility 是一个 Supplier<Boolean>，调用 get() 获取实际布尔值
        // 这样可以实现动态可见性 (例如：只有当另一个 BoolValue 启用时才显示)
        return this.value.visibility.get();
    }

    /**
     * 渲染组件，包括值名称和控件。
     */
    public abstract void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks);

    /**
     * 处理鼠标点击事件。
     */
    public abstract boolean mouseClicked(double mouseX, double mouseY, int button);

    /**
     * 处理鼠标释放事件。
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // 默认实现，子类可以覆盖
        return false;
    }

    /**
     * 检查鼠标是否在组件区域内。
     */
    protected boolean isHovered(double mouseX, double mouseY) {
        // 注意：这里不需要检查 isVisible()，因为 ModuleComponent 会提前过滤
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
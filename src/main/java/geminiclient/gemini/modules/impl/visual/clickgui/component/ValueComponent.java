package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.values.ValueParent;
import net.minecraft.client.gui.GuiGraphicsExtractor;

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

    public boolean isVisible() {
        return this.value.visibility.get();
    }

    public abstract void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks);

    public abstract boolean mouseClicked(double mouseX, double mouseY, int button);

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    protected boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
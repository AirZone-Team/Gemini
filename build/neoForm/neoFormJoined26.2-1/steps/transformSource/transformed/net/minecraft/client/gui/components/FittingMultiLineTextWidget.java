package net.minecraft.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FittingMultiLineTextWidget extends AbstractTextAreaWidget {
    private final MultiLineTextWidget multilineWidget;

    public FittingMultiLineTextWidget(int x, int y, int width, int height, Component message, Font font) {
        super(x, y, width, height, message, AbstractScrollArea.defaultSettings(9));
        this.multilineWidget = new MultiLineTextWidget(message, font).setMaxWidth(this.getWidth() - this.totalInnerPadding());
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        this.multilineWidget.setMaxWidth(this.getWidth() - this.totalInnerPadding());
    }

    @Override
    protected int getInnerHeight() {
        return this.multilineWidget.getHeight();
    }

    public void minimizeHeight() {
        if (!this.showingScrollBar()) {
            this.setHeight(this.getInnerHeight() + this.totalInnerPadding());
        }
    }

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics) {
        super.extractBackground(graphics);
    }

    public boolean showingScrollBar() {
        return super.scrollable();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(this.getInnerLeft(), this.getInnerTop());
        this.multilineWidget.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.pose().popMatrix();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getMessage());
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        this.multilineWidget.setMessage(message);
    }
}

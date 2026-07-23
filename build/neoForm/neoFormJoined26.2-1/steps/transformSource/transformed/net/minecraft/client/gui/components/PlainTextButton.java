package net.minecraft.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PlainTextButton extends Button {
    private final Font font;
    private final Component message;
    private final Component underlinedMessage;

    public PlainTextButton(int x, int y, int width, int height, Component message, Button.OnPress onPress, Font font) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.font = font;
        this.message = message;
        this.underlinedMessage = ComponentUtils.mergeStyles(message, Style.EMPTY.withUnderlined(true));
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Component messageToRender = this.isHoveredOrFocused() ? this.underlinedMessage : this.message;
        graphics.text(this.font, messageToRender, this.getX(), this.getY(), 16777215 | Mth.ceil(this.alpha * 255.0F) << 24);
    }
}

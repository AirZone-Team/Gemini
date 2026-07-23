package net.minecraft.client.gui.components;

import net.minecraft.client.Options;
import net.minecraft.network.chat.CommonComponents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractOptionSliderButton extends AbstractSliderButton {
    protected final Options options;

    protected AbstractOptionSliderButton(Options options, int x, int y, int width, int height, double initialValue) {
        super(x, y, width, height, CommonComponents.EMPTY, initialValue);
        this.options = options;
    }
}

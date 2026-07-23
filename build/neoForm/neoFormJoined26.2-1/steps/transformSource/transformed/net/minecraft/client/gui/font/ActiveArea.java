package net.minecraft.client.gui.font;

import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface ActiveArea {
    Style style();

    float activeLeft();

    float activeTop();

    float activeRight();

    float activeBottom();
}

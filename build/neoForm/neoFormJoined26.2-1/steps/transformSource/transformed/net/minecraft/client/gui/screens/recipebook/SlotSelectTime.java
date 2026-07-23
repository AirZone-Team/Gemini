package net.minecraft.client.gui.screens.recipebook;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@FunctionalInterface
@OnlyIn(Dist.CLIENT)
public interface SlotSelectTime {
    int currentIndex();
}

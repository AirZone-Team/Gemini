package net.minecraft.client.gui.screens.options;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface HasDifficultyReaction {
    void onDifficultyChanged();
}

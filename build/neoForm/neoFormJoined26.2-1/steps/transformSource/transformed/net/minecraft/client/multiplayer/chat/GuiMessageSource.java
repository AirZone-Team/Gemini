package net.minecraft.client.multiplayer.chat;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum GuiMessageSource {
    PLAYER,
    SYSTEM_SERVER,
    SYSTEM_CLIENT;
}

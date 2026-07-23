package net.minecraft.world;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuConstructor;

import net.neoforged.neoforge.common.extensions.IMenuProviderExtension;

public interface MenuProvider extends MenuConstructor, IMenuProviderExtension {
    Component getDisplayName();
}

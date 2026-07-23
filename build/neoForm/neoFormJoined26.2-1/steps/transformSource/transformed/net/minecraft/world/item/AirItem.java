package net.minecraft.world.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class AirItem extends Item {
    public AirItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack itemStack) {
        return itemStack.typeHolder().components().getOrDefault(DataComponents.ITEM_NAME, CommonComponents.EMPTY);
    }
}

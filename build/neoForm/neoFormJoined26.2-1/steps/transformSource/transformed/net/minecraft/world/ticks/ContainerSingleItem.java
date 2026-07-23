package net.minecraft.world.ticks;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface ContainerSingleItem extends Container {
    ItemStack getTheItem();

    default ItemStack splitTheItem(int count) {
        return this.getTheItem().split(count);
    }

    void setTheItem(final ItemStack itemStack);

    default ItemStack removeTheItem() {
        return this.splitTheItem(this.getMaxStackSize());
    }

    @Override
    default int getContainerSize() {
        return 1;
    }

    @Override
    default boolean isEmpty() {
        return this.getTheItem().isEmpty();
    }

    @Override
    default void clearContent() {
        this.removeTheItem();
    }

    @Override
    default ItemStack removeItemNoUpdate(int slot) {
        return this.removeItem(slot, this.getMaxStackSize());
    }

    @Override
    default ItemStack getItem(int slot) {
        return slot == 0 ? this.getTheItem() : ItemStack.EMPTY;
    }

    @Override
    default ItemStack removeItem(int slot, int count) {
        return slot != 0 ? ItemStack.EMPTY : this.splitTheItem(count);
    }

    @Override
    default void setItem(int slot, ItemStack itemStack) {
        if (slot == 0) {
            this.setTheItem(itemStack);
        }
    }

    interface BlockContainerSingleItem extends ContainerSingleItem {
        BlockEntity getContainerBlockEntity();

        @Override
        default boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(this.getContainerBlockEntity(), player);
        }
    }
}

package net.minecraft.world.inventory;

import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class PlayerEnderChestContainer extends SimpleContainer {
    private @Nullable EnderChestBlockEntity activeChest;

    public PlayerEnderChestContainer() {
        super(27);
    }

    public void setActiveChest(EnderChestBlockEntity activeChest) {
        this.activeChest = activeChest;
    }

    public boolean isActiveChest(EnderChestBlockEntity chest) {
        return this.activeChest == chest;
    }

    public void fromSlots(ValueInput.TypedInputList<ItemStackWithSlot> list) {
        for (int i = 0; i < this.getContainerSize(); i++) {
            this.setItem(i, ItemStack.EMPTY);
        }

        for (ItemStackWithSlot item : list) {
            if (item.isValidInContainer(this.getContainerSize())) {
                this.setItem(item.slot(), item.stack());
            }
        }
    }

    public void storeAsSlots(ValueOutput.TypedOutputList<ItemStackWithSlot> output) {
        for (int i = 0; i < this.getContainerSize(); i++) {
            ItemStack itemStack = this.getItem(i);
            if (!itemStack.isEmpty()) {
                output.add(new ItemStackWithSlot(i, itemStack));
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.activeChest != null && !this.activeChest.stillValid(player) ? false : super.stillValid(player);
    }

    @Override
    public void startOpen(ContainerUser containerUser) {
        if (this.activeChest != null) {
            this.activeChest.startOpen(containerUser);
        }

        super.startOpen(containerUser);
    }

    @Override
    public void stopOpen(ContainerUser containerUser) {
        if (this.activeChest != null) {
            this.activeChest.stopOpen(containerUser);
        }

        super.stopOpen(containerUser);
        this.activeChest = null;
    }
}

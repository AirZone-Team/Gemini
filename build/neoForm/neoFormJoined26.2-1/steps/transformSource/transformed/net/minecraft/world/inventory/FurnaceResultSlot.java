package net.minecraft.world.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public class FurnaceResultSlot extends Slot {
    private final Player player;
    private int removeCount;

    public FurnaceResultSlot(Player player, Container container, int slot, int x, int y) {
        super(container, slot, x, y);
        this.player = player;
    }

    @Override
    public boolean mayPlace(ItemStack itemStack) {
        return false;
    }

    @Override
    public ItemStack remove(int amount) {
        if (this.hasItem()) {
            this.removeCount = this.removeCount + Math.min(amount, this.getItem().getCount());
        }

        return super.remove(amount);
    }

    @Override
    public void onTake(Player player, ItemStack carried) {
        this.checkTakeAchievements(carried);
        super.onTake(player, carried);
    }

    @Override
    protected void onQuickCraft(ItemStack picked, int count) {
        this.removeCount += count;
        this.checkTakeAchievements(picked);
    }

    @Override
    protected void checkTakeAchievements(ItemStack carried) {
        carried.onCraftedBy(this.player, this.removeCount);
        if (this.player instanceof ServerPlayer serverPlayer && this.container instanceof AbstractFurnaceBlockEntity abstractFurnaceBlockEntity) {
            abstractFurnaceBlockEntity.awardUsedRecipesAndPopExperience(serverPlayer);
        }

        if (this.removeCount != 0) {
            net.neoforged.neoforge.event.EventHooks.firePlayerSmeltedEvent(this.player, carried, this.removeCount);
        }
        this.removeCount = 0;
    }
}

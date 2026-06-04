package geminiclient.gemini.modules.impl.player.invmanager;

import geminiclient.gemini.base.MinecraftInstance;
import geminiclient.gemini.modules.impl.player.InvManager;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public class ExcessHandler implements MinecraftInstance {

    public static boolean handleExcess(InventoryActions actions,
                                        boolean hasBowSlot, int maxArrowSize,
                                        boolean hasBlockSlot, int maxBlockSize,
                                        boolean keepProjectile, int maxProjectileSize) {
        if (hasBowSlot && InvUtils.getItemCount(net.minecraft.world.item.Items.ARROW) > maxArrowSize) {
            ItemStack worstArrow = InvUtils.getWorstArrow();
            if (worstArrow != null && actions.throwItem(worstArrow)) return true;
        }
        if (hasBlockSlot && InvUtils.getBlockCountInInventory() > maxBlockSize) {
            ItemStack worstBlock = InvUtils.getWorstBlock();
            if (worstBlock != null && actions.throwItem(worstBlock)) return true;
        }
        if (keepProjectile) {
            int projCount = InvUtils.getItemCount(net.minecraft.world.item.Items.EGG)
                    + InvUtils.getItemCount(net.minecraft.world.item.Items.SNOWBALL);
            if (projCount > maxProjectileSize) {
                ItemStack worstProj = InvUtils.getWorstProjectile();
                if (worstProj != null && actions.throwItem(worstProj)) return true;
            }
        }
        return false;
    }

    public static boolean throwJunk(InventoryActions actions, InvManager manager) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (!manager.isItemUseful(stack)) {
                actions.clickSlot(InventoryActions.toContainerSlot(i), 1, ContainerInput.THROW);
                actions.markAction();
                return true;
            }
        }
        return false;
    }
}

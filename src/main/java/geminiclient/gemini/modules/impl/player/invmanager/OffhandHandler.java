package geminiclient.gemini.modules.impl.player.invmanager;

import geminiclient.gemini.base.MinecraftInstance;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class OffhandHandler implements MinecraftInstance {

    public static boolean handle(ListValue offhandMode, InventoryActions actions) {
        String mode = offhandMode.get();
        if ("None".equals(mode)) return false;

        ItemStack offHand = mc.player.getOffhandItem();

        return switch (mode) {
            case "Golden Apple" -> handleGoldenApple(offHand, actions);
            case "Projectile" -> handleProjectile(offHand, actions);
            case "Fishing Rod" -> handleFishingRod(offHand, actions);
            case "Block" -> handleBlock(offHand, actions);
            default -> false;
        };
    }

    private static boolean handleGoldenApple(ItemStack offHand, InventoryActions actions) {
        int slot = InvUtils.getItemSlot(Items.GOLDEN_APPLE);
        if (slot == -1 || !actions.isReady()) return false;

        if (offHand.getItem() == Items.GOLDEN_APPLE) {
            ItemStack gaStack = mc.player.getInventory().getItem(slot);
            if (offHand.getCount() + gaStack.getCount() <= 64) {
                return actions.startOffhandStack(slot);
            }
        } else {
            return actions.swapOffHand(slot);
        }
        return false;
    }

    private static boolean handleProjectile(ItemStack offHand, InventoryActions actions) {
        ItemStack bestProjectile = InvUtils.getBestProjectile();
        if (bestProjectile == null) return false;

        int slot = InvUtils.getItemStackSlot(bestProjectile);
        boolean shouldSwap = offHand.getItem() != Items.EGG && offHand.getItem() != Items.SNOWBALL
                || offHand.getCount() < bestProjectile.getCount();
        if (shouldSwap && slot != -1 && actions.isReady()) {
            return actions.swapOffHand(slot);
        }
        return false;
    }

    private static boolean handleFishingRod(ItemStack offHand, InventoryActions actions) {
        int slot = InvUtils.getItemSlot(Items.FISHING_ROD);
        if (slot != -1 && actions.isReady() && offHand.getItem() != Items.FISHING_ROD) {
            return actions.swapOffHand(slot);
        }
        return false;
    }

    private static boolean handleBlock(ItemStack offHand, InventoryActions actions) {
        ItemStack bestBlock = InvUtils.getBestBlock();
        if (bestBlock == null) return false;

        int slot = InvUtils.getItemStackSlot(bestBlock);
        boolean shouldSwap = !InvUtils.isValidStack(offHand)
                || offHand.getCount() < bestBlock.getCount();
        if (shouldSwap && slot != -1 && actions.isReady()) {
            return actions.swapOffHand(slot);
        }
        return false;
    }
}

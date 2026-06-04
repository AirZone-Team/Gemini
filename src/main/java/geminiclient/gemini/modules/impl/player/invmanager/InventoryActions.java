package geminiclient.gemini.modules.impl.player.invmanager;

import geminiclient.gemini.base.MinecraftInstance;
import geminiclient.gemini.utils.MathHelper;
import geminiclient.gemini.utils.TimerUtils;
import geminiclient.gemini.values.impl.IntRangeValue;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class InventoryActions implements MinecraftInstance {

    private final TimerUtils timer = new TimerUtils();
    private final IntRangeValue delay;
    public boolean inventoryOpen = false;
    public boolean clickOffHand = false;

    public InventoryActions(IntRangeValue delay) {
        this.delay = delay;
    }

    public boolean isReady() {
        return timer.hasTimeElapsed(
                (long) MathHelper.getRandom(delay.getMinValue(), delay.getMaxValue()), false);
    }

    public void markAction() {
        this.inventoryOpen = true;
        timer.reset();
    }

    // ==================== Slot Slot Mapping ====================

    public static int toContainerSlot(int inventoryIndex) {
        if (inventoryIndex < 9) return inventoryIndex + 36;
        if (inventoryIndex < 36) return inventoryIndex;
        if (inventoryIndex < 40) return 8 - (inventoryIndex - 36);
        if (inventoryIndex == 40) return 45;
        return inventoryIndex;
    }

    // ==================== Raw Click ====================

    public void clickSlot(int slotNum, int buttonNum, ContainerInput containerInput) {
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
                slotNum, buttonNum, containerInput, mc.player);
    }

    // ==================== Offhand Stacking (two-tick) ====================

    public boolean startOffhandStack(int slot) {
        if (!isReady()) return false;
        clickSlot(toContainerSlot(slot), 0, ContainerInput.PICKUP);
        this.inventoryOpen = true;
        this.clickOffHand = true;
        timer.reset();
        return true;
    }

    public boolean finishOffhandPickup() {
        if (!clickOffHand || !isReady()) return false;
        clickSlot(45, 0, ContainerInput.PICKUP);
        this.inventoryOpen = true;
        this.clickOffHand = false;
        timer.reset();
        return true;
    }

    // ==================== Swap Operations ====================

    public boolean swapOffHand(int slot) {
        if (!isReady()) return false;
        clickSlot(toContainerSlot(slot), 40, ContainerInput.SWAP);
        markAction();
        return true;
    }

    public boolean swapItem(int targetSlot, ItemStack bestItem) {
        if (bestItem == null) return false;
        ItemStack currentSlot = mc.player.getInventory().getItem(targetSlot);
        if (bestItem == currentSlot) return false;
        if (!isReady()) return false;

        int bestItemSlot = InvUtils.getItemStackSlot(bestItem);
        if (bestItemSlot != -1) {
            clickSlot(toContainerSlot(bestItemSlot), targetSlot, ContainerInput.SWAP);
            markAction();
            return true;
        }
        return false;
    }

    public boolean swapItem(int targetSlot, Item item) {
        ItemStack currentSlot = mc.player.getInventory().getItem(targetSlot);
        if (!isReady()) return false;

        int bestItemSlot = findBestSlotForItem(item);
        if (bestItemSlot != -1 && bestItemSlot != targetSlot) {
            ItemStack bestItemStack = mc.player.getInventory().getItem(bestItemSlot);
            if (currentSlot.getItem() != item || currentSlot.getCount() < bestItemStack.getCount()) {
                clickSlot(toContainerSlot(bestItemSlot), targetSlot, ContainerInput.SWAP);
                markAction();
                return true;
            }
        }
        return false;
    }

    // ==================== Throw ====================

    public boolean throwItem(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        if (!isReady()) return false;

        int itemSlot = InvUtils.getItemStackSlot(item);
        if (itemSlot != -1) {
            clickSlot(toContainerSlot(itemSlot), 1, ContainerInput.THROW);
            markAction();
            return true;
        }
        return false;
    }

    // ==================== Utility ====================

    public int findBestSlotForItem(Item item) {
        int bestSlot = -1;
        int bestCount = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == item && stack.getCount() > bestCount) {
                bestCount = stack.getCount();
                bestSlot = i;
            }
        }
        return bestSlot;
    }
}

package geminiclient.gemini.modules.impl.player.invmanager;

import geminiclient.gemini.base.MinecraftInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

public class AutoArmorHandler implements MinecraftInstance {

    public static boolean handle(InventoryActions actions) {
        EquipmentSlot[] armorSlots = {EquipmentSlot.FEET, EquipmentSlot.LEGS,
                EquipmentSlot.CHEST, EquipmentSlot.HEAD};

        for (int i = 0; i < 4; i++) {
            ItemStack stack = mc.player.getItemBySlot(armorSlots[i]);
            if (!stack.is(ItemTags.ARMOR_ENCHANTABLE)) continue;

            Equippable equipment = stack.get(DataComponents.EQUIPPABLE);
            if (equipment == null) continue;

            if (!stack.isEmpty()
                    && actions.isReady()
                    && InvUtils.getBestArmorScore(equipment.slot()) > InvUtils.getProtection(stack)) {
                actions.clickSlot(8 - i, 1, ContainerInput.THROW);
                actions.markAction();
                return true;
            }
        }

        for (int ix = 0; ix < 36; ix++) {
            ItemStack stack = mc.player.getInventory().getItem(ix);
            if (stack.isEmpty() || !stack.is(ItemTags.ARMOR_ENCHANTABLE)) continue;

            float currentItemScore = InvUtils.getProtection(stack);
            Equippable equipment = stack.get(DataComponents.EQUIPPABLE);
            if (equipment == null) continue;

            if (currentItemScore > InvUtils.getCurrentArmorScore(equipment.slot())
                    && currentItemScore == InvUtils.getBestArmorScore(equipment.slot())
                    && actions.isReady()) {
                actions.clickSlot(InventoryActions.toContainerSlot(ix), 0, ContainerInput.QUICK_MOVE);
                actions.markAction();
                return true;
            }
        }
        return false;
    }
}

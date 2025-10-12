package geminiclient.gemini.utils;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Optional;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class InventoryUtils {
    public static boolean isGodItem(ItemStack stack) {
        RegistryAccess drm =
                mc.level.registryAccess();
        Registry<Enchantment> registry = drm.freeze().lookupOrThrow(Registries.ENCHANTMENT);

        Optional<Holder.Reference<Enchantment>> protection =
                registry.get(Enchantments.SHARPNESS);

        Optional<Holder.Reference<Enchantment>> protection1 =
                registry.get(Enchantments.KNOCKBACK);
        if (stack.isEmpty()) {
            return false;
        } else if (stack.getItem() instanceof AxeItem
                && stack.getItem() == Items.GOLDEN_AXE
                && protection.map(e -> EnchantmentHelper.getItemEnchantmentLevel(e,stack)).orElse(0) > 100) {
            return true;
        } else if (stack.getItem() == Items.SLIME_BALL && protection1.map(e -> EnchantmentHelper.getItemEnchantmentLevel(e,stack)).orElse(0) > 1) {
            return true;
        } else {
            return stack.getItem() == Items.TOTEM_OF_UNDYING || stack.getItem() == Items.END_CRYSTAL;
        }
    }
}

package geminiclient.gemini.utils;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class InventoryUtils {
    /**
     * 检查给定的 ItemStack 是否符合“God Item”标准（非标准附魔或关键生存物品）。
     */
    public static boolean isGodItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        // 获取附魔注册表查找器
        HolderLookup.RegistryLookup<Enchantment> enchantmentLookup = 
                mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        // 尝试获取 Sharpness 和 Knockback 的 Holder
        Holder<Enchantment> sharpnessHolder;
        Holder<Enchantment> knockbackHolder;
        
        try {
            sharpnessHolder = enchantmentLookup.getOrThrow(Enchantments.SHARPNESS);
            knockbackHolder = enchantmentLookup.getOrThrow(Enchantments.KNOCKBACK);
        } catch (Exception e) {
            // 如果查找失败，返回 false
            return false; 
        }

        // 1. 金斧/锋利神装检查 (Sharpness > 100)
        if (stack.getItem() == Items.GOLDEN_AXE) {
            int sharpnessLevel = EnchantmentHelper.getItemEnchantmentLevel(sharpnessHolder, stack);
            if (sharpnessLevel > 100) {
                return true;
            }
        } 
        
        // 2. 粘液球/击退神装检查 (Knockback > 1)
        if (stack.getItem() == Items.SLIME_BALL) {
            int knockbackLevel = EnchantmentHelper.getItemEnchantmentLevel(knockbackHolder, stack);
            if (knockbackLevel > 1) {
                return true;
            }
        } 
        
        // 3. 关键生存物品检查
        return stack.getItem() == Items.TOTEM_OF_UNDYING || stack.getItem() == Items.END_CRYSTAL;
    }
}

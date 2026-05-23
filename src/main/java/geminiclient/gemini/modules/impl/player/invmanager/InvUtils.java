package geminiclient.gemini.modules.impl.player.invmanager;

import geminiclient.gemini.base.MinecraftInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;

import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class InvUtils implements MinecraftInstance {

    public static final List<Block> blacklistedBlocks = Arrays.asList(
            Blocks.AIR,
            Blocks.WATER,
            Blocks.LAVA,
            Blocks.ENCHANTING_TABLE,
            Blocks.GLASS_PANE,
            Blocks.IRON_BARS,
            Blocks.SNOW,
            Blocks.COAL_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.EMERALD_ORE,
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.TORCH,
            Blocks.ANVIL,
            Blocks.NOTE_BLOCK,
            Blocks.JUKEBOX,
            Blocks.TNT,
            Blocks.GOLD_ORE,
            Blocks.IRON_ORE,
            Blocks.LAPIS_ORE,
            Blocks.STONE_PRESSURE_PLATE,
            Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
            Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Blocks.STONE_BUTTON,
            Blocks.LEVER,
            Blocks.TALL_GRASS,
            Blocks.TRIPWIRE,
            Blocks.TRIPWIRE_HOOK,
            Blocks.RAIL,
            Blocks.CORNFLOWER,
            Blocks.RED_MUSHROOM,
            Blocks.BROWN_MUSHROOM,
            Blocks.VINE,
            Blocks.SUNFLOWER,
            Blocks.LADDER,
            Blocks.FURNACE,
            Blocks.SAND,
            Blocks.CACTUS,
            Blocks.DISPENSER,
            Blocks.DROPPER,
            Blocks.CRAFTING_TABLE,
            Blocks.COBWEB,
            Blocks.PUMPKIN,
            Blocks.COBBLESTONE_WALL,
            Blocks.OAK_FENCE,
            Blocks.REDSTONE_TORCH,
            Blocks.FLOWER_POT
    );

    // ==================== Enchantment Helpers ====================

    public static int getEnchantmentLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack.isEmpty() || mc.player == null) return 0;
        ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
        if (enchants == null || enchants.isEmpty()) return 0;
        try {
            Holder<Enchantment> holder = mc.player.registryAccess().holderOrThrow(key);
            return enchants.getLevel(holder);
        } catch (Exception e) {
            return 0;
        }
    }

    // ==================== Feature Control ====================

    public static boolean shouldDisableFeatures() {
        return getAllItems().stream().anyMatch(item -> {
            if (item.isEmpty()) return false;
            String string = item.getDisplayName().getString();
            return string.contains("长按点击") || string.contains("点击使用")
                    || string.contains("离开游戏") || string.contains("选择一个队伍")
                    || string.contains("再来一局");
        });
    }

    // ==================== Item Type Checks ====================

    public static boolean isGoldenHead(ItemStack e) {
        if (e.isEmpty()) return false;
        return e.getItem() instanceof BlockItem item && item.getBlock() instanceof SkullBlock;
    }

    public static boolean isSharpnessAxe(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ItemTags.AXES)) return false;
        int level = getEnchantmentLevel(stack, Enchantments.SHARPNESS);
        return level >= 8 && level < 50;
    }

    public static boolean isGodAxe(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() == Items.GOLDEN_AXE
                && getEnchantmentLevel(stack, Enchantments.SHARPNESS) > 100;
    }

    public static boolean isEnchantedGApple(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
    }

    public static boolean isEndCrystal(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.END_CRYSTAL;
    }

    public static boolean isKBBall(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() == Items.SLIME_BALL
                && getEnchantmentLevel(stack, Enchantments.KNOCKBACK) > 1;
    }

    public static boolean isKBStick(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() == Items.STICK
                && getEnchantmentLevel(stack, Enchantments.KNOCKBACK) > 1;
    }

    // ==================== Inventory Slot Finders ====================

    public static int findEmptyInventory() {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    public static int findEmptySlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    public static Integer findItemHotbar(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == item) return i;
        }
        return null;
    }

    // ==================== All Items ====================

    public static List<ItemStack> getAllItems() {
        List<ItemStack> list = new ArrayList<>(40);
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            list.add(mc.player.getInventory().getItem(i));
        }
        return list;
    }

    // ==================== Armor ====================

    public static float getBestArmorScore(EquipmentSlot slot) {
        return getAllItems().stream()
                .filter(item -> {
                    if (item.isEmpty() || !item.is(ItemTags.ARMOR_ENCHANTABLE)) return false;
                    var equippable = item.get(DataComponents.EQUIPPABLE);
                    return equippable != null && equippable.slot() == slot;
                })
                .map(InvUtils::getProtection)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static float getCurrentArmorScore(EquipmentSlot slot) {
        return getProtection(mc.player.getItemBySlot(slot));
    }

    // ==================== Swords / Weapons ====================

    public static float getBestSwordDamage() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.is(ItemTags.SWORDS))
                .map(InvUtils::getSwordDamage)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestSword() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.is(ItemTags.SWORDS))
                .max(Comparator.comparingInt(s -> (int) (getSwordDamage(s) * 100.0F)))
                .orElse(null);
    }

    public static ItemStack getBestShapeAxe() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.is(ItemTags.AXES)
                        && isSharpnessAxe(item) && !isNotItemValid(item) && !isGodAxe(item))
                .max(Comparator.comparingInt(s -> (int) (getAxeDamage(s) * 100.0F)))
                .orElse(null);
    }

    // ==================== Tools ====================

    public static float getBestPickaxeScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.is(ItemTags.PICKAXES) && !isNotItemValid(item))
                .map(InvUtils::getToolScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestPickaxe() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.is(ItemTags.PICKAXES) && !isNotItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getToolScore(s) * 100.0F)))
                .orElse(null);
    }

    public static float getBestAxeScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.is(ItemTags.AXES)
                        && !isSharpnessAxe(item) && !isNotItemValid(item))
                .map(InvUtils::getToolScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestAxe() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.is(ItemTags.AXES)
                        && !isSharpnessAxe(item) && !isNotItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getToolScore(s) * 100.0F)))
                .orElse(null);
    }

    public static float getBestShovelScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.is(ItemTags.SHOVELS) && !isNotItemValid(item))
                .map(InvUtils::getToolScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestShovel() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.is(ItemTags.SHOVELS) && !isNotItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getToolScore(s) * 100.0F)))
                .orElse(null);
    }

    // ==================== Bows / Crossbows ====================

    public static float getBestCrossbowScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof CrossbowItem && !isNotItemValid(item))
                .map(InvUtils::getCrossbowScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestCrossbow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof CrossbowItem && !isNotItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getCrossbowScore(s) * 100.0F)))
                .orElse(null);
    }

    public static float getBestPunchBowScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && !isNotItemValid(item))
                .map(InvUtils::getPunchBowScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestPunchBow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && !isNotItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getPunchBowScore(s) * 100.0F)))
                .orElse(null);
    }

    public static float getBestPowerBowScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && !isNotItemValid(item))
                .map(InvUtils::getPowerBowScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestPowerBow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && !isNotItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getPowerBowScore(s) * 100.0F)))
                .orElse(null);
    }

    public static boolean isPunchBow(ItemStack stack) {
        return getEnchantmentLevel(stack, Enchantments.PUNCH) > 0 && !isNotItemValid(stack);
    }

    public static boolean isPowerBow(ItemStack stack) {
        return getEnchantmentLevel(stack, Enchantments.POWER) > 0 && !isNotItemValid(stack);
    }

    // ==================== Projectiles ====================

    public static ItemStack getBestProjectile() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty()
                        && (item.getItem() == Items.EGG || item.getItem() == Items.SNOWBALL)
                        && !isNotItemValid(item))
                .max(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getWorstProjectile() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty()
                        && (item.getItem() == Items.EGG || item.getItem() == Items.SNOWBALL))
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getWorstArrow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof ArrowItem && !isNotItemValid(item))
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    // ==================== Blocks ====================

    public static ItemStack getBestBlock() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BlockItem
                        && isValidStack(item) && !isNotItemValid(item))
                .max(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getWorstBlock() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BlockItem
                        && isValidBlockType(item) && !isNotItemValid(item))
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static int getBlockCountInInventory() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BlockItem
                        && isValidBlockType(item) && !isNotItemValid(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    // ==================== Food ====================

    public static ItemStack getBestFood() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.has(DataComponents.FOOD) && !isNotItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getFoodScore(s) * 100.0F)))
                .orElse(null);
    }

    public static float getFoodScore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0F;
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return 0.0F;
        return food.nutrition() * 2.0F + food.saturation() * 1.5F;
    }

    // ==================== Fishing Rod ====================

    public static ItemStack getFishingRod() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof FishingRodItem
                        && !isNotItemValid(item))
                .findAny().orElse(null);
    }

    // ==================== Slot / Item Lookup ====================

    public static int getItemStackSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        int size = mc.player.getInventory().getContainerSize();

        // 第一遍比对：引用比对（高效精确）
        for (int i = 0; i < size; i++) {
            if (mc.player.getInventory().getItem(i) == stack) return i;
        }

        // 第二遍比对：结构体模糊比对（规避 Minecraft 内部实例重构导致的 == 失效风险）
        for (int i = 0; i < size; i++) {
            ItemStack item = mc.player.getInventory().getItem(i);
            if (!item.isEmpty() && item.getItem() == stack.getItem() && item.getCount() == stack.getCount()) {
                return i;
            }
        }
        return -1;
    }

    public static int getItemSlot(Item item) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == item) return i;
        }
        return -1;
    }

    public static boolean hasItem(Item checkItem) {
        return getAllItems().stream().anyMatch(item -> !item.isEmpty() && item.getItem() == checkItem);
    }

    public static int getItemCount(Item checkItem) {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() == checkItem)
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    // ==================== Item Validation ====================

    public static boolean isNotItemValid(ItemStack s) {
        if (s.isEmpty()) return false;
        if (s.getItem() instanceof PlayerHeadItem) return true;
        String string = s.getDisplayName().getString();
        if (string.contains("Click")) return true;
        if (string.contains("Right")) return true;
        if (string.contains("点击")) return true;
        if (string.contains("Teleport")) return true;
        if (string.contains("使用")) return true;
        if (string.contains("传送")) return true;
        return string.contains("再来");
    }

    public static boolean isNotItemValidSB(ItemStack s) {
        if (s.getItem() instanceof PlayerHeadItem) return true;
        String string = s.getDisplayName().getString();
        if (string.contains("Click")) return true;
        if (string.contains("Right")) return true;
        if (string.contains("点击")) return true;
        if (string.contains("Teleport")) return true;
        if (string.contains("使用")) return true;
        if (string.contains("传送")) return true;
        return string.contains("再来");
    }

    private static boolean isValidBlockType(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof BlockItem)) return false;
        if (isNotItemValid(stack)) return false;
        if (stack.has(DataComponents.CUSTOM_NAME)) return false;
        String string = stack.getDisplayName().getString();
        if (string.contains("Click") || string.contains("点击")) return false;
        Block block = ((BlockItem) stack.getItem()).getBlock();
        if (block instanceof FlowerBlock) return false;
        if (block instanceof BushBlock) return false;
        if (block instanceof FlowerPotBlock || block instanceof NetherFungusBlock) return false;
        if (block instanceof CropBlock) return false;
        if (block instanceof SlabBlock) return false;
        return !blacklistedBlocks.contains(block);
    }

    public static boolean isValidStack(ItemStack stack) {
        return isValidBlockType(stack) && stack.getCount() > 1;
    }

    // ==================== God Item ====================

    public static boolean isGodItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() == Items.GOLDEN_AXE
                && getEnchantmentLevel(stack, Enchantments.SHARPNESS) > 100) return true;
        if (stack.getItem() == Items.SLIME_BALL
                && getEnchantmentLevel(stack, Enchantments.KNOCKBACK) > 1) return true;
        return stack.getItem() == Items.TOTEM_OF_UNDYING || stack.getItem() == Items.END_CRYSTAL;
    }

    public static boolean isCommonItemUseful(ItemStack stack) {
        if (stack.isEmpty()) return true;
        Item item = stack.getItem();
        if (item instanceof BlockItem block) {
            if (block.getBlock() == Blocks.ENCHANTING_TABLE) return false;
            return block.getBlock() != Blocks.COBWEB;
        }
        if (item == Items.BOOK || item == Items.ENCHANTED_BOOK
                || item == Items.WRITTEN_BOOK || item == Items.WRITABLE_BOOK) return false;
        if (item instanceof ExperienceBottleItem) return false;
        if (item instanceof FireworkRocketItem) return false;
        if (item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS
                || item == Items.MELON_SEEDS || item == Items.PUMPKIN_SEEDS) return false;
        return item != Items.FLINT_AND_STEEL;
    }

    // ==================== Scoring ====================

    public static float getProtection(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !itemStack.is(ItemTags.ARMOR_ENCHANTABLE)) return 0.0F;

        float armor = 0.0F;
        float toughness = 0.0F;
        float knockbackResistance = 0.0F;

        ItemAttributeModifiers attrComp = itemStack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (attrComp != null) {
            for (var entry : attrComp.modifiers()) {
                if (entry.attribute().value() == Attributes.ARMOR.value()) {
                    armor += (float) entry.modifier().amount();
                } else if (entry.attribute().value() == Attributes.ARMOR_TOUGHNESS.value()) {
                    toughness += (float) entry.modifier().amount();
                } else if (entry.attribute().value() == Attributes.KNOCKBACK_RESISTANCE.value()) {
                    knockbackResistance += (float) entry.modifier().amount();
                }
            }
        }

        int protection = getEnchantmentLevel(itemStack, Enchantments.PROTECTION);
        int blastProtection = getEnchantmentLevel(itemStack, Enchantments.BLAST_PROTECTION);
        int fireProtection = getEnchantmentLevel(itemStack, Enchantments.FIRE_PROTECTION);
        int projectileProtection = getEnchantmentLevel(itemStack, Enchantments.PROJECTILE_PROTECTION);
        int featherFalling = getEnchantmentLevel(itemStack, Enchantments.FEATHER_FALLING);
        int thorns = getEnchantmentLevel(itemStack, Enchantments.THORNS);
        int unbreaking = getEnchantmentLevel(itemStack, Enchantments.UNBREAKING);
        int mending = getEnchantmentLevel(itemStack, Enchantments.MENDING);
        int bindingCurse = getEnchantmentLevel(itemStack, Enchantments.BINDING_CURSE);
        int vanishingCurse = getEnchantmentLevel(itemStack, Enchantments.VANISHING_CURSE);

        float durabilityScore = 0.0F;
        if (itemStack.isDamageableItem() && itemStack.getMaxDamage() > 0) {
            float remaining = 1.0F - ((float) itemStack.getDamageValue() / (float) itemStack.getMaxDamage());
            durabilityScore = remaining * 0.75F;
        }

        float enchantScore = protection * 4.0F
                + (blastProtection + fireProtection + projectileProtection) * 3.0F
                + featherFalling * 2.5F + thorns * 0.5F + unbreaking * 0.25F + mending * 1.5F
                - (bindingCurse + vanishingCurse) * 50.0F;

        return armor * 10.0F + toughness * 8.0F + knockbackResistance * 30.0F + durabilityScore + enchantScore;
    }

    public static float getSwordDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0F;

        float valence = 0.0F;
        if (stack.is(ItemTags.SWORDS)) {
            Item item = stack.getItem();
            if (item == Items.WOODEN_SWORD || item == Items.GOLDEN_SWORD) valence += 4.0F;
            else if (item == Items.STONE_SWORD) valence += 5.0F;
            else if (item == Items.IRON_SWORD) valence += 6.0F;
            else if (item == Items.DIAMOND_SWORD) valence += 7.0F;
            else if (item == Items.NETHERITE_SWORD) valence += 8.0F;
            else valence += 5.0F;
        }

        int sharpness = getEnchantmentLevel(stack, Enchantments.SHARPNESS);
        if (sharpness > 0) valence += 0.5F + 0.5F * sharpness;
        return valence;
    }

    public static float getAxeDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0F;

        float valence = 0.0F;
        if (stack.is(ItemTags.AXES) && isSharpnessAxe(stack)) {
            Item axe = stack.getItem();
            if (axe == Items.WOODEN_AXE) valence += 4.0F;
            else if (axe == Items.STONE_AXE) valence += 5.0F;
            else if (axe == Items.IRON_AXE) valence += 6.0F;
            else if (axe == Items.GOLDEN_AXE) valence += 4.0F;
            else if (axe == Items.DIAMOND_AXE) valence += 7.0F;
        }

        int sharpness = getEnchantmentLevel(stack, Enchantments.SHARPNESS);
        if (sharpness > 0) valence += 0.5F + 0.5F * sharpness;
        return valence;
    }

    public static float getToolScore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0F;
        if (isGodItem(stack)) return 0.0F;
        if (isSharpnessAxe(stack)) return 0.0F;

        float valence = 0.0F;
        if (stack.is(ItemTags.PICKAXES)) {
            valence += stack.getDestroySpeed(Blocks.STONE.defaultBlockState());
        } else if (stack.is(ItemTags.AXES)) {
            valence += stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState());
        } else if (stack.is(ItemTags.SHOVELS)) {
            valence += stack.getDestroySpeed(Blocks.DIRT.defaultBlockState());
        } else {
            return 0.0F;
        }

        int efficiency = getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
        if (efficiency > 0) valence += (float) efficiency * 0.0075F;
        return valence;
    }

    public static float getCrossbowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof CrossbowItem)) return 0.0F;
        int valence = getEnchantmentLevel(stack, Enchantments.QUICK_CHARGE)
                + getEnchantmentLevel(stack, Enchantments.MULTISHOT)
                + getEnchantmentLevel(stack, Enchantments.PIERCING);
        return (float) valence;
    }

    public static float getPowerBowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BowItem)) return 0.0F;
        float valence = 10.0F;
        valence += (float) getEnchantmentLevel(stack, Enchantments.PUNCH) / 10.0F;
        valence += (float) getEnchantmentLevel(stack, Enchantments.INFINITY);
        valence += (float) getEnchantmentLevel(stack, Enchantments.FLAME);
        valence += (float) getEnchantmentLevel(stack, Enchantments.POWER);
        return valence + (float) stack.getDamageValue() / (float) stack.getMaxDamage();
    }

    public static float getPunchBowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BowItem)) return 0.0F;
        float valence = 10.0F;
        valence += (float) getEnchantmentLevel(stack, Enchantments.PUNCH);
        valence += (float) getEnchantmentLevel(stack, Enchantments.INFINITY);
        valence += (float) getEnchantmentLevel(stack, Enchantments.FLAME);
        valence += (float) getEnchantmentLevel(stack, Enchantments.POWER) / 10.0F;
        return valence + (float) stack.getDamageValue() / (float) stack.getMaxDamage();
    }
}

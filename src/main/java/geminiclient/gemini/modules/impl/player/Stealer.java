package geminiclient.gemini.modules.impl.player;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.TimerUtils;
import geminiclient.gemini.values.impl.IntRangeValue;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.*;

public class Stealer extends Module {
    // 配置常量
    private static final String CHEST_KEY = "container.chest";
    private static final String LARGE_CHEST_KEY = "container.chestDouble";
    private static final String FALLBACK_CHEST_TITLE = "Chest";

    // 物品价值配置
    private static final class ItemValues {
        // 高价值物品（基础材料）
        static final Set<Item> VALUABLE_ITEMS = Set.of(
                Items.DIAMOND, Items.NETHERITE_INGOT, Items.EMERALD, Items.GOLD_INGOT, Items.IRON_INGOT,
                Items.ENDER_PEARL, Items.OBSIDIAN, Items.ENCHANTED_BOOK, Items.EXPERIENCE_BOTTLE,
                Items.BEACON, Items.ENDER_EYE, Items.BLAZE_ROD, Items.GHAST_TEAR, Items.SHULKER_SHELL,
                Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE);

        // 工具和武器
        static final Set<Item> TOOLS_AND_WEAPONS = Set.of(
                Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD,
                Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
                Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE,
                Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE,
                Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE,
                Items.DIAMOND_AXE, Items.NETHERITE_AXE,
                Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.GOLDEN_SHOVEL,
                Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL,
                Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.FISHING_ROD, Items.MACE);

        // 盔甲
        static final Set<Item> ARMOR = Set.of(
                Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
                Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS,
                Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
                Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
                Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
                Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
                Items.TURTLE_HELMET);

        // 物品基础价值
        static final Map<Item, Integer> BASE_VALUES = createBaseValueMap();

        private static Map<Item, Integer> createBaseValueMap() {
            Map<Item, Integer> baseValues = new HashMap<>();

            // 材料价值
            baseValues.put(Items.NETHERITE_INGOT, 100);
            baseValues.put(Items.NETHERITE_SCRAP, 100);
            baseValues.put(Items.DIAMOND, 80);
            baseValues.put(Items.EMERALD, 60);
            baseValues.put(Items.GOLD_INGOT, 40);
            baseValues.put(Items.GOLD_NUGGET, 40);
            baseValues.put(Items.IRON_INGOT, 30);
            baseValues.put(Items.IRON_NUGGET, 30);

            // 工具和武器基础价值
            baseValues.put(Items.MACE, 150);
            baseValues.put(Items.NETHERITE_SWORD, 120);
            baseValues.put(Items.DIAMOND_SWORD, 100);
            baseValues.put(Items.IRON_SWORD, 60);
            baseValues.put(Items.GOLDEN_SWORD, 40);
            baseValues.put(Items.STONE_SWORD, 30);
            baseValues.put(Items.WOODEN_SWORD, 20);

            baseValues.put(Items.NETHERITE_AXE, 110);
            baseValues.put(Items.DIAMOND_AXE, 90);
            baseValues.put(Items.IRON_AXE, 50);
            baseValues.put(Items.GOLDEN_AXE, 35);
            baseValues.put(Items.STONE_AXE, 25);
            baseValues.put(Items.WOODEN_AXE, 15);

            baseValues.put(Items.NETHERITE_PICKAXE, 110);
            baseValues.put(Items.DIAMOND_PICKAXE, 90);
            baseValues.put(Items.IRON_PICKAXE, 50);
            baseValues.put(Items.GOLDEN_PICKAXE, 35);
            baseValues.put(Items.STONE_PICKAXE, 25);
            baseValues.put(Items.WOODEN_PICKAXE, 15);

            baseValues.put(Items.BOW, 40);
            baseValues.put(Items.CROSSBOW, 50);
            baseValues.put(Items.TRIDENT, 80);
            baseValues.put(Items.FISHING_ROD, 20);

            // 盔甲基础价值
            baseValues.put(Items.NETHERITE_HELMET, 80);
            baseValues.put(Items.NETHERITE_CHESTPLATE, 100);
            baseValues.put(Items.NETHERITE_LEGGINGS, 90);
            baseValues.put(Items.NETHERITE_BOOTS, 70);

            baseValues.put(Items.DIAMOND_HELMET, 60);
            baseValues.put(Items.DIAMOND_CHESTPLATE, 80);
            baseValues.put(Items.DIAMOND_LEGGINGS, 70);
            baseValues.put(Items.DIAMOND_BOOTS, 50);

            baseValues.put(Items.IRON_HELMET, 30);
            baseValues.put(Items.IRON_CHESTPLATE, 40);
            baseValues.put(Items.IRON_LEGGINGS, 35);
            baseValues.put(Items.IRON_BOOTS, 25);

            baseValues.put(Items.GOLDEN_HELMET, 20);
            baseValues.put(Items.GOLDEN_CHESTPLATE, 25);
            baseValues.put(Items.GOLDEN_LEGGINGS, 22);
            baseValues.put(Items.GOLDEN_BOOTS, 18);

            baseValues.put(Items.TURTLE_HELMET, 35);

            // 特殊物品价值
            baseValues.put(Items.BEACON, 150);
            baseValues.put(Items.ENCHANTED_GOLDEN_APPLE, 200);
            baseValues.put(Items.ENCHANTED_BOOK, 100);
            baseValues.put(Items.ENDER_EYE, 70);
            baseValues.put(Items.SHULKER_SHELL, 70);
            baseValues.put(Items.GOLDEN_APPLE, 50);
            baseValues.put(Items.ENDER_PEARL, 30);
            baseValues.put(Items.BLAZE_ROD, 25);
            baseValues.put(Items.GHAST_TEAR, 35);
            baseValues.put(Items.OBSIDIAN, 20);
            baseValues.put(Items.EXPERIENCE_BOTTLE, 40);

            return baseValues;
        }

        // 材料类型价值加成
        static int getMaterialBonus(Item item) {
            String name = item.toString().toLowerCase();
            if (name.contains("netherite"))
                return 100;
            if (name.contains("diamond"))
                return 80;
            if (name.contains("gold"))
                return 40;
            if (name.contains("iron"))
                return 30;
            return 20; // 默认价值
        }
    }

    // 模块配置
    private final IntRangeValue stealDelay = new IntRangeValue("StealDelay", 100, 200, 0, 500);
    private final IntRangeValue closeDelay = new IntRangeValue("CloseDelay", 100, 200, 0, 500);

    // 状态管理
    private final TimerUtils stealTimer = new TimerUtils();
    private final TimerUtils closeTimer = new TimerUtils();
    private final Random random = new Random();
    private Screen lastScreen;

    // 记录已经处理的物品类别，避免重复拿取同类物品
    private final Set<ItemCategory> processedCategories = new HashSet<>();
    // 记录箱子中是否有值得拿取的物品
    private boolean hasUpgradeableItems = false;

    public Stealer() {
        super("Stealer", ModuleEnum.Player);
        addValue(stealDelay);
        addValue(closeDelay);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (shouldSkipProcessing(event))
            return;

        Screen currentScreen = mc.screen;
        if (!(currentScreen instanceof ContainerScreen container)) {
            handleScreenChange(currentScreen);
            processedCategories.clear(); // 关闭箱子时清空处理记录
            hasUpgradeableItems = false; // 重置状态
            return;
        }

        if (currentScreen != lastScreen) {
            resetState();
            processedCategories.clear(); // 切换到新箱子时清空处理记录
            hasUpgradeableItems = false; // 重置状态
        }

        lastScreen = currentScreen;

        if (isTargetChest(container)) {
            processChest(container.getMenu());
        }
    }

    // ========== 核心逻辑方法 ==========

    /**
     * 处理箱子内容
     */
    private void processChest(ChestMenu menu) {
        int stealDelayMs = random.nextInt(stealDelay.getMinValue(), stealDelay.getMaxValue());
        int closeDelayMs = random.nextInt(closeDelay.getMinValue(), closeDelay.getMaxValue());

        // 检查是否有值得拿取的物品
        if (!hasUpgradeableItems) {
            // 首次检查箱子中是否有比背包中同类物品价值更高的物品
            hasUpgradeableItems = hasUpgradeableItems(menu);
        }

        if (shouldCloseChest(menu)) {
            handleChestClosing(closeDelayMs);
        } else {
            handleItemStealing(menu, stealDelayMs);
        }
    }

    /**
     * 检查箱子中是否有比背包中同类物品价值更高的物品
     */
    private boolean hasUpgradeableItems(ChestMenu menu) {
        // 扫描背包，找出每个类别中最有价值的物品
        Map<ItemCategory, Integer> bestInventoryValues = getBestItemValuesInInventory();

        // 扫描箱子，找出比背包中同类物品价值更高的物品
        int chestSize = menu.getRowCount() * 9;

        for (int i = 0; i < chestSize; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty() && isValuableItem(stack)) {
                ItemCategory category = getItemCategory(stack.getItem());

                // 如果已经处理过这个类别，跳过
                if (processedCategories.contains(category)) {
                    continue;
                }

                int value = calculateItemValue(stack);
                int bestInventoryValue = bestInventoryValues.getOrDefault(category, 0);

                // 只有当箱子中的物品价值高于背包中的同类物品时才返回true
                if (value > bestInventoryValue) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 处理箱子关闭逻辑
     */
    private void handleChestClosing(int closeDelayMs) {
        if (closeTimer.hasTimeElapsed(closeDelayMs, false)) {
            if (mc.player != null) {
                mc.player.closeContainer();
            }
            closeTimer.reset();
            processedCategories.clear();
            hasUpgradeableItems = false; // 重置状态
        }
    }

    /**
     * 处理物品偷取逻辑
     */
    private void handleItemStealing(ChestMenu menu, int stealDelayMs) {
        if (stealTimer.hasTimeElapsed(stealDelayMs, true) || stealDelayMs == 0) {
            boolean stoleItem = attemptSteal(menu);
            stealTimer.reset();

            // 如果没有偷取到物品，可能是因为没有更多值得拿取的物品
            if (!stoleItem) {
                // 重新检查是否还有值得拿取的物品
                hasUpgradeableItems = hasUpgradeableItems(menu);
            }
        }
    }

    /**
     * 尝试偷取高价值物品
     * 
     * @return 是否成功偷取了物品
     */
    private boolean attemptSteal(ChestMenu menu) {
        // 使用新的逻辑来查找最有价值的槽位 (考虑背包中的同类物品)
        Optional<Integer> valuableSlot = findBestItemSlotToSteal(menu);
        if (valuableSlot.isPresent()) {
            int slotId = valuableSlot.get();
            if (mc.player != null) {
                if (mc.gameMode != null) {
                    mc.gameMode.handleContainerInput(menu.containerId, slotId, 0,
                            ContainerInput.QUICK_MOVE, mc.player);

                    // 记录已经处理的物品类别
                    ItemStack stack = menu.getSlot(slotId).getItem();
                    if (!stack.isEmpty()) {
                        ItemCategory category = getItemCategory(stack.getItem());
                        processedCategories.add(category);
                    }

                    return true;
                }
            }
        }

        return false;
    }

    // ========== 工具方法 ==========

    /**
     * 检查是否应该跳过处理
     */
    private boolean shouldSkipProcessing(MotionEvent event) {
        return event.getTimeEnum() != TimeEnum.Pre || mc.player == null || mc.gameMode == null;
    }

    /**
     * 处理屏幕变化
     */
    private void handleScreenChange(Screen newScreen) {
        lastScreen = newScreen;
    }

    /**
     * 重置模块状态
     */
    private void resetState() {
        stealTimer.reset();
        closeTimer.reset();
    }

    /**
     * 检查是否为目标箱子
     */
    private boolean isTargetChest(ContainerScreen container) {
        String title = container.getTitle().getString().toLowerCase();
        String chestTitle = Component.translatable(CHEST_KEY).getString().toLowerCase();
        String largeChestTitle = Component.translatable(LARGE_CHEST_KEY).getString().toLowerCase();

        return title.contains(chestTitle) || title.contains(largeChestTitle) ||
                title.contains(FALLBACK_CHEST_TITLE.toLowerCase());
    }

    /**
     * 检查是否应该关闭箱子
     */
    private boolean shouldCloseChest(ChestMenu menu) {
        // 如果箱子为空，或者没有高价值物品，或者没有值得拿取的物品，则关闭箱子
        return isChestEmpty(menu) || !hasValuableItems(menu) || !hasUpgradeableItems;
    }

    /**
     * 检查箱子是否为空
     */
    private boolean isChestEmpty(ChestMenu menu) {
        int chestSize = menu.getRowCount() * 9;
        for (int i = 0; i < chestSize; i++) {
            if (!menu.getSlot(i).getItem().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查箱子中是否有高价值物品
     */
    private boolean hasValuableItems(ChestMenu menu) {
        int chestSize = menu.getRowCount() * 9;
        for (int i = 0; i < chestSize; i++) {
            if (isValuableItem(menu.getSlot(i).getItem())) {
                return true;
            }
        }
        return false;
    }

    // ========== 核心物品选择逻辑 ==========

    /**
     * 根据新的"只取同类中价值最高且比背包中同类物品价值更高"的策略，查找最佳物品槽位进行偷取。
     * 
     * @param menu 当前的箱子菜单
     * @return 最有价值的槽位ID，如果没有值得偷取的物品则返回 empty
     */
    private Optional<Integer> findBestItemSlotToSteal(ChestMenu menu) {
        // 步骤 1: 扫描背包，找出每个类别中最有价值的物品
        Map<ItemCategory, Integer> bestInventoryValues = getBestItemValuesInInventory();

        // 步骤 2: 扫描箱子，找出比背包中同类物品价值更高的物品
        int chestSize = menu.getRowCount() * 9;
        Map<ItemCategory, SlotValue> upgradeableItems = new EnumMap<>(ItemCategory.class);

        for (int i = 0; i < chestSize; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty() && isValuableItem(stack)) {
                ItemCategory category = getItemCategory(stack.getItem());

                // 如果已经处理过这个类别，跳过
                if (processedCategories.contains(category)) {
                    continue;
                }

                int value = calculateItemValue(stack);
                int bestInventoryValue = bestInventoryValues.getOrDefault(category, 0);

                // 只有当箱子中的物品价值高于背包中的同类物品时才考虑
                if (value > bestInventoryValue) {
                    SlotValue currentBest = upgradeableItems.get(category);

                    // 更新该类别中最有价值的物品
                    if (currentBest == null || value > currentBest.value()) {
                        upgradeableItems.put(category, new SlotValue(i, value));
                    }
                }
            }
        }

        // 步骤 3: 在所有可升级的物品中，选出总价值最高的槽位进行偷取
        int bestSlot = -1;
        int bestValue = -1;

        for (SlotValue slotValue : upgradeableItems.values()) {
            if (slotValue.value() > bestValue) {
                bestValue = slotValue.value();
                bestSlot = slotValue.slotId();
            }
        }

        return bestSlot != -1 ? Optional.of(bestSlot) : Optional.empty();
    }

    /**
     * 扫描玩家背包，返回每个物品类别中的最高价值
     */
    private Map<ItemCategory, Integer> getBestItemValuesInInventory() {
        Map<ItemCategory, Integer> bestValues = new EnumMap<>(ItemCategory.class);

        if (mc.player == null) {
            return bestValues;
        }

        // 扫描玩家背包（0-35槽位）
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && isValuableItem(stack)) {
                ItemCategory category = getItemCategory(stack.getItem());
                int value = calculateItemValue(stack);

                int currentBest = bestValues.getOrDefault(category, 0);
                if (value > currentBest) {
                    bestValues.put(category, value);
                }
            }
        }

        return bestValues;
    }

    /**
     * 辅助记录类：存储槽位ID和物品价值
     */
    private record SlotValue(int slotId, int value) {
    }

    /**
     * 定义物品类别，用于比较同类物品。
     */
    private enum ItemCategory {
        WEAPON, TOOL, HELMET, CHESTPLATE, LEGGINGS, BOOTS, TRIDENT, BOW, CROSSBOW, FISHING_ROD, MATERIAL, OTHER_VALUABLE
    }

    /**
     * 根据物品类型获取其类别，用于同类比较。
     */
    private ItemCategory getItemCategory(Item item) {
        // 如果是特殊高价值物品（如信标、金苹果），且不是工具/武器/盔甲，归为 MATERIAL
        if (ItemValues.VALUABLE_ITEMS.contains(item) && !ItemValues.TOOLS_AND_WEAPONS.contains(item)
                && !ItemValues.ARMOR.contains(item)) {
            return ItemCategory.MATERIAL;
        }

        // 精细分类武器和工具
        if (ItemValues.TOOLS_AND_WEAPONS.contains(item)) {
            String name = item.toString().toLowerCase();
            if (name.contains("sword") || item.equals(Items.MACE))
                return ItemCategory.WEAPON;
            if (name.contains("pickaxe"))
                return ItemCategory.TOOL;
            if (name.contains("axe"))
                return ItemCategory.TOOL;
            if (name.contains("shovel"))
                return ItemCategory.TOOL;
            if (item.equals(Items.BOW))
                return ItemCategory.BOW;
            if (item.equals(Items.CROSSBOW))
                return ItemCategory.CROSSBOW;
            if (item.equals(Items.TRIDENT))
                return ItemCategory.TRIDENT;
            if (item.equals(Items.FISHING_ROD))
                return ItemCategory.FISHING_ROD;
            // 未明确分类的工具/武器
            return ItemCategory.OTHER_VALUABLE;
        }

        // 精细分类盔甲
        if (ItemValues.ARMOR.contains(item)) {
            String name = item.toString().toLowerCase();
            if (name.contains("helmet"))
                return ItemCategory.HELMET;
            if (name.contains("chestplate"))
                return ItemCategory.CHESTPLATE;
            if (name.contains("leggings"))
                return ItemCategory.LEGGINGS;
            if (name.contains("boots"))
                return ItemCategory.BOOTS;
        }

        // 默认返回基础材料或"其他有价值"类别
        return ItemCategory.OTHER_VALUABLE;
    }

    /**
     * 判断物品是否有价值
     */
    private boolean isValuableItem(ItemStack stack) {
        if (stack.isEmpty())
            return false;

        Item item = stack.getItem();
        return ItemValues.VALUABLE_ITEMS.contains(item) ||
                ItemValues.TOOLS_AND_WEAPONS.contains(item) ||
                ItemValues.ARMOR.contains(item) ||
                hasEnchantments(stack);
    }

    /**
     * 计算物品价值
     */
    private int calculateItemValue(ItemStack stack) {
        int value = 0;
        Item item = stack.getItem();

        // 基础价值
        value += ItemValues.BASE_VALUES.getOrDefault(item, 0);

        // 材料加成
        value += ItemValues.getMaterialBonus(item);

        // 工具/武器/盔甲加成
        if (ItemValues.TOOLS_AND_WEAPONS.contains(item) || ItemValues.ARMOR.contains(item)) {
            value += 30;
        }

        // 附魔价值
        value += calculateEnchantmentValue(stack);

        // 堆叠数量加成
        if (stack.getCount() > 1) {
            value += stack.getCount();
        }

        return value;
    }

    /**
     * 检查物品是否有附魔
     */
    private boolean hasEnchantments(ItemStack stack) {
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        return enchantments != null && !enchantments.entrySet().isEmpty();
    }

    /**
     * 计算附魔价值
     */
    private int calculateEnchantmentValue(ItemStack stack) {
        int value = 0;
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);

        if (enchantments != null) {
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
                value += entry.getIntValue() * 10; // 每级附魔价值10点
            }
        }

        return value;
    }
}
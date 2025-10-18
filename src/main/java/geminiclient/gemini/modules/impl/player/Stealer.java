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
import net.minecraft.world.inventory.ClickType;
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
                Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.FISHING_ROD);

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
            Map<Item, Integer> values = new HashMap<>();

            // 材料价值
            values.put(Items.NETHERITE_INGOT, 100);
            values.put(Items.NETHERITE_SCRAP, 100);
            values.put(Items.DIAMOND, 80);
            values.put(Items.EMERALD, 60);
            values.put(Items.GOLD_INGOT, 40);
            values.put(Items.GOLD_NUGGET, 40);
            values.put(Items.IRON_INGOT, 30);
            values.put(Items.IRON_NUGGET, 30);

            // 特殊物品价值
            values.put(Items.BEACON, 150);
            values.put(Items.ENCHANTED_GOLDEN_APPLE, 200);
            values.put(Items.ENCHANTED_BOOK, 100);
            values.put(Items.ENDER_EYE, 70);
            values.put(Items.SHULKER_SHELL, 70);
            values.put(Items.GOLDEN_APPLE, 50);

            return Collections.unmodifiableMap(values);
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
    private final IntRangeValue stealDelay = new IntRangeValue("Steal Delay", 100, 200, 0, 500);
    private final IntRangeValue closeDelay = new IntRangeValue("Close Delay", 100, 200, 0, 500);

    // 状态管理
    private final TimerUtils stealTimer = new TimerUtils();
    private final TimerUtils closeTimer = new TimerUtils();
    private final Random random = new Random();
    private Screen lastScreen;
    private boolean isStealing = false;

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
            return;
        }

        if (currentScreen != lastScreen) {
            resetState();
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

        if (shouldCloseChest(menu)) {
            handleChestClosing(menu, closeDelayMs);
        } else {
            handleItemStealing(menu, stealDelayMs);
        }
    }

    /**
     * 处理箱子关闭逻辑
     */
    private void handleChestClosing(ChestMenu menu, int closeDelayMs) {
        if (closeTimer.hasTimeElapsed(closeDelayMs, false)) {
            mc.player.closeContainer();
            closeTimer.reset();
            isStealing = false;
        }
    }

    /**
     * 处理物品偷取逻辑
     */
    private void handleItemStealing(ChestMenu menu, int stealDelayMs) {
        if (stealTimer.hasTimeElapsed(stealDelayMs, true)) {
            attemptSteal(menu);
            stealTimer.reset();
            isStealing = true;
        }
    }

    /**
     * 尝试偷取高价值物品
     */
    private void attemptSteal(ChestMenu menu) {
        Optional<Integer> valuableSlot = findMostValuableSlot(menu);
        valuableSlot.ifPresent(slotId -> mc.gameMode.handleInventoryMouseClick(menu.containerId, slotId, 0,
                ClickType.QUICK_MOVE, mc.player));
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
        isStealing = false;
    }

    /**
     * 重置模块状态
     */
    private void resetState() {
        stealTimer.reset();
        closeTimer.reset();
        isStealing = false;
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
        return isChestEmpty(menu) || !hasValuableItems(menu);
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

    /**
     * 查找最有价值的槽位
     */
    private Optional<Integer> findMostValuableSlot(ChestMenu menu) {
        int chestSize = menu.getRowCount() * 9;
        int bestSlot = -1;
        int bestValue = -1;

        for (int i = 0; i < chestSize; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty() && isValuableItem(stack)) {
                int value = calculateItemValue(stack);
                if (value > bestValue) {
                    bestValue = value;
                    bestSlot = i;
                }
            }
        }

        return bestSlot != -1 ? Optional.of(bestSlot) : Optional.empty();
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
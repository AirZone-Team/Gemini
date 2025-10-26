package geminiclient.gemini.modules.impl.player;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.TimerUtils;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.IntRangeValue;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.*;

public class InvManager extends Module {
    // 配置
    private final BoolValue dropEnabled = new BoolValue("DropEnabled", true);
    private final BoolValue equipEnabled = new BoolValue("EquipEnabled", true);
    private final IntRangeValue startDelay = new IntRangeValue("StartDelay", 1000, 3000, 0, 10000);
    private final IntRangeValue dropDelay = new IntRangeValue("DropDelay", 100, 500, 0, 2000);
    private final IntRangeValue equipDelay = new IntRangeValue("EquipDelay", 100, 500, 0, 2000);

    // 状态
    private final TimerUtils startTimer = new TimerUtils();
    private final TimerUtils dropTimer = new TimerUtils();
    private final TimerUtils equipTimer = new TimerUtils();
    private final TimerUtils actionTimer = new TimerUtils();
    private boolean started = false;
    private boolean inventoryWasOpen = false;
    private boolean isProcessing = false;
    private int currentAction = 0; // 0=无动作, 1=装备, 2=丢弃
    private int sourceSlot = -1;
    private int targetSlot = -1;
    private long actionStartTime = 0; // 操作开始时间

    // 超时设置
    private static final long ACTION_TIMEOUT = 2000; // 2秒超时

    // 装备槽位定义
    private static final int HELMET_SLOT = 5;
    private static final int CHESTPLATE_SLOT = 6;
    private static final int LEGGINGS_SLOT = 7;
    private static final int BOOTS_SLOT = 8;

    public InvManager() {
        super("InvManager", ModuleEnum.Player);
        addValue(dropEnabled);
        addValue(equipEnabled);
        addValue(startDelay);
        addValue(dropDelay);
        addValue(equipDelay);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.getTimeEnum() != TimeEnum.Pre || mc.player == null || mc.gameMode == null)
            return;

        boolean inventoryOpen = mc.screen instanceof InventoryScreen;

        if (inventoryOpen && !inventoryWasOpen) {
            resetState();
        }
        inventoryWasOpen = inventoryOpen;

        if (!inventoryOpen) {
            resetState();
            return;
        }

        if (!started) {
            int delay = getRandomDelay(startDelay);
            if (startTimer.hasTimeElapsed(delay, false)) {
                started = true;
            } else {
                return;
            }
        }

        // 处理当前操作
        if (isProcessing) {
            handleCurrentAction();
            return;
        }

        // 装备优先于丢弃
        if (equipEnabled.enabled && equipTimer.hasTimeElapsed(getRandomDelay(equipDelay), true)) {
            if (tryEquipBestItems()) {
                return;
            }
        }

        if (dropEnabled.enabled && dropTimer.hasTimeElapsed(getRandomDelay(dropDelay), true)) {
            tryDropLowValueItems();
        }
    }

    private void resetState() {
        startTimer.reset();
        dropTimer.reset();
        equipTimer.reset();
        actionTimer.reset();
        started = false;
        isProcessing = false;
        currentAction = 0;
        sourceSlot = -1;
        targetSlot = -1;
        actionStartTime = 0;
    }

    private void handleCurrentAction() {
        // 检查超时
        if (System.currentTimeMillis() - actionStartTime > ACTION_TIMEOUT) {
            resetState();
            return;
        }

        if (actionTimer.hasTimeElapsed(100, true)) {
            switch (currentAction) {
                case 1: // 装备操作
                    handleEquipAction();
                    break;
                case 2: // 丢弃操作
                    handleDropAction();
                    break;
            }
        }
    }

    private void handleEquipAction() {
        // 第一步：拾取物品
        mc.gameMode.handleInventoryMouseClick(
                mc.player.containerMenu.containerId,
                sourceSlot,
                0,
                net.minecraft.world.inventory.ClickType.PICKUP,
                mc.player);

        // 第二步：放置到目标槽位
        mc.gameMode.handleInventoryMouseClick(
                mc.player.containerMenu.containerId,
                targetSlot,
                0,
                net.minecraft.world.inventory.ClickType.PICKUP,
                mc.player);

        // 第三步：如果源槽位有物品（交换后），则放回源槽位
        if (!mc.player.getInventory().getItem(sourceSlot).isEmpty()) {
            mc.gameMode.handleInventoryMouseClick(
                    mc.player.containerMenu.containerId,
                    sourceSlot,
                    0,
                    net.minecraft.world.inventory.ClickType.PICKUP,
                    mc.player);
        }

        isProcessing = false;
        currentAction = 0;
    }

    private void handleDropAction() {
        // 丢弃物品
        mc.gameMode.handleInventoryMouseClick(
                mc.player.containerMenu.containerId,
                sourceSlot,
                0, // 丢弃单个物品
                net.minecraft.world.inventory.ClickType.THROW,
                mc.player);

        isProcessing = false;
        currentAction = 0;
    }

    private boolean tryEquipBestItems() {
        Inventory inventory = mc.player.getInventory();

        // 检查每种装备类型
        for (EquipmentType type : EquipmentType.values()) {
            EquipResult result = findBestEquipment(inventory, type);
            if (result != null && result.shouldEquip()) {
                startAction(1, result.sourceSlot, result.targetSlot);
                return true;
            }
        }

        return false;
    }

    private void tryDropLowValueItems() {
        Inventory inventory = mc.player.getInventory();

        // 检查每种装备类型
        for (EquipmentType type : EquipmentType.values()) {
            DropResult result = findItemsToDrop(inventory, type);
            if (result != null) {
                startAction(2, result.slotToDrop, -1);
                return;
            }
        }
    }

    private EquipResult findBestEquipment(Inventory inventory, EquipmentType type) {
        int equippedSlot = type.getEquippedSlot(inventory);
        ItemStack equippedItem = inventory.getItem(equippedSlot);
        int equippedValue = calculateItemValue(equippedItem);

        int bestSlot = -1;
        int bestValue = equippedValue;

        // 检查所有背包槽位（9-35），不包括装备槽位
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && type.matchesItem(stack.getItem())) {
                int value = calculateItemValue(stack);
                if (value > bestValue) {
                    bestValue = value;
                    bestSlot = slot;
                }
            }
        }

        // 检查物品栏槽位（0-8），不包括当前选中的槽位
        int selectedSlot = inventory.selected;
        for (int slot = 0; slot < 9; slot++) {
            if (slot == selectedSlot)
                continue; // 跳过当前选中的槽位

            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && type.matchesItem(stack.getItem())) {
                int value = calculateItemValue(stack);
                if (value > bestValue) {
                    bestValue = value;
                    bestSlot = slot;
                }
            }
        }

        if (bestSlot != -1 && bestValue > equippedValue) {
            return new EquipResult(bestSlot, equippedSlot, bestValue, equippedValue);
        }

        return null;
    }

    private DropResult findItemsToDrop(Inventory inventory, EquipmentType type) {
        // 找到最佳物品
        int bestSlot = -1;
        int bestValue = 0;

        // 检查所有槽位（0-35），不包括装备槽位
        for (int slot = 0; slot < 36; slot++) {
            if (slot >= 5 && slot <= 8)
                continue; // 跳过装备槽位

            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && type.matchesItem(stack.getItem())) {
                int value = calculateItemValue(stack);
                if (value > bestValue) {
                    bestValue = value;
                    bestSlot = slot;
                }
            }
        }

        // 如果没有找到该类物品，直接返回
        if (bestSlot == -1)
            return null;

        // 寻找低价值物品丢弃
        for (int slot = 0; slot < 36; slot++) {
            if (slot >= 5 && slot <= 8)
                continue; // 跳过装备槽位
            if (slot == bestSlot)
                continue; // 跳过最佳物品

            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && type.matchesItem(stack.getItem())) {
                int value = calculateItemValue(stack);
                // 如果当前物品价值明显低于最佳物品，则丢弃
                if (value < bestValue * 0.7) { // 价值低于最佳物品的70%
                    return new DropResult(slot, value, bestValue);
                }
            }
        }

        return null;
    }

    private void startAction(int action, int source, int target) {
        isProcessing = true;
        currentAction = action;
        sourceSlot = source;
        targetSlot = target;
        actionTimer.reset();
        actionStartTime = System.currentTimeMillis(); // 记录操作开始时间
    }

    private int getRandomDelay(IntRangeValue delay) {
        return delay.getMinValue() + (int) (Math.random() * (delay.getMaxValue() - delay.getMinValue()));
    }

    private int calculateItemValue(ItemStack stack) {
        if (stack.isEmpty())
            return 0;

        Item item = stack.getItem();
        int value = getBaseValue(item);

        // 材料加成
        String name = item.toString().toLowerCase();
        if (name.contains("netherite"))
            value += 100;
        else if (name.contains("diamond"))
            value += 80;
        else if (name.contains("gold"))
            value += 40;
        else if (name.contains("iron"))
            value += 30;
        else
            value += 20;

        // 工具/武器/盔甲加成
        if (isToolWeaponOrArmor(item))
            value += 30;

        // 附魔价值
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            for (Object2IntMap.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> entry : enchantments
                    .entrySet()) {
                value += entry.getIntValue() * 10;
            }
        }

        // 堆叠数量加成
        value += stack.getCount();

        return value;
    }

    private int getBaseValue(Item item) {
        if (item == Items.NETHERITE_INGOT || item == Items.NETHERITE_SCRAP)
            return 100;
        if (item == Items.DIAMOND)
            return 80;
        if (item == Items.EMERALD)
            return 60;
        if (item == Items.GOLD_INGOT)
            return 40;
        if (item == Items.IRON_INGOT)
            return 30;

        if (item == Items.NETHERITE_SWORD)
            return 120;
        if (item == Items.DIAMOND_SWORD)
            return 100;
        if (item == Items.IRON_SWORD)
            return 60;

        if (item == Items.NETHERITE_PICKAXE || item == Items.NETHERITE_AXE)
            return 110;
        if (item == Items.DIAMOND_PICKAXE || item == Items.DIAMOND_AXE)
            return 90;
        if (item == Items.IRON_PICKAXE || item == Items.IRON_AXE)
            return 50;

        if (item == Items.TRIDENT)
            return 80;

        if (item == Items.NETHERITE_HELMET)
            return 80;
        if (item == Items.NETHERITE_CHESTPLATE)
            return 100;
        if (item == Items.NETHERITE_LEGGINGS)
            return 90;
        if (item == Items.NETHERITE_BOOTS)
            return 70;

        if (item == Items.DIAMOND_HELMET)
            return 60;
        if (item == Items.DIAMOND_CHESTPLATE)
            return 80;
        if (item == Items.DIAMOND_LEGGINGS)
            return 70;
        if (item == Items.DIAMOND_BOOTS)
            return 50;

        if (item == Items.BEACON)
            return 150;
        if (item == Items.ENCHANTED_GOLDEN_APPLE)
            return 200;

        return 0;
    }

    private boolean isToolWeaponOrArmor(Item item) {
        String name = item.toString().toLowerCase();
        return name.contains("sword") ||
                name.contains("pickaxe") ||
                name.contains("axe") ||
                name.contains("shovel") ||
                name.contains("helmet") ||
                name.contains("chestplate") ||
                name.contains("leggings") ||
                name.contains("boots");
    }

    // 装备类型枚举
    private enum EquipmentType {
        HELMET {
            @Override
            public boolean matchesItem(Item item) {
                return item.toString().toLowerCase().contains("helmet");
            }

            @Override
            public int getEquippedSlot(Inventory inventory) {
                return HELMET_SLOT;
            }
        },
        CHESTPLATE {
            @Override
            public boolean matchesItem(Item item) {
                return item.toString().toLowerCase().contains("chestplate");
            }

            @Override
            public int getEquippedSlot(Inventory inventory) {
                return CHESTPLATE_SLOT;
            }
        },
        LEGGINGS {
            @Override
            public boolean matchesItem(Item item) {
                return item.toString().toLowerCase().contains("leggings");
            }

            @Override
            public int getEquippedSlot(Inventory inventory) {
                return LEGGINGS_SLOT;
            }
        },
        BOOTS {
            @Override
            public boolean matchesItem(Item item) {
                return item.toString().toLowerCase().contains("boots");
            }

            @Override
            public int getEquippedSlot(Inventory inventory) {
                return BOOTS_SLOT;
            }
        },
        WEAPON {
            @Override
            public boolean matchesItem(Item item) {
                String name = item.toString().toLowerCase();
                return name.contains("sword") ||
                        item == Items.TRIDENT ||
                        item == Items.MACE;
            }

            @Override
            public int getEquippedSlot(Inventory inventory) {
                return 36 + inventory.selected; // 主手槽位
            }
        },
        TOOL {
            @Override
            public boolean matchesItem(Item item) {
                String name = item.toString().toLowerCase();
                return name.contains("pickaxe") ||
                        name.contains("axe") ||
                        name.contains("shovel");
            }

            @Override
            public int getEquippedSlot(Inventory inventory) {
                return 36 + inventory.selected; // 主手槽位
            }
        };

        public abstract boolean matchesItem(Item item);

        public abstract int getEquippedSlot(Inventory inventory);
    }

    // 装备结果类
    private static class EquipResult {
        public final int sourceSlot;
        public final int targetSlot;
        public final int newValue;
        public final int currentValue;

        public EquipResult(int sourceSlot, int targetSlot, int newValue, int currentValue) {
            this.sourceSlot = sourceSlot;
            this.targetSlot = targetSlot;
            this.newValue = newValue;
            this.currentValue = currentValue;
        }

        public boolean shouldEquip() {
            return newValue > currentValue;
        }
    }

    // 丢弃结果类
    private static class DropResult {
        public final int slotToDrop;

        public DropResult(int slotToDrop, int dropValue, int bestValue) {
            this.slotToDrop = slotToDrop;
        }
    }
}
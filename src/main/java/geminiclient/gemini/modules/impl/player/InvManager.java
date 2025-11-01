package geminiclient.gemini.modules.impl.player;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.TimerUtils;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.IntRangeValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.*;

public class InvManager extends Module {
    // 配置
    private final BoolValue autoArmor = new BoolValue("Auto Armor", true);
    private final BoolValue switchSword = new BoolValue("Switch Sword", true);
    private final FloatValue swordSlot = new FloatValue("Sword Slot", 1.0F, 1.0F, 9.0F);
    private final BoolValue switchPickaxe = new BoolValue("Switch Pickaxe", true);
    private final FloatValue pickaxeSlot = new FloatValue("Pickaxe Slot", 2.0F, 1.0F, 9.0F);
    private final BoolValue switchAxe = new BoolValue("Switch Axe", true);
    private final FloatValue axeSlot = new FloatValue("Axe Slot", 3.0F, 1.0F, 9.0F);
    private final BoolValue switchBow = new BoolValue("Switch Bow", true);
    private final FloatValue bowSlot = new FloatValue("Bow Slot", 4.0F, 1.0F, 9.0F);
    private final BoolValue switchBlock = new BoolValue("Switch Block", true);
    private final FloatValue blockSlot = new FloatValue("Block Slot", 5.0F, 1.0F, 9.0F);
    private final BoolValue throwItems = new BoolValue("Throw Items", true);
    private final IntRangeValue delay = new IntRangeValue("Delay", 100, 300, 50, 1000);

    private final ListValue offhandMode = new ListValue("Offhand", "None", new String[]{
            "None","Golden Apple", "Projectile", "Rod", "Block"
    });

    // 状态
    private final TimerUtils timer = new TimerUtils();
    private boolean inventoryOpen = false;
    private boolean inventoryWasOpen = false;
    private final Set<Integer> usedSlots = new HashSet<>();

    public InvManager() {
        super("InvManager", ModuleEnum.Player);
        addValue(autoArmor);
        addValue(switchSword);
        addValue(swordSlot);
        addValue(switchPickaxe);
        addValue(pickaxeSlot);
        addValue(switchAxe);
        addValue(axeSlot);
        addValue(switchBow);
        addValue(bowSlot);
        addValue(switchBlock);
        addValue(blockSlot);
        addValue(throwItems);
        addValue(delay);
        addValue(offhandMode);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.getTimeEnum() != TimeEnum.Pre || mc.player == null || mc.gameMode == null)
            return;

        boolean currentInventoryOpen = mc.screen instanceof InventoryScreen;

        if (currentInventoryOpen && !inventoryWasOpen) {
            resetState();
        }
        inventoryWasOpen = currentInventoryOpen;

        if (!currentInventoryOpen) {
            resetState();
            return;
        }

        inventoryOpen = true;

        if (!timer.hasTimeElapsed(getRandomDelay(delay), true)) {
            return;
        }

        // 检查槽位冲突
        if (!checkSlotConflicts()) {
            return;
        }

        // 处理自动装备盔甲
        if (autoArmor.enabled) {
            handleAutoArmor();
        }

        // 处理副手物品
        handleOffhand();

        // 切换武器和工具
        if (switchSword.enabled) {
            handleWeaponSwitch();
        }
        if (switchPickaxe.enabled) {
            handleToolSwitch(Items.DIAMOND_PICKAXE, (int)pickaxeSlot.getValue() - 1, ToolType.PICKAXE);
        }
        if (switchAxe.enabled) {
            handleToolSwitch(Items.DIAMOND_AXE, (int)axeSlot.getValue() - 1, ToolType.AXE);
        }
        if (switchBow.enabled) {
            handleBowSwitch();
        }
        if (switchBlock.enabled) {
            handleBlockSwitch();
        }

        // 丢弃无用物品
        if (throwItems.enabled) {
            handleItemThrowing();
        }
    }

    private void resetState() {
        timer.reset();
        inventoryOpen = false;
        usedSlots.clear();
    }

    private boolean checkSlotConflicts() {
        usedSlots.clear();

        List<Pair<BoolValue, FloatValue>> slotConfigs = Arrays.asList(
                Pair.of(switchSword, swordSlot),
                Pair.of(switchPickaxe, pickaxeSlot),
                Pair.of(switchAxe, axeSlot),
                Pair.of(switchBow, bowSlot),
                Pair.of(switchBlock, blockSlot)
        );

        for (Pair<BoolValue, FloatValue> config : slotConfigs) {
            if (config.left().enabled) {
                int slot = (int)config.right().getValue() - 1;
                if (usedSlots.contains(slot)) {
                    return false; // 槽位冲突
                }
                usedSlots.add(slot);
            }
        }

        return true;
    }

    private void handleAutoArmor() {
        Inventory inventory = mc.player.getInventory();

        // 检查每个盔甲槽位
        for (ArmorType type : ArmorType.values()) {
            int equippedSlot = type.getInventorySlot();
            ItemStack equippedItem = inventory.getItem(equippedSlot);
            float equippedScore = calculateArmorScore(equippedItem);

            // 寻找更好的盔甲
            int bestSlot = -1;
            float bestScore = equippedScore;

            for (int slot = 9; slot < 36; slot++) { // 背包槽位
                ItemStack stack = inventory.getItem(slot);
                if (type.matchesItem(stack.getItem())) {
                    float score = calculateArmorScore(stack);
                    if (score > bestScore) {
                        bestScore = score;
                        bestSlot = slot;
                    }
                }
            }

            // 检查快捷栏（跳过已使用的槽位）
            for (int slot = 0; slot < 9; slot++) {
                if (usedSlots.contains(slot)) continue;

                ItemStack stack = inventory.getItem(slot);
                if (type.matchesItem(stack.getItem())) {
                    float score = calculateArmorScore(stack);
                    if (score > bestScore) {
                        bestScore = score;
                        bestSlot = slot;
                    }
                }
            }

            if (bestSlot != -1) {
                swapItems(bestSlot, equippedSlot);
                return; // 一次只处理一件装备
            }
        }
    }

    private void handleOffhand() {
        ItemStack offhandItem = mc.player.getInventory().getItem(40); // 副手槽位

        switch (offhandMode.get()) {
            case "Golden Apple":
                handleOffhandItem(Items.GOLDEN_APPLE, offhandItem);
                break;
            case "Projectile":
                handleOffhandProjectile(offhandItem);
                break;
            case "Rod":
                handleOffhandItem(Items.FISHING_ROD, offhandItem);
                break;
            case "Block":
                handleOffhandBlock(offhandItem);
                break;
        }
    }

    private void handleOffhandItem(Item targetItem, ItemStack currentOffhand) {
        if (currentOffhand.getItem() != targetItem) {
            int slot = findItemSlot(targetItem);
            if (slot != -1) {
                swapWithOffhand(slot);
            }
        }
    }

    private void handleOffhandProjectile(ItemStack currentOffhand) {
        // 优先选择数量多的投掷物
        int bestSlot = -1;
        int bestCount = currentOffhand.getCount();

        for (int slot = 0; slot < 36; slot++) {
            if (usedSlots.contains(slot % 9)) continue;

            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.getItem() == Items.SNOWBALL || stack.getItem() == Items.EGG) {
                if (stack.getCount() > bestCount) {
                    bestCount = stack.getCount();
                    bestSlot = slot;
                }
            }
        }

        if (bestSlot != -1) {
            swapWithOffhand(bestSlot);
        }
    }

    private void handleOffhandBlock(ItemStack currentOffhand) {
        int bestSlot = -1;
        int bestCount = currentOffhand.getCount();

        for (int slot = 0; slot < 36; slot++) {
            if (usedSlots.contains(slot % 9)) continue;

            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.getItem() instanceof BlockItem) {
                if (stack.getCount() > bestCount) {
                    bestCount = stack.getCount();
                    bestSlot = slot;
                }
            }
        }

        if (bestSlot != -1) {
            swapWithOffhand(bestSlot);
        }
    }

    private void handleWeaponSwitch() {
        int targetSlot = (int)swordSlot.getValue() - 1;
        ItemStack currentWeapon = mc.player.getInventory().getItem(targetSlot);
        float currentDamage = calculateWeaponDamage(currentWeapon);

        int bestSlot = -1;
        float bestDamage = currentDamage;

        // 寻找更好的武器
        for (int slot = 0; slot < 36; slot++) {
            if (slot % 9 == targetSlot) continue; // 跳过目标槽位本身

            ItemStack stack = mc.player.getInventory().getItem(slot);
            float damage = calculateWeaponDamage(stack);
            if (damage > bestDamage) {
                bestDamage = damage;
                bestSlot = slot;
            }
        }

        if (bestSlot != -1) {
            swapItems(bestSlot, targetSlot);
        }
    }

    private void handleToolSwitch(Item baseItem, int targetSlot, ToolType toolType) {
        ItemStack currentTool = mc.player.getInventory().getItem(targetSlot);
        float currentScore = calculateToolScore(currentTool, toolType);

        int bestSlot = -1;
        float bestScore = currentScore;

        for (int slot = 0; slot < 36; slot++) {
            if (slot % 9 == targetSlot) continue;

            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (toolType.matchesItem(stack.getItem())) {
                float score = calculateToolScore(stack, toolType);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = slot;
                }
            }
        }

        if (bestSlot != -1) {
            swapItems(bestSlot, targetSlot);
        }
    }

    private void handleBowSwitch() {
        int targetSlot = (int)bowSlot.getValue() - 1;
        ItemStack currentBow = mc.player.getInventory().getItem(targetSlot);
        float currentScore = calculateBowScore(currentBow);

        int bestSlot = -1;
        float bestScore = currentScore;

        for (int slot = 0; slot < 36; slot++) {
            if (slot % 9 == targetSlot) continue;

            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (isRangedWeapon(stack)) {
                float score = calculateBowScore(stack);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = slot;
                }
            }
        }

        if (bestSlot != -1) {
            swapItems(bestSlot, targetSlot);
        }
    }

    private void handleBlockSwitch() {
        int targetSlot = (int)blockSlot.getValue() - 1;
        ItemStack currentBlock = mc.player.getInventory().getItem(targetSlot);
        int currentCount = currentBlock.getCount();

        int bestSlot = -1;
        int bestCount = currentCount;

        for (int slot = 0; slot < 36; slot++) {
            if (slot % 9 == targetSlot) continue;

            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.getItem() instanceof BlockItem && stack.getCount() > bestCount) {
                bestCount = stack.getCount();
                bestSlot = slot;
            }
        }

        if (bestSlot != -1) {
            swapItems(bestSlot, targetSlot);
        }
    }

    private void handleItemThrowing() {
        Inventory inventory = mc.player.getInventory();

        // 从背包开始检查（9-35）
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && isUselessItem(stack)) {
                throwItem(slot);
                return; // 一次只丢弃一个物品
            }
        }

        // 检查快捷栏（跳过配置的槽位）
        for (int slot = 0; slot < 9; slot++) {
            if (usedSlots.contains(slot)) continue;

            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && isUselessItem(stack)) {
                throwItem(slot);
                return;
            }
        }
    }

    // 新的判断方法
    private boolean isArmor(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.has(DataComponents.EQUIPPABLE) ||
                stack.getItem().toString().toLowerCase().contains("helmet") ||
                stack.getItem().toString().toLowerCase().contains("chestplate") ||
                stack.getItem().toString().toLowerCase().contains("leggings") ||
                stack.getItem().toString().toLowerCase().contains("boots");
    }

    private boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.has(DataComponents.TOOL) || stack.has(DataComponents.ATTRIBUTE_MODIFIERS)) {
            ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
            if (modifiers != null) {
                // 检查是否有攻击伤害属性
                return modifiers.modifiers().stream()
                        .anyMatch(modifier -> modifier.attribute().value().getDescriptionId().contains("attack_damage"));
            }
        }
        return stack.getItem().toString().toLowerCase().contains("sword") ||
                stack.getItem().toString().toLowerCase().contains("axe");
    }

    private boolean isTool(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.has(DataComponents.TOOL)) {
            return true;
        }
        return stack.getItem().toString().toLowerCase().contains("pickaxe") ||
                stack.getItem().toString().toLowerCase().contains("axe") ||
                stack.getItem().toString().toLowerCase().contains("shovel") ||
                stack.getItem().toString().toLowerCase().contains("hoe");
    }

    private boolean isRangedWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String itemName = stack.getItem().toString().toLowerCase();
        return itemName.contains("bow") || itemName.contains("crossbow");
    }

    private boolean isUselessItem(ItemStack stack) {
        if (stack.isEmpty()) return true;

        Item item = stack.getItem();

        // 使用新的组件系统判断物品类型
        if (isArmor(stack) || isWeapon(stack) || isTool(stack) ||
                item == Items.GOLDEN_APPLE || item == Items.ENDER_PEARL ||
                item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET ||
                item instanceof BlockItem) {
            return false;
        }

        // 材料类物品不丢弃
        return item != Items.DIAMOND && item != Items.EMERALD &&
                item != Items.GOLD_INGOT && item != Items.IRON_INGOT &&
                item != Items.NETHERITE_INGOT;
    }

    // 工具方法
    private void swapItems(int sourceSlot, int targetSlot) {
        if (sourceSlot < 0 || targetSlot < 0) return;

        mc.gameMode.handleInventoryMouseClick(
                mc.player.containerMenu.containerId,
                sourceSlot < 9 ? sourceSlot + 36 : sourceSlot,
                targetSlot,
                net.minecraft.world.inventory.ClickType.SWAP,
                mc.player
        );
        timer.reset();
    }

    private void swapWithOffhand(int sourceSlot) {
        mc.gameMode.handleInventoryMouseClick(
                mc.player.containerMenu.containerId,
                sourceSlot < 9 ? sourceSlot + 36 : sourceSlot,
                40,
                net.minecraft.world.inventory.ClickType.SWAP,
                mc.player
        );
        timer.reset();
    }

    private void throwItem(int slot) {
        mc.gameMode.handleInventoryMouseClick(
                mc.player.containerMenu.containerId,
                slot < 9 ? slot + 36 : slot,
                1,
                net.minecraft.world.inventory.ClickType.THROW,
                mc.player
        );
        timer.reset();
    }

    private int findItemSlot(Item item) {
        Inventory inventory = mc.player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            if (usedSlots.contains(slot % 9)) continue;
            if (inventory.getItem(slot).getItem() == item) {
                return slot;
            }
        }
        return -1;
    }

    private int getRandomDelay(IntRangeValue delay) {
        return delay.getMinValue() + (int)(Math.random() * (delay.getMaxValue() - delay.getMinValue()));
    }

    // 价值计算方法 - 适配 NeoForge 1.21.9
    private float calculateArmorScore(ItemStack stack) {
        if (stack.isEmpty() || !isArmor(stack)) return 0;

        float score = 0;

        // 使用组件获取防御值
        if (stack.has(DataComponents.EQUIPPABLE)) {
            var armor = stack.get(DataComponents.EQUIPPABLE);
//            score += armor.defense();
        }

        // 材质加成
        String material = stack.getItem().toString().toLowerCase();
        if (material.contains("netherite")) score += 10;
        else if (material.contains("diamond")) score += 8;
        else if (material.contains("iron")) score += 5;
        else if (material.contains("gold")) score += 3;

        // 附魔加成
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            for (var entry : enchantments.entrySet()) {
                score += entry.getIntValue() * 2;
            }
        }

        // 耐久度考虑
        if (stack.isDamageableItem()) {
            float durability = (float)(stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
            score *= durability;
        }

        return score;
    }

    private float calculateWeaponDamage(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        float damage = 0;

        // 使用属性修饰器获取伤害值
        var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            for (var modifier : modifiers.modifiers()) {
//                if (modifier.attribute().value().getDescriptionId().contains("attack_damage")) {
//                    damage += modifier.attribute().value().;
//                }
            }
        }

        // 附魔加成
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            for (var entry : enchantments.entrySet()) {
                damage += entry.getIntValue();
            }
        }

        return damage;
    }

    private float calculateToolScore(ItemStack stack, ToolType toolType) {
        if (stack.isEmpty() || !toolType.matchesItem(stack.getItem())) return 0;

        float score = 0;

        // 材质加成
        String material = stack.getItem().toString().toLowerCase();
        if (material.contains("netherite")) score += 30;
        else if (material.contains("diamond")) score += 20;
        else if (material.contains("iron")) score += 15;
        else if (material.contains("gold")) score += 10;

        // 效率附魔
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            for (var entry : enchantments.entrySet()) {
                score += entry.getIntValue() * 5;
            }
        }

        return score;
    }

    private float calculateBowScore(ItemStack stack) {
        if (stack.isEmpty() || !isRangedWeapon(stack)) return 0;

        float score = 0;

        // 基础分数
        if (stack.getItem().toString().toLowerCase().contains("crossbow")) score += 10;
        else score += 5;

        // 附魔加成
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            for (var entry : enchantments.entrySet()) {
                score += entry.getIntValue() * 3;
            }
        }

        return score;
    }

    // 枚举和内部类
    private enum ArmorType {
        HELMET(5, "helmet"),
        CHESTPLATE(6, "chestplate"),
        LEGGINGS(7, "leggings"),
        BOOTS(8, "boots");

        private final int inventorySlot;
        private final String typeName;

        ArmorType(int slot, String name) {
            this.inventorySlot = slot;
            this.typeName = name;
        }

        public int getInventorySlot() {
            return inventorySlot;
        }

        public boolean matchesItem(Item item) {
            return item.toString().toLowerCase().contains(typeName);
        }
    }

    private enum ToolType {
        PICKAXE("pickaxe"),
        AXE("axe"),
        SHOVEL("shovel");

        private final String typeName;

        ToolType(String name) {
            this.typeName = name;
        }

        public boolean matchesItem(Item item) {
            boolean contains = item.toString().toLowerCase().contains(typeName);
            return contains ||
                    (item.getDefaultInstance().has(DataComponents.TOOL) &&
                            contains);
        }
    }

    // 简单的Pair实现
    private record Pair<L, R>(L left, R right) {
        public static <L, R> Pair<L, R> of(L left, R right) {
            return new Pair<>(left, right);
        }
    }
}
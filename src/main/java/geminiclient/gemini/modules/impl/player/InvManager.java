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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.core.Holder;

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
    private final Set<Integer> usedSlots = new HashSet<>(); // 快捷栏槽位 (0-8)

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
            handleToolSwitch(ToolType.PICKAXE, (int)pickaxeSlot.getValue() - 1);
        }
        if (switchAxe.enabled) {
            handleToolSwitch(ToolType.AXE, (int)axeSlot.getValue() - 1);
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

        for (ArmorType type : ArmorType.values()) {
            int playerArmorIndex = type.getPlayerArmorIndex(); // 36-39
            ItemStack equippedItem = inventory.getItem(playerArmorIndex);
            float equippedScore = calculateArmorScore(equippedItem);

            int bestSlot = -1;
            float bestScore = equippedScore;

            // 搜索背包 (9-35)
            for (int slot = 9; slot < 36; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (type.matchesItem(stack)) {
                    float score = calculateArmorScore(stack);
                    if (score > bestScore) {
                        bestScore = score;
                        bestSlot = slot;
                    }
                }
            }

            // 搜索快捷栏 (0-8)，跳过被占用的槽位
            for (int slot = 0; slot < 9; slot++) {
                if (usedSlots.contains(slot)) continue;
                ItemStack stack = inventory.getItem(slot);
                if (type.matchesItem(stack)) {
                    float score = calculateArmorScore(stack);
                    if (score > bestScore) {
                        bestScore = score;
                        bestSlot = slot;
                    }
                }
            }

            if (bestSlot != -1) {
                int sourceContainerSlot = bestSlot < 9 ? bestSlot + 36 : bestSlot;
                swapItems(sourceContainerSlot, type.getInventorySlot()); // 目标为容器菜单盔甲槽 5-8
                return; // 一次只处理一件装备
            }
        }
    }

    private void handleOffhand() {
        ItemStack offhandItem = mc.player.getInventory().getItem(40); // 副手槽位在玩家背包中是40

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
        int targetSlot = (int)swordSlot.getValue() - 1; // 玩家背包索引 0-8
        ItemStack currentWeapon = mc.player.getInventory().getItem(targetSlot);
        float currentDamage = calculateWeaponDamage(currentWeapon);

        int bestSlot = -1;
        float bestDamage = currentDamage;

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
            int sourceContainerSlot = bestSlot < 9 ? bestSlot + 36 : bestSlot;
            int targetContainerSlot = 36 + targetSlot; // 快捷栏在容器菜单中是36-44
            swapItems(sourceContainerSlot, targetContainerSlot);
        }
    }

    private void handleToolSwitch(ToolType toolType, int targetSlot) { // targetSlot 玩家背包索引 0-8
        ItemStack currentTool = mc.player.getInventory().getItem(targetSlot);
        float currentScore = calculateToolScore(currentTool, toolType);

        int bestSlot = -1;
        float bestScore = currentScore;

        for (int slot = 0; slot < 36; slot++) {
            if (slot % 9 == targetSlot) continue;

            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (toolType.matchesItem(stack)) {
                float score = calculateToolScore(stack, toolType);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = slot;
                }
            }
        }

        if (bestSlot != -1) {
            int sourceContainerSlot = bestSlot < 9 ? bestSlot + 36 : bestSlot;
            int targetContainerSlot = 36 + targetSlot;
            swapItems(sourceContainerSlot, targetContainerSlot);
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
            int sourceContainerSlot = bestSlot < 9 ? bestSlot + 36 : bestSlot;
            int targetContainerSlot = 36 + targetSlot;
            swapItems(sourceContainerSlot, targetContainerSlot);
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
            int sourceContainerSlot = bestSlot < 9 ? bestSlot + 36 : bestSlot;
            int targetContainerSlot = 36 + targetSlot;
            swapItems(sourceContainerSlot, targetContainerSlot);
        }
    }

    private void handleItemThrowing() {
        Inventory inventory = mc.player.getInventory();

        // 从背包开始检查（9-35）
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && isUselessItem(stack)) {
                throwItem(slot);
                return;
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

    // 物品类型判断
    private boolean isArmor(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.has(DataComponents.EQUIPPABLE);
    }

    private boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // 检查是否有攻击伤害属性修饰符
        var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            for (var entry : modifiers.modifiers()) {
                if (entry.attribute().is(Attributes.ATTACK_DAMAGE)) {
                    return true;
                }
            }
        }
        // 后备：根据名称
        String name = stack.getItem().toString().toLowerCase();
        return name.contains("sword") || name.contains("axe");
    }

    private boolean isTool(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.has(DataComponents.TOOL)) return true;
        String name = stack.getItem().toString().toLowerCase();
        return name.contains("pickaxe") || name.contains("axe") ||
                name.contains("shovel") || name.contains("hoe");
    }

    private boolean isRangedWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = stack.getItem().toString().toLowerCase();
        return name.contains("bow") || name.contains("crossbow");
    }

    private boolean isUselessItem(ItemStack stack) {
        if (stack.isEmpty()) return true;

        Item item = stack.getItem();

        // 保留有用的物品
        if (isArmor(stack) || isWeapon(stack) || isTool(stack) || isRangedWeapon(stack) ||
                item == Items.GOLDEN_APPLE || item == Items.ENDER_PEARL ||
                item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET ||
                item instanceof BlockItem) {
            return false;
        }

        // 保留矿物/锭
        if (item == Items.DIAMOND || item == Items.EMERALD ||
                item == Items.GOLD_INGOT || item == Items.IRON_INGOT ||
                item == Items.NETHERITE_INGOT || item == Items.COAL ||
                item == Items.REDSTONE || item == Items.LAPIS_LAZULI ||
                item == Items.QUARTZ || item == Items.AMETHYST_SHARD ||
                item == Items.COPPER_INGOT) {
            return false;
        }

        // 保留食物（通过食物组件判断）
        if (stack.has(DataComponents.FOOD)) {
            return false;
        }

        return true;
    }

    // 工具方法
    private void swapItems(int sourceContainerSlot, int targetContainerSlot) {
        if (sourceContainerSlot < 0 || targetContainerSlot < 0) return;

        mc.gameMode.handleInventoryMouseClick(
                mc.player.containerMenu.containerId,
                sourceContainerSlot,
                targetContainerSlot,
                net.minecraft.world.inventory.ClickType.SWAP,
                mc.player
        );
        timer.reset();
    }

    private void swapWithOffhand(int sourcePlayerSlot) { // sourcePlayerSlot 玩家背包索引 0-35
        int sourceContainerSlot = sourcePlayerSlot < 9 ? sourcePlayerSlot + 36 : sourcePlayerSlot;
        mc.gameMode.handleInventoryMouseClick(
                mc.player.containerMenu.containerId,
                sourceContainerSlot,
                40, // 副手在容器菜单中的索引
                net.minecraft.world.inventory.ClickType.SWAP,
                mc.player
        );
        timer.reset();
    }

    private void throwItem(int playerSlot) {
        int containerSlot = playerSlot < 9 ? playerSlot + 36 : playerSlot;
        mc.gameMode.handleInventoryMouseClick(
                mc.player.containerMenu.containerId,
                containerSlot,
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

    // 价值计算方法
    private float calculateArmorScore(ItemStack stack) {
        if (stack.isEmpty() || !isArmor(stack)) return 0;

        float score = 0;

        // 从属性修饰符获取护甲值和韧性
        var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            for (var entry : modifiers.modifiers()) {
                Holder<Attribute> attribute = entry.attribute();
                if (attribute.is(Attributes.ARMOR)) {
                    score += entry.modifier().amount();
                } else if (attribute.is(Attributes.ARMOR_TOUGHNESS)) {
                    score += entry.modifier().amount() * 1.5f;
                }
            }
        }

        // 材质加成
        String material = stack.getItem().toString().toLowerCase();
        if (material.contains("netherite")) score += 10;
        else if (material.contains("diamond")) score += 8;
        else if (material.contains("iron")) score += 5;
        else if (material.contains("gold")) score += 3;
        else if (material.contains("leather")) score += 1;

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

        var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            for (var entry : modifiers.modifiers()) {
                if (entry.attribute().is(Attributes.ATTACK_DAMAGE)) {
                    damage += entry.modifier().amount();
                }
            }
        }

        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            for (var entry : enchantments.entrySet()) {
                damage += entry.getIntValue(); // 简单叠加
            }
        }

        return damage;
    }

    private float calculateToolScore(ItemStack stack, ToolType toolType) {
        if (stack.isEmpty() || !toolType.matchesItem(stack)) return 0;

        float score = 0;

        var tool = stack.get(DataComponents.TOOL);
        if (tool != null) {
            score += tool.defaultMiningSpeed();
        }

        String material = stack.getItem().toString().toLowerCase();
        if (material.contains("netherite")) score += 30;
        else if (material.contains("diamond")) score += 20;
        else if (material.contains("iron")) score += 15;
        else if (material.contains("gold")) score += 10;
        else if (material.contains("stone")) score += 5;
        else if (material.contains("wood")) score += 2;

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

        if (stack.getItem().toString().toLowerCase().contains("crossbow")) score += 10;
        else score += 5;

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
        HELMET(5, 36, EquipmentSlot.HEAD),
        CHESTPLATE(6, 37, EquipmentSlot.CHEST),
        LEGGINGS(7, 38, EquipmentSlot.LEGS),
        BOOTS(8, 39, EquipmentSlot.FEET);

        private final int containerSlot;   // 容器菜单索引
        private final int playerSlot;      // 玩家背包索引
        private final EquipmentSlot equipmentSlot;

        ArmorType(int containerSlot, int playerSlot, EquipmentSlot equipmentSlot) {
            this.containerSlot = containerSlot;
            this.playerSlot = playerSlot;
            this.equipmentSlot = equipmentSlot;
        }

        public int getInventorySlot() {
            return containerSlot; // 用于容器菜单操作
        }

        public int getPlayerArmorIndex() {
            return playerSlot;    // 用于从玩家背包获取物品
        }

        public boolean matchesItem(ItemStack stack) {
            var equippable = stack.get(DataComponents.EQUIPPABLE);
            if (equippable != null) {
                return equippable.slot() == this.equipmentSlot;
            }
            // 后备：通过名称判断
            return stack.getItem().toString().toLowerCase().contains(this.name().toLowerCase());
        }
    }

    private enum ToolType {
        PICKAXE("pickaxe"),
        AXE("axe"),
        SHOVEL("shovel"),
        HOE("hoe");

        private final String typeName;

        ToolType(String name) {
            this.typeName = name;
        }

        public boolean matchesItem(ItemStack stack) {
            stack.has(DataComponents.TOOL);
            return stack.getItem().toString().toLowerCase().contains(typeName);
        }
    }

    private record Pair<L, R>(L left, R right) {
        public static <L, R> Pair<L, R> of(L left, R right) {
            return new Pair<>(left, right);
        }
    }
}
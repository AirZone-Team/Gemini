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
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;

import java.util.*;

public class InvManager extends Module {
    // 基础配置
    private final BoolValue autoArmor = new BoolValue("Auto Armor", true);
    private final BoolValue throwItems = new BoolValue("Throw Items", true);
    private final IntRangeValue delay = new IntRangeValue("Delay", 100, 300, 50, 1000);
    private final ListValue offhandMode = new ListValue("Offhand", "None", new String[]{
            "None", "Golden Apple", "Projectile", "Block"
    });

    // 快捷栏配置 (1-9 栏全支持)
    private final BoolValue sortSword = new BoolValue("Sort Sword", true);
    private final FloatValue swordSlot = new FloatValue("Sword Slot", 1.0F, 1.0F, 9.0F);
    private final BoolValue sortPickaxe = new BoolValue("Sort Pickaxe", true);
    private final FloatValue pickaxeSlot = new FloatValue("Pickaxe Slot", 2.0F, 1.0F, 9.0F);
    private final BoolValue sortAxe = new BoolValue("Sort Axe", true);
    private final FloatValue axeSlot = new FloatValue("Axe Slot", 3.0F, 1.0F, 9.0F);
    private final BoolValue sortShovel = new BoolValue("Sort Shovel", true);
    private final FloatValue shovelSlot = new FloatValue("Shovel Slot", 4.0F, 1.0F, 9.0F);
    private final BoolValue sortBow = new BoolValue("Sort Bow", true);
    private final FloatValue bowSlot = new FloatValue("Bow Slot", 5.0F, 1.0F, 9.0F);
    private final BoolValue sortBlock = new BoolValue("Sort Block", true);
    private final FloatValue blockSlot = new FloatValue("Block Slot", 6.0F, 1.0F, 9.0F);
    private final BoolValue sortFood = new BoolValue("Sort Food", true);
    private final FloatValue foodSlot = new FloatValue("Food Slot", 7.0F, 1.0F, 9.0F);
    private final BoolValue sortPearl = new BoolValue("Sort Pearl", true);
    private final FloatValue pearlSlot = new FloatValue("Pearl Slot", 8.0F, 1.0F, 9.0F);
    private final BoolValue sortGApple = new BoolValue("Sort GApple", true);
    private final FloatValue gAppleSlot = new FloatValue("GApple Slot", 9.0F, 1.0F, 9.0F);

    // 内部状态
    private final TimerUtils timer = new TimerUtils();
    private boolean inventoryWasOpen = false;
    private final Set<Integer> usedSlots = new HashSet<>();
    private final Map<String, Integer> bestItems = new HashMap<>(); // 类别 -> 最佳物品所在玩家背包索引(0-40)

    public InvManager() {
        super("InvManager", ModuleEnum.Player);
        addValue(autoArmor); addValue(throwItems); addValue(delay); addValue(offhandMode);
        addValue(sortSword); addValue(swordSlot);
        addValue(sortPickaxe); addValue(pickaxeSlot);
        addValue(sortAxe); addValue(axeSlot);
        addValue(sortShovel); addValue(shovelSlot);
        addValue(sortBow); addValue(bowSlot);
        addValue(sortBlock); addValue(blockSlot);
        addValue(sortFood); addValue(foodSlot);
        addValue(sortPearl); addValue(pearlSlot);
        addValue(sortGApple); addValue(gAppleSlot);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.getTimeEnum() != TimeEnum.Pre || mc.player == null || mc.gameMode == null) return;

        boolean currentInventoryOpen = mc.screen instanceof InventoryScreen;
        if (currentInventoryOpen && !inventoryWasOpen) {
            timer.reset();
        }
        inventoryWasOpen = currentInventoryOpen;

        if (!currentInventoryOpen) return;

        // 验证设置是否存在冲突
        if (!checkSlotConflicts()) return;

        // 每次动作需要满足延迟（防止被反作弊检测）
        if (!timer.hasTimeElapsed(getRandomDelay(delay), false)) return;

        // 1. 扫描整个背包，找出每类物品的“最强者”
        populateBestItems();

        // 2. 丢弃多余的劣质品或垃圾（每次 Tick 只丢一个）
        if (throwItems.enabled && handleThrowing()) return;

        // 3. 自动穿戴护甲
        if (autoArmor.enabled) {
            if (equipBestArmor("helmet", 39)) return;
            if (equipBestArmor("chestplate", 38)) return;
            if (equipBestArmor("leggings", 37)) return;
            if (equipBestArmor("boots", 36)) return;
        }

        // 4. 副手管理
        if (handleOffhand()) return;

        // 5. 快捷栏整理 (将最强物品移动到设定槽位)
        if (sortHotbar("sword", sortSword, swordSlot)) return;
        if (sortHotbar("pickaxe", sortPickaxe, pickaxeSlot)) return;
        if (sortHotbar("axe", sortAxe, axeSlot)) return;
        if (sortHotbar("shovel", sortShovel, shovelSlot)) return;
        if (sortHotbar("bow", sortBow, bowSlot)) return;
        if (sortHotbar("block", sortBlock, blockSlot)) return;
        if (sortHotbar("food", sortFood, foodSlot)) return;
        if (sortHotbar("pearl", sortPearl, pearlSlot)) return;
        if (sortHotbar("gapple", sortGApple, gAppleSlot)) return;
    }

    // ================= 核心分析逻辑 =================

    private void populateBestItems() {
        bestItems.clear();
        Map<String, Float> bestScores = new HashMap<>();
        Inventory inv = mc.player.getInventory();

        for (int i = 0; i <= 40; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            String category = getItemCategory(stack);
            if (category == null) continue;

            float score = getCategoryScore(stack, category);

            // 如果此类别还没记录，或者当前物品分数更高，则更新为“最佳”
            if (!bestItems.containsKey(category) || score > bestScores.get(category)) {
                bestItems.put(category, i);
                bestScores.put(category, score);
            }
        }
    }

    // 处理丢东西（保留好的，丢掉坏的）
    private boolean handleThrowing() {
        Inventory inv = mc.player.getInventory();
        // 遍历所有非装备栏槽位 (0-35)
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            boolean shouldThrow = isJunkItem(stack);
            String category = getItemCategory(stack);

            // 如果是装备/武器/工具，但不是“最强者”，则说明是多余的劣质品
            if (!shouldThrow && category != null && isGearCategory(category)) {
                if (bestItems.get(category) != null && bestItems.get(category) != i) {
                    shouldThrow = true;
                }
            }

            if (shouldThrow) {
                throwItem(i);
                return true; // 每 tick 丢弃一个
            }
        }
        return false;
    }

    // ================= 背包操作行为 =================

    private boolean sortHotbar(String category, BoolValue enabledCheck, FloatValue slotCheck) {
        if (!enabledCheck.enabled) return false;

        int targetHotbarIndex = (int) slotCheck.getValue() - 1; // 0-8
        Integer bestSlot = bestItems.get(category);

        if (bestSlot != null && bestSlot != targetHotbarIndex) {
            moveToHotbar(bestSlot, targetHotbarIndex);
            return true;
        }
        return false;
    }

    private boolean equipBestArmor(String category, int armorInvIndex) {
        Integer bestSlot = bestItems.get(category);
        // 如果最好的护甲存在，且目前不在对应的护甲槽位里
        if (bestSlot != null && bestSlot != armorInvIndex) {
            shiftClick(bestSlot);
            return true;
        }
        return false;
    }

    private boolean handleOffhand() {
        String mode = offhandMode.get();
        if ("None".equals(mode)) return false;

        String targetCategory = switch (mode) {
            case "Golden Apple" -> "gapple";
            case "Projectile" -> "projectile";
            case "Block" -> "block";
            default -> null;
        };

        if (targetCategory != null) {
            Integer bestSlot = bestItems.get(targetCategory);
            if (bestSlot != null && bestSlot != 40) { // 40 是副手在 Inventory 中的下标
                // 按键 40 并使用 SWAP 可以在底层将对应槽位的物品与副手互换
                windowClick(getContainerSlot(bestSlot), 40, ContainerInput.SWAP);
                return true;
            }
        }
        return false;
    }

    // ================= 类别与评分判断 =================

    private String getItemCategory(ItemStack stack) {
        Item item = stack.getItem();
        String name = item.toString().toLowerCase();

        // 护甲
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null) {
            if (equippable.slot() == EquipmentSlot.HEAD) return "helmet";
            if (equippable.slot() == EquipmentSlot.CHEST) return "chestplate";
            if (equippable.slot() == EquipmentSlot.LEGS) return "leggings";
            if (equippable.slot() == EquipmentSlot.FEET) return "boots";
        }

        // 武器 / 工具
        if (name.contains("sword")) return "sword";
        if (name.contains("pickaxe")) return "pickaxe";
        if (name.contains("axe") && !name.contains("pickaxe")) return "axe";
        if (name.contains("shovel")) return "shovel";
        if (name.contains("bow") || name.contains("crossbow")) return "bow";

        // 消耗品 / 投掷物 / 方块
        if (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE) return "gapple";
        if (item == Items.ENDER_PEARL) return "pearl";
        if (item == Items.SNOWBALL || item == Items.EGG) return "projectile";
        if (stack.has(DataComponents.FOOD)) return "food";
        if (item instanceof BlockItem) return "block";

        return null; // 未分类物品 (例如矿物、材料，保留但不管)
    }

    private boolean isGearCategory(String category) {
        List<String> gears = Arrays.asList("helmet", "chestplate", "leggings", "boots",
                "sword", "pickaxe", "axe", "shovel", "bow");
        return gears.contains(category);
    }

    private float getCategoryScore(ItemStack stack, String category) {
        // 对于消耗品和方块，数量多的优先
        if (!isGearCategory(category)) {
            return stack.getCount();
        }

        return switch (category) {
            case "sword" -> calculateWeaponDamage(stack);
            case "pickaxe", "axe", "shovel" -> calculateToolScore(stack);
            case "bow" -> calculateBowScore(stack);
            case "helmet", "chestplate", "leggings", "boots" -> calculateArmorScore(stack);
            default -> 0;
        };
    }

    private boolean isJunkItem(ItemStack stack) {
        Item item = stack.getItem();
        // 在这里添加你认为无用的垃圾（种子、腐肉等），这类物品直接丢掉
        return item == Items.ROTTEN_FLESH || item == Items.SPIDER_EYE ||
                item == Items.POISONOUS_POTATO || item == Items.WHEAT_SEEDS;
    }

    // ================= NeoForge/Vanilla 库存交互 =================

    /**
     * 将 Vanilla Inventory (0-40) 的索引转换为 InventoryMenu 容器中的 SlotId
     */
    private int getContainerSlot(int inventoryIndex) {
        if (inventoryIndex >= 0 && inventoryIndex <= 8) return 36 + inventoryIndex; // 快捷栏
        if (inventoryIndex >= 9 && inventoryIndex <= 35) return inventoryIndex; // 主背包
        if (inventoryIndex == 36) return 8; // 靴子
        if (inventoryIndex == 37) return 7; // 护腿
        if (inventoryIndex == 38) return 6; // 胸甲
        if (inventoryIndex == 39) return 5; // 头盔
        if (inventoryIndex == 40) return 45; // 副手
        return -1;
    }

    private void windowClick(int containerSlotId, int mouseButton, ContainerInput type) {
        mc.gameMode.handleInventoryButtonClick(containerSlotId, mouseButton);
        timer.reset(); // 重置延迟
    }

    private void moveToHotbar(int inventoryIndex, int hotbarIndex) {
        // SWAP 可以直接将目标容器槽位的物品与快捷栏(0-8)中的物品互换
        windowClick(getContainerSlot(inventoryIndex), hotbarIndex, ContainerInput.SWAP);
    }

    private void throwItem(int inventoryIndex) {
        // mouseButton=1 并且使用 THROW 会丢弃整组物品
        windowClick(getContainerSlot(inventoryIndex), 1, ContainerInput.THROW);
    }

    private void shiftClick(int inventoryIndex) {
        // QUICK_MOVE 即为 Shift 左键
        windowClick(getContainerSlot(inventoryIndex), 0, ContainerInput.QUICK_MOVE);
    }

    // ================= 辅助/计算方法 =================

    private boolean checkSlotConflicts() {
        usedSlots.clear();
        List<Pair<BoolValue, FloatValue>> configs = Arrays.asList(
                Pair.of(sortSword, swordSlot), Pair.of(sortPickaxe, pickaxeSlot), Pair.of(sortAxe, axeSlot),
                Pair.of(sortShovel, shovelSlot), Pair.of(sortBow, bowSlot), Pair.of(sortBlock, blockSlot),
                Pair.of(sortFood, foodSlot), Pair.of(sortPearl, pearlSlot), Pair.of(sortGApple, gAppleSlot)
        );

        for (Pair<BoolValue, FloatValue> config : configs) {
            if (config.left().enabled) {
                int slot = (int)config.right().getValue() - 1;
                if (usedSlots.contains(slot)) return false;
                usedSlots.add(slot);
            }
        }
        return true;
    }

    private int getRandomDelay(IntRangeValue delayValue) {
        return delayValue.getMinValue() + (int)(Math.random() * (delayValue.getMaxValue() - delayValue.getMinValue()));
    }

    private float calculateArmorScore(ItemStack stack) {
        float score = 0;
        var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            for (var entry : modifiers.modifiers()) {
                Holder<Attribute> attribute = entry.attribute();
                if (attribute.is(Attributes.ARMOR)) {
                    score += (float) entry.modifier().amount();
                } else if (attribute.is(Attributes.ARMOR_TOUGHNESS)) {
                    score += (float) (entry.modifier().amount() * 1.5f);
                }
            }
        }

        String material = stack.getItem().toString().toLowerCase();
        if (material.contains("netherite")) score += 10;
        else if (material.contains("diamond")) score += 8;
        else if (material.contains("iron")) score += 5;
        else if (material.contains("gold")) score += 3;
        else if (material.contains("leather")) score += 1;

        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            for (var entry : enchantments.entrySet()) score += entry.getIntValue() * 2;
        }

        if (stack.isDamageableItem()) {
            float durability = (float)(stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
            score *= durability;
        }
        return score;
    }

    private float calculateWeaponDamage(ItemStack stack) {
        float damage = 0;
        var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            for (var entry : modifiers.modifiers()) {
                if (entry.attribute().is(Attributes.ATTACK_DAMAGE)) damage += (float) entry.modifier().amount();
            }
        }
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            for (var entry : enchantments.entrySet()) damage += entry.getIntValue();
        }
        return damage;
    }

    private float calculateToolScore(ItemStack stack) {
        float score = 0;
        var tool = stack.get(DataComponents.TOOL);
        if (tool != null) score += tool.defaultMiningSpeed();

        String material = stack.getItem().toString().toLowerCase();
        if (material.contains("netherite")) score += 30;
        else if (material.contains("diamond")) score += 20;
        else if (material.contains("iron")) score += 15;
        else if (material.contains("gold")) score += 10;
        else if (material.contains("stone")) score += 5;
        else if (material.contains("wood")) score += 2;

        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            for (var entry : enchantments.entrySet()) score += entry.getIntValue() * 5;
        }
        return score;
    }

    private float calculateBowScore(ItemStack stack) {
        float score = 0;
        if (stack.getItem().toString().toLowerCase().contains("crossbow")) score += 10;
        else score += 5;

        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            for (var entry : enchantments.entrySet()) score += entry.getIntValue() * 3;
        }
        return score;
    }

    private record Pair<L, R>(L left, R right) {
        public static <L, R> Pair<L, R> of(L left, R right) {
            return new Pair<>(left, right);
        }
    }
}
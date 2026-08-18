package geminiclient.gemini.modules.impl.player;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.player.invmanager.InvUtils;
import geminiclient.gemini.utils.TimerUtils;
import geminiclient.gemini.values.impl.IntRangeValue;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class Stealer extends Module {
    private static final String CHEST_KEY = "container.chest";
    private static final String LARGE_CHEST_KEY = "container.chestDouble";
    private static final String FALLBACK_CHEST_TITLE = "Chest";

    /** 同一槽位 QUICK_MOVE 连续失败达到该次数后视为卡死（如背包已满），不再重试。 */
    private static final int MAX_FAILED_MOVE_ATTEMPTS = 2;

    private final IntRangeValue openDelay = new IntRangeValue("OpenDelay", 150, 300, 0, 500);
    private final IntRangeValue stealDelay = new IntRangeValue("StealDelay", 100, 200, 0, 500);
    private final IntRangeValue closeDelay = new IntRangeValue("CloseDelay", 100, 200, 0, 500);

    private final TimerUtils openTimer = new TimerUtils();
    private final TimerUtils stealTimer = new TimerUtils();
    private final TimerUtils closeTimer = new TimerUtils();
    private Screen lastScreen;
    private final Set<ItemCategory> processedCategories = EnumSet.noneOf(ItemCategory.class);
    /** 每个箱子槽位已尝试 QUICK_MOVE 的次数，用于识别搬运失败（背包满等）的卡死槽位。 */
    private final Map<Integer, Integer> failedMoveAttempts = new HashMap<>();
    private boolean hasUpgradeableItems = false;
    private boolean waitingForOpenDelay = true;
    private int openDelayMs;
    private int stealDelayMs;
    private int closeDelayMs;

    public Stealer() {
        super("Stealer", ModuleEnum.Player);
        addValue(openDelay);
        addValue(stealDelay);
        addValue(closeDelay);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (shouldSkipProcessing(event))
            return;

        Screen currentScreen = mc.gui.screen();
        if (!(currentScreen instanceof AbstractContainerScreen<?> container)) {
            handleScreenChange(currentScreen);
            processedCategories.clear();
            hasUpgradeableItems = false;
            return;
        }

        if (currentScreen != lastScreen) {
            resetState();
            processedCategories.clear();
            hasUpgradeableItems = false;
        }

        lastScreen = currentScreen;

        if (container.getMenu() instanceof ChestMenu menu && isTargetChest(container)) {
            processChest(menu);
        }
    }

    // ========== Core Logic ==========

    private void processChest(ChestMenu menu) {
        // Wait a configurable interval after opening before touching the container.
        if (waitingForOpenDelay) {
            if (openTimer.hasTimeElapsed(openDelayMs, false)) {
                waitingForOpenDelay = false;
            } else {
                return;
            }
        }

        if (!hasUpgradeableItems) {
            hasUpgradeableItems = hasUpgradeableItems(menu);
        }

        if (shouldCloseChest(menu)) {
            handleChestClosing(closeDelayMs);
        } else {
            closeTimer.reset();
            handleItemStealing(menu, stealDelayMs);
        }
    }

    private boolean hasUpgradeableItems(ChestMenu menu) {
        Map<ItemCategory, Float> bestInvScores = getBestOwnedItemScores();
        int chestSize = menu.getRowCount() * 9;

        for (int i = 0; i < chestSize; i++) {
            if (isSlotStuck(i))
                continue;

            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty()) {
                failedMoveAttempts.remove(i);
                continue;
            }
            if (!isValuableItem(stack))
                continue;

            ItemCategory category = getItemCategory(stack);
            if (processedCategories.contains(category))
                continue;

            if (category == ItemCategory.MATERIAL) {
                return true;
            }

            float score = calculateItemScore(stack);
            if (score > bestInvScores.getOrDefault(category, 0f)) {
                return true;
            }
        }
        return false;
    }

    private void handleChestClosing(int closeDelayMs) {
        if (closeTimer.hasTimeElapsed(closeDelayMs, false)) {
            if (mc.player != null) {
                mc.player.closeContainer();
            }
            closeTimer.reset();
            processedCategories.clear();
            failedMoveAttempts.clear();
            hasUpgradeableItems = false;
        }
    }

    private void handleItemStealing(ChestMenu menu, int stealDelayMs) {
        if (stealTimer.hasTimeElapsed(stealDelayMs, false)) {
            boolean stoleItem = attemptSteal(menu);
            stealTimer.reset();
            if (!stoleItem) {
                hasUpgradeableItems = hasUpgradeableItems(menu);
            }
        }
    }

    private boolean attemptSteal(ChestMenu menu) {
        Optional<SlotValue> valuableSlot = findBestItemSlotToSteal(menu);
        if (valuableSlot.isEmpty())
            return false;

        SlotValue slotValue = valuableSlot.get();
        if (mc.player == null || mc.gameMode == null)
            return false;

        mc.gameMode.handleContainerInput(menu.containerId, slotValue.slotId(), 0,
                ContainerInput.QUICK_MOVE, mc.player);

        // 无法立刻确认服务端是否搬运成功：记录尝试次数，若槽位多次未清空（如背包已满）
        // 则判定卡死并跳过，避免对同槽位无限重发 QUICK_MOVE。
        failedMoveAttempts.merge(slotValue.slotId(), 1, Integer::sum);

        if (slotValue.category() != ItemCategory.MATERIAL) {
            processedCategories.add(slotValue.category());
        }
        return true;
    }

    // ========== Item Selection ==========

    private Optional<SlotValue> findBestItemSlotToSteal(ChestMenu menu) {
        Map<ItemCategory, Float> bestInvScores = getBestOwnedItemScores();
        int chestSize = menu.getRowCount() * 9;
        Map<ItemCategory, SlotValue> upgradeableItems = new EnumMap<>(ItemCategory.class);

        for (int i = 0; i < chestSize; i++) {
            if (isSlotStuck(i))
                continue;

            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty()) {
                failedMoveAttempts.remove(i);
                continue;
            }
            if (!isValuableItem(stack))
                continue;

            ItemCategory category = getItemCategory(stack);
            if (processedCategories.contains(category))
                continue;

            float score = calculateItemScore(stack);
            boolean isUpgrade;
            if (category == ItemCategory.MATERIAL) {
                isUpgrade = true;
            } else {
                isUpgrade = score > bestInvScores.getOrDefault(category, 0f);
            }

            if (isUpgrade) {
                SlotValue currentBest = upgradeableItems.get(category);
                if (currentBest == null || score > currentBest.score()) {
                    upgradeableItems.put(category, new SlotValue(i, score, category));
                }
            }
        }

        // 全局价值最高的优先拿；同分时优先盔甲与武器。
        SlotValue bestSlot = null;
        for (SlotValue sv : upgradeableItems.values()) {
            if (bestSlot == null
                    || sv.score() > bestSlot.score()
                    || (sv.score() == bestSlot.score()
                        && categoryPriority(sv.category()) < categoryPriority(bestSlot.category()))) {
                bestSlot = sv;
            }
        }

        return Optional.ofNullable(bestSlot);
    }

    /** 扫描玩家全部可持有槽位（背包 0-35、盔甲 36-39、副手 40），与箱子中的物品按类别比价值。 */
    private Map<ItemCategory, Float> getBestOwnedItemScores() {
        Map<ItemCategory, Float> bestScores = new EnumMap<>(ItemCategory.class);
        if (mc.player == null)
            return bestScores;

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && isValuableItem(stack)) {
                ItemCategory category = getItemCategory(stack);
                float score = calculateItemScore(stack);
                bestScores.merge(category, score, Math::max);
            }
        }
        return bestScores;
    }

    private boolean isSlotStuck(int slotId) {
        return failedMoveAttempts.getOrDefault(slotId, 0) >= MAX_FAILED_MOVE_ATTEMPTS;
    }

    /** 价值相同时的拿取优先级：值越小越优先（盔甲/武器 > 远程/工具 > 方块/材料）。 */
    private static int categoryPriority(ItemCategory category) {
        return switch (category) {
            case WEAPON, ARMOR_HEAD, ARMOR_CHEST, ARMOR_LEGS, ARMOR_FEET -> 0;
            case BOW, CROSSBOW, TOOL, FISHING_ROD -> 1;
            case BLOCK, MATERIAL -> 2;
        };
    }

    private record SlotValue(int slotId, float score, ItemCategory category) {}

    // ========== Item Value Judgment (using InvUtils) ==========

    private enum ItemCategory {
        WEAPON, TOOL, ARMOR_HEAD, ARMOR_CHEST, ARMOR_LEGS, ARMOR_FEET,
        BOW, CROSSBOW, FISHING_ROD, BLOCK, MATERIAL
    }

    private static final Map<Item, Float> MATERIAL_SCORES = Map.ofEntries(
            Map.entry(Items.ENCHANTED_GOLDEN_APPLE, 200f),
            Map.entry(Items.BEACON, 150f),
            Map.entry(Items.NETHERITE_INGOT, 100f),
            Map.entry(Items.NETHERITE_SCRAP, 100f),
            Map.entry(Items.ENCHANTED_BOOK, 100f),
            Map.entry(Items.DIAMOND, 80f),
            Map.entry(Items.SHULKER_SHELL, 70f),
            Map.entry(Items.ENDER_EYE, 70f),
            Map.entry(Items.EMERALD, 60f),
            Map.entry(Items.GOLDEN_APPLE, 50f),
            Map.entry(Items.GOLD_INGOT, 40f),
            Map.entry(Items.GOLD_NUGGET, 40f),
            Map.entry(Items.EXPERIENCE_BOTTLE, 40f),
            Map.entry(Items.GHAST_TEAR, 35f),
            Map.entry(Items.IRON_INGOT, 30f),
            Map.entry(Items.IRON_NUGGET, 30f),
            Map.entry(Items.ENDER_PEARL, 30f),
            Map.entry(Items.BLAZE_ROD, 25f),
            Map.entry(Items.OBSIDIAN, 20f)
    );

    private boolean isValuableItem(ItemStack stack) {
        if (stack.isEmpty() || InvUtils.isNotItemValid(stack))
            return false;

        Item item = stack.getItem();

        if (InvUtils.isGodItem(stack)
                || InvUtils.isEnchantedGApple(stack)
                || InvUtils.isEndCrystal(stack)
                || InvUtils.isSharpnessAxe(stack))
            return true;

        if (item == Items.GOLDEN_APPLE
                || item == Items.ENDER_PEARL
                || item == Items.BEACON
                || item == Items.ENCHANTED_BOOK
                || item == Items.EXPERIENCE_BOTTLE
                || item == Items.SHULKER_SHELL
                || item == Items.ENDER_EYE
                || item == Items.BLAZE_ROD
                || item == Items.GHAST_TEAR
                || item == Items.OBSIDIAN
                || item == Items.DIAMOND
                || item == Items.NETHERITE_INGOT
                || item == Items.NETHERITE_SCRAP
                || item == Items.EMERALD
                || item == Items.GOLD_INGOT
                || item == Items.GOLD_NUGGET
                || item == Items.IRON_INGOT
                || item == Items.IRON_NUGGET
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item == Items.TRIDENT
                || item == Items.MACE
                || item == Items.FISHING_ROD
                || isStealableBlock(item)) {
            return true;
        }

        if (isLowTierGearWithoutEnchants(stack))
            return false;

        return stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.ARMOR_ENCHANTABLE);
    }

    private float calculateItemScore(ItemStack stack) {
        Item item = stack.getItem();

        // 神棍物品（锋利 100+ 神斧、图腾、末影水晶等）价值最高，必须在工具分支前判断，
        // 否则神斧会被 getToolScore 打成 0 分而永远不被拿取。
        if (InvUtils.isGodItem(stack))
            return 500f;

        if (stack.is(ItemTags.SWORDS))
            return InvUtils.getSwordDamage(stack) * 10f;
        if (InvUtils.isSharpnessAxe(stack))
            return InvUtils.getAxeDamage(stack) * 10f;
        if (item == Items.MACE)
            return 150f;
        if (item == Items.TRIDENT)
            return 80f;

        if (item instanceof BowItem)
            return Math.max(InvUtils.getPowerBowScore(stack), InvUtils.getPunchBowScore(stack)) * 10f;
        if (item instanceof CrossbowItem)
            return InvUtils.getCrossbowScore(stack) * 10f;

        if (stack.is(ItemTags.ARMOR_ENCHANTABLE))
            return InvUtils.getProtection(stack);

        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS))
            return InvUtils.getToolScore(stack) * 100f;

        if (item == Items.FISHING_ROD)
            return 20f;

        // 材料评分必须放在 BlockItem 分支之前：信标、黑曜石等方块型材料
        // 若按方块数量计分会得到极低的分数，导致其在与已有物品比较时被跳过。
        Float materialScore = MATERIAL_SCORES.get(item);
        if (materialScore != null) {
            return stack.getCount() > 1 ? materialScore + stack.getCount() : materialScore;
        }

        if (item instanceof BlockItem)
            return stack.getCount();

        return 0f;
    }

    private ItemCategory getItemCategory(ItemStack stack) {
        // 神斧锋利等级超出 isSharpnessAxe 的区间，会被误判为普通工具，这里显式归为武器。
        if (InvUtils.isGodAxe(stack))
            return ItemCategory.WEAPON;

        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null) {
            return switch (equippable.slot()) {
                case HEAD -> ItemCategory.ARMOR_HEAD;
                case CHEST -> ItemCategory.ARMOR_CHEST;
                case LEGS -> ItemCategory.ARMOR_LEGS;
                case FEET -> ItemCategory.ARMOR_FEET;
                default -> ItemCategory.MATERIAL;
            };
        }

        Item item = stack.getItem();

        if (item == Items.MACE || stack.is(ItemTags.SWORDS) || InvUtils.isSharpnessAxe(stack))
            return ItemCategory.WEAPON;

        if (item instanceof BowItem)
            return ItemCategory.BOW;
        if (item instanceof CrossbowItem)
            return ItemCategory.CROSSBOW;
        if (item == Items.TRIDENT)
            return ItemCategory.WEAPON;
        if (item == Items.FISHING_ROD)
            return ItemCategory.FISHING_ROD;

        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS))
            return ItemCategory.TOOL;

        // 材料优先于方块判断：信标、黑曜石等方块型材料归为 MATERIAL（始终可拿），
        // 而不是按方块数量与已有方块比较后被跳过。
        if (MATERIAL_SCORES.containsKey(item))
            return ItemCategory.MATERIAL;

        if (item instanceof BlockItem)
            return ItemCategory.BLOCK;

        return ItemCategory.MATERIAL;
    }

    private boolean isStealableBlock(Item item) {
        if (!(item instanceof BlockItem bi))
            return false;
        Block block = bi.getBlock();
        return !InvUtils.blacklistedBlocks.contains(block);
    }

    private boolean isLowTierGearWithoutEnchants(ItemStack stack) {
        ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
        if (enchants != null && !enchants.isEmpty())
            return false;
        Item item = stack.getItem();
        if (isHighTierMaterial(item))
            return false;
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.ARMOR_ENCHANTABLE);
    }

    private static boolean isHighTierMaterial(Item item) {
        return item == Items.IRON_SWORD || item == Items.IRON_PICKAXE || item == Items.IRON_AXE
                || item == Items.IRON_SHOVEL || item == Items.IRON_HOE
                || item == Items.DIAMOND_SWORD || item == Items.DIAMOND_PICKAXE || item == Items.DIAMOND_AXE
                || item == Items.DIAMOND_SHOVEL || item == Items.DIAMOND_HOE
                || item == Items.NETHERITE_SWORD || item == Items.NETHERITE_PICKAXE || item == Items.NETHERITE_AXE
                || item == Items.NETHERITE_SHOVEL || item == Items.NETHERITE_HOE
                || item == Items.IRON_HELMET || item == Items.IRON_CHESTPLATE || item == Items.IRON_LEGGINGS || item == Items.IRON_BOOTS
                || item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS
                || item == Items.NETHERITE_HELMET || item == Items.NETHERITE_CHESTPLATE || item == Items.NETHERITE_LEGGINGS || item == Items.NETHERITE_BOOTS;
    }

    // ========== Utility ==========

    private boolean shouldSkipProcessing(MotionEvent event) {
        return event.getTimeEnum() != TimeEnum.Pre || mc.player == null || mc.gameMode == null;
    }

    private void handleScreenChange(Screen newScreen) {
        lastScreen = newScreen;
    }

    private void resetState() {
        stealTimer.reset();
        closeTimer.reset();
        openTimer.reset();
        waitingForOpenDelay = true;
        openDelayMs = getRandomDelay(openDelay);
        stealDelayMs = getRandomDelay(stealDelay);
        closeDelayMs = getRandomDelay(closeDelay);
        failedMoveAttempts.clear();
    }

    private int getRandomDelay(IntRangeValue delay) {
        int min = Math.min(delay.getMinValue(), delay.getMaxValue());
        int max = Math.max(delay.getMinValue(), delay.getMaxValue());
        return min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private boolean isTargetChest(AbstractContainerScreen<?> container) {
        String title = container.getTitle().getString().toLowerCase();
        String chestTitle = Component.translatable(CHEST_KEY).getString().toLowerCase();
        String largeChestTitle = Component.translatable(LARGE_CHEST_KEY).getString().toLowerCase();
        return title.contains(chestTitle) || title.contains(largeChestTitle)
                || title.contains(FALLBACK_CHEST_TITLE.toLowerCase());
    }

    private boolean shouldCloseChest(ChestMenu menu) {
        return isChestEmpty(menu) || !hasValuableItems(menu) || !hasUpgradeableItems;
    }

    private boolean isChestEmpty(ChestMenu menu) {
        int chestSize = menu.getRowCount() * 9;
        for (int i = 0; i < chestSize; i++) {
            if (!menu.getSlot(i).getItem().isEmpty())
                return false;
        }
        return true;
    }

    private boolean hasValuableItems(ChestMenu menu) {
        int chestSize = menu.getRowCount() * 9;
        for (int i = 0; i < chestSize; i++) {
            if (isValuableItem(menu.getSlot(i).getItem()))
                return true;
        }
        return false;
    }
}

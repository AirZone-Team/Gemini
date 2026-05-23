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
import net.minecraft.world.item.equipment.Equippable;

import java.util.*;

public class Stealer extends Module {
    private static final String CHEST_KEY = "container.chest";
    private static final String LARGE_CHEST_KEY = "container.chestDouble";
    private static final String FALLBACK_CHEST_TITLE = "Chest";

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

    private final IntRangeValue stealDelay = new IntRangeValue("StealDelay", 100, 200, 0, 500);
    private final IntRangeValue closeDelay = new IntRangeValue("CloseDelay", 100, 200, 0, 500);

    private final TimerUtils stealTimer = new TimerUtils();
    private final TimerUtils closeTimer = new TimerUtils();
    private final Random random = new Random();
    private Screen lastScreen;
    private final Set<ItemCategory> processedCategories = EnumSet.noneOf(ItemCategory.class);
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

        if (isTargetChest(container)) {
            processChest((ChestMenu) container.getMenu());
        }
    }

    // ========== Core Logic ==========

    private void processChest(ChestMenu menu) {
        int stealDelayMs = random.nextInt(stealDelay.getMinValue(), stealDelay.getMaxValue());
        int closeDelayMs = random.nextInt(closeDelay.getMinValue(), closeDelay.getMaxValue());

        if (!hasUpgradeableItems) {
            hasUpgradeableItems = hasUpgradeableItems(menu);
        }

        if (shouldCloseChest(menu)) {
            handleChestClosing(closeDelayMs);
        } else {
            handleItemStealing(menu, stealDelayMs);
        }
    }

    private boolean hasUpgradeableItems(ChestMenu menu) {
        Map<ItemCategory, Float> bestInvScores = getBestItemScoresInInventory();
        int chestSize = menu.getRowCount() * 9;

        for (int i = 0; i < chestSize; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty() && isValuableItem(stack)) {
                ItemCategory category = getItemCategory(stack);
                if (processedCategories.contains(category))
                    continue;

                float score = calculateItemScore(stack);
                if (score > bestInvScores.getOrDefault(category, 0f)) {
                    return true;
                }
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
            hasUpgradeableItems = false;
        }
    }

    private void handleItemStealing(ChestMenu menu, int stealDelayMs) {
        if (stealTimer.hasTimeElapsed(stealDelayMs, true) || stealDelayMs == 0) {
            boolean stoleItem = attemptSteal(menu);
            stealTimer.reset();
            if (!stoleItem) {
                hasUpgradeableItems = hasUpgradeableItems(menu);
            }
        }
    }

    private boolean attemptSteal(ChestMenu menu) {
        Optional<Integer> valuableSlot = findBestItemSlotToSteal(menu);
        if (valuableSlot.isEmpty())
            return false;

        int slotId = valuableSlot.get();
        if (mc.player == null || mc.gameMode == null)
            return false;

        mc.gameMode.handleContainerInput(menu.containerId, slotId, 0,
                ContainerInput.QUICK_MOVE, mc.player);

        ItemStack stack = menu.getSlot(slotId).getItem();
        if (!stack.isEmpty()) {
            processedCategories.add(getItemCategory(stack));
        }
        return true;
    }

    // ========== Item Selection ==========

    private Optional<Integer> findBestItemSlotToSteal(ChestMenu menu) {
        Map<ItemCategory, Float> bestInvScores = getBestItemScoresInInventory();
        int chestSize = menu.getRowCount() * 9;
        Map<ItemCategory, SlotValue> upgradeableItems = new EnumMap<>(ItemCategory.class);

        for (int i = 0; i < chestSize; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty() || !isValuableItem(stack))
                continue;

            ItemCategory category = getItemCategory(stack);
            if (processedCategories.contains(category))
                continue;

            float score = calculateItemScore(stack);
            if (score > bestInvScores.getOrDefault(category, 0f)) {
                SlotValue currentBest = upgradeableItems.get(category);
                if (currentBest == null || score > currentBest.score()) {
                    upgradeableItems.put(category, new SlotValue(i, score));
                }
            }
        }

        int bestSlot = -1;
        float bestScore = -1;
        for (SlotValue sv : upgradeableItems.values()) {
            if (sv.score() > bestScore) {
                bestScore = sv.score();
                bestSlot = sv.slotId();
            }
        }

        return bestSlot != -1 ? Optional.of(bestSlot) : Optional.empty();
    }

    private Map<ItemCategory, Float> getBestItemScoresInInventory() {
        Map<ItemCategory, Float> bestScores = new EnumMap<>(ItemCategory.class);
        if (mc.player == null)
            return bestScores;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && isValuableItem(stack)) {
                ItemCategory category = getItemCategory(stack);
                float score = calculateItemScore(stack);
                bestScores.merge(category, score, Math::max);
            }
        }
        return bestScores;
    }

    private record SlotValue(int slotId, float score) {}

    // ========== Item Scoring (delegates to InvUtils) ==========

    private float calculateItemScore(ItemStack stack) {
        Item item = stack.getItem();

        // Weapons — use InvUtils damage scoring
        if (stack.is(ItemTags.SWORDS))
            return InvUtils.getSwordDamage(stack) * 10f;
        if (InvUtils.isSharpnessAxe(stack))
            return InvUtils.getAxeDamage(stack) * 10f;
        if (item == Items.MACE)
            return 150f;
        if (item == Items.TRIDENT)
            return 80f;

        // Bows — use InvUtils bow scoring
        if (item instanceof BowItem)
            return Math.max(InvUtils.getPowerBowScore(stack), InvUtils.getPunchBowScore(stack)) * 10f;
        if (item instanceof CrossbowItem)
            return InvUtils.getCrossbowScore(stack) * 10f;

        // Armor — use InvUtils protection scoring
        if (stack.is(ItemTags.ARMOR_ENCHANTABLE))
            return InvUtils.getProtection(stack);

        // Tools — use InvUtils tool scoring (after sharpness axe check)
        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS))
            return InvUtils.getToolScore(stack) * 100f;

        // Misc
        if (item == Items.FISHING_ROD)
            return 20f;

        // God items (totems, crystals, god apples, kb balls, kb sticks)
        if (InvUtils.isGodItem(stack))
            return 500f;

        // Materials — use simplified value map
        Float materialScore = MATERIAL_SCORES.get(item);
        if (materialScore != null) {
            return stack.getCount() > 1 ? materialScore + stack.getCount() : materialScore;
        }
        return 0f;
    }

    // ========== Item Classification ==========

    private boolean isValuableItem(ItemStack stack) {
        if (stack.isEmpty() || InvUtils.isNotItemValid(stack))
            return false;
        Item item = stack.getItem();
        return item == Items.ENCHANTED_GOLDEN_APPLE
                || item == Items.GOLDEN_APPLE
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
                || InvUtils.isGodItem(stack)
                || stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.ARMOR_ENCHANTABLE)
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item == Items.TRIDENT
                || item == Items.MACE
                || item == Items.FISHING_ROD;
    }

    private enum ItemCategory {
        WEAPON, TOOL, HELMET, CHESTPLATE, LEGGINGS, BOOTS,
        BOW, CROSSBOW, TRIDENT, FISHING_ROD, MATERIAL, OTHER_VALUABLE
    }

    private ItemCategory getItemCategory(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null) {
            return switch (equippable.slot()) {
                case HEAD -> ItemCategory.HELMET;
                case CHEST -> ItemCategory.CHESTPLATE;
                case LEGS -> ItemCategory.LEGGINGS;
                case FEET -> ItemCategory.BOOTS;
                default -> ItemCategory.OTHER_VALUABLE;
            };
        }

        Item item = stack.getItem();

        if (item == Items.MACE || stack.is(ItemTags.SWORDS))
            return ItemCategory.WEAPON;
        if (InvUtils.isSharpnessAxe(stack))
            return ItemCategory.WEAPON;

        if (item instanceof BowItem)
            return ItemCategory.BOW;
        if (item instanceof CrossbowItem)
            return ItemCategory.CROSSBOW;
        if (item == Items.TRIDENT)
            return ItemCategory.TRIDENT;
        if (item == Items.FISHING_ROD)
            return ItemCategory.FISHING_ROD;

        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS))
            return ItemCategory.TOOL;

        return ItemCategory.MATERIAL;
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

package geminiclient.gemini.modules.impl.player;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.PacketEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.event.events.impl.enums.IOEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.ClientUtils;
import geminiclient.gemini.modules.impl.player.invmanager.InvUtils;
import geminiclient.gemini.utils.MathHelper;
import geminiclient.gemini.utils.MovementUtils;
import geminiclient.gemini.utils.TimerUtils;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.IntRangeValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.equipment.Equippable;

import java.util.*;

public class InvManager extends Module {
    private final IntRangeValue delay = new IntRangeValue("Delay", 90, 110, 0, 1000);

    // ==================== Slot Item Types ====================

    private static final String[] SLOT_OPTIONS = {
            "None", "Sword", "Pickaxe", "Axe", "Shovel", "Bow", "Block", "Food",
            "Ender Pearl", "Golden Apple", "Water Bucket", "Fire Charge", "Projectile", "Fishing Rod"
    };

    // ==================== Settings ====================

    private final ListValue slot1 = new ListValue("Slot 1", "Sword", SLOT_OPTIONS);
    private final ListValue slot2 = new ListValue("Slot 2", "Golden Apple", SLOT_OPTIONS);
    private final ListValue slot3 = new ListValue("Slot 3", "Pickaxe", SLOT_OPTIONS);
    private final ListValue slot4 = new ListValue("Slot 4", "Axe", SLOT_OPTIONS);
    private final ListValue slot5 = new ListValue("Slot 5", "Bow", SLOT_OPTIONS);
    private final ListValue slot6 = new ListValue("Slot 6", "Water Bucket", SLOT_OPTIONS);
    private final ListValue slot7 = new ListValue("Slot 7", "Ender Pearl", SLOT_OPTIONS);
    private final ListValue slot8 = new ListValue("Slot 8", "Fire Charge", SLOT_OPTIONS);
    private final ListValue slot9 = new ListValue("Slot 9", "Block", SLOT_OPTIONS);

    private final ListValue[] slotConfigs = {slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9};

    private final ListValue offhandMode = new ListValue("Offhand",
            "None", new String[]{"None", "Golden Apple", "Projectile", "Fishing Rod", "Block"});

    private final ListValue preferBow = new ListValue("Bow Priority", "Crossbow",
            new String[]{"Crossbow", "Power Bow", "Punch Bow"}, this::hasBowSlot);

    private final BoolValue autoArmor = new BoolValue("Auto Armor", true);
    private final BoolValue throwItems = new BoolValue("Throw Items", true);
    private final BoolValue inventoryOnly = new BoolValue("Inventory Only", true);
    private final BoolValue keepProjectile = new BoolValue("Keep Projectile", true);

    private final IntValue maxBlockSize = new IntValue("Max Block Size", 256, 64, 512, this::hasBlockSlot);
    private final IntValue maxArrowSize = new IntValue("Max Arrow Size", 256, 64, 512, this::hasBowSlot);
    private final IntValue maxProjectileSize = new IntValue("Max Projectile Size", 64, 16, 256, () -> keepProjectile.enabled);
    private final IntValue waterBucketCount = new IntValue("Keep Water Buckets", 1, 0, 5, () -> throwItems.enabled);
    private final IntValue lavaBucketCount = new IntValue("Keep Lava Buckets", 1, 0, 5, () -> throwItems.enabled);

    // ==================== State ====================

    private final TimerUtils timer = new TimerUtils();
    private int noMoveTicks = 0;
    private boolean clickOffHand = false;
    private boolean inventoryOpen = false;

    public InvManager() {
        super("InvManager", ModuleEnum.Player);
        addValue(slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9,
                offhandMode, preferBow, autoArmor, throwItems, inventoryOnly, keepProjectile,
                maxBlockSize, maxArrowSize, maxProjectileSize,
                waterBucketCount, lavaBucketCount);
    }

    // ==================== Slot Mapping Helper ====================

    private static int toContainerSlot(int inventoryIndex) {
        if (inventoryIndex < 9) return inventoryIndex + 36;        // 快捷栏 0-8 -> 36-44
        if (inventoryIndex < 36) return inventoryIndex;            // 主背包 9-35 -> 9-35
        if (inventoryIndex < 40) return 8 - (inventoryIndex - 36); // 盔甲栏 36-39 -> 8-5 (头到脚)
        if (inventoryIndex == 40) return 45;                       // 副手栏 40 -> 45
        return inventoryIndex;
    }

    // ==================== Slot Visibility Helpers ====================

    private boolean hasBowSlot() {
        for (ListValue slot : slotConfigs) {
            if (slot.is("Bow")) return true;
        }
        return false;
    }

    private boolean hasBlockSlot() {
        for (ListValue slot : slotConfigs) {
            if (slot.is("Block")) return true;
        }
        return false;
    }

    // ==================== Public Getters ====================

    public int getMaxBlockSize() { return maxBlockSize.getValue(); }
    public boolean shouldKeepProjectile() { return keepProjectile.enabled; }
    public int getMaxProjectileSize() { return maxProjectileSize.getValue(); }
    public int getMaxArrowSize() { return maxArrowSize.getValue(); }
    public int getWaterBucketCount() { return waterBucketCount.getValue(); }
    public int getLavaBucketCount() { return lavaBucketCount.getValue(); }

    public boolean isItemUseful(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (InvUtils.isGodItem(stack)) return true;
        if (stack.getDisplayName().getString().contains("点击使用")) return true;

        if (stack.is(ItemTags.ARMOR_ENCHANTABLE)) {
            float protection = InvUtils.getProtection(stack);
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            if (equippable == null) return false;
            if (InvUtils.getCurrentArmorScore(equippable.slot()) >= protection) return false;
            return InvUtils.getBestArmorScore(equippable.slot()) <= protection;
        }
        if (stack.is(ItemTags.SWORDS)) return InvUtils.getBestSword() == stack;
        if (stack.is(ItemTags.PICKAXES)) return InvUtils.getBestPickaxe() == stack;
        if (stack.getItem() instanceof AxeItem && !InvUtils.isSharpnessAxe(stack))
            return InvUtils.getBestAxe() == stack;
        if (stack.getItem() instanceof ShovelItem) return InvUtils.getBestShovel() == stack;
        if (stack.getItem() instanceof CrossbowItem) return InvUtils.getBestCrossbow() == stack;
        if (stack.getItem() instanceof BowItem && InvUtils.isPunchBow(stack))
            return InvUtils.getBestPunchBow() == stack;
        if (stack.getItem() instanceof BowItem && InvUtils.isPowerBow(stack))
            return InvUtils.getBestPowerBow() == stack;
        if (stack.getItem() instanceof BowItem && InvUtils.getItemCount(Items.BOW) > 1) return false;
        if (stack.getItem() == Items.WATER_BUCKET
                && InvUtils.getItemCount(Items.WATER_BUCKET) > getWaterBucketCount()) return false;
        if (stack.getItem() == Items.LAVA_BUCKET
                && InvUtils.getItemCount(Items.LAVA_BUCKET) > getLavaBucketCount()) return false;
        if (stack.getItem() instanceof FishingRodItem
                && InvUtils.getItemCount(Items.FISHING_ROD) > 1) return false;
        if ((stack.getItem() == Items.SNOWBALL || stack.getItem() == Items.EGG)
                && !shouldKeepProjectile()) return false;

        return !stack.has(DataComponents.CUSTOM_NAME) && InvUtils.isCommonItemUseful(stack);
    }

    // ==================== Packet Event ====================

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getIoEnum() != IOEnum.Out) return;

        if (event.getPacket() instanceof ServerboundContainerClosePacket) {
            this.inventoryOpen = false;
        }
    }

    // ==================== Main Tick ====================

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.getTimeEnum() != TimeEnum.Pre || mc.player == null || mc.gameMode == null) return;

        if (!checkSlotConflicts()) {
            ClientUtils.addChatMessage("Duplicate item type in hotbar slots! Check your config!");
            setEnabled(false);
            return;
        }

        if (InvUtils.shouldDisableFeatures()) return;

        if (MovementUtils.moving()) {
            this.noMoveTicks = 0;
        } else {
            this.noMoveTicks++;
        }

        Stealer stealer = Gemini.moduleManager.getModule(Stealer.class);
        boolean stealerWorking = stealer != null && stealer.enabled
                && mc.screen instanceof AbstractContainerScreen<?>;

        if (stealerWorking
                || (this.inventoryOnly.enabled
                ? !(mc.screen instanceof InventoryScreen)
                : this.noMoveTicks <= 1)) {
            this.clickOffHand = false;
            return;
        }

        if (mc.screen instanceof AbstractContainerScreen<?> container
                && container.getMenu().containerId != mc.player.inventoryMenu.containerId) {
            return;
        }

        // --- Auto Armor ---
        if (this.autoArmor.enabled && handleAutoArmor()) return;

        // --- Click Offhand (golden apple stacking) ---
        if (this.clickOffHand
                && timer.hasTimeElapsed((long) MathHelper.getRandom(delay.getMinValue(), delay.getMaxValue()), false)) {
            clickSlot(45, 0, ContainerInput.PICKUP);
            this.inventoryOpen = true;
            this.clickOffHand = false;
            timer.reset();
            return;
        }

        // --- Offhand Management ---
        if (handleOffhand()) return;

        // --- Hotbar Slot Management ---
        for (int i = 0; i < 9; i++) {
            String type = slotConfigs[i].get();
            if ("None".equals(type)) continue;
            if (handleSlot(i, type)) return;
        }

        // --- Excess Item Limits ---
        if (handleExcessItems()) return;

        // --- Throw Junk ---
        if (this.throwItems.enabled) {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);

                // 【修复1】必须检查空物品，否则会陷入无限点击丢弃空气的死循环
                if (stack.isEmpty()) continue;

                if (!this.isItemUseful(stack)) {
                    clickSlot(toContainerSlot(i), 1, ContainerInput.THROW);
                    this.inventoryOpen = true;
                    timer.reset();
                    return;
                }
            }
        }
    }

    // ==================== Auto Armor ====================

    private boolean handleAutoArmor() {
        EquipmentSlot[] armorSlots = {EquipmentSlot.FEET, EquipmentSlot.LEGS,
                EquipmentSlot.CHEST, EquipmentSlot.HEAD};
        for (int i = 0; i < 4; i++) {
            ItemStack stack = mc.player.getItemBySlot(armorSlots[i]);
            if (stack.is(ItemTags.ARMOR_ENCHANTABLE)) {
                Equippable equipment = stack.get(DataComponents.EQUIPPABLE);
                if (equipment == null) continue;
                if (!stack.isEmpty()
                        && timer.hasTimeElapsed((long) MathHelper.getRandom(delay.getMinValue(), delay.getMaxValue()), false)
                        && InvUtils.getBestArmorScore(equipment.slot()) > InvUtils.getProtection(stack)) {
                    clickSlot(8 - i, 1, ContainerInput.THROW);
                    this.inventoryOpen = true;
                    timer.reset();
                    return true;
                }
            }
        }

        for (int ix = 0; ix < 36; ix++) {
            ItemStack stack = mc.player.getInventory().getItem(ix);
            if (!stack.isEmpty() && stack.is(ItemTags.ARMOR_ENCHANTABLE)) {
                float currentItemScore = InvUtils.getProtection(stack);
                Equippable equipment = stack.get(DataComponents.EQUIPPABLE);
                if (equipment == null) continue;
                if (currentItemScore > InvUtils.getCurrentArmorScore(equipment.slot())
                        && currentItemScore == InvUtils.getBestArmorScore(equipment.slot())
                        && timer.hasTimeElapsed((long) MathHelper.getRandom(delay.getMinValue(), delay.getMaxValue()), false)) {
                    clickSlot(toContainerSlot(ix), 0, ContainerInput.QUICK_MOVE);
                    this.inventoryOpen = true;
                    timer.reset();
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== Offhand ====================

    private boolean handleOffhand() {
        String mode = offhandMode.get();
        if ("None".equals(mode)) return false;

        ItemStack offHand = mc.player.getOffhandItem();

        if ("Golden Apple".equals(mode)) {
            int slot = InvUtils.getItemSlot(Items.GOLDEN_APPLE);
            if (slot != -1 && timer.hasTimeElapsed((long) MathHelper.getRandom(delay.getMinValue(), delay.getMaxValue()), false)) {
                if (offHand.getItem() == Items.GOLDEN_APPLE) {
                    ItemStack gaStack = mc.player.getInventory().getItem(slot);
                    if (offHand.getCount() + gaStack.getCount() <= 64) {
                        clickSlot(toContainerSlot(slot), 0, ContainerInput.PICKUP);
                        this.inventoryOpen = true;
                        this.clickOffHand = true;
                        timer.reset();
                        return true;
                    }
                } else {
                    swapOffHand(slot);
                    return true;
                }
            }
        } else if ("Projectile".equals(mode)) {
            ItemStack bestProjectile = InvUtils.getBestProjectile();
            if (bestProjectile != null) {
                int slot = InvUtils.getItemStackSlot(bestProjectile);
                boolean shouldSwap = offHand.getItem() != Items.EGG && offHand.getItem() != Items.SNOWBALL
                        || offHand.getCount() < bestProjectile.getCount();
                if (shouldSwap && slot != -1
                        && timer.hasTimeElapsed((long) MathHelper.getRandom(delay.getMinValue(), delay.getMaxValue()), false)) {
                    swapOffHand(slot);
                    return true;
                }
            }
        } else if ("Fishing Rod".equals(mode)) {
            int slot = InvUtils.getItemSlot(Items.FISHING_ROD);
            if (slot != -1 && timer.hasTimeElapsed((long) MathHelper.getRandom(delay.getMinValue(), delay.getMaxValue()), false)
                    && offHand.getItem() != Items.FISHING_ROD) {
                swapOffHand(slot);
                return true;
            }
        } else if ("Block".equals(mode)) {
            ItemStack bestBlock = InvUtils.getBestBlock();
            if (bestBlock != null) {
                int slot = InvUtils.getItemStackSlot(bestBlock);
                boolean shouldSwap = !InvUtils.isValidStack(offHand)
                        || offHand.getCount() < bestBlock.getCount();
                if (shouldSwap && slot != -1
                        && timer.hasTimeElapsed((long) MathHelper.getRandom(delay.getMinValue(), delay.getMaxValue()), false)) {
                    swapOffHand(slot);
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== Hotbar Slot Dispatch ====================

    private boolean handleSlot(int hotbarIndex, String type) {
        return switch (type) {
            case "Sword" -> handleSword(hotbarIndex);
            case "Pickaxe" -> handlePickaxe(hotbarIndex);
            case "Axe" -> handleAxe(hotbarIndex);
            case "Shovel" -> handleShovel(hotbarIndex);
            case "Bow" -> handleBow(hotbarIndex);
            case "Block" -> handleBlock(hotbarIndex);
            case "Food" -> handleFood(hotbarIndex);
            case "Ender Pearl" -> handleSimpleItem(hotbarIndex, Items.ENDER_PEARL);
            case "Golden Apple" -> offhandMode.is("Golden Apple") ? false : handleSimpleItem(hotbarIndex, Items.GOLDEN_APPLE);
            case "Water Bucket" -> handleSimpleItem(hotbarIndex, Items.WATER_BUCKET);
            case "Fire Charge" -> handleSimpleItem(hotbarIndex, Items.FIRE_CHARGE);
            case "Projectile" -> handleProjectile(hotbarIndex);
            case "Fishing Rod" -> handleFishingRod(hotbarIndex);
            default -> false;
        };
    }

    private boolean handleSimpleItem(int slot, Item item) {
        if (InvUtils.getItemCount(item) == 0) return false;
        return swapItem(slot, item);
    }

    private boolean handleSword(int slot) {
        ItemStack current = mc.player.getInventory().getItem(slot);
        ItemStack bestSword = InvUtils.getBestSword();
        ItemStack bestShapeAxe = InvUtils.getBestShapeAxe();
        if (InvUtils.getAxeDamage(bestShapeAxe) > InvUtils.getSwordDamage(bestSword)) {
            bestSword = bestShapeAxe;
        }
        if (bestSword == null) return false;

        float curDmg = current.is(ItemTags.SWORDS)
                ? InvUtils.getSwordDamage(current) : InvUtils.getAxeDamage(current);
        float bestDmg = bestSword.is(ItemTags.SWORDS)
                ? InvUtils.getSwordDamage(bestSword) : InvUtils.getAxeDamage(bestSword);
        if (bestDmg > curDmg) {
            return swapItem(slot, bestSword);
        }
        return false;
    }

    private boolean handlePickaxe(int slot) {
        ItemStack best = InvUtils.getBestPickaxe();
        ItemStack current = mc.player.getInventory().getItem(slot);
        if (best != null && (InvUtils.getToolScore(best) > InvUtils.getToolScore(current)
                || !current.is(ItemTags.PICKAXES))) {
            return swapItem(slot, best);
        }
        return false;
    }

    private boolean handleAxe(int slot) {
        ItemStack best = InvUtils.getBestAxe();
        ItemStack current = mc.player.getInventory().getItem(slot);
        if (best != null && (InvUtils.getToolScore(best) > InvUtils.getToolScore(current)
                || !current.is(ItemTags.AXES))) {
            return swapItem(slot, best);
        }
        return false;
    }

    private boolean handleShovel(int slot) {
        ItemStack best = InvUtils.getBestShovel();
        ItemStack current = mc.player.getInventory().getItem(slot);
        if (best != null && (InvUtils.getToolScore(best) > InvUtils.getToolScore(current)
                || !(current.getItem() instanceof ShovelItem))) {
            return swapItem(slot, best);
        }
        return false;
    }

    private boolean handleBow(int slot) {
        ItemStack current = mc.player.getInventory().getItem(slot);
        String priority = preferBow.get();

        String[] order = {"Crossbow", "Power Bow", "Punch Bow"};
        if ("Power Bow".equals(priority)) order = new String[]{"Power Bow", "Crossbow", "Punch Bow"};
        else if ("Punch Bow".equals(priority)) order = new String[]{"Punch Bow", "Crossbow", "Power Bow"};

        for (String type : order) {
            ItemStack best = switch (type) {
                case "Crossbow" -> InvUtils.getBestCrossbow();
                case "Power Bow" -> InvUtils.getBestPowerBow();
                default -> InvUtils.getBestPunchBow();
            };
            if (best == null) continue;

            float bestScore = switch (type) {
                case "Crossbow" -> InvUtils.getCrossbowScore(best);
                case "Power Bow" -> InvUtils.getPowerBowScore(best);
                default -> InvUtils.getPunchBowScore(best);
            };
            float curScore = switch (type) {
                case "Crossbow" -> InvUtils.getCrossbowScore(current);
                case "Power Bow" -> InvUtils.getPowerBowScore(current);
                default -> InvUtils.getPunchBowScore(current);
            };

            if (bestScore > curScore) {
                return swapItem(slot, best);
            }
        }
        return false;
    }

    private boolean handleBlock(int slot) {
        if (offhandMode.is("Block")) return false;
        ItemStack current = mc.player.getInventory().getItem(slot);
        ItemStack best = InvUtils.getBestBlock();
        if (best != null
                && (best.getCount() > current.getCount() || !InvUtils.isValidStack(current))) {
            return swapItem(slot, best);
        }
        return false;
    }

    private boolean handleFood(int slot) {
        ItemStack best = InvUtils.getBestFood();
        ItemStack current = mc.player.getInventory().getItem(slot);
        if (best != null && (InvUtils.getFoodScore(best) > InvUtils.getFoodScore(current)
                || !current.has(DataComponents.FOOD))) {
            return swapItem(slot, best);
        }
        return false;
    }

    private boolean handleProjectile(int slot) {
        if (offhandMode.is("Projectile")) return false;
        ItemStack best = InvUtils.getBestProjectile();
        if (best == null) return false;
        return swapItem(slot, best);
    }

    private boolean handleFishingRod(int slot) {
        if (offhandMode.is("Fishing Rod")) return false;
        ItemStack best = InvUtils.getFishingRod();
        if (best == null) return false;
        if (mc.player.getInventory().getItem(slot).getItem() instanceof FishingRodItem) return false;
        return swapItem(slot, best);
    }

    // ==================== Excess Items ====================

    private boolean handleExcessItems() {
        if (hasBowSlot() && InvUtils.getItemCount(Items.ARROW) > maxArrowSize.getValue()) {
            ItemStack worstArrow = InvUtils.getWorstArrow();
            if (worstArrow != null && throwItem(worstArrow)) return true;
        }
        if (hasBlockSlot() && InvUtils.getBlockCountInInventory() > maxBlockSize.getValue()) {
            ItemStack worstBlock = InvUtils.getWorstBlock();
            if (worstBlock != null && throwItem(worstBlock)) return true;
        }
        if (keepProjectile.enabled) {
            int projCount = InvUtils.getItemCount(Items.EGG) + InvUtils.getItemCount(Items.SNOWBALL);
            if (projCount > maxProjectileSize.getValue()) {
                ItemStack worstProj = InvUtils.getWorstProjectile();
                if (worstProj != null && throwItem(worstProj)) return true;
            }
        }
        return false;
    }

    // ==================== Config Validation ====================

    private boolean checkSlotConflicts() {
        Set<String> used = new HashSet<>();
        for (ListValue slot : slotConfigs) {
            String type = slot.get();
            if ("None".equals(type)) continue;
            if (!used.add(type)) return false;
        }
        return true;
    }

    // ==================== Inventory Actions ====================

    private void swapOffHand(int slot) {
        clickSlot(toContainerSlot(slot), 40, ContainerInput.SWAP);
        this.inventoryOpen = true;
        timer.reset();
    }

    // ==================== 修改 throwItem 让其返回动作状态 ====================
    private boolean throwItem(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        if (!timer.hasTimeElapsed((long) MathHelper.getRandom(delay.getMinValue(), delay.getMaxValue()), false)) return false;

        int itemSlot = InvUtils.getItemStackSlot(item);
        if (itemSlot != -1) {
            clickSlot(toContainerSlot(itemSlot), 1, ContainerInput.THROW);
            this.inventoryOpen = true;
            timer.reset();
            return true; // 真正丢出了物品，返回 true
        }
        return false;
    }

    private boolean swapItem(int targetSlot, ItemStack bestItem) {
        if (bestItem == null) return false;
        ItemStack currentSlot = mc.player.getInventory().getItem(targetSlot);

        // 【修复2】移除了 InvUtils.isNotItemValid(currentSlot)，以允许物品被移入"空"快捷栏槽位
        if (bestItem == currentSlot) return false;

        if (!timer.hasTimeElapsed((long) MathHelper.getRandom(delay.getMinValue(), delay.getMaxValue()), false)) return false;

        int bestItemSlot = InvUtils.getItemStackSlot(bestItem);
        if (bestItemSlot != -1) {
            clickSlot(toContainerSlot(bestItemSlot), targetSlot, ContainerInput.SWAP);
            this.inventoryOpen = true;
            timer.reset();
            return true;
        }
        return false;
    }

    private boolean swapItem(int targetSlot, Item item) {
        ItemStack currentSlot = mc.player.getInventory().getItem(targetSlot);

        // 【修复2】同样的，移除拦截以确保可以将物品填入空槽
        if (!timer.hasTimeElapsed((long) MathHelper.getRandom(delay.getMinValue(), delay.getMaxValue()), false)) return false;

        int bestItemSlot = findBestSlotForItem(item);
        if (bestItemSlot != -1 && bestItemSlot != targetSlot) {
            ItemStack bestItemStack = mc.player.getInventory().getItem(bestItemSlot);
            if (currentSlot.getItem() != item || currentSlot.getCount() < bestItemStack.getCount()) {
                clickSlot(toContainerSlot(bestItemSlot), targetSlot, ContainerInput.SWAP);
                this.inventoryOpen = true;
                timer.reset();
                return true;
            }
        }
        return false;
    }

    private int findBestSlotForItem(Item item) {
        int bestSlot = -1;
        int bestCount = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == item && stack.getCount() > bestCount) {
                bestCount = stack.getCount();
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private void clickSlot(int slotNum, int buttonNum, ContainerInput containerInput) {
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
                slotNum, buttonNum, containerInput, mc.player);
    }
}
package geminiclient.gemini.modules.impl.player.invmanager;

import geminiclient.gemini.base.MinecraftInstance;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;

public class HotbarHandler implements MinecraftInstance {

    public static boolean handleSlot(int hotbarIndex, String type,
                                      ListValue offhandMode, ListValue preferBow,
                                      InventoryActions actions) {
        return switch (type) {
            case "Sword" -> handleSword(hotbarIndex, actions);
            case "Pickaxe" -> handlePickaxe(hotbarIndex, actions);
            case "Axe" -> handleAxe(hotbarIndex, actions);
            case "Shovel" -> handleShovel(hotbarIndex, actions);
            case "Bow" -> handleBow(hotbarIndex, preferBow, actions);
            case "Block" -> handleBlock(hotbarIndex, offhandMode, actions);
            case "Food" -> handleFood(hotbarIndex, actions);
            case "Ender Pearl" -> handleSimpleItem(hotbarIndex, Items.ENDER_PEARL, actions);
            case "Golden Apple" -> offhandMode.is("Golden Apple") ? false
                    : handleSimpleItem(hotbarIndex, Items.GOLDEN_APPLE, actions);
            case "Water Bucket" -> handleSimpleItem(hotbarIndex, Items.WATER_BUCKET, actions);
            case "Fire Charge" -> handleSimpleItem(hotbarIndex, Items.FIRE_CHARGE, actions);
            case "Projectile" -> handleProjectile(hotbarIndex, offhandMode, actions);
            case "Fishing Rod" -> handleFishingRod(hotbarIndex, offhandMode, actions);
            default -> false;
        };
    }

    // ==================== Simple Item ====================

    private static boolean handleSimpleItem(int slot, Item item, InventoryActions actions) {
        if (InvUtils.getItemCount(item) == 0) return false;
        return actions.swapItem(slot, item);
    }

    // ==================== Sword ====================

    private static boolean handleSword(int slot, InventoryActions actions) {
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
            return actions.swapItem(slot, bestSword);
        }
        return false;
    }

    // ==================== Tools ====================

    private static boolean handlePickaxe(int slot, InventoryActions actions) {
        ItemStack best = InvUtils.getBestPickaxe();
        ItemStack current = mc.player.getInventory().getItem(slot);
        if (best != null && (InvUtils.getToolScore(best) > InvUtils.getToolScore(current)
                || !current.is(ItemTags.PICKAXES))) {
            return actions.swapItem(slot, best);
        }
        return false;
    }

    private static boolean handleAxe(int slot, InventoryActions actions) {
        ItemStack best = InvUtils.getBestAxe();
        ItemStack current = mc.player.getInventory().getItem(slot);
        if (best != null && (InvUtils.getToolScore(best) > InvUtils.getToolScore(current)
                || !current.is(ItemTags.AXES))) {
            return actions.swapItem(slot, best);
        }
        return false;
    }

    private static boolean handleShovel(int slot, InventoryActions actions) {
        ItemStack best = InvUtils.getBestShovel();
        ItemStack current = mc.player.getInventory().getItem(slot);
        if (best != null && (InvUtils.getToolScore(best) > InvUtils.getToolScore(current)
                || !(current.getItem() instanceof ShovelItem))) {
            return actions.swapItem(slot, best);
        }
        return false;
    }

    // ==================== Bow ====================

    private static boolean handleBow(int slot, ListValue preferBow, InventoryActions actions) {
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
                return actions.swapItem(slot, best);
            }
        }
        return false;
    }

    // ==================== Block ====================

    private static boolean handleBlock(int slot, ListValue offhandMode, InventoryActions actions) {
        if (offhandMode.is("Block")) return false;
        ItemStack current = mc.player.getInventory().getItem(slot);
        ItemStack best = InvUtils.getBestBlock();
        if (best != null
                && (best.getCount() > current.getCount() || !InvUtils.isValidStack(current))) {
            return actions.swapItem(slot, best);
        }
        return false;
    }

    // ==================== Food ====================

    private static boolean handleFood(int slot, InventoryActions actions) {
        ItemStack best = InvUtils.getBestFood();
        ItemStack current = mc.player.getInventory().getItem(slot);
        if (best != null && (InvUtils.getFoodScore(best) > InvUtils.getFoodScore(current)
                || !current.has(DataComponents.FOOD))) {
            return actions.swapItem(slot, best);
        }
        return false;
    }

    // ==================== Projectile ====================

    private static boolean handleProjectile(int slot, ListValue offhandMode, InventoryActions actions) {
        if (offhandMode.is("Projectile")) return false;
        ItemStack best = InvUtils.getBestProjectile();
        if (best == null) return false;
        return actions.swapItem(slot, best);
    }

    // ==================== Fishing Rod ====================

    private static boolean handleFishingRod(int slot, ListValue offhandMode, InventoryActions actions) {
        if (offhandMode.is("Fishing Rod")) return false;
        ItemStack best = InvUtils.getFishingRod();
        if (best == null) return false;
        if (mc.player.getInventory().getItem(slot).getItem() instanceof FishingRodItem) return false;
        return actions.swapItem(slot, best);
    }
}

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
import geminiclient.gemini.modules.impl.player.invmanager.*;
import geminiclient.gemini.utils.MathHelper;
import geminiclient.gemini.utils.MovementUtils;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.IntRangeValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.equipment.Equippable;

import java.util.*;

public class InvManager extends Module {
    private final IntRangeValue delay = new IntRangeValue("Delay", 90, 110, 0, 1000);
    private final IntRangeValue openDelay = new IntRangeValue("Open Delay", 150, 250, 0, 2000);

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

    private final InventoryActions actions = new InventoryActions(delay);
    private int noMoveTicks = 0;
    private long openDelayUntil = -1;

    public InvManager() {
        super("InvManager", ModuleEnum.Player);
        addValue(slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9,
                offhandMode, preferBow, autoArmor, throwItems, inventoryOnly, keepProjectile,
                maxBlockSize, maxArrowSize, maxProjectileSize,
                waterBucketCount, lavaBucketCount, openDelay);
    }

    @Override
    public void onEnabled() {
        this.openDelayUntil = -1;
        this.noMoveTicks = 0;
    }

    @Override
    public void onDisabled() {
        this.openDelayUntil = -1;
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

    // ==================== Item Usefulness ====================

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
            actions.inventoryOpen = false;
        }
    }

    // ==================== Main Tick ====================

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.getTimeEnum() != TimeEnum.Pre || mc.player == null || mc.gameMode == null) return;

        // 1. 始终重置打开延迟 (防止 OpenDelay 卡死)
        if (!(mc.screen instanceof InventoryScreen)) {
            openDelayUntil = -1;
        }

        if (!checkSlotConflicts()) {
            ClientUtils.addChatMessage("Duplicate item type in hotbar slots! Check your config!");
            setEnabled(false);
            return;
        }

        if (InvUtils.shouldDisableFeatures()) return;

        // 2. 计算静止 Tick (修复原代码中遗漏的 noMoveTicks 逻辑)
        if (MovementUtils.moving()) {
            this.noMoveTicks = 0;
        } else {
            this.noMoveTicks++;
        }

        // 3. 外部容器拦截 (箱子、熔炉等)
        // 绝不能在浏览外部容器时整理背包，否则会误点箱子里的物品，也会与 Stealer 冲突
        boolean inForeignContainer = mc.screen instanceof AbstractContainerScreen<?> container
                && container.getMenu().containerId != mc.player.inventoryMenu.containerId;

//        if (inForeignContainer) {
//            return;
//        }

        // 4. 修复后的 inventoryOnly 核心逻辑
        if (this.inventoryOnly.enabled) {
            // 开启时：仅在明确打开自身物品栏时才允许工作
            if (!(mc.screen instanceof InventoryScreen)) {
                actions.clickOffHand = false;
                return;
            }
        }

        // --- Open Delay ---
        if (mc.screen instanceof InventoryScreen) {
            if (openDelayUntil == -1) {
                openDelayUntil = (long) (System.currentTimeMillis()
                                        + MathHelper.getRandom(openDelay.getMinValue(), openDelay.getMaxValue()));
            }
            if (System.currentTimeMillis() < openDelayUntil) {
                actions.clickOffHand = false;
                return;
            }
        }

        // --- Auto Armor ---
        if (this.autoArmor.enabled && AutoArmorHandler.handle(actions)) return;

        // --- Click Offhand (golden apple stacking) ---
        if (actions.finishOffhandPickup()) return;

        // --- Offhand Management ---
        if (OffhandHandler.handle(offhandMode, actions)) return;

        // --- Hotbar Slot Management ---
        for (int i = 0; i < 9; i++) {
            String type = slotConfigs[i].get();
            if ("None".equals(type)) continue;
            if (HotbarHandler.handleSlot(i, type, offhandMode, preferBow, actions)) return;
        }

        // --- Excess Item Limits ---
        if (ExcessHandler.handleExcess(actions,
                hasBowSlot(), maxArrowSize.getValue(),
                hasBlockSlot(), maxBlockSize.getValue(),
                keepProjectile.enabled, maxProjectileSize.getValue())) return;

        // --- Throw Junk ---
        if (this.throwItems.enabled && ExcessHandler.throwJunk(actions, this)) return;
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
}

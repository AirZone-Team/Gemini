package geminiclient.gemini.modules.impl.player;

import com.mojang.blaze3d.platform.InputConstants;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.RotationManager;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.MoveInputEvent;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.combat.killaura.Rotation;
import geminiclient.gemini.modules.impl.player.scaffold.ScaffoldBlockCounterRenderer;
import geminiclient.gemini.utils.MathHelper;
import geminiclient.gemini.utils.MovementUtils;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import org.lwjgl.glfw.GLFW;

import java.util.IdentityHashMap;
import java.util.Map;

public class Scaffold extends Module {

    // ---- Mode settings ----
    private final ListValue mode = new ListValue("Mode", "TellyBridge",
            new String[]{"GodBridge", "TellyBridge"});
    private final ListValue swapMode = new ListValue("SwapMode", "Normal",
            new String[]{"None", "Normal", "Silent", "InvSwitch"});

    // ---- Swap settings ----
    private final BoolValue swapBack = new BoolValue("SwapBack", true,
            () -> swapMode.is("Normal"));

    // ---- General settings ----
    private final BoolValue swingHand = new BoolValue("SwingHand", true);
    private final IntValue tellyTick = new IntValue("TellyTick", 0, 0, 8,
            () -> mode.is("TellyBridge"));
    private final BoolValue keepY = new BoolValue("KeepY", true,
            () -> mode.is("TellyBridge"));
    private final FloatValue rotationSpeed = new FloatValue("RotationSpeed", 10f, 1f, 360f);
    private final FloatValue rotationBackSpeed = new FloatValue("RotationBackSpeed", 10f, 0f, 360f,
            () -> mode.is("TellyBridge"));
    private final BoolValue rotationVariation = new BoolValue("RotationVariation", false);
    private final BoolValue sideCheck = new BoolValue("SideCheck", false);
    private final BoolValue safeWalk = new BoolValue("SafeWalk", true);

    // ---- Optional quality-of-life features (disabled by default) ----
    private final BoolValue blockCounter = new BoolValue("BlockCounter", false);
    private final ListValue countScope = new ListValue("CountScope", "Inventory",
            new String[]{"Hotbar", "Inventory"}, () -> blockCounter.enabled);
    private final IntValue lowBlockThreshold = new IntValue("LowBlockThreshold", 16, 1, 128,
            () -> blockCounter.enabled);
    private final BoolValue counterShadow = new BoolValue("CounterShadow", true,
            () -> blockCounter.enabled);
    private final BoolValue autoDisableEmpty = new BoolValue("AutoDisableEmpty", false);

    // ---- State ----
    private int yLevel;
    private int airTicks;
    private boolean shouldSwapBack;
    private BlockInfo blockInfo;
    private float serverYaw, serverPitch;
    private float pendingPlacementYawChange;
    private float previousPlacementYawChange;
    private boolean hasPreviousPlacementYawChange;
    private boolean safeWalkPressed;
    private int previousSlot = -1;
    private int[] swapSlots;

    // Inventory data is sampled once per game tick, not once per rendered frame.
    private int inventorySnapshotTick = Integer.MIN_VALUE;
    private BlockCountSnapshot hotbarSnapshot = BlockCountSnapshot.EMPTY;
    private BlockCountSnapshot inventorySnapshot = BlockCountSnapshot.EMPTY;
    private int heldBlockCount;
    private final Map<Block, Boolean> countableBlockCache = new IdentityHashMap<>();

    public Scaffold() {
        super("Scaffold", ModuleEnum.Player);
        hudX = 6;
        hudY = 248;
        addValue(mode, swapMode, swapBack, swingHand, tellyTick, keepY,
                rotationSpeed, rotationBackSpeed, rotationVariation, sideCheck, safeWalk,
                blockCounter, countScope, lowBlockThreshold, counterShadow,
                autoDisableEmpty);
    }

    @Override
    public void onEnabled() {
        blockInfo = null;
        shouldSwapBack = false;
        safeWalkPressed = false;
        previousSlot = -1;
        swapSlots = null;
        resetPlacementYawHistory();
        invalidateInventorySnapshot();
        if (mc.player != null) {
            serverYaw = mc.player.getYRot();
            serverPitch = mc.player.getXRot();
            yLevel = Mth.floor(mc.player.getY()) - 1;
            airTicks = 0;
        }
    }

    @Override
    public void onDisabled() {
        blockInfo = null;
        releaseSafeWalk();
        if (mc.player != null && previousSlot != -1) {
            swapBack();
        }
        if (mc.player != null && mc.gameMode != null && swapSlots != null) {
            invSwapBack();
        }
        previousSlot = -1;
        swapSlots = null;
        shouldSwapBack = false;
        resetPlacementYawHistory();
        invalidateInventorySnapshot();
        Gemini.rotationManager.releaseRotation(this);
    }

    @SuppressWarnings("unused")
    @EventTarget(0)
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) {
            blockInfo = null;
            releaseSafeWalk();
            invalidateInventorySnapshot();
            return;
        }

        if (mc.player.isPassenger()) {
            resetPlacementYawHistory();
        }

        if (autoDisableEmpty.enabled && countBlocksForActiveSwapMode() == 0) {
            setEnabled(false);
            return;
        }

        updateBlockInfo();

        if (mode.is("TellyBridge")) {
            if (mc.player.onGround()) {
                yLevel = Mth.floor(mc.player.getY()) - 1;
                airTicks = 0;
                blockInfo = null;
                Gemini.rotationManager.releaseRotation(this);
            } else {
                if (airTicks >= tellyTick.getValue() && blockInfo != null) {
                    FindItemResult item = findItem();
                    if (item.found()) {
                        Rotation rot = getRotation(blockInfo);
                        Gemini.rotationManager.requestRotation(this, rot.getYaw(), rot.getPitch(),
                                RotationManager.PRIORITY_SCAFFOLD, true);
                        place(item);
                    }
                }
                airTicks++;
            }
        } else {
            // GodBridge
            if (blockInfo != null) {
                FindItemResult item = findItem();
                if (item.found()) {
                    Rotation rot = getRotation(blockInfo);
                    Gemini.rotationManager.requestRotation(this, rot.getYaw(), rot.getPitch(),
                            RotationManager.PRIORITY_SCAFFOLD, true);
                    place(item);
                }
            } else {
                Gemini.rotationManager.releaseRotation(this);
            }
        }
    }

    @SuppressWarnings("unused")
    @EventTarget(5)
    public void onMotion(MotionEvent event) {
        // SafeWalk: hold shift when on block edge in GodBridge mode
        boolean shouldPress = safeWalk.enabled
                && mode.is("GodBridge")
                && mc.player != null
                && mc.player.onGround()
                && isOnBlockEdge();
        updateSafeWalk(shouldPress);
    }

    @EventTarget(5)
    public void onKeyInput(MoveInputEvent event) {
        if (mc.player != null && mc.player.onGround() && MovementUtils.moving()
                && mode.is("TellyBridge") && !mc.options.keyJump.isDown()) {
            event.setJump(true);
        }
    }

    @SuppressWarnings("unused")
    @EventTarget(10)
    public void onRender2D(Render2DEvent event) {
        if (!blockCounter.enabled || mc.player == null || mc.level == null) return;

        BlockCountSnapshot snapshot = getDisplaySnapshot();
        ScaffoldBlockCounterRenderer.render(
                event.guiGraphics(), this, snapshot.count(), snapshot.displayStack(),
                snapshot.displayName(),
                lowBlockThreshold.getValue(), counterShadow.enabled);
    }

    @Override
    public void renderEditorPlaceholder(GuiGraphicsExtractor graphics) {
        if (!blockCounter.enabled) return;

        if (enabled) {
            ScaffoldBlockCounterRenderer.renderOutline(
                    graphics, this, getDisplaySnapshot().displayName());
        } else {
            ScaffoldBlockCounterRenderer.renderPlaceholder(
                    graphics, this, lowBlockThreshold.getValue(), counterShadow.enabled);
        }
    }

    // ========================================================================
    //  Position finding
    // ========================================================================

    private void updateBlockInfo() {
        blockInfo = null;
        Vec3 eyePos = mc.player.getEyePosition();
        int yl = getYLevel();
        BlockPos base = BlockPos.containing(eyePos.x, yl, eyePos.z);
        int baseX = base.getX();
        int baseZ = base.getZ();

        if (mc.level.getBlockState(base).entityCanStandOn(mc.level, base, mc.player)) return;

        Block blockBelow = mc.level.getBlockState(base).getBlock();
        if (!(blockBelow instanceof AirBlock || blockBelow instanceof LiquidBlock)) return;

        if (checkBlock(eyePos, base, yl)) return;

        for (int d = 1; d <= 6; d++) {
            if (checkBlock(eyePos, new BlockPos(baseX, yl - d, baseZ), yl)) return;

            for (int x = 0; x <= d; x++) {
                for (int z = 0; z <= d - x; z++) {
                    int y = d - x - z;
                    int maxRx = x == 0 ? 0 : 1;
                    int maxRz = z == 0 ? 0 : 1;
                    for (int rx = 0; rx <= maxRx; rx++) {
                        for (int rz = 0; rz <= maxRz; rz++) {
                            int cx = baseX + (rx == 0 ? x : -x);
                            int cz = baseZ + (rz == 0 ? z : -z);
                            if (checkBlock(eyePos, new BlockPos(cx, yl - y, cz), yl)) return;
                        }
                    }
                }
            }
        }
    }

    private boolean checkBlock(Vec3 baseVec, BlockPos pos, int maxY) {
        if (pos.getY() > maxY) return false;

        Vec3 center = pos.getBottomCenter();
        for (Direction dir : Direction.values()) {
            Vec3 normal = dir.getUnitVec3();
            Vec3 hit = center.add(normal.scale(0.5));
            BlockPos baseBlockPos = pos.relative(dir);

            BlockState state = mc.level.getBlockState(baseBlockPos);
            if (state.getCollisionShape(mc.level, baseBlockPos).isEmpty() || state.getMenuProvider(mc.level, baseBlockPos) != null) {
                continue;
            }

            Direction face = dir.getOpposite();
            if (hit.distanceToSqr(baseVec) > 4.5 * 4.5 || hit.subtract(baseVec).dot(normal) < 0.0) {
                continue;
            }

            if (face == Direction.UP && MovementUtils.moving() && !mc.options.keyJump.isDown()) {
                continue;
            }

            blockInfo = new BlockInfo(pos, baseBlockPos, dir.getOpposite());
            return true;
        }

        return false;
    }

    private boolean canPlaceAgainst(BlockState state, BlockPos pos) {
        if (state.isAir()) return false;
        if (state.getBlock() instanceof LiquidBlock) return false;
        return Block.isShapeFullBlock(state.getCollisionShape(mc.level, pos));
    }

    // ========================================================================
    //  Placement
    // ========================================================================

    private void place(FindItemResult item) {
        BlockInfo info = blockInfo;
        if (info == null || mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (!onAir()) return;
        if (!canPlaceAt(info.blockPos)) return;
        if (rotationVariation.enabled && !mc.player.isPassenger()
                && !Gemini.rotationManager.isSourceControlling(this)) return;

        boolean facing = isFacingBlock(info.position, info.dir);
        if (!facing) return;

        // Swap to item
        switch (swapMode.get()) {
            case "Normal" -> {
                boolean save = swapBack.enabled;
                swap(item.slot, save);
                if (save) shouldSwapBack = true;
            }
            case "Silent" -> swap(item.slot, true);
            case "InvSwitch" -> invSwap(item.slot);
        }

        try {
            Vec3 clickLoc = getClickLocation(info.position, info.dir);
            BlockHitResult hit = new BlockHitResult(clickLoc, info.dir, info.position, false);
            InteractionHand hand = item.slot == 40
                    ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hit);

            if (result.consumesAction()) {
                recordPlacementYawChange();
                if (swingHand.enabled) {
                    mc.player.swing(hand);
                }
            }
        } finally {
            // Temporary swap modes must recover even when interaction rendering
            // or another client hook throws during useItemOn.
            switch (swapMode.get()) {
                case "Silent" -> swapBack();
                case "InvSwitch" -> invSwapBack();
            }
        }
    }

    // ========================================================================
    //  Rotation
    // ========================================================================

    private Rotation getRotation(BlockInfo info) {
        Rotation forward = calculateRotation(info.position, info.dir);
        Rotation reverse = new Rotation(Mth.wrapDegrees(mc.player.getYRot() - 180), forward.getPitch());

        Rotation target;
        float speed;
        if (isFacingBlockWithRotation(reverse, info.position, info.dir)) {
            target = reverse;
            speed = rotationBackSpeed.getValue();
        } else {
            target = forward;
            speed = rotationSpeed.getValue();
        }

        float yawDiff = MathHelper.wrapAngleTo180_float(target.getYaw() - serverYaw);
        float pitchDiff = target.getPitch() - serverPitch;

        float yawChange = Math.copySign(Math.min(Math.abs(yawDiff), speed), yawDiff);
        yawChange = varyRepeatedPlacementYawChange(yawChange);
        serverYaw += yawChange;
        serverPitch += Math.copySign(Math.min(Math.abs(pitchDiff), speed), pitchDiff);
        pendingPlacementYawChange = Math.abs(MathHelper.wrapAngleTo180_float(yawChange));

        return new Rotation(serverYaw, serverPitch);
    }

    private float varyRepeatedPlacementYawChange(float yawChange) {
        if (!rotationVariation.enabled || mc.player.isPassenger()) {
            hasPreviousPlacementYawChange = false;
            return yawChange;
        }

        float magnitude = Math.abs(MathHelper.wrapAngleTo180_float(yawChange));
        if (!hasPreviousPlacementYawChange || magnitude <= 2f
                || Math.abs(magnitude - previousPlacementYawChange) >= 0.0001f) {
            return yawChange;
        }

        float variedMagnitude = Math.max(0f, magnitude - 0.001f);
        return Math.copySign(variedMagnitude, yawChange);
    }

    private void recordPlacementYawChange() {
        if (!rotationVariation.enabled || mc.player.isPassenger()) {
            resetPlacementYawHistory();
            return;
        }

        previousPlacementYawChange = pendingPlacementYawChange;
        hasPreviousPlacementYawChange = true;
    }

    private void resetPlacementYawHistory() {
        pendingPlacementYawChange = 0f;
        previousPlacementYawChange = 0f;
        hasPreviousPlacementYawChange = false;
    }

    private Rotation calculateRotation(BlockPos pos, Direction dir) {
        double x = pos.getX() + 0.5 + dir.getStepX() * 0.5;
        double y = pos.getY() + 0.5 + dir.getStepY() * 0.5;
        double z = pos.getZ() + 0.5 + dir.getStepZ() * 0.5;

        double dx = x - mc.player.getX();
        double dy = y - (mc.player.getY() + mc.player.getEyeHeight());
        double dz = z - mc.player.getZ();

        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, dist));

        return new Rotation(yaw, Mth.clamp(pitch, -90, 90));
    }

    private boolean isFacingBlock(BlockPos pos, Direction dir) {
        return isFacingBlockWithRotation(
                new Rotation(Gemini.rotationManager.getYaw(), Gemini.rotationManager.getPitch()), pos, dir);
    }

    private boolean isFacingBlockWithRotation(Rotation rot, BlockPos pos, Direction dir) {
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = Vec3.directionFromRotation(rot.getPitch(), rot.getYaw());
        Vec3 endVec = eyePos.add(lookVec.scale(4.5));

        BlockHitResult result = mc.level.clip(new ClipContext(
                eyePos, endVec, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, mc.player));

        if (result.getType() == HitResult.Type.MISS) return false;

        if (!result.getBlockPos().equals(pos)) return false;

        return !sideCheck.enabled || result.getDirection() == dir;
    }

    // ========================================================================
    //  Inventory
    // ========================================================================

    private FindItemResult findItem() {
        switch (swapMode.get()) {
            case "None" -> {
                if (isValidItem(mc.player.getOffhandItem())) {
                    return new FindItemResult(40);
                }
                if (isValidItem(mc.player.getMainHandItem())) {
                    return new FindItemResult(mc.player.getInventory().getSelectedSlot());
                }
                return new FindItemResult(-1);
            }
            case "InvSwitch" -> {
                return findInInventory();
            }
            default -> {
                return findInHotbar();
            }
        }
    }

    private FindItemResult findInHotbar() {
        if (isValidItem(mc.player.getOffhandItem())) {
            return new FindItemResult(40);
        }
        if (isValidItem(mc.player.getMainHandItem())) {
            return new FindItemResult(mc.player.getInventory().getSelectedSlot());
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isValidItem(stack)) {
                return new FindItemResult(i);
            }
        }
        return new FindItemResult(-1);
    }

    private FindItemResult findInInventory() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isValidItem(stack)) {
                return new FindItemResult(i);
            }
        }
        if (isValidItem(mc.player.getOffhandItem())) {
            return new FindItemResult(40);
        }
        return new FindItemResult(-1);
    }

    private boolean isValidItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || mc.level == null || blockInfo == null) return false;
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        if (block instanceof TntBlock) return false;
        if (!Block.isShapeFullBlock(block.defaultBlockState().getCollisionShape(mc.level, blockInfo.blockPos))) return false;
        return !(block instanceof FallingBlock) || !FallingBlock.isFree(mc.level.getBlockState(blockInfo.blockPos));
    }

    /**
     * Counts blocks for the optional HUD without depending on a current placement
     * target. This deliberately stays separate from {@link #isValidItem(ItemStack)}
     * so enabling the counter cannot alter placement selection.
     */
    private BlockCountSnapshot getDisplaySnapshot() {
        refreshInventorySnapshot();
        return countScope.is("Inventory") ? inventorySnapshot : hotbarSnapshot;
    }

    private int countBlocksForActiveSwapMode() {
        refreshInventorySnapshot();
        if (swapMode.is("None")) {
            return heldBlockCount;
        }
        return swapMode.is("InvSwitch") ? inventorySnapshot.count() : hotbarSnapshot.count();
    }

    private void refreshInventorySnapshot() {
        if (mc.player == null || mc.level == null) {
            invalidateInventorySnapshot();
            return;
        }

        int tick = mc.player.tickCount;
        if (inventorySnapshotTick == tick) return;
        inventorySnapshotTick = tick;

        int hotbarCount = 0;
        int totalCount = 0;
        int inventorySize = Math.min(36, mc.player.getInventory().getContainerSize());
        ItemStack firstInventory = ItemStack.EMPTY;

        for (int i = 0; i < inventorySize; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isCountableBlock(stack)) continue;

            totalCount += stack.getCount();
            if (i < 9) hotbarCount += stack.getCount();
            if (firstInventory.isEmpty()) firstInventory = stack;
        }

        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offhand = mc.player.getOffhandItem();
        boolean mainValid = isCountableBlock(mainHand);
        boolean offhandValid = isCountableBlock(offhand);
        heldBlockCount = (mainValid ? mainHand.getCount() : 0)
                + (offhandValid ? offhand.getCount() : 0);

        if (offhandValid) {
            hotbarCount += offhand.getCount();
            totalCount += offhand.getCount();
        }

        ItemStack preferredHotbar = offhandValid ? offhand
                : mainValid ? mainHand : firstValidHotbarStack();
        ItemStack preferredInventory = !preferredHotbar.isEmpty()
                ? preferredHotbar : firstInventory;

        hotbarSnapshot = snapshot(hotbarCount, preferredHotbar);
        inventorySnapshot = snapshot(totalCount, preferredInventory);
    }

    private ItemStack firstValidHotbarStack() {
        int limit = Math.min(9, mc.player.getInventory().getContainerSize());
        for (int i = 0; i < limit; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isCountableBlock(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private BlockCountSnapshot snapshot(int count, ItemStack displayStack) {
        if (displayStack == null || displayStack.isEmpty()) {
            return new BlockCountSnapshot(count, ItemStack.EMPTY, "Building blocks");
        }
        return new BlockCountSnapshot(count, displayStack, displayStack.getItemName().getString());
    }

    private void invalidateInventorySnapshot() {
        inventorySnapshotTick = Integer.MIN_VALUE;
        hotbarSnapshot = BlockCountSnapshot.EMPTY;
        inventorySnapshot = BlockCountSnapshot.EMPTY;
        heldBlockCount = 0;
    }

    private boolean isCountableBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || !(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        if (block instanceof TntBlock) return false;

        return countableBlockCache.computeIfAbsent(block, candidate ->
                Block.isShapeFullBlock(candidate.defaultBlockState().getCollisionShape(
                        EmptyBlockGetter.INSTANCE, BlockPos.ZERO)));
    }

    private void swap(int slot, boolean savePrevious) {
        if (mc.player == null || slot < 0 || slot > 8) return;
        if (slot == 40 || slot == mc.player.getInventory().getSelectedSlot()) return;

        if (savePrevious && previousSlot == -1) {
            previousSlot = mc.player.getInventory().getSelectedSlot();
        } else if (!savePrevious) {
            previousSlot = -1;
        }

        mc.player.getInventory().setSelectedSlot(slot);
    }

    private void swapBack() {
        if (mc.player == null || previousSlot < 0 || previousSlot > 8) {
            previousSlot = -1;
            return;
        }
        mc.player.getInventory().setSelectedSlot(previousSlot);
        previousSlot = -1;
    }

    private void invSwap(int slot) {
        if (mc.player == null || mc.gameMode == null || slot == 40) return;
        if (slot < 0 || slot >= mc.player.getInventory().getContainerSize()) return;

        int containerSlot = slot;
        if (slot < 9) containerSlot += 36;

        int selected = mc.player.getInventory().getSelectedSlot();
        mc.gameMode.handleContainerInput(
                mc.player.containerMenu.containerId, containerSlot, selected,
                ContainerInput.SWAP, mc.player);
        swapSlots = new int[]{containerSlot, selected};
    }

    private void invSwapBack() {
        if (swapSlots == null || mc.player == null || mc.gameMode == null) {
            swapSlots = null;
            return;
        }
        mc.gameMode.handleContainerInput(
                mc.player.containerMenu.containerId, swapSlots[0], swapSlots[1],
                ContainerInput.SWAP, mc.player);
        swapSlots = null;
    }

    // ========================================================================
    //  Helpers
    // ========================================================================

    private int getYLevel() {
        if (keepY.enabled && !mc.options.keyJump.isDown()
                && MovementUtils.moving() && mode.is("TellyBridge")
                && mc.player.fallDistance <= 0.25) {
            return yLevel;
        }
        return Mth.floor(mc.player.getY()) - 1;
    }

    private boolean onAir() {
        Vec3 eyePos = mc.player.getEyePosition();
        BlockPos base = BlockPos.containing(eyePos.x, getYLevel(), eyePos.z);
        return mc.level.getBlockState(base).getBlock() instanceof AirBlock
                || mc.level.getBlockState(base).getBlock() instanceof LiquidBlock;
    }

    private boolean canPlaceAt(BlockPos pos) {
        if (!mc.level.getBlockState(pos).canBeReplaced()) return false;
        AABB box = new AABB(pos);
        for (Entity entity : mc.level.getEntities((Entity) null, box,
                e -> !(e instanceof ItemEntity || e instanceof ExperienceOrb
                        || e instanceof AbstractArrow))) {
            return false;
        }
        return true;
    }

    private Vec3 getClickLocation(BlockPos pos, Direction face) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        if (face.getAxis() == Direction.Axis.Y) {
            x += MathHelper.getRandom(0.3, -0.3);
            z += MathHelper.getRandom(0.3, -0.3);
        } else {
            y += MathHelper.getRandom(0.3, -0.3);
        }
        if (face.getAxis() == Direction.Axis.X) {
            z += MathHelper.getRandom(0.3, -0.3);
        }
        if (face.getAxis() == Direction.Axis.Z) {
            x += MathHelper.getRandom(0.3, -0.3);
        }
        return new Vec3(x, y, z);
    }

    private boolean isOnBlockEdge() {
        if (mc.player == null || mc.level == null) return false;
        double x = mc.player.getX();
        double z = mc.player.getZ();
        double y = mc.player.getY() - 1.0;

        BlockPos pos = BlockPos.containing(x, y, z);
        return mc.level.getBlockState(pos).canBeReplaced();
    }

    private void updateSafeWalk(boolean pressed) {
        if (pressed) {
            mc.options.keyShift.setDown(true);
            safeWalkPressed = true;
        } else {
            releaseSafeWalk();
        }
    }

    private void releaseSafeWalk() {
        if (!safeWalkPressed) return;

        InputConstants.Key key = mc.options.keyShift.getKey();
        long window = mc.getWindow().handle();
        boolean physicallyDown = key.getType() == InputConstants.Type.MOUSE
                ? GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS
                : InputConstants.isKeyDown(mc.getWindow(), key.getValue());
        mc.options.keyShift.setDown(physicallyDown);
        safeWalkPressed = false;
    }

    // ========================================================================
    //  Inner types
    // ========================================================================

    private record BlockInfo(BlockPos blockPos, BlockPos position, Direction dir) {
    }

    private record FindItemResult(int slot) {
        boolean found() {
            return slot != -1;
        }
    }

    private record BlockCountSnapshot(int count, ItemStack displayStack, String displayName) {
        private static final BlockCountSnapshot EMPTY =
                new BlockCountSnapshot(0, ItemStack.EMPTY, "Building blocks");
    }
}

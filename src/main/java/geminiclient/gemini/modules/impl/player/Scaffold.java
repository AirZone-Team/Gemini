package geminiclient.gemini.modules.impl.player;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.RotationManager;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.MoveInputEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.combat.killaura.Rotation;
import geminiclient.gemini.utils.MathHelper;
import geminiclient.gemini.utils.MovementUtils;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
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
    private final BoolValue sideCheck = new BoolValue("SideCheck", false);
    private final BoolValue safeWalk = new BoolValue("SafeWalk", true);

    // ---- State ----
    private int yLevel;
    private int airTicks;
    private boolean shouldSwapBack;
    private BlockInfo blockInfo;
    private float serverYaw, serverPitch;

    public Scaffold() {
        super("Scaffold", ModuleEnum.Player);
        addValue(mode, swapMode, swapBack, swingHand, tellyTick, keepY,
                rotationSpeed, rotationBackSpeed, sideCheck, safeWalk);
    }

    @Override
    public void onEnabled() {
        blockInfo = null;
        shouldSwapBack = false;
        if (mc.player != null) {
            serverYaw = mc.player.getYRot();
            serverPitch = mc.player.getXRot();
        }
    }

    @Override
    public void onDisabled() {
        blockInfo = null;
        if (shouldSwapBack) {
            swapBack();
            shouldSwapBack = false;
        }
        Gemini.rotationManager.releaseRotation(this);
    }

    @SuppressWarnings("unused")
    @EventTarget(0)
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;

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
        if (safeWalk.enabled && mode.is("GodBridge") && mc.player != null && mc.player.onGround()) {
            mc.options.keyShift.setDown(isOnBlockEdge());
        }
    }

    @EventTarget(5)
    public void onKeyInput(MoveInputEvent event) {
        if (mc.player.onGround() && MovementUtils.moving() && mode.is("TellyBridge") && !mc.options.keyJump.isDown()) {
            event.setJump(true);
        }
    }

    // ========================================================================
    //  Position finding
    // ========================================================================

    private void updateBlockInfo() {
        Vec3 eyePos = mc.player.getEyePosition();
        int yl = getYLevel();
        BlockPos base = BlockPos.containing(eyePos.x, yl, eyePos.z);
        int baseX = base.getX();
        int baseZ = base.getZ();

        if (mc.level.getBlockState(base).entityCanStandOn(mc.level, base, mc.player)) return;

        if (checkBlock(eyePos, base)) return;

        for (int d = 1; d <= 6; d++) {
            if (checkBlock(eyePos, new BlockPos(baseX, yl - d, baseZ))) return;

            for (int x = 0; x <= d; x++) {
                for (int z = 0; z <= d - x; z++) {
                    int y = d - x - z;
                    for (int rx = 0; rx <= 1; rx++) {
                        for (int rz = 0; rz <= 1; rz++) {
                            int cx = baseX + (rx == 0 ? x : -x);
                            int cz = baseZ + (rz == 0 ? z : -z);
                            if (checkBlock(eyePos, new BlockPos(cx, yl - y, cz))) return;
                        }
                    }
                }
            }
        }
    }

    private boolean checkBlock(Vec3 eyePos, BlockPos pos) {
        if (!(mc.level.getBlockState(pos).getBlock() instanceof AirBlock)) {
            return false;
        }

        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.offset(dir.getUnitVec3i());
            BlockState neighborState = mc.level.getBlockState(neighborPos);

            if (!canPlaceAgainst(neighborState, neighborPos)) continue;

            Vec3 faceCenter = center.add(Vec3.atLowerCornerOf(dir.getUnitVec3i()).scale(0.5));
            Vec3 toFace = faceCenter.subtract(eyePos);

            if (toFace.lengthSqr() <= 4.5 * 4.5 && toFace.dot(Vec3.atLowerCornerOf(dir.getUnitVec3i())) >= 0) {
                // Skip UP face placement in GodBridge when moving and not jumping
                if (dir == Direction.DOWN && mode.is("GodBridge") && MovementUtils.moving() && !mc.options.keyJump.isDown()) {
                    continue;
                }
                blockInfo = new BlockInfo(pos, neighborPos, dir.getOpposite());
                return true;
            }
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
        if (!onAir()) return;
        if (!canPlaceAt(blockInfo.blockPos)) return;

        boolean facing = isFacingBlock(blockInfo.position, blockInfo.dir);
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

        Vec3 clickLoc = getClickLocation(blockInfo.position, blockInfo.dir);
        BlockHitResult hit = new BlockHitResult(clickLoc, blockInfo.dir, blockInfo.position, false);
        InteractionResult result = mc.gameMode.useItemOn(mc.player,
                item.slot == 40 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, hit);

        if (result.consumesAction() && swingHand.enabled) {
            mc.player.swing(item.slot == 40 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        }

        // Swap back
        switch (swapMode.get()) {
            case "Silent" -> swapBack();
            case "InvSwitch" -> invSwapBack();
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

        serverYaw += Math.copySign(Math.min(Math.abs(yawDiff), speed), yawDiff);
        serverPitch += Math.copySign(Math.min(Math.abs(pitchDiff), speed), pitchDiff);

        return new Rotation(serverYaw, serverPitch);
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
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        if (block instanceof TntBlock) return false;
        if (!Block.isShapeFullBlock(block.defaultBlockState().getCollisionShape(mc.level, blockInfo.blockPos))) return false;
        return !(block instanceof FallingBlock) || !FallingBlock.isFree(mc.level.getBlockState(blockInfo.blockPos));
    }

    private int previousSlot = -1;

    private void swap(int slot, boolean savePrevious) {
        if (slot == 40 || slot == mc.player.getInventory().getSelectedSlot()) return;

        if (savePrevious && previousSlot == -1) {
            previousSlot = mc.player.getInventory().getSelectedSlot();
        } else if (!savePrevious) {
            previousSlot = -1;
        }

        mc.player.getInventory().setSelectedSlot(slot);
    }

    private void swapBack() {
        if (previousSlot == -1) return;
        mc.player.getInventory().setSelectedSlot(previousSlot);
        previousSlot = -1;
    }

    private int[] swapSlots = null;

    private void invSwap(int slot) {
        int containerSlot = slot;
        if (slot < 9) containerSlot += 36;
        else if (slot == 40) containerSlot = 45;

        int selected = mc.player.getInventory().getSelectedSlot();
        mc.gameMode.handleContainerInput(
                mc.player.containerMenu.containerId, containerSlot, selected,
                ContainerInput.SWAP, mc.player);
        swapSlots = new int[]{containerSlot, selected};
    }

    private void invSwapBack() {
        if (swapSlots == null) return;
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
}

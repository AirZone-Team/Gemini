package geminiclient.gemini.modules.impl.player;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.player.invmanager.InvUtils;
import geminiclient.gemini.utils.TimerUtils;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 自动工具模块：根据目标（方块或实体）自动切换至热键栏中的最佳工具或武器。
 */
public final class AutoTool extends Module {

    private static final String SMART_MODE = "SMART";
    private static final String FAST_MODE = "FAST";
    private static final String SIMPLE_MODE = "SIMPLE";
    private static final String[] MODES = new String[] { SMART_MODE, FAST_MODE, SIMPLE_MODE };
    private static final float SMART_IMPROVEMENT = 1.15f;

    private final IntValue switchDelay = new IntValue("SwitchDelay", 5, 0, 500);
    private final ListValue switchMode = new ListValue("SwitchMode", SMART_MODE, MODES);
    private final BoolValue switchForCombat = new BoolValue("CombatSwitch", true);
    private final BoolValue requireSneak = new BoolValue("RequireSneak", false);
    private final BoolValue protectTool = new BoolValue("ProtectTool", true);
    private final IntValue minThreshold = new IntValue("MinThreshold", 10, 1, 100, () -> protectTool.enabled);

    private final TimerUtils switchTimer = new TimerUtils();
    private int previousSlot = -1;
    private int autoSelectedSlot = -1;

    public AutoTool() {
        super("AutoTool", ModuleEnum.Player);
        this.addValue(switchDelay, switchMode, switchForCombat, requireSneak, protectTool, minThreshold);
    }

    private void setQuickbarSlot(int slot) {
        if (mc.player == null || !isQuickbarSlot(slot)) return;
        mc.player.getInventory().setSelectedSlot(slot);
    }

    private int getCurrentSlot() {
        return mc.player != null ? mc.player.getInventory().getSelectedSlot() : -1;
    }

    private boolean trySwitchToSlot(int newSlot) {
        int currentSlot = getCurrentSlot();
        if (!isQuickbarSlot(newSlot) || newSlot == currentSlot) return false;
        if (!switchMode.is(FAST_MODE) && !switchTimer.hasTimeElapsed(switchDelay.getValue(), false)) return false;

        if (!switchMode.is(SIMPLE_MODE) && previousSlot == -1) {
            previousSlot = currentSlot;
        }

        setQuickbarSlot(newSlot);
        autoSelectedSlot = switchMode.is(SIMPLE_MODE) ? -1 : newSlot;
        switchTimer.reset();
        return true;
    }

    private void tryRollback() {
        if (!switchMode.is(SIMPLE_MODE) && isQuickbarSlot(previousSlot)) {
            setQuickbarSlot(previousSlot);
        }
        clearSwitchState();
    }

    private void clearSwitchState() {
        previousSlot = -1;
        autoSelectedSlot = -1;
    }

    private boolean isQuickbarSlot(int slot) {
        return slot >= 0 && slot < 9;
    }

    @Override
    public void onDisabled() {
        tryRollback();
        super.onDisabled();
    }

    @SuppressWarnings("unused")
    @EventTarget
    private void onMotionEvent(MotionEvent event) {
        if (event.getTimeEnum() != TimeEnum.Pre) return;
        if (mc.player == null || mc.level == null) {
            clearSwitchState();
            return;
        }

        // 用户主动换槽后不再拥有该次切换，避免结束操作时覆盖用户选择。
        if (autoSelectedSlot != -1 && getCurrentSlot() != autoSelectedSlot) {
            clearSwitchState();
        }

        if ((requireSneak.enabled && !mc.player.isCrouching()) || !mc.options.keyAttack.isDown()) {
            tryRollback();
            return;
        }

        HitResult hitResult = mc.hitResult;
        if (hitResult == null) {
            tryRollback();
            return;
        }

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            handleMining((BlockHitResult) hitResult);
        } else if (hitResult.getType() == HitResult.Type.ENTITY && switchForCombat.enabled) {
            handleCombat((EntityHitResult) hitResult);
        } else {
            tryRollback();
        }
    }

    private void handleMining(BlockHitResult blockHit) {
        if (mc.player == null || mc.level == null) return;

        BlockPos blockPos = blockHit.getBlockPos();
        BlockState blockState = mc.level.getBlockState(blockPos);
        if (blockState.getDestroySpeed(mc.level, blockPos) < 0) {
            tryRollback();
            return;
        }

        Candidate bestTool = findBestTool(blockState);
        if (bestTool == null) {
            tryRollback();
            return;
        }

        int currentSlot = getCurrentSlot();
        if (bestTool.slot() == currentSlot) return;

        if (switchMode.is(SMART_MODE)) {
            float currentScore = calculateToolScore(mc.player.getMainHandItem(), blockState);
            if (bestTool.score() <= currentScore * SMART_IMPROVEMENT) return;
        }

        trySwitchToSlot(bestTool.slot());
    }

    private void handleCombat(EntityHitResult entityHit) {
        Entity entity = entityHit.getEntity();
        if (!(entity instanceof LivingEntity) || entity == mc.player) {
            tryRollback();
            return;
        }

        Candidate bestWeapon = findBestWeapon();
        if (bestWeapon == null) {
            tryRollback();
            return;
        }

        trySwitchToSlot(bestWeapon.slot());
    }

    private Candidate findBestWeapon() {
        if (mc.player == null) return null;

        Candidate best = null;
        int currentSlot = getCurrentSlot();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty() || (!stack.is(ItemTags.SWORDS) && !stack.is(ItemTags.AXES))) continue;
            if (protectTool.enabled && hasLowDurability(stack)) continue;

            Candidate candidate = new Candidate(slot, calculateWeaponScore(stack), remainingDurability(stack));
            if (isBetterCandidate(candidate, best, currentSlot)) best = candidate;
        }
        return best;
    }

    private Candidate findBestTool(BlockState blockState) {
        if (mc.player == null) return null;

        Candidate best = null;
        int currentSlot = getCurrentSlot();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty() || (protectTool.enabled && hasLowDurability(stack))) continue;
            if (!isToolEffective(stack, blockState)) continue;

            Candidate candidate = new Candidate(slot, calculateToolScore(stack, blockState), remainingDurability(stack));
            if (isBetterCandidate(candidate, best, currentSlot)) best = candidate;
        }
        return best;
    }

    private boolean isBetterCandidate(Candidate candidate, Candidate best, int currentSlot) {
        if (best == null) return true;

        int scoreComparison = Float.compare(candidate.score(), best.score());
        if (scoreComparison != 0) return scoreComparison > 0;
        if (candidate.slot() == currentSlot || best.slot() == currentSlot) return candidate.slot() == currentSlot;
        if (candidate.remainingDurability() != best.remainingDurability()) {
            return candidate.remainingDurability() > best.remainingDurability();
        }
        return candidate.slot() < best.slot();
    }

    private boolean isToolEffective(ItemStack tool, BlockState blockState) {
        boolean correctTool = tool.isCorrectToolForDrops(blockState);
        if (blockState.requiresCorrectToolForDrops()) return correctTool;
        return correctTool || tool.getDestroySpeed(blockState) > 1.0f;
    }

    private float calculateToolScore(ItemStack tool, BlockState blockState) {
        if (tool.isEmpty()) return 0.0f;
        if (blockState.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(blockState)) return 0.0f;

        float score = tool.getDestroySpeed(blockState);
        if (score > 1.0f) {
            int efficiency = InvUtils.getEnchantmentLevel(tool, Enchantments.EFFICIENCY);
            if (efficiency > 0) score += efficiency * efficiency + 1.0f;
        }
        return score;
    }

    private float calculateWeaponScore(ItemStack stack) {
        float score = 0.0f;
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            for (var entry : modifiers.modifiers()) {
                if (entry.attribute().value() == Attributes.ATTACK_DAMAGE.value()) {
                    score += (float) entry.modifier().amount();
                }
            }
        }

        int sharpness = InvUtils.getEnchantmentLevel(stack, Enchantments.SHARPNESS);
        if (sharpness > 0) score += 0.5f + sharpness * 0.5f;
        return score;
    }

    private boolean hasLowDurability(ItemStack stack) {
        return stack.isDamageableItem() && remainingDurability(stack) <= minThreshold.getValue();
    }

    private int remainingDurability(ItemStack stack) {
        return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
    }

    private record Candidate(int slot, float score, int remainingDurability) {
    }
}

package geminiclient.gemini.modules.impl.player;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.InventoryUtils;
import geminiclient.gemini.values.impl.BoolValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;

public class AutoTool extends Module {
    private final BoolValue checkSword = new BoolValue("CheckSword");
    private final BoolValue switchBack = new BoolValue("SwitchBack");

    private int originSlot = -1;
    private boolean wasDestroying = false;

    public AutoTool() {
        super("AutoTool", ModuleEnum.Player);
        addValue(checkSword,switchBack);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onMotion(MotionEvent e) {
        if (mc.player == null || mc.level == null) return;

        if (e.getTimeEnum() == TimeEnum.Pre) {
            handlePreMotion();
        } else {
            handlePostMotion();
        }
    }

    private void handlePreMotion() {
        if (!mc.gameMode.isDestroying()) {
            wasDestroying = false;
            return;
        }

        // 检查是否需要跳过武器
        if (shouldSkipWeaponCheck()) return;

        // 获取最佳工具并切换
        if (mc.hitResult instanceof BlockHitResult hitResult &&
                hitResult.getType() == HitResult.Type.BLOCK) {
            switchToBestTool(hitResult.getBlockPos());
        }
    }

    private void handlePostMotion() {
        if (shouldSwitchBack() && !mc.gameMode.isDestroying() && originSlot != -1) {
            mc.player.getInventory().setSelectedSlot(originSlot);
            originSlot = -1;
        }
    }

    private boolean shouldSkipWeaponCheck() {
        return checkSword.enabled &&
                mc.player.getMainHandItem().has(DataComponents.WEAPON);
    }

    private boolean shouldSwitchBack() {
        return switchBack.enabled && wasDestroying;
    }

    private void switchToBestTool(BlockPos pos) {
        int bestToolSlot = getBestTool(pos);
        if (isValidToolSlot(bestToolSlot)) {
            originSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(bestToolSlot);
            wasDestroying = true;
        }
    }

    private boolean isValidToolSlot(int slot) {
        return slot != -1 && slot != mc.player.getInventory().getSelectedSlot();
    }

    private int getBestTool(BlockPos pos) {
        if (mc.player == null || mc.level == null) return -1;

        BlockState blockState = mc.level.getBlockState(pos);
        if (blockState.isAir()) return -1;

        int bestSlot = -1;
        float bestSpeed = 1.0f;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = mc.player.getInventory().getItem(slot);

            if (isValidTool(item, blockState)) {
                float speed = calculateToolEffectiveness(item, blockState);

                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = slot;
                }
            }
        }

        return bestSlot;
    }

    private boolean isValidTool(ItemStack item, BlockState blockState) {
        return !item.isEmpty() &&
                !InventoryUtils.isGodItem(item) &&
                (!item.has(DataComponents.WEAPON) || blockState.getBlock() instanceof WebBlock);
    }

    private float calculateToolEffectiveness(ItemStack item, BlockState blockState) {
        float baseSpeed = item.getItem().getDestroySpeed(item, blockState);

        if (baseSpeed <= 1.0f) return baseSpeed;

        Block block = blockState.getBlock();
        // 对某些方块类型不应用效率附魔加成
        if (!(block instanceof DropExperienceBlock) && !(block instanceof RedStoneOreBlock)) {
            baseSpeed += getEfficiencyBonus(item);
        }

        return baseSpeed;
    }

    private float getEfficiencyBonus(ItemStack item) {
        int prtPoints;
        RegistryAccess drm =
                mc.level.registryAccess();
        Registry<Enchantment> registry = drm.freeze().lookupOrThrow(Registries.ENCHANTMENT);

        Optional<Holder.Reference<Enchantment>> protection =
                registry.get(Enchantments.EFFICIENCY);

        prtPoints = protection
                .map(entry -> EnchantmentHelper.getItemEnchantmentLevel(entry, item))
                .orElse(0);
        return prtPoints > 0 ? (float) (prtPoints * prtPoints + 1) : 0;
    }
}
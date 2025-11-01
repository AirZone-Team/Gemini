package geminiclient.gemini.modules.impl.movement;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.MovementUtils;
import geminiclient.gemini.values.impl.BoolValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.GameType;

public class Sprint extends Module {
    private final BoolValue checkHunger = new BoolValue("CheckHunger", true);
    private final BoolValue usingItem = new BoolValue("UsingItem", false);
    private final BoolValue inventory = new BoolValue("Inventory", false);

    public Sprint() {
        super("Sprint", ModuleEnum.Movement);
        addValue(checkHunger);
        addValue(usingItem);
        addValue(inventory);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null || !MovementUtils.moving())
            return;

        LocalPlayer player = mc.player;

        // 检查背包开关状态
        if (shouldCancelSprint()) {
            if (player.isSprinting()) {
                player.setSprinting(false);
            }
            return;
        }

        // 没有移动输入时停止疾跑
        if (player.xxa == 0 && player.zza == 0 && player.isSprinting()) {
            player.setSprinting(false);
            return;
        }

        // 满足条件时自动疾跑
        if (!mc.options.keySprint.isDown() && canSprint(player)) {
            player.setSprinting(true);
        }
    }

    private boolean shouldCancelSprint() {
        // 背包开关关闭且当前有GUI打开时取消疾跑
        return !inventory.enabled && mc.screen != null;
    }

    private boolean canSprint(LocalPlayer player) {
        if (mc.gameMode == null)
            return false;

        // 使用物品检查
        if (!usingItem.enabled && player.isUsingItem())
            return false;

        // 环境条件检查
        if (player.horizontalCollision || player.isPassenger() ||
                player.isInWater() || player.isInLava())
            return false;

        // 饥饿值检查
        if (checkHunger.enabled && player.getFoodData().getFoodLevel() <= 6)
            return false;

        // 飞行模式检查
        GameType gameType = mc.gameMode.getPlayerMode();
        boolean creativeOrSpectator = gameType == GameType.CREATIVE || gameType == GameType.SPECTATOR;
        return !player.getAbilities().flying || creativeOrSpectator;
    }

    @Override
    public void onDisabled() {
        if (mc.player != null && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }
    }
}
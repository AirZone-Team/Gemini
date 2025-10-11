package geminiclient.gemini.modules.impl.movement;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.GameType;

public class Sprint extends Module {
    private final BoolValue checkHunger = new BoolValue("CheckHunger", true);

    public Sprint() {
        super("Sprint", ModuleEnum.Movement);

        addValue(checkHunger);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        // 使用更高效的空值检查
        if (!isPlayerValid()) {
            return;
        }

        LocalPlayer player = mc.player;

        // 检查玩家是否正在移动（优化性能）
        if (player.xxa == 0 && player.zza == 0) {
            // 玩家没有移动输入，不需要疾跑
            if (player.isSprinting()) {
                player.setSprinting(false);
            }
            return;
        }

        if (!mc.options.keySprint.isDown() && canSprint(player)) {
            player.setSprinting(true);
        }
    }

    /**
     * 检查玩家是否有效且可用
     */
    private boolean isPlayerValid() {
        return mc != null && mc.player != null && mc.level != null;
    }

    /**
     * 判断玩家是否可以疾跑
     */
    private boolean canSprint(LocalPlayer player) {
        // 基础条件检查
        if (player.isUsingItem() || // 使用物品中
                player.isShiftKeyDown() || // 潜行中
                player.horizontalCollision || // 水平碰撞
                player.isPassenger()) { // 乘坐载具
            return false;
        }

        // 检查是否在液体中（水或熔岩）
        if (player.isInWater() || player.isInLava()) {
            return false;
        }

        // 可选的饥饿值检查（固定为6）
        if (checkHunger.enabled && player.getFoodData().getFoodLevel() <= 6) {
            return false;
        }

        // 检查玩家游戏模式
        GameType gameType = mc.gameMode.getPlayerMode();
        boolean isCreativeOrSpectator = gameType == GameType.CREATIVE || gameType == GameType.SPECTATOR;

        // 如果玩家在飞行，只有在创造模式或旁观模式下才允许疾跑
        if (player.getAbilities().flying) {
            return isCreativeOrSpectator;
        }

        // 对于非飞行状态，允许在地面或空中疾跑
        return true;
    }

    public void onDisable() {
        if (isPlayerValid() && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }
    }
}

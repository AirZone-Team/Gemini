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
    private final BoolValue sprintUsingItem = new BoolValue("Using Item", false); // 使用物品也能疾跑

    public Sprint() {
        super("Sprint", ModuleEnum.Movement);

        addValue(checkHunger);
        addValue(sprintUsingItem);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isPlayerValid()) {
            return;
        }

        LocalPlayer player = mc.player;

        // 玩家没有移动输入，不需要疾跑
        if (player.xxa == 0 && player.zza == 0) {
            if (player.isSprinting()) {
                player.setSprinting(false);
            }
            return;
        }

        if (!mc.options.keySprint.isDown() && canSprint(player)) {
            player.setSprinting(true);
        }
    }

    private boolean isPlayerValid() {
        return mc != null && mc.player != null && mc.level != null;
    }

    private boolean canSprint(LocalPlayer player) {
        // 蹲下时不疾跑
        if (player.isShiftKeyDown()) {
            return false;
        }

        // 使用物品检查（开关关闭时阻止疾跑）
        if (!sprintUsingItem.enabled && player.isUsingItem()) {
            return false;
        }

        if (player.horizontalCollision || player.isPassenger()) {
            return false;
        }

        if (player.isInWater() || player.isInLava()) {
            return false;
        }

        if (checkHunger.enabled && player.getFoodData().getFoodLevel() <= 6) {
            return false;
        }

        GameType gameType = mc.gameMode.getPlayerMode();
        boolean isCreativeOrSpectator = gameType == GameType.CREATIVE || gameType == GameType.SPECTATOR;

        if (player.getAbilities().flying) {
            return isCreativeOrSpectator;
        }

        return true;
    }

    public void onDisable() {
        if (isPlayerValid() && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }
    }
}

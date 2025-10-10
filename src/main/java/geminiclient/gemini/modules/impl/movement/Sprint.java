package geminiclient.gemini.modules.impl.movement;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.client.option.KeyBinding;

/**
 * 自动冲刺模块（支持 Minecraft 1.21.9）
 * 逻辑：
 * - 玩家前进时自动按下冲刺键
 * - 潜行/游泳/攀爬/骑乘等状态不触发
 * - 模块禁用时自动释放冲刺键
 */
public class Sprint extends Module {

    private boolean isSprinting = false;

    public Sprint() {
        super("Sprint", ModuleEnum.Movement);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;
        if (mc.player == null) return;

        // 禁止条件（不冲刺的情况）
        if (!mc.player.isAlive()
                || mc.player.isSpectator()
                || mc.player.isSubmergedInWater()
                || mc.player.isClimbing()
                || mc.player.isRiding()
                || mc.player.isSneaking()) {
            setSprintState(false);
            return;
        }

        // 判定玩家是否应该冲刺
        boolean shouldSprint = mc.player.forwardSpeed > 0.0f
                && !mc.player.getAbilities().creativeMode;

        setSprintState(shouldSprint);
    }

    private void setSprintState(boolean state) {
        // 仅当状态变化时更新（避免重复调用）
        if (state != isSprinting) {
            KeyBinding sprintKey = mc.options.keySprint;
            sprintKey.setDown(state);
            isSprinting = state;
        }
    }

    @Override
    public void onDisable() {
        // 禁用模块时释放冲刺键
        KeyBinding sprintKey = mc.options.keySprint;
        sprintKey.setDown(false);
        isSprinting = false;
        super.onDisable();
    }
}
package geminiclient.gemini.modules.impl.combat;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.RotationManager;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.combat.killaura.KillAuraSettings;
import geminiclient.gemini.modules.impl.combat.killaura.RotationEngine;
import geminiclient.gemini.modules.impl.combat.killaura.SettleWobble;
import geminiclient.gemini.modules.impl.combat.killaura.TargetIndicatorRenderer;
import geminiclient.gemini.modules.impl.combat.killaura.TargetManager;
import geminiclient.gemini.utils.MathHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * KillAura 入口：只负责事件编排（目标 → 瞄准点 → 旋转 → 点击 → 指示器），
 * 具体逻辑分别委托给 killaura 包下的子组件。
 */
public class KillAura extends Module {
    private final KillAuraSettings s = new KillAuraSettings();
    private final TargetManager targetManager = new TargetManager(s);
    private final RotationEngine rotation = new RotationEngine(s);
    private final TargetIndicatorRenderer indicators = new TargetIndicatorRenderer(s);
    private final SettleWobble settleWobbleState = new SettleWobble();

    public KillAura() {
        super("KillAura", ModuleEnum.Combat);
        addValue(s.allValues());
    }

    @Override
    public void onEnabled() {
        rotation.reseedPerlin();
        rotation.reset();
        targetManager.reset(0.0); // 初始化第一次延迟
        settleWobbleState.reset();
        indicators.reset();
    }

    @Override
    public void onDisabled() {
        Gemini.rotationManager.releaseRotation(this);
        rotation.reset();
        targetManager.resetState();
        settleWobbleState.reset();
        indicators.reset();
    }

    /** 当前锁定目标（供 BackTrack 等模块跟随），无目标时为 null。 */
    public Entity currentTarget() {
        return targetManager.current();
    }

    @SuppressWarnings("unused")
    @EventTarget(5)
    public void onMotion(MotionEvent event) {
        if (targetManager.current() == null || mc.player == null || !targetManager.canWork())
            return;
        if (event.getTimeEnum() == TimeEnum.Pre) {
            if (!s.silentRotate.enabled) {
                mc.player.setYRot(rotation.yaw());
                mc.player.setXRot(rotation.pitch());
            }
        }
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.gameMode == null)
            return;

        targetManager.findTargets();

        if (targetManager.current() != null && targetManager.canWork()) {
            updateTargetAngles(targetManager.current());
            Gemini.rotationManager.requestRotation(this, rotation.yaw(), rotation.pitch(),
                    RotationManager.PRIORITY_KILLAURA, true);

            if (s.rayTrace.enabled
                    && !targetManager.isLookingAt(targetManager.current(), rotation.yaw(), rotation.pitch()))
                return;

            // 切换目标后重新开始点击节奏（人眼重新锁定新目标需要时间）
            if (targetManager.current() != targetManager.clickTarget()) {
                targetManager.onTargetSwitched(
                        targetManager.current(), mc.player.distanceTo(targetManager.current()));
            }

            if (s.noCoolDown.enabled) {
                // [修复] 使用固定的 nextAttackDelay 进行比较，而不是一直生成新随机数
                if (targetManager.isClickDue()) {
                    mc.gameMode.attack(mc.player, targetManager.current());
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    targetManager.onAttack(mc.player.distanceTo(targetManager.current()));
                    indicators.onAttack();
                }
            } else {
                // 原版冷却模式
                if (mc.player.getAttackStrengthScale(0.5f) >= 1.0f) {
                    mc.gameMode.attack(mc.player, targetManager.current());
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    indicators.onAttack();
                }
            }
        } else {
            Gemini.rotationManager.releaseRotation(this);
            rotation.reset();
            targetManager.resetState();
            settleWobbleState.reset();
        }
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (targetManager.current() == null || targetManager.all().isEmpty()) return;

        List<Entity> renderTargets = s.indicatorTargets.is("Current")
                ? List.of(targetManager.current())
                : targetManager.all();
        indicators.render(event.poseStack(), renderTargets, targetManager.current(),
                event.partialTick());
    }

    private void updateTargetAngles(Entity entity) {
        if (mc.player == null)
            return;

        Vec3 eyePosition = new Vec3(
                mc.player.getX(),
                mc.player.getY() + mc.player.getEyeHeight(),
                mc.player.getZ());
        Vec3 reactedWorldAim = targetManager.computeAim(
                entity, eyePosition, rotation.yaw(), rotation.pitch());

        Vec3 aimDelta = reactedWorldAim.subtract(eyePosition);
        double horizontalDistance = MathHelper.sqrt_double(
                aimDelta.x * aimDelta.x + aimDelta.z * aimDelta.z);
        float targetYaw = (float) (Math.atan2(aimDelta.z, aimDelta.x) * 180 / Math.PI) - 90;
        float targetPitch = (float) (-(Math.atan2(aimDelta.y, horizontalDistance) * 180 / Math.PI));

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
        float pitchDiff = MathHelper.wrapAngleTo180_float(targetPitch - currentPitch);
        float aimDistance = (float) eyePosition.distanceTo(reactedWorldAim);
        rotation.updateJitter(entity, aimDistance);

        // 用“服务器视角离目标的角度误差”驱动人类微调颤振：靠近目标时手部会做阻尼微动
        settleWobbleState.update(angularError(targetYaw, targetPitch),
                s.settleWobble.getValue(), s.settleRange.getValue());

        float finalYaw = currentYaw + yawDiff + rotation.jitterYaw() + settleWobbleState.getYaw();
        float finalPitch = Mth.clamp(
                currentPitch + pitchDiff + rotation.jitterPitch() + settleWobbleState.getPitch(),
                -90.0f, 90.0f);
        rotation.apply(finalYaw, finalPitch, entity, aimDistance);
    }

    /** 服务器视角 (serverYaw/serverPitch) 与目标方向的夹角误差（度）。 */
    private float angularError(float targetYaw, float targetPitch) {
        return (float) Math.hypot(
                Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - rotation.yaw())),
                Math.abs(targetPitch - rotation.pitch()));
    }
}

package geminiclient.gemini.modules.impl.combat;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.moveFixEvent.JumpEvent;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.StrafeEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.combat.killaura.Rotation;
import geminiclient.gemini.utils.MathHelper;
import geminiclient.gemini.utils.TimerUtils;
import geminiclient.gemini.values.impl.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class KillAura extends Module {
    private final BoolValue noCoolDown = new BoolValue("NoCoolDown", false);
    private final IntRangeValue cps = new IntRangeValue("CPS", 10, 18, 1, 20, () -> noCoolDown.enabled);
    private final IntValue hurtTime = new IntValue("HurtTime", 10, 1, 10);
    private final FloatValue range = new FloatValue("Range", 3.0f, 1.0f, 6.0f);
    private final FloatValue fov = new FloatValue("FOV", 180f, 30f, 360f);
    private final FloatValue rotationSpeed = new FloatValue("RotationSpeed", 180f, 30f, 360f);
    private final ListValue pro = new ListValue("Priority", "Distance", new String[] {
            "Distance", "Health", "hurtTime"
    });
    private final CheckboxValue targets = new CheckboxValue("Targets", new BoolValue[] {
            new BoolValue("AttackPlayers", true), new BoolValue("AttackMobs", true),
            new BoolValue("AttackAnimals", false), new BoolValue("AttackDead")
    });
    private final CheckboxValue stop = new CheckboxValue("StopWorking", new BoolValue[] {
            new BoolValue("UsingItem"),
            new BoolValue("OpeningScreen")
    });
    private final BoolValue silentRotate = new BoolValue("SilentRotate", true);

    private final List<Entity> entities = new CopyOnWriteArrayList<>();
    private Entity curr;
    private final TimerUtils attackTimer = new TimerUtils();
    private boolean attackNextTick = false;

    Rotation rotation = new Rotation(0, 0);

    public KillAura() {
        super("KillAura", ModuleEnum.Combat);
        addValue(noCoolDown, cps, range, fov, hurtTime, pro,
                targets, stop, rotationSpeed,
                silentRotate);
    }

    @Override
    public void onEnabled() {
        rotation.setActive(true);
    }

    @Override
    public void onDisabled() {
        rotation.setActive(false);
    }

    @SuppressWarnings("unused")
    @EventTarget(0)
    public void onStrafe(StrafeEvent event) {
        if (curr != null)
            rotation.handleStrafe(event);
    }

    @SuppressWarnings("unused")
    @EventTarget(0)
    public void onJump(JumpEvent event) {
        if (curr != null)
            rotation.handleJump(event);
    }

    @SuppressWarnings("unused")
    @EventTarget(5)
    public void onMotion(MotionEvent event) {
        if (curr == null || mc.player == null || !stopWorkingMethod())
            return;
        if (event.getTimeEnum() == TimeEnum.Pre) {
            if (!silentRotate.enabled) {
                // 如果 SilentRotate 未启用，则实际旋转玩家视角
                mc.player.setYRot(rotation.getYaw());
                mc.player.setXRot(rotation.getPitch());
            } else {
                event.setyRot(rotation.getYaw());
                event.setxRot(rotation.getPitch());
                mc.player.yHeadRot = rotation.getYaw();
                mc.player.yBodyRot = rotation.getYaw();
            }
        }
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.gameMode == null)
            return;

        findTargets();

        if (curr != null) {
            updateTargetAngles(curr);
            if (stopWorkingMethod()) {
                if (noCoolDown.enabled) {
                    // 无冷却模式 (CPS 控制)
                    if (attackTimer.getTimeElapsed() >= getRandomDelay()) {
                        attackNextTick = true;
                        attackTimer.reset();
                    }

                    if (attackNextTick) {
                        mc.gameMode.attack(mc.player, curr);
                        mc.player.swing(InteractionHand.MAIN_HAND);
                        attackNextTick = false;
                    }
                } else {
                    // 原版冷却模式
                    if (mc.player.getAttackStrengthScale(0.5f) >= 1.0f) {
                        mc.gameMode.attack(mc.player, curr);
                        mc.player.swing(InteractionHand.MAIN_HAND);
                    }
                }
            }
        }
    }

    private long getRandomDelay() {
        if (!noCoolDown.enabled)
            return 0;

        int minCps = cps.getMinValue();
        int maxCps = cps.getMaxValue();
        int cpsVal = minCps + (int) (Math.random() * (maxCps - minCps + 1));

        return (long) (1000.0 / Math.max(1, cpsVal));
    }

    private void findTargets() {
        if (mc.level == null || mc.player == null)
            return;

        entities.clear();
        curr = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (isValidTarget(entity) && isInFov(entity) && mc.player.distanceTo(entity) < range.getValue()) {
                entities.add(entity);
            }
        }

        if (entities.isEmpty())
            return;

        switch (pro.get()) {
            case "Distance":
                entities.sort(Comparator.comparingDouble(mc.player::distanceTo));
                break;
            case "Health":
                entities.sort(
                        Comparator.comparingDouble(e -> e instanceof LivingEntity living ? living.getHealth() : 0.0));
                break;
            case "hurtTime":
                entities.sort(
                        Comparator.comparingDouble(e -> e instanceof LivingEntity living ? living.hurtTime : 0.0));
                break;
        }

        curr = entities.getFirst();
    }

    private boolean stopWorkingMethod() {
        if (mc.player == null)
            return false;
        if (mc.player.isUsingItem() && stop.boolValues[0].enabled)
            return false;
        return mc.screen == null || !stop.boolValues[1].enabled;
    }

    private boolean isValidTarget(Entity entity) {
        if (mc.player == null)
            return false;
        if (entity == null || entity == mc.player)
            return false;
        if (!(entity instanceof LivingEntity))
            return false;
        if (((LivingEntity) entity).hurtTime > hurtTime.getValue())
            return false;
        if (entity instanceof ArmorStand)
            return false;
        if (entity.isAlive() || targets.boolValues[3].enabled) {
            if (entity instanceof Player && targets.boolValues[0].enabled)
                return true;
            if ((entity instanceof Mob || entity instanceof Slime || entity instanceof Bat)
                    && targets.boolValues[1].enabled)
                return true;
            return entity instanceof Animal && targets.boolValues[2].enabled;
        }
        return false;
    }

    private boolean isInFov(Entity entity) {
        if (mc.player == null)
            return false;
        if (fov.getValue() >= 360f)
            return true;
        double dx = entity.getX() - mc.player.getX();
        double dz = entity.getZ() - mc.player.getZ();
        double yawToEntity = Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        double yawDiff = Math.abs(((mc.player.getYRot() - yawToEntity) % 360 + 540) % 360 - 180);
        return yawDiff <= fov.getValue() / 2;
    }

    /**
     * 瞬时瞄准，不进行平滑或预测。
     */
    private void updateTargetAngles(Entity entity) {
        if (mc.player == null)
            return;

        final double xSize = entity.getX() - mc.player.getX();
        final double ySize = entity.getY() + entity.getEyeHeight() / 2 - (mc.player.getY() + mc.player.getEyeHeight());
        final double zSize = entity.getZ() - mc.player.getZ();
        final double theta = MathHelper.sqrt_double(xSize * xSize + zSize * zSize);

        // 计算基础目标角度
        final float targetYaw = (float) (Math.atan2(zSize, xSize) * 180 / Math.PI) - 90;
        final float targetPitch = (float) (-(Math.atan2(ySize, theta) * 180 / Math.PI));

        // 获取当前真实的玩家角度（保留了超出 360 度的累加值）
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        // 核心修复：计算当前角度与目标角度的“最短差值”（将差值 wrap 到 -180 ~ 180）
        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
        float pitchDiff = MathHelper.wrapAngleTo180_float(targetPitch - currentPitch);

        // 将差值加到当前累加角度上，彻底消除跨越 180/-180 边界时的 360° 突变
        float finalYaw = currentYaw + yawDiff;

        // Pitch 的范围在原版中严格限制在 -90 到 90 之间，不会出现 360 累加，直接 Clamp 即可
        float finalPitch = net.minecraft.util.Mth.clamp(currentPitch + pitchDiff, -90.0f, 90.0f);

        // 设置修复后的最终角度 — 每 tick 最多旋转 rotationSpeed 度
        float step = rotationSpeed.getValue();
        float rotYawDiff = MathHelper.wrapAngleTo180_float(finalYaw - rotation.getYaw());
        rotation.setYaw(rotation.getYaw() + Math.copySign(Math.min(Math.abs(rotYawDiff), step), rotYawDiff));
        float rotPitchDiff = MathHelper.wrapAngleTo180_float(finalPitch - rotation.getPitch());
        rotation.setPitch(rotation.getPitch() + Math.copySign(Math.min(Math.abs(rotPitchDiff), step), rotPitchDiff));
    }
}

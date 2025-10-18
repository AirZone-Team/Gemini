package geminiclient.gemini.modules.impl.combat;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.moveFixEvent.AttackYawEvent;
import geminiclient.gemini.event.events.impl.moveFixEvent.JumpEvent;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.moveFixEvent.RayTraceEvent;
import geminiclient.gemini.event.events.impl.StrafeEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.combat.killaura.Rotation;
import geminiclient.gemini.utils.MathHelper;
import geminiclient.gemini.utils.MovementUtils;
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
    private final BoolValue moveFix = new BoolValue("MoveFix");

    private final List<Entity> entities = new CopyOnWriteArrayList<>();
    private Entity curr;
    private final TimerUtils attackTimer = new TimerUtils();
    private boolean attackNextTick = false;

    Rotation rotation = new Rotation(0, 0);

    public KillAura() {
        super("KillAura", ModuleEnum.Combat);
        addValue(noCoolDown, cps, range, fov, hurtTime, pro,
                targets, stop,
                silentRotate, moveFix);
    }

    @Override
    public void onEnabled() {
        if (mc.player != null) {
            rotation.setYaw(mc.player.getYRot());
            rotation.setPitch(mc.player.getXRot());
        }
    }

    @SuppressWarnings("unused")
    @EventTarget(0)
    public void onStrafe(StrafeEvent event) {
        if (mc.player == null)
            return;
        if (curr != null && moveFix.enabled) {
            MovementUtils.fixMovement(event, rotation.getYaw());
            // mc.player.input.getMoveVector() = new Vec2(this.keyPresses.left() ==
            // this.keyPresses.right() ? 0.0F : (this.keyPresses.left() ? 1.0F : -1.0F),
            // this.keyPresses.forward() == this.keyPresses.backward() ? 0.0F :
            // (this.keyPresses.forward() ? 1.0F : -1.0F));
            event.setStrafe(mc.player.input.keyPresses.left() == mc.player.input.keyPresses.right() ? 0.0F
                    : (mc.player.input.keyPresses.left() ? 1.0F : -1.0F));
            event.setForward(mc.player.input.keyPresses.forward() == mc.player.input.keyPresses.backward() ? 0.0F
                    : (mc.player.input.keyPresses.forward() ? 1.0F : -1.0F));
            event.setYaw(rotation.getYaw());
        }
    }

    @SuppressWarnings("unused")
    @EventTarget(0)
    public void onJump(JumpEvent event) {
        if (curr != null && moveFix.enabled) {
            event.setYaw(rotation.getYaw());
        }
    }

    @SuppressWarnings("unused")
    @EventTarget(0)
    public void onAttackYaw(AttackYawEvent event) {
        if (curr != null && moveFix.enabled) {
            event.setYaw(rotation.getYaw());
        }
    }

    @SuppressWarnings("unused")
    @EventTarget(0)
    public void onRay(RayTraceEvent event) {
        if (curr != null && event.entity == mc.player) {
            event.setYaw(rotation.getYaw());
            event.setPitch(rotation.getPitch());
        }
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

        final float targetYaw = (float) (Math.atan2(zSize, xSize) * 180 / Math.PI) - 90;
        final float targetPitch = (float) (-(Math.atan2(ySize, theta) * 180 / Math.PI));

        // 包装角度
        float wrappedTargetYaw = MathHelper.wrapAngleTo180_float(targetYaw);
        float wrappedTargetPitch = net.minecraft.util.Mth.clamp(targetPitch, -90.0f, 90.0f);

        // 获取当前角度
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        // 计算角度差（考虑角度环绕）
        float deltaYaw = MathHelper.wrapAngleTo180_float(wrappedTargetYaw - currentYaw);

        // 限制单次旋转变化不超过安全阈值（远小于320度）
        float maxRotationPerTick = 120.0f; // 安全阈值

        if (Math.abs(deltaYaw) > maxRotationPerTick) {
            // 逐步旋转：只旋转最大允许的角度
            float stepYaw = Math.signum(deltaYaw) * maxRotationPerTick;
            rotation.setYaw(currentYaw + stepYaw);
        } else {
            // 直接旋转到目标
            rotation.setYaw(wrappedTargetYaw);
        }

        // Pitch 通常变化较小，可以直接设置
        rotation.setPitch(wrappedTargetPitch);
    }

    private float wrapDegrees(double degrees) {
        degrees %= 360.0;
        if (degrees >= 180.0)
            degrees -= 360.0;
        if (degrees < -180.0)
            degrees += 360.0;
        return (float) degrees;
    }
}

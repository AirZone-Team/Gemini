package geminiclient.gemini.modules.impl.combat;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.TimerUtils;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.CheckboxValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.IntRangeValue;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
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
    private final FloatValue range = new FloatValue("Range", 3.0f, 1.0f, 6.0f);
    private final FloatValue fov = new FloatValue("FOV", 180f, 30f, 360f);
    private final CheckboxValue targets = new CheckboxValue("Targets", new BoolValue[]{
            new BoolValue("AttackPlayers", true)
            , new BoolValue("AttackMobs", true)
            , new BoolValue("AttackAnimals", false)
            , new BoolValue("AttackDied")
    });
    private final CheckboxValue stop = new CheckboxValue("StopWorking",new BoolValue[]{
            new BoolValue("UsingItem"),
            new BoolValue("OpeningScreen")
    });
    private final BoolValue silentRotate = new BoolValue("SilentRotate", true);

    private final List<Entity> entities = new CopyOnWriteArrayList<>();
    private Entity curr;
    private final TimerUtils attackTimer = new TimerUtils();
    private boolean attackNextTick = false;
    private float yawLogic;
    private float pitchLogic;

    public KillAura() {
        super("KillAura", ModuleEnum.Combat);
        // 重新添加 silentRotate
        addValue(noCoolDown, cps, range, fov,
                targets,stop,
                silentRotate);
    }

    @Override
    public void onEnabled() {
        if (mc.player != null) {
            yawLogic = mc.player.getYRot();
            pitchLogic = mc.player.getXRot();
        }
    }

    @SuppressWarnings("unused")
    @EventTarget(0)
    public void onMotion(MotionEvent event) {
        if (curr == null || mc.player == null || !stopWorkingMethod()) return;
        if (!silentRotate.enabled) {
            // 如果 SilentRotate 未启用，则实际旋转玩家视角
            mc.player.setYRot(yawLogic);
            mc.player.setXRot(pitchLogic);
        } else {
            mc.player.connection.getConnection().send(new ServerboundMovePlayerPacket.Rot(yawLogic, pitchLogic, mc.player.onGround(), mc.player.horizontalCollision));
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
        if (!noCoolDown.enabled) return 0;

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

        if (entities.isEmpty()) return;

        entities.sort(Comparator.comparingDouble(mc.player::distanceTo));

        curr = entities.getFirst();
    }

    private boolean stopWorkingMethod() {
        if (mc.player == null) return false;
        if (mc.player.isUsingItem() && stop.boolValues[0].enabled) return false;
        if (mc.screen != null && stop.boolValues[1].enabled) return false;

        return true;
    }

    private boolean isValidTarget(Entity entity) {
        if (mc.player == null) return false;
        if (entity == null || entity == mc.player || !entity.isAlive()) return false;
        if (!(entity instanceof LivingEntity)) return false;
        if (entity instanceof ArmorStand) return false;
        if (mc.player.distanceTo(entity) > range.getValue()) return false;

        if (entity instanceof Player && targets.boolValues[0].enabled) return true;

        if ((entity instanceof Mob || entity instanceof Slime || entity instanceof Bat) && targets.boolValues[1].enabled)
            return true;

        if (entity.isAlive() || targets.boolValues[3].enabled) return true;

        return entity instanceof Animal && targets.boolValues[2].enabled;
    }

    private boolean isInFov(Entity entity) {
        if (mc.player == null) return false;
        if (fov.getValue() >= 360f) return true;
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
        if (mc.player == null) return;
        // 直接瞄准目标当前位置
        double dx = entity.getX() - mc.player.getX();
        double dz = entity.getZ() - mc.player.getZ();
        double dy = entity.getEyeY() - mc.player.getEyeY();

        double dist = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90F);
        float targetPitch = (float) -(Math.toDegrees(Math.atan2(dy, dist)));

        // 瞬时更新角度
        yawLogic = targetYaw;
        pitchLogic = targetPitch;

        pitchLogic = Math.max(-90f, Math.min(90f, pitchLogic));
        yawLogic = wrapDegrees(yawLogic);
    }

    private float wrapDegrees(double degrees) {
        degrees %= 360.0;
        if (degrees >= 180.0) degrees -= 360.0;
        if (degrees < -180.0) degrees += 360.0;
        return (float) degrees;
    }
}

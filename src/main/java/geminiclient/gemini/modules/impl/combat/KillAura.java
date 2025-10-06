package geminiclient.gemini.modules.impl.combat;

import com.cubk.event.annotations.EventTarget;
import geminiclient.gemini.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.TimerUtils;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.IntRangeValue;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity; 
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList; 
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class KillAura extends Module {

    private final List<Entity> entities = new CopyOnWriteArrayList<>();
    private Entity curr;
    private final TimerUtils attackTimer = new TimerUtils();
    private boolean attackNextTick = false; 

    // --- 保留的设置项 ---
    private final BoolValue noCoolDown = new BoolValue("NoCoolDown", false);
    private final IntRangeValue cps = new IntRangeValue("CPS", 10, 18, 1, 20, () -> noCoolDown.enabled);
    private final FloatValue range = new FloatValue("Range", 3.0f, 1.0f, 6.0f);
    private final FloatValue fov = new FloatValue("FOV", 180f, 30f, 360f);
    
    private final BoolValue attackPlayers = new BoolValue("AttackPlayers", true);
    private final BoolValue attackMobs = new BoolValue("AttackMobs", true);
    private final BoolValue attackAnimals = new BoolValue("AttackAnimals", false);
    
    // --- 重新添加 SilentRotate ---
    private final BoolValue silentRotate = new BoolValue("SilentRotate", true);
    // -------------------

    private float yawLogic;
    private float pitchLogic;

    public KillAura() {
        super("KillAura", ModuleEnum.Combat, true);
        // 重新添加 silentRotate
        addValue(noCoolDown, cps, range, fov, 
                attackPlayers, attackMobs, attackAnimals,
                silentRotate); 
    }
    
    public void onEnable() {
        if (player != null) {
            yawLogic = player.getYRot();
            pitchLogic = player.getXRot();
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (player == null || mc.gameMode == null) return;

        findTargets(); 

        if (curr != null && curr.isAlive()) {
            // 瞬时计算目标角度
            updateTargetAngles(curr);

            // --- 重新添加 SilentRotate 检查逻辑 ---
            if (!silentRotate.enabled) {
                // 如果 SilentRotate 未启用，则实际旋转玩家视角
                player.setYRot(yawLogic);
                player.setXRot(pitchLogic);
            }
            // ----------------------------------------
            
            double distance = player.distanceTo(curr);

            if (distance < range.getValue()) {
                
                if (noCoolDown.enabled) {
                    // 无冷却模式 (CPS 控制)
                    if (attackTimer.getTimeElapsed() >= getRandomDelay()) {
                        attackNextTick = true;
                        attackTimer.reset();
                    }

                    if (attackNextTick) {
                        mc.gameMode.attack(player, curr);
                        player.swing(InteractionHand.MAIN_HAND);
                        attackNextTick = false;
                    }
                } else {
                    // 原版冷却模式
                    if (player.getAttackStrengthScale(0.5f) >= 1.0f) {
                        mc.gameMode.attack(player, curr);
                        player.swing(InteractionHand.MAIN_HAND);
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
        entities.clear();
        curr = null;
        if (mc.level == null) return;
        
        List<Entity> potentialTargets = new ArrayList<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (isValidTarget(entity) && isInFov(entity)) {
                potentialTargets.add(entity);
            }
        }

        if (potentialTargets.isEmpty()) return;
        
        potentialTargets.sort(Comparator.comparingDouble(player::distanceTo));

        curr = potentialTargets.get(0);
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity == player || !entity.isAlive()) return false;
        if (!(entity instanceof LivingEntity)) return false; 
        if (entity instanceof ArmorStand) return false;
        if (player.distanceTo(entity) > range.getValue()) return false;

        if (entity instanceof Player && attackPlayers.enabled) return true;
        
        if ((entity instanceof Mob || entity instanceof Slime || entity instanceof Bat) && attackMobs.enabled) return true;
        
        if (entity instanceof Animal && attackAnimals.enabled) return true;

        return false;
    }

    private boolean isInFov(Entity entity) {
        if (fov.getValue() >= 360f) return true;
        double dx = entity.getX() - player.getX();
        double dz = entity.getZ() - player.getZ();
        double yawToEntity = Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        double yawDiff = Math.abs(((player.getYRot() - yawToEntity) % 360 + 540) % 360 - 180);
        return yawDiff <= fov.getValue() / 2;
    }

    /**
     * 瞬时瞄准，不进行平滑或预测。
     */
    private void updateTargetAngles(Entity entity) {
        // 直接瞄准目标当前位置
        double dx = entity.getX() - player.getX();
        double dz = entity.getZ() - player.getZ();
        double dy = entity.getEyeY() - player.getEyeY();

        double dist = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90F);
        float targetPitch = (float) -(Math.toDegrees(Math.atan2(dy, dist)));

        // 瞬时更新角度
        yawLogic = targetYaw;
        pitchLogic = targetPitch;
        
        pitchLogic = Math.max(-90f, Math.min(90f, pitchLogic)); 
        yawLogic = wrapDegrees(yawLogic); 
    }

    private float clamp(double value, double min, double max) {
        return (float) Math.max(min, Math.min(max, value));
    }

    private float wrapDegrees(double degrees) {
        degrees %= 360.0;
        if (degrees >= 180.0) degrees -= 360.0;
        if (degrees < -180.0) degrees += 360.0;
        return (float) degrees;
    }
}

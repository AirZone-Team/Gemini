package geminiclient.gemini.modules.impl.combat;

import com.cubk.event.annotations.EventTarget;
import geminiclient.gemini.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.TimerUtils;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.IntRangeValue;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.List;

public class KillAura extends Module {
    private final List<Entity> entities = new ArrayList<>();
    private Entity curr;
    TimerUtils attackTimer = new TimerUtils();
    private boolean attackNextTick = false;

    BoolValue noCoolDown = new BoolValue("NoCoolDown");
    IntRangeValue cps = new IntRangeValue("CPS",10,18,0,20,() -> noCoolDown.enabled);

    public KillAura() {
        super("KillAura", ModuleEnum.Combat, true);
        addValue(noCoolDown,cps);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void updateEvent(UpdateEvent event) {
        if (curr != null) {
            long delay1 = (long) (1000.0 / cps.getMinValue());
            long delay2 = (long) (1000.0 / cps.getMaxValue());

            delay1 = Math.max(delay1, delay2);

            long delay = (long) (delay2 + (delay1 - delay2) * Math.random());

            if (attackTimer.getTimeElapsed() >= delay) {
                attackNextTick = true;
                attackTimer.reset();
            }
        }
        if (player == null || mc.gameMode == null)
            return;

        findTargets();
        if (curr != null && curr.isAlive() && player.distanceTo(curr) <= 3.0f) {
            if (entities.isEmpty()) {
                attackTimer.reset();
            } else if ((attackNextTick && noCoolDown.enabled) || (player.getAttackStrengthScale(0.5f) >= 1.0f && !noCoolDown.enabled)) {
                mc.gameMode.attack(player, curr);
                player.swing(InteractionHand.MAIN_HAND);
                attackNextTick = false;
            }
        }
    }

    private void findTargets() {
        entities.clear();
        curr = null;

        if (player == null || mc.level == null)
            return;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (isValidTarget(entity)) {
                entities.add(entity);
            }
        }

        if (!entities.isEmpty()) {
            curr = entities.stream()
                    .min((e1, e2) -> Float.compare(player.distanceTo(e1), player.distanceTo(e2)))
                    .orElse(null);
        }
    }

    private boolean isValidTarget(Entity entity) {
        if (player == null) return false;
        if (entity == null) return false;
        if (entity == player) return false;
        if (!entity.isAlive()) return false;

        if (player.distanceTo(entity) > 3.0f) return false;

        if (entity instanceof ArmorStand) return false;

        if (!(entity instanceof Player) && !isHostileMob(entity)) return false;

        return player.hasLineOfSight(entity);
    }

    private boolean isHostileMob(Entity entity) {
        return entity instanceof Mob || entity instanceof Slime || entity instanceof Bat;
    }
}
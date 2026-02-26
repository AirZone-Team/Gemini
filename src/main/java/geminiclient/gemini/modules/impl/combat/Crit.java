package geminiclient.gemini.modules.impl.combat;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.AttackEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public class Crit extends Module {
    private final ListValue modes = new ListValue("Modes", "Jump", new String[]{
            "Jump",
            "Packet",
            "Packet2"
    });

    public Crit() {
        super("Crit", ModuleEnum.Combat);
        addValue(modes);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (mc.player == null)
            return;

        if (mc.getConnection() == null)
            return;
        switch (modes.get()) {
            case "Jump":
                if (mc.player.onGround())
                    mc.player.jumpFromGround();
                break;
            case "Packet":
                if (mc.player.onGround()) {
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ(), mc.player.onGround(), mc.player.horizontalCollision));
                }
                break;
            case "Packet2":
                if (mc.player.onGround()) {
                    Vec3 vec3 = mc.player.getDeltaMovement();
                    mc.player.setDeltaMovement(new Vec3(vec3.x, vec3.y + 0.02, vec3.z));
                }
        }
    }
}

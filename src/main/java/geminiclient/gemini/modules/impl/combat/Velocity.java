package geminiclient.gemini.modules.impl.combat;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.PacketEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

public class Velocity extends Module {

    public Velocity() {
        super("Velocity",ModuleEnum.Combat);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onPacket(PacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundSetEntityMotionPacket) {
            event.setCancelled(true);
        }
    }
}

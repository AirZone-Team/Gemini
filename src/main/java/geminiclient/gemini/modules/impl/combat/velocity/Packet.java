package geminiclient.gemini.modules.impl.combat.velocity;

import geminiclient.gemini.event.events.impl.PacketEvent;
import geminiclient.gemini.modules.impl.Mode;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

public class Packet extends Mode {
    public Packet() {
        super("Packet");
    }

    @Override
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof ClientboundSetEntityMotionPacket) {
            event.setCancelled(true);
        }
    }
}

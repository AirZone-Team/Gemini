package geminiclient.gemini.modules.impl.player.nofalls;

import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.impl.Mode;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class PacketNoFall extends Mode {
    public PacketNoFall() {
        super("Packet");
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null)
            return;

        if (mc.player.fallDistance > 3) {
            mc.player.connection.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true,mc.player.horizontalCollision));
        }
    }
}

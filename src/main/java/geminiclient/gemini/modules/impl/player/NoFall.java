package geminiclient.gemini.modules.impl.player;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class NoFall extends Module {
    public NoFall() {
        super("NoFall", ModuleEnum.Player);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null)
            return;

        if (mc.player.fallDistance > 3) {
            mc.player.connection.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true,mc.player.horizontalCollision));
        }
    }
}

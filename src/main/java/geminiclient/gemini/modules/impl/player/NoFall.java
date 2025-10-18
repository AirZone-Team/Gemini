package geminiclient.gemini.modules.impl.player;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.PacketEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.Mode;
import geminiclient.gemini.modules.impl.player.nofalls.PacketNoFall;
import geminiclient.gemini.modules.impl.player.nofalls.SpoofLandingNoFall;
import geminiclient.gemini.values.impl.ListValue;

import java.util.ArrayList;
import java.util.List;

public class NoFall extends Module {
    List<Mode> modeList = new ArrayList<>();
    private final ListValue modes = new ListValue("Modes","Packet",new String[]{
            "Packet","SpoofLanding"
    });
    public NoFall() {
        super("NoFall", ModuleEnum.Player);
        addValue(modes);
        modeList.add(new PacketNoFall());
        modeList.add(new SpoofLandingNoFall());
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        for (Mode mode : modeList) {
            if (modes.is(mode.getName()))
                mode.onUpdate(event);
        }
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onPacket(PacketEvent event) {
        for (Mode mode : modeList) {
            if (modes.is(mode.getName()))
                mode.onPacket(event);
        }
    }

    @Override
    public void onDisabled() {
        for (Mode mode : modeList) {
            if (modes.is(mode.getName()))
                mode.onDisabled();
        }
    }
}

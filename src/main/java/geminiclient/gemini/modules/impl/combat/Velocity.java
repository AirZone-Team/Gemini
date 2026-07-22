package geminiclient.gemini.modules.impl.combat;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.PacketEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.Mode;
import geminiclient.gemini.modules.impl.combat.velocity.Heypixel;
import geminiclient.gemini.modules.impl.combat.velocity.Packet;
import geminiclient.gemini.values.impl.*;
import net.minecraft.network.protocol.game.*;

import java.util.ArrayList;
import java.util.List;

public class Velocity extends Module {
    List<Mode> modeList = new ArrayList<>();
    private final ListValue modes = new ListValue("Modes","Packet",new String[]{"Packet","Heypixel"});

    public IntValue getAttackAmount() {
        return attackAmount;
    }

    private final IntValue attackAmount = new IntValue("CPS",4,1,8,() -> modes.is("Heypixel"));
    public Velocity() {
        super("Velocity",ModuleEnum.Combat);
        addValue(modes);
        modeList.add(new Packet());
        modeList.add(new Heypixel());
    }

    @Override
    public void onEnabled() {
        for (Mode mode : modeList) {
            if (modes.is(mode.getName()))
                mode.onEnabled();
        }
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
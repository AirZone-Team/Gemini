package geminiclient.gemini.modules.impl;

import geminiclient.gemini.base.MinecraftInstance;
import geminiclient.gemini.event.events.impl.PacketEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;

public class Mode implements MinecraftInstance {
    public String getName() {
        return name;
    }

    private final String name;
    public Mode(String name) {
        this.name = name;
    }
    public void onUpdate(UpdateEvent event) {}
    public void onPacket(PacketEvent event) {}
    public void onDisabled() {}
    public void onEnabled() {}
}
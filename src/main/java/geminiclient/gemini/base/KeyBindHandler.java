package geminiclient.gemini.base;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.KeyEvent;
import geminiclient.gemini.modules.Module;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class KeyBindHandler {
    private long lastTriggerTime = 0;

    public KeyBindHandler() {
        Gemini.eventManager.register(this);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void keyEvent(KeyEvent event) {
        long currentTime = System.currentTimeMillis();
        final long MIN_INTERVAL_MS = 180;
        for (Module module : Gemini.moduleManager.getModules()) {
            if (module.key == event.key() && mc.screen == null && (currentTime - lastTriggerTime > MIN_INTERVAL_MS)) {
                lastTriggerTime = currentTime;
                module.toggle();
            }
        }
    }
}

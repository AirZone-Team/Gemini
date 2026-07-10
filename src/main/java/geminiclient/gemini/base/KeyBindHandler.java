package geminiclient.gemini.base;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.KeyInputEvent;
import geminiclient.gemini.modules.Module;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class KeyBindHandler {
    private long lastTriggerTime = 0;

    public KeyBindHandler() {
        Gemini.eventManager.register(this);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void keyEvent(KeyInputEvent event) {
        long currentTime = System.currentTimeMillis();
        final long MIN_INTERVAL_MS = 180;
        if (mc.screen != null || (currentTime - lastTriggerTime <= MIN_INTERVAL_MS)) {
            return;
        }
        boolean triggered = false;
        for (Module module : Gemini.moduleManager.getModules()) {
            if (module.key == event.key()) {
                module.toggle();
                triggered = true;
            }
        }
        if (triggered) {
            lastTriggerTime = currentTime;
            // 不发送全量快照 — module.toggle() → setEnabled() 已自动发送 module_update 增量
        }
    }
}

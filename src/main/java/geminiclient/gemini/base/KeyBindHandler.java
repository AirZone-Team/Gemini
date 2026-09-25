package geminiclient.gemini.base;

import com.mojang.blaze3d.platform.InputConstants;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.KeyInputEvent;
import geminiclient.gemini.event.events.impl.MouseButtonInputEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.utils.KeyUtils;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class KeyBindHandler {
    private long lastTriggerTime = 0;

    public KeyBindHandler() {
        Gemini.eventManager.register(this);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void keyEvent(KeyInputEvent event) {
        // 只在按下瞬间触发，忽略重复(REPEAT)与松开(RELEASE)，
        // 避免按住 Shift 等键时每 180ms 反复切换模块
        if (event.action() != InputConstants.PRESS) {
            return;
        }
        checkBind(event.key());
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void mouseEvent(MouseButtonInputEvent event) {
        if (event.action() != InputConstants.PRESS) {
            return;
        }
        checkBind(KeyUtils.mouseButtonToCode(KeyUtils.toModButton(event.button())));
    }

    /** 匹配并切换所有绑定了该键码（键盘码或鼠标负码）的模块。 */
    private void checkBind(int code) {
        if (code == 0) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        final long MIN_INTERVAL_MS = 180;
        if (mc.gui.screen() != null || (currentTime - lastTriggerTime <= MIN_INTERVAL_MS)) {
            return;
        }
        boolean triggered = false;
        for (Module module : Gemini.moduleManager.getModules()) {
            if (module.key == code) {
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

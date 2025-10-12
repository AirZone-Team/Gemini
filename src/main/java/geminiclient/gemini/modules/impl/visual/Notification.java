package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;

public class Notification extends Module {
    public Notification() {
        super("Notification", ModuleEnum.Visual);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onRender2D(Render2DEvent event) {
        Gemini.notificationManager.renderAll(event.guiGraphics());
    }
}

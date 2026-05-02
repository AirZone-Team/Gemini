package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.RenderUtils;

import java.awt.*;

public class Notification extends Module {
    public Notification() {
        super("Notification", ModuleEnum.Visual);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onRender2D(Render2DEvent event) {
        Gemini.notificationManager.renderAll(event.guiGraphics());
        RenderUtils.fillRoundedRect(event.guiGraphics(),0,0,event.guiGraphics().guiWidth(),event.guiGraphics().guiHeight(),20, Color.red.getRGB());
    }
}

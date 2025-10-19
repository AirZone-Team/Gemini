package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.visual.effectDisplay.RenderEffect;

public class EffectDisplay extends Module {
    public EffectDisplay() {
        super("EffectDisplay", ModuleEnum.Visual);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        RenderEffect.getInstance().render(event.guiGraphics());
    }
}

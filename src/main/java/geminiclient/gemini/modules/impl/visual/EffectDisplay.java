package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.visual.effectDisplay.RenderEffect;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class EffectDisplay extends Module {
    public EffectDisplay() {
        super("EffectDisplay", ModuleEnum.Visual);
        hudX = 6;
        hudY = 200;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        RenderEffect.getInstance().render(event.guiGraphics(), this);
    }

    @Override
    public void renderEditorPlaceholder(GuiGraphicsExtractor g) {
        if (!enabled) {
            RenderEffect.getInstance().renderPlaceholder(g, this);
        } else {
            RenderEffect.getInstance().renderOutline(g, this);
        }
    }
}

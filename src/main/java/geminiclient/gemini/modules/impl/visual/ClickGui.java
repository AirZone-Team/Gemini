package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.visual.clickgui.AbstractClickGuiScreen;
import geminiclient.gemini.modules.impl.visual.clickgui.ClickGuiScreen;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.MD3ClickGuiScreen;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;

public class ClickGui extends Module {

    private final ListValue mode = new ListValue("Mode", "Material3",
            new String[]{"Classic", "Material3"});

    /**
     * Background blur radius applied to everything rendered below the ClickGui
     * (world + HUD elements such as the ArrayList). 0 disables the blur.
     */
    private final IntValue blurStrength = new IntValue("Blur", 8, 0, 20);

    public ClickGui() {
        super("ClickGui", ModuleEnum.Visual);
        this.key = 79;
        addValue(mode);
        addValue(blurStrength);
    }

    public int getBlurStrength() {
        return blurStrength.getValue();
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        if (mc.screen == null) {
            mc.setScreen(mode.is("Material3") ? new MD3ClickGuiScreen() : new ClickGuiScreen());
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        Gemini.fileSystem.saveConfig();
        if (mc.screen instanceof AbstractClickGuiScreen) {
            mc.setScreen(null);
        }
    }
}

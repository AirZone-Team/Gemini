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

    private final IntValue md3SliderHeight = new IntValue("MD3 Slider Height", 44, 32, 72,
            () -> mode.is("Material3"));
    private final IntValue md3SliderTrack = new IntValue("MD3 Slider Track", 4, 2, 10,
            () -> mode.is("Material3"));
    private final IntValue md3SliderHandle = new IntValue("MD3 Slider Handle", 20, 12, 32,
            () -> mode.is("Material3"));
    private final IntValue md3ButtonScale = new IntValue("MD3 Button Scale", 100, 60, 160,
            () -> mode.is("Material3"));
    private final IntValue md3ButtonRowHeight = new IntValue("MD3 Button Row Height", 36, 28, 60,
            () -> mode.is("Material3"));

    public ClickGui() {
        super("ClickGui", ModuleEnum.Visual);
        this.key = 79;
        addValue(mode);
        addValue(blurStrength);
        addValue(md3SliderHeight);
        addValue(md3SliderTrack);
        addValue(md3SliderHandle);
        addValue(md3ButtonScale);
        addValue(md3ButtonRowHeight);
    }

    public int getBlurStrength() {
        return blurStrength.getValue();
    }

    private static ClickGui instance() {
        return Gemini.moduleManager == null ? null : Gemini.moduleManager.getModule(ClickGui.class);
    }

    public static int md3SliderHeight() {
        ClickGui gui = instance();
        return gui == null ? 44 : gui.md3SliderHeight.getValue();
    }

    public static int md3SliderTrackHeight() {
        ClickGui gui = instance();
        return gui == null ? 4 : gui.md3SliderTrack.getValue();
    }

    public static int md3SliderHandleHeight(boolean active) {
        ClickGui gui = instance();
        int base = gui == null ? 20 : gui.md3SliderHandle.getValue();
        return active ? Math.min(36, base + 2) : base;
    }

    public static int md3ButtonRowHeight() {
        ClickGui gui = instance();
        return gui == null ? 36 : gui.md3ButtonRowHeight.getValue();
    }

    public static int md3SwitchWidth() {
        ClickGui gui = instance();
        int scale = gui == null ? 100 : gui.md3ButtonScale.getValue();
        return Math.round(40 * scale / 100f);
    }

    public static int md3SwitchHeight() {
        ClickGui gui = instance();
        int scale = gui == null ? 100 : gui.md3ButtonScale.getValue();
        return Math.round(24 * scale / 100f);
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        if (mc.gui.screen() == null) {
            mc.gui.setScreen(mode.is("Material3") ? new MD3ClickGuiScreen() : new ClickGuiScreen());
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        Gemini.fileSystem.saveConfig();
        if (mc.gui.screen() instanceof AbstractClickGuiScreen) {
            mc.gui.setScreen(null);
        }
    }
}

package geminiclient.gemini.modules.impl.visual.clickgui.md3.component;

import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Anim;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Fonts;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3RenderUtils;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Theme;
import geminiclient.gemini.modules.impl.visual.ClickGui;
import geminiclient.gemini.values.impl.BoolValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * MD3 boolean control: label + trailing switch; the whole row toggles.
 */
public class Md3BoolValueComponent extends Md3ValueComponent {

    private final BoolValue boolValue;
    private final Md3Anim switchAnim = Md3Anim.shortAnim();

    public Md3BoolValueComponent(BoolValue value, int width, Md3Overlay.Host host) {
        super(value, width, 36, host);
        this.boolValue = value;
    }

    @Override
    public int getTotalHeight() {
        return ClickGui.md3ButtonRowHeight();
    }

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        height = getTotalHeight();
        switchAnim.setTarget(boolValue.enabled ? 1.0f : 0.0f);

        // Hover state layer (consistent across all MD3 rows)
        drawHoverState(gui, mouseX, mouseY);

        // Row label
        var font = Md3Fonts.body();
        float lh = Md3Fonts.lineHeight(font);
        Md3Fonts.drawText(gui, font, boolValue.getName(), x, y + (height - lh) / 2f,
                Md3Theme.ON_SURFACE);

        // Trailing switch
        int swX = x + width - Md3RenderUtils.switchWidth();
        int swY = y + (height - Md3RenderUtils.switchHeight()) / 2;
        boolean hovered = isHovered(mouseX, mouseY);
        Md3RenderUtils.drawSwitch(gui, swX, swY, switchAnim.getValue(), hovered, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            boolValue.setEnabled(!boolValue.enabled);
            return true;
        }
        return false;
    }
}

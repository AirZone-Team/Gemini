package geminiclient.gemini.modules.impl.visual.clickgui.md3.component;

import geminiclient.gemini.modules.impl.visual.ClickGui;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Anim;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Fonts;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3RenderUtils;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Theme;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.CheckboxValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * MD3 checkbox group: header row with an expand chevron; expanding reveals
 * one nested switch row per child BoolValue.
 */
public class Md3CheckboxValueComponent extends Md3ValueComponent {


    private final CheckboxValue checkboxValue;
    private final List<Md3BoolValueComponent> children = new ArrayList<>();
    private final Md3Anim expandAnim = Md3Anim.mediumAnim();
    private boolean expanded = false;

    public Md3CheckboxValueComponent(CheckboxValue value, int width, Md3Overlay.Host host) {
        super(value, width, 36, host);
        this.checkboxValue = value;
        for (BoolValue child : value.boolValues) {
            children.add(new Md3BoolValueComponent(child, width, host));
        }
    }

    private float expandProgress() {
        return expandAnim.getValue();
    }

    private int childHeight() {
        return ClickGui.md3ButtonRowHeight();
    }

    @Override
    public int getExtraHeight() {
        return (int) (children.size() * childHeight() * expandProgress());
    }

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        expandAnim.setTarget(expanded ? 1.0f : 0.0f);

        // Hover state layer on the header row
        drawHoverState(gui, mouseX, mouseY);

        var font = Md3Fonts.body();
        float lh = Md3Fonts.lineHeight(font);
        Md3Fonts.drawText(gui, font, checkboxValue.getName(), x, y + (height - lh) / 2f,
                Md3Theme.ON_SURFACE);

        // Chevron rotates with expansion and tints primary while hovered
        Md3RenderUtils.drawChevron(gui, x + width - 7, y + height / 2, 6, !expanded,
                isHovered(mouseX, mouseY) ? Md3Theme.PRIMARY : Md3Theme.ON_SURFACE_VARIANT);

        // Children (scissored during animation)
        int childHeight = childHeight();
        int contentH = children.size() * childHeight;
        int visibleH = (int) (contentH * expandProgress());
        if (visibleH > 0) {
            gui.enableScissor(x, y + height, x + width, y + height + visibleH);
            int childY = y + height;
            for (Md3BoolValueComponent child : children) {
                child.x = x + 12;
                child.y = childY;
                child.width = width - 12;
                child.height = childHeight;
                child.render(gui, mouseX, mouseY, partialTicks);
                childY += childHeight;
            }
            gui.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            expanded = !expanded;
            return true;
        }
        if (expandProgress() > 0.01f) {
            int childHeight = childHeight();
            int childY = y + height;
            for (Md3BoolValueComponent child : children) {
                child.x = x + 12;
                child.y = childY;
                child.width = width - 12;
                child.height = childHeight;
                if (child.mouseClicked(mouseX, mouseY, button)) return true;
                childY += childHeight;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (Md3BoolValueComponent child : children) {
            if (child.mouseReleased(mouseX, mouseY, button)) return true;
        }
        return false;
    }
}

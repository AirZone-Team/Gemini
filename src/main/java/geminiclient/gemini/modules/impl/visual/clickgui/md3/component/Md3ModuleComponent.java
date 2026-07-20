package geminiclient.gemini.modules.impl.visual.clickgui.md3.component;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Anim;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Fonts;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3ModuleDescriptions;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3RenderUtils;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Theme;
import geminiclient.gemini.values.ValueParent;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Filled Material 3 card for a module and its settings.
 */
public class Md3ModuleComponent {

    public static final int ROW_HEIGHT = 58;
    private static final int PAD_H = 12;
    private static final int VALUE_PADDING = 12;
    private static final int VALUE_SPACING = 4;
    private static final int EXPAND_GAP = 8;

    public final Module module;
    public int x, y, width;

    private final List<Md3ValueComponent> valueComponents = new ArrayList<>();
    private boolean expanded;

    private final Md3Anim expandAnim = Md3Anim.mediumAnim();
    private final Md3Anim switchAnim = Md3Anim.shortAnim();
    private final Md3Anim favAnim = Md3Anim.shortAnim();
    private final Md3Anim hoverAnim = Md3Anim.shortAnim();

    private boolean switchPressed;
    private final List<Md3RenderUtils.Ripple> ripples = new ArrayList<>();

    public Md3ModuleComponent(Module module, Md3Overlay.Host overlayHost) {
        this.module = module;
        for (ValueParent value : module.getValues()) {
            Md3ValueComponent component = Md3ValueComponentFactory.create(value, width, overlayHost);
            if (component != null) {
                valueComponents.add(component);
            }
        }
        expandAnim.snap(0f);
        switchAnim.snap(module.enabled ? 1f : 0f);
        favAnim.snap(module.favorite ? 1f : 0f);
    }

    public void advanceAnimation(float partialTicks) {
        expandAnim.setTarget(expanded ? 1f : 0f);
        switchAnim.setTarget(module.enabled ? 1f : 0f);
        favAnim.setTarget(module.favorite ? 1f : 0f);
    }

    private float expandProgress() {
        return expandAnim.getValue();
    }

    public int getTotalHeight() {
        int total = ROW_HEIGHT;
        float progress = expandProgress();
        if (progress > 0.005f) {
            total += (int) ((computeContentHeight() + EXPAND_GAP) * progress);
        }
        return total;
    }

    private int computeContentHeight() {
        List<Md3ValueComponent> visible = getVisibleComponents();
        if (visible.isEmpty()) return 0;
        int height = VALUE_PADDING * 2;
        for (Md3ValueComponent component : visible) {
            height += component.getTotalHeight() + VALUE_SPACING;
        }
        return height - VALUE_SPACING;
    }

    private List<Md3ValueComponent> getVisibleComponents() {
        return valueComponents.stream()
                .filter(Md3ValueComponent::isVisible)
                .collect(Collectors.toList());
    }

    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        int cardHeight = getTotalHeight();
        float enabledT = switchAnim.getValue();
        int cardColor = Md3Theme.lerpColor(Md3Theme.SURFACE_CONTAINER_LOWEST,
                Md3Theme.PRIMARY_CONTAINER, enabledT * 0.30f);

        CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, width, cardHeight,
                Md3Theme.R_ROW, cardColor);

        hoverAnim.setTarget(isOverRow(mouseX, mouseY) ? 1f : 0f);
        float hoverT = hoverAnim.getValue();
        if (hoverT > 0.01f) {
            CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, width, ROW_HEIGHT,
                    Md3Theme.R_ROW,
                    Md3Theme.modulateAlpha(Md3Theme.hoverState(Md3Theme.ON_SURFACE), hoverT));
        }

        ripples.removeIf(Md3RenderUtils.Ripple::isFinished);
        if (!ripples.isEmpty()) {
            gui.enableScissor(x, y, x + width, y + ROW_HEIGHT);
            for (Md3RenderUtils.Ripple ripple : ripples) {
                ripple.render(gui, width * 0.5f, Md3Theme.PRIMARY);
            }
            gui.disableScissor();
        }

        renderLeadingIcon(gui, enabledT);
        renderText(gui);
        renderTrailingActions(gui, mouseX, mouseY);
        renderExpandedSettings(gui, mouseX, mouseY, partialTicks);
    }

    private void renderLeadingIcon(GuiGraphicsExtractor gui, float enabledT) {
        int iconSize = 32;
        int iconX = x + PAD_H;
        int iconY = y + (ROW_HEIGHT - iconSize) / 2;
        int container = Md3Theme.lerpColor(Md3Theme.SURFACE_CONTAINER_HIGH,
                Md3Theme.SECONDARY_CONTAINER, enabledT);
        int content = Md3Theme.lerpColor(Md3Theme.ON_SURFACE_VARIANT,
                Md3Theme.ON_SECONDARY_CONTAINER, enabledT);
        CustomRoundedRectRenderer.drawRoundedRect(gui, iconX, iconY, iconSize, iconSize,
                Md3Theme.R_MEDIUM, container);
        Md3RenderUtils.drawCategoryIcon(gui, module.getModuleEnum(),
                iconX + iconSize / 2, iconY + iconSize / 2, 17, content);
    }

    private void renderText(GuiGraphicsExtractor gui) {
        int textX = x + PAD_H + 42;
        var titleFont = Md3Fonts.title();
        var labelFont = Md3Fonts.label();

        Md3Fonts.drawText(gui, titleFont, module.getName(), textX, y + 12,
                Md3Theme.ON_SURFACE);

        int settingCount = getVisibleComponents().size();
        String supporting = formatCategory() + " · " + settingCount
                + (settingCount == 1 ? " setting" : " settings");
        String description = Md3ModuleDescriptions.get(module.getName());
        if (!description.isEmpty()) {
            supporting += " — " + description;
        }
        Md3Fonts.drawText(gui, labelFont, supporting, textX, y + 34,
                Md3Theme.ON_SURFACE_VARIANT);
    }

    private void renderTrailingActions(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        if (!valueComponents.isEmpty()) {
            Md3RenderUtils.drawChevron(gui, heartCenterX() - 25, y + ROW_HEIGHT / 2,
                    7, !expanded, Md3Theme.ON_SURFACE_VARIANT);
        }

        float favoriteT = favAnim.getValue();
        int idle = isOverHeart(mouseX, mouseY)
                ? Md3Theme.ON_SURFACE : Md3Theme.ON_SURFACE_VARIANT;
        Md3RenderUtils.drawFavoriteIcon(gui, heartCenterX(), y + ROW_HEIGHT / 2,
                20, favoriteT, idle, Md3Theme.PRIMARY);

        int switchX = switchX();
        int switchY = y + (ROW_HEIGHT - Md3RenderUtils.switchHeight()) / 2;
        Md3RenderUtils.drawSwitch(gui, switchX, switchY, switchAnim.getValue(),
                isOverSwitch(mouseX, mouseY), switchPressed);
    }

    private void renderExpandedSettings(GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                        float partialTicks) {
        float progress = expandProgress();
        if (progress <= 0.005f) return;

        int contentHeight = computeContentHeight();
        int visibleHeight = (int) ((contentHeight + EXPAND_GAP) * progress);
        int areaY = y + ROW_HEIGHT + EXPAND_GAP / 2;

        gui.enableScissor(x, y + ROW_HEIGHT, x + width,
                y + ROW_HEIGHT + visibleHeight);
        gui.fill(x + 16, y + ROW_HEIGHT, x + width - 16, y + ROW_HEIGHT + 1,
                Md3Theme.withAlpha(Md3Theme.OUTLINE_VARIANT, 0.70f));
        CustomRoundedRectRenderer.drawRoundedRect(gui, x + 8, areaY, width - 16,
                contentHeight, Md3Theme.R_MEDIUM, Md3Theme.SURFACE_CONTAINER);

        int currentY = areaY + VALUE_PADDING;
        for (Md3ValueComponent component : getVisibleComponents()) {
            layoutComponent(component, currentY);
            component.render(gui, mouseX, mouseY, partialTicks);
            currentY += component.getTotalHeight() + VALUE_SPACING;
        }
        gui.disableScissor();
    }

    private void layoutComponent(Md3ValueComponent component, int componentY) {
        component.x = x + 8 + VALUE_PADDING;
        component.y = componentY;
        component.width = width - 16 - VALUE_PADDING * 2;
    }

    private String formatCategory() {
        String name = module.getModuleEnum().name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    private int switchX() {
        return x + width - PAD_H - Md3RenderUtils.switchWidth();
    }

    private int heartCenterX() {
        return switchX() - 25;
    }

    private boolean isOverRow(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + ROW_HEIGHT;
    }

    private boolean isOverSwitch(double mouseX, double mouseY) {
        int switchX = switchX();
        int switchY = y + (ROW_HEIGHT - Md3RenderUtils.switchHeight()) / 2;
        return mouseX >= switchX - 6
                && mouseX <= switchX + Md3RenderUtils.switchWidth() + 6
                && mouseY >= switchY - 6
                && mouseY <= switchY + Md3RenderUtils.switchHeight() + 6;
    }

    private boolean isOverHeart(double mouseX, double mouseY) {
        int centerX = heartCenterX();
        int centerY = y + ROW_HEIGHT / 2;
        return mouseX >= centerX - 16 && mouseX <= centerX + 16
                && mouseY >= centerY - 16 && mouseY <= centerY + 16;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverSwitch(mouseX, mouseY)) {
            switchPressed = true;
            module.toggle();
            return true;
        }
        if (button == 0 && isOverHeart(mouseX, mouseY)) {
            module.favorite = !module.favorite;
            return true;
        }
        if (button == 0 && isOverRow(mouseX, mouseY)) {
            if (!valueComponents.isEmpty()) {
                expanded = !expanded;
                ripples.add(new Md3RenderUtils.Ripple((float) mouseX, (float) mouseY));
            }
            return true;
        }

        if (expandProgress() > 0.01f) {
            int areaY = y + ROW_HEIGHT + EXPAND_GAP / 2;
            int currentY = areaY + VALUE_PADDING;
            for (Md3ValueComponent component : getVisibleComponents()) {
                layoutComponent(component, currentY);
                if (component.mouseClicked(mouseX, mouseY, button)) return true;
                currentY += component.getTotalHeight() + VALUE_SPACING;
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            switchPressed = false;
        }
        for (Md3ValueComponent component : valueComponents) {
            if (component.mouseReleased(mouseX, mouseY, button)) return true;
        }
        return false;
    }
}

package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.modules.impl.visual.clickgui.ClassicTheme;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.utils.animation.SpringAnimation;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;
import java.util.List;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class ListValueComponent extends ValueComponent {

    private boolean isExpanded = false;
    public static final int MODE_HEIGHT = 12;

    private static final int LIST_BG = new Color(26, 26, 36, 235).getRGB();
    private static final int ITEM_HOVER = new Color(255, 255, 255, 14).getRGB();
    private static final int ITEM_SELECTED_BG = new Color(139, 92, 246, 30).getRGB();

    private final SpringAnimation expandSpring = SpringAnimation.snappy();
    private final SpringAnimation hoverSpring = SpringAnimation.smooth();

    public ListValueComponent(ListValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16);
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    /** Padding inside the dropdown container (top + bottom). */
    private static final int LIST_PAD = 4;

    /** Animated 0..1 expand progress; drives both height and reveal. */
    private float expandProgress() {
        return SpringAnimation.easeOutCubic(Math.max(0f, Math.min(1f, expandSpring.getValue())));
    }

    /** Full dropdown block height (items + container padding). */
    private int fullListHeight() {
        ListValue listValue = (ListValue) this.value;
        return listValue.list.size() * MODE_HEIGHT + LIST_PAD;
    }

    public int getExpandedListHeight() {
        return (int) (fullListHeight() * expandProgress());
    }

    @Override
    public int getExtraHeight() {
        return getExpandedListHeight();
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        ListValue listValue = (ListValue) this.value;

        expandSpring.setTarget(isExpanded ? 1.0f : 0.0f);
        expandSpring.update(partialTicks);

        boolean hovered = isHovered(mouseX, mouseY);
        hoverSpring.setTarget(hovered ? 1.0f : 0.0f);
        hoverSpring.update(partialTicks);
        float hoverT = hoverSpring.getValue();

        ClassicTheme.drawRow(guiGraphics, x, y, width, height, hoverT);

        // Label (left) + current mode (right) + chevron
        guiGraphics.text(mc.font, this.value.getName(), x + 7, y + 4, ClassicTheme.TEXT, true);

        String current = listValue.get();
        int chevronX = x + width - 12;
        guiGraphics.text(mc.font, current, chevronX - 4 - mc.font.width(current), y + 4,
                ClassicTheme.TEXT_DIM, true);

        String chevron = isExpanded ? "▼" : "▶";
        int chevronColor = (isExpanded || hoverT > 0.5f) ? ClassicTheme.ACCENT : ClassicTheme.TEXT_DIM;
        guiGraphics.text(mc.font, chevron, chevronX, y + 4, chevronColor, true);

        // Dropdown list, revealed with the expand animation
        float progress = expandProgress();
        if (progress > 0.01f) {
            drawModeList(guiGraphics, mouseX, mouseY, progress);
        }
    }

    private void drawModeList(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float progress) {
        ListValue listValue = (ListValue) this.value;
        List<String> modes = listValue.list;

        int listX = x;
        int listY = y + height;
        int listWidth = width;
        int fullHeight = fullListHeight();
        int visibleHeight = (int) (fullHeight * progress);

        guiGraphics.enableScissor(listX, listY, listX + listWidth, listY + visibleHeight);

        CustomRoundedRectRenderer.drawRoundedRect(guiGraphics, listX, listY + 1,
                listWidth, fullHeight - 1, ClassicTheme.R_CONTROL, LIST_BG);
        CustomRoundedRectRenderer.drawRoundedOutline(guiGraphics, listX, listY + 1,
                listWidth, fullHeight - 1, ClassicTheme.R_CONTROL, ClassicTheme.BORDER, 1);

        int currentY = listY + 2;
        for (String mode : modes) {
            boolean isModeHovered = mouseX >= listX && mouseX <= listX + listWidth &&
                    mouseY >= currentY && mouseY <= currentY + MODE_HEIGHT;
            boolean isSelected = listValue.is(mode);

            if (isSelected) {
                CustomRoundedRectRenderer.drawRoundedRect(guiGraphics, listX + 2, currentY,
                        listWidth - 4, MODE_HEIGHT, 3, ITEM_SELECTED_BG);
                // Accent bar on the left edge of the selected item
                CustomRoundedRectRenderer.drawRoundedRect(guiGraphics, listX + 3, currentY + 2,
                        2, MODE_HEIGHT - 4, 1, ClassicTheme.ACCENT);
            } else if (isModeHovered) {
                CustomRoundedRectRenderer.drawRoundedRect(guiGraphics, listX + 2, currentY,
                        listWidth - 4, MODE_HEIGHT, 3, ITEM_HOVER);
            }

            int modeTextColor = isSelected ? ClassicTheme.TEXT : ClassicTheme.TEXT_DIM;
            guiGraphics.text(mc.font, mode, listX + 8, currentY + 2, modeTextColor, true);

            currentY += MODE_HEIGHT;
        }

        guiGraphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ListValue listValue = (ListValue) this.value;

        if (isVisible()) {
            if (isExpanded) {
                int listX = x;
                int listY = y + height;
                int listWidth = width;
                List<String> modes = listValue.list;

                int currentY = listY + 2;
                for (int i = 0; i < modes.size(); i++) {
                    if (mouseX >= listX && mouseX <= listX + listWidth &&
                            mouseY >= currentY && mouseY <= currentY + MODE_HEIGHT) {

                        listValue.setMode(modes.get(i));
                        return true;
                    }
                    currentY += MODE_HEIGHT;
                }
            }

            if (isHovered(mouseX, mouseY) && (button == 0 || button == 1)) {
                isExpanded = !isExpanded;
                return true;
            }
        }
        return false;
    }
}

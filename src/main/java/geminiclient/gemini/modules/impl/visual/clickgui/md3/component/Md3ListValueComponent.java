package geminiclient.gemini.modules.impl.visual.clickgui.md3.component;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Anim;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Fonts;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3RenderUtils;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Theme;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * MD3 dropdown: row shows label + current value + chevron; clicking opens an
 * exposed MD3 menu as a screen overlay (elevation 2, outside the scissor).
 */
public class Md3ListValueComponent extends Md3ValueComponent {

    private final ListValue listValue;

    public Md3ListValueComponent(ListValue value, int width, Md3Overlay.Host host) {
        super(value, width, 36, host);
        this.listValue = value;
    }

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        var bodyFont = Md3Fonts.body();
        float lh = Md3Fonts.lineHeight(bodyFont);
        float textY = y + (height - lh) / 2f;

        boolean hovered = isHovered(mouseX, mouseY);
        if (hovered) {
            drawHoverState(gui, mouseX, mouseY);
        }

        Md3Fonts.drawText(gui, bodyFont, listValue.getName(), x, textY, Md3Theme.ON_SURFACE);

        // Current value + chevron on the right (chevron tints primary on hover)
        String current = listValue.get();
        var labelFont = Md3Fonts.label();
        float vw = Md3Fonts.width(labelFont, current);
        float right = x + width - 14;
        Md3Fonts.drawText(gui, labelFont, current, right - vw, y + (height - Md3Fonts.lineHeight(labelFont)) / 2f,
                Md3Theme.ON_SURFACE_VARIANT);
        Md3RenderUtils.drawChevron(gui, (int) (x + width - 7), y + height / 2, 6, false,
                hovered ? Md3Theme.PRIMARY : Md3Theme.ON_SURFACE_VARIANT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            overlayHost.openOverlay(new Md3Menu(x + width, y + height / 2));
            return true;
        }
        return false;
    }

    // ── Exposed dropdown menu overlay ───────────────────

    private final class Md3Menu implements Md3Overlay {

        private static final int ITEM_HEIGHT = 40;
        private static final int MENU_WIDTH = 168;

        private final int menuX, menuY, menuH;
        private final List<String> modes = listValue.list;
        private final long openedAtMs = System.currentTimeMillis();

        Md3Menu(int anchorRight, int anchorCenterY) {
            menuH = modes.size() * ITEM_HEIGHT + 8;
            this.menuX = Math.max(8, Math.min(anchorRight - MENU_WIDTH,
                    overlayHost.rightEdge() - MENU_WIDTH - 8));
            this.menuY = Math.max(8, Math.min(anchorCenterY - ITEM_HEIGHT / 2,
                    overlayHost.bottomEdge() - menuH - 8));
        }

        @Override
        public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
            // Entrance: settle into place with a short ease-out rise
            float openT = Math.min(1.0f, (System.currentTimeMillis() - openedAtMs) / 150f);
            int drawY = menuY - (int) ((1.0f - Md3Anim.easeOutCubic(openT)) * 4);

            int r = Md3Theme.R_CONTROL;
            Md3Theme.elevation2(gui, menuX, drawY, MENU_WIDTH, menuH, r);
            CustomRoundedRectRenderer.drawRoundedRect(gui, menuX, drawY, MENU_WIDTH, menuH, r,
                    Md3Theme.SURFACE_CONTAINER);

            var font = Md3Fonts.body();
            float lh = Md3Fonts.lineHeight(font);

            for (int i = 0; i < modes.size(); i++) {
                String mode = modes.get(i);
                int itemY = drawY + 4 + i * ITEM_HEIGHT;
                boolean hovered = mouseX >= menuX && mouseX <= menuX + MENU_WIDTH
                        && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT;
                boolean selected = listValue.is(mode);

                if (selected) {
                    CustomRoundedRectRenderer.drawRoundedRect(gui, menuX + 4, itemY + 4,
                            MENU_WIDTH - 8, ITEM_HEIGHT - 8, Md3Theme.R_CONTROL,
                            Md3Theme.PRIMARY_CONTAINER);
                } else if (hovered) {
                    CustomRoundedRectRenderer.drawRoundedRect(gui, menuX + 4, itemY + 4,
                            MENU_WIDTH - 8, ITEM_HEIGHT - 8, Md3Theme.R_CONTROL,
                            Md3Theme.hoverState(Md3Theme.ON_SURFACE));
                }

                int textColor = selected ? Md3Theme.ON_PRIMARY_CONTAINER : Md3Theme.ON_SURFACE;
                float textX = menuX + 16;
                if (selected) {
                    int checkSize = 16;
                    Md3RenderUtils.drawCheck(gui, menuX + 12,
                            itemY + (ITEM_HEIGHT - checkSize) / 2, checkSize,
                            Md3Theme.ON_PRIMARY_CONTAINER);
                    textX = menuX + 36;
                }
                Md3Fonts.drawText(gui, font, mode, textX, itemY + (ITEM_HEIGHT - lh) / 2f, textColor);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || !contains(mouseX, mouseY)) return false;
            for (int i = 0; i < modes.size(); i++) {
                int itemY = menuY + 4 + i * ITEM_HEIGHT;
                if (mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT) {
                    listValue.setMode(modes.get(i));
                    overlayHost.closeOverlay();
                    return true;
                }
            }
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return false;
        }

        @Override
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= menuX && mouseX <= menuX + MENU_WIDTH
                    && mouseY >= menuY && mouseY <= menuY + menuH;
        }
    }
}

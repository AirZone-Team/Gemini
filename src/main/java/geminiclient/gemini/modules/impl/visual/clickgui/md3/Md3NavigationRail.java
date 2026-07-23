package geminiclient.gemini.modules.impl.visual.clickgui.md3;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Persistent Material 3 navigation rail.
 *
 * <p>Material navigation rails are intentionally stable rather than revealed
 * on hover: destinations keep a compact active indicator and an always-visible
 * label, so category changes never move the content under the pointer.</p>
 */
public class Md3NavigationRail {

    public static final int WIDTH = 76;

    private static final int HEADER_HEIGHT = 32;
    private static final int BLOCK_HEIGHT = 44;
    private static final int BLOCK_SPACING = 2;
    private static final int PILL_W = 48;
    private static final int PILL_H = 26;
    private static final int ICON_SIZE = 16;

    public int x, y, height;

    private int selectedIndex = 0;
    private final Md3Anim selectionAnim = Md3Anim.mediumAnim();
    private final Destination[] destinations;

    public Md3NavigationRail(int x, int y) {
        this.x = x;
        this.y = y;

        ModuleEnum[] categories = ModuleEnum.values();
        destinations = new Destination[categories.length + 1];
        for (int i = 0; i < categories.length; i++) {
            destinations[i] = new CategoryDestination(categories[i]);
        }
        destinations[categories.length] = new FavoritesDestination();
        selectionAnim.snap(0f);
    }

    public int getCollapsedX() {
        return x;
    }

    public int getCurrentWidth() {
        return WIDTH;
    }

    public boolean isExpanded() {
        return true;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public boolean isFavoritesSelected() {
        return destinations[selectedIndex] instanceof FavoritesDestination;
    }

    public ModuleEnum getSelectedCategory() {
        Destination destination = destinations[selectedIndex];
        return destination instanceof CategoryDestination category ? category.category : null;
    }

    private int blockTop(int index) {
        return y + HEADER_HEIGHT + Math.round(index * blockStep());
    }

    private float pillCenterY(float index) {
        return y + HEADER_HEIGHT + 2 + PILL_H / 2f
                + index * blockStep();
    }

    private float blockStep() {
        if (height <= 0) {
            return BLOCK_HEIGHT + BLOCK_SPACING;
        }
        return Math.min(BLOCK_HEIGHT + BLOCK_SPACING,
                Math.max(40f, (height - HEADER_HEIGHT) / (float) destinations.length));
    }

    /**
     * Retained for the screen's shared component update path. A persistent
     * navigation rail does not change width on hover.
     */
    public void updateHover(double mouseX, double mouseY) {
    }

    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        int railHeight = getTotalHeight();

        int railRadius = Md3Theme.R_EXTRA_LARGE;
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, WIDTH, railHeight, railRadius,
                Md3Theme.SURFACE_CONTAINER_LOW);
        // Keep the rail square where it joins the app bar and content surface.
        gui.fill(x, y, x + WIDTH, y + railRadius, Md3Theme.SURFACE_CONTAINER_LOW);
        gui.fill(x + railRadius, y + railHeight - railRadius,
                x + WIDTH, y + railHeight, Md3Theme.SURFACE_CONTAINER_LOW);
        gui.fill(x + WIDTH - 1, y + 8, x + WIDTH, y + railHeight - 8,
                Md3Theme.withAlpha(Md3Theme.OUTLINE_VARIANT, 0.55f));

        var labelFont = Md3Fonts.label();
        String browse = "BROWSE";
        float browseWidth = Md3Fonts.width(labelFont, browse);
        Md3Fonts.drawText(gui, labelFont, browse, x + (WIDTH - browseWidth) / 2f,
                y + 9, Md3Theme.ON_SURFACE_VARIANT);

        selectionAnim.setTarget(selectedIndex);
        float activeCenterY = pillCenterY(selectionAnim.getValue());
        int pillX = x + (WIDTH - PILL_W) / 2;
        CustomRoundedRectRenderer.drawRoundedRect(gui, pillX,
                Math.round(activeCenterY - PILL_H / 2f), PILL_W, PILL_H,
                PILL_H / 2, Md3Theme.SECONDARY_CONTAINER);

        for (int i = 0; i < destinations.length; i++) {
            Destination destination = destinations[i];
            boolean selected = i == selectedIndex;
            boolean hovered = isOverBlock(mouseX, mouseY, i);
            int centerY = Math.round(pillCenterY(i));

            if (hovered && !selected) {
                CustomRoundedRectRenderer.drawRoundedRect(gui, pillX,
                        centerY - PILL_H / 2, PILL_W, PILL_H, PILL_H / 2,
                        Md3Theme.hoverState(Md3Theme.ON_SURFACE));
            }

            int contentColor = selected
                    ? Md3Theme.ON_SECONDARY_CONTAINER
                    : Md3Theme.ON_SURFACE_VARIANT;

            if (destination instanceof CategoryDestination category) {
                Md3RenderUtils.drawCategoryIcon(gui, category.category, x + WIDTH / 2,
                        centerY, ICON_SIZE, contentColor);
            } else {
                Md3RenderUtils.drawHeartPlusIcon(gui, x + WIDTH / 2,
                        centerY, ICON_SIZE, contentColor);
            }

            String label = destination.getLabel();
            float labelWidth = Md3Fonts.width(labelFont, label);
            Md3Fonts.drawText(gui, labelFont, label, x + (WIDTH - labelWidth) / 2f,
                    centerY + PILL_H / 2f + 2, contentColor);
        }
    }

    private boolean isOverBlock(double mouseX, double mouseY, int index) {
        int top = blockTop(index);
        return mouseX >= x && mouseX <= x + WIDTH
                && mouseY >= top && mouseY <= top + blockStep();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        for (int i = 0; i < destinations.length; i++) {
            if (isOverBlock(mouseX, mouseY, i)) {
                selectedIndex = i;
                return true;
            }
        }
        return false;
    }

    public int getTotalHeight() {
        int contentHeight = HEADER_HEIGHT
                + destinations.length * (BLOCK_HEIGHT + BLOCK_SPACING);
        return height > 0 ? height : contentHeight;
    }

    private interface Destination {
        String getLabel();
    }

    private record CategoryDestination(ModuleEnum category) implements Destination {
        @Override
        public String getLabel() {
            String name = category.name();
            return name.charAt(0) + name.substring(1).toLowerCase();
        }
    }

    private static final class FavoritesDestination implements Destination {
        @Override
        public String getLabel() {
            return "Favorites";
        }
    }
}

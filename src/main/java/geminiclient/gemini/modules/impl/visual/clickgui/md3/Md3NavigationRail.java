package geminiclient.gemini.modules.impl.visual.clickgui.md3;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Collapsible MD3 NavigationRail.
 *
 * <p>Default state: a narrow icon-only strip (48dp) showing category icons and
 * the hamburger menu. When the mouse hovers over the strip, it animates open
 * to 80dp, raises above the content with a shadow, and reveals text labels
 * beneath each icon.</p>
 */
public class Md3NavigationRail {

    private static final int COLLAPSED_W = 48;
    private static final int EXPANDED_W = 88;

    private static final int PILL_W = 40;
    private static final int PILL_H = 28;
    private static final int ICON_SIZE = 20;
    private static final int ICON_LABEL_GAP = 2;
    private static final int BLOCK_HEIGHT = 50;     // icon pill + label
    private static final int BLOCK_SPACING = 2;
    private static final int HEADER_HEIGHT = 36;

    public int x, y;
    private int collapsedY;

    private int selectedIndex = 0;
    private final Md3Anim widthAnim = Md3Anim.mediumAnim();

    private final Destination[] destinations;

    public Md3NavigationRail(int x, int y) {
        this.x = x;
        this.y = y;

        ModuleEnum[] cats = ModuleEnum.values();
        destinations = new Destination[cats.length + 1];
        for (int i = 0; i < cats.length; i++) {
            destinations[i] = new CategoryDestination(cats[i]);
        }
        destinations[cats.length] = new FavoritesDestination();
        widthAnim.snap(0);
    }

    public int getCollapsedX() {
        return x;
    }

    public int getCurrentWidth() {
        float p = widthAnim.getValue();
        return (int) (COLLAPSED_W + (EXPANDED_W - COLLAPSED_W) * p);
    }

    public boolean isExpanded() {
        return widthAnim.getValue() > 0.1f;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public boolean isFavoritesSelected() {
        return destinations[selectedIndex] instanceof FavoritesDestination;
    }

    public ModuleEnum getSelectedCategory() {
        Destination d = destinations[selectedIndex];
        return d instanceof CategoryDestination cd ? cd.category : null;
    }

    private int blockTop(int index) {
        return y + HEADER_HEIGHT + index * (BLOCK_HEIGHT + BLOCK_SPACING);
    }

    private float pillCenterY(int index) {
        return blockTop(index) + 2 + PILL_H / 2f;
    }

    public void updateHover(double mouseX, double mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + getCurrentWidth()
                && mouseY >= y && mouseY <= y + getTotalHeight();
        widthAnim.setTarget(hovered ? 1.0f : 0.0f);
    }

    /**
     * Render the rail. Should be drawn AFTER the main content so the expanded
     * panel visually sits on top.
     */
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        int currentW = getCurrentWidth();
        boolean expanded = isExpanded();
        float p = widthAnim.getValue();

        // Elevated surface + shadow when expanded
        if (expanded) {
            Md3Theme.elevation2(gui, x, y, currentW, getTotalHeight(), 0);
            CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, currentW, getTotalHeight(),
                    0, Md3Theme.SURFACE_CONTAINER);
        } else {
            // Collapsed: subtle tonal strip flush with the window background
            CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, currentW, getTotalHeight(),
                    0, Md3Theme.SURFACE);
        }

        // Hamburger
        Md3RenderUtils.drawHamburger(gui, x + COLLAPSED_W / 2, y + HEADER_HEIGHT / 2, 16,
                Md3Theme.ON_SURFACE_VARIANT);

        // Active pill slides with expansion
        float pos = selectedIndex;
        float clamped = Math.max(0, Math.min(destinations.length - 1, pos));
        int lower = (int) Math.floor(clamped);
        int upper = Math.min(destinations.length - 1, lower + 1);
        float t = clamped - lower;
        float activeY = pillCenterY(lower) + (pillCenterY(upper) - pillCenterY(lower)) * t;

        int iconCenterX = x + COLLAPSED_W / 2;
        int pillX = x + (COLLAPSED_W - PILL_W) / 2;
        CustomRoundedRectRenderer.drawRoundedRect(gui, pillX,
                (int) (activeY - PILL_H / 2f), PILL_W, PILL_H, PILL_H / 2,
                Md3Theme.SECONDARY_CONTAINER);

        for (int i = 0; i < destinations.length; i++) {
            Destination dest = destinations[i];
            boolean selected = i == selectedIndex;
            boolean blockHovered = isOverBlock(mouseX, mouseY, i);
            float centerY = pillCenterY(i);

            if (blockHovered && !selected) {
                CustomRoundedRectRenderer.drawRoundedRect(gui, pillX,
                        (int) (centerY - PILL_H / 2f), PILL_W, PILL_H, PILL_H / 2,
                        Md3Theme.hoverState(Md3Theme.ON_SURFACE));
            }

            int contentColor = selected ? Md3Theme.ON_SECONDARY_CONTAINER : Md3Theme.ON_SURFACE_VARIANT;

            if (dest instanceof CategoryDestination cd) {
                Md3RenderUtils.drawCategoryIcon(gui, cd.category, iconCenterX, (int) centerY,
                        ICON_SIZE, contentColor);
            } else {
                // Favorites heart icon
                int cell = Math.max(1, ICON_SIZE / 10);
                int w = 11 * cell, h = 9 * cell;
                Md3RenderUtils.drawHeart(gui, iconCenterX - w / 2, (int) centerY - h / 2,
                        cell, true, contentColor, contentColor);
            }

            // Labels fade in when expanded
            if (p > 0.05f) {
                int labelAlpha = (int) (255 * p);
                var font = Md3Fonts.label();
                String name = dest.getLabel();
                float tw = Md3Fonts.width(font, name);
                int labelColor = Md3Theme.withAlpha(
                        selected ? Md3Theme.ON_SECONDARY_CONTAINER : Md3Theme.ON_SURFACE_VARIANT,
                        labelAlpha / 255f);
                Md3Fonts.drawText(gui, font, name, x + (currentW - tw) / 2f,
                        centerY + PILL_H / 2f + ICON_LABEL_GAP, labelColor);
            }
        }
    }

    private boolean isOverBlock(double mouseX, double mouseY, int index) {
        int top = blockTop(index);
        return mouseX >= x && mouseX <= x + getCurrentWidth()
                && mouseY >= top && mouseY <= top + BLOCK_HEIGHT;
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
        return HEADER_HEIGHT + destinations.length * (BLOCK_HEIGHT + BLOCK_SPACING);
    }

    // ── Destination abstraction ─────────────────────────

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

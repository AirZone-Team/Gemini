package geminiclient.gemini.modules.impl.visual.clickgui.md3.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A floating overlay (dropdown menu, color picker) rendered above the
 * scrolled content, outside its scissor region. Owned by the screen via
 * {@link Host}; created by value components when their row is clicked.
 */
public interface Md3Overlay {

    void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks);

    /** Handle a click inside the overlay. Return true if consumed. */
    boolean mouseClicked(double mouseX, double mouseY, int button);

    boolean mouseReleased(double mouseX, double mouseY, int button);

    /** Whether the point is inside the overlay bounds (outside clicks dismiss it). */
    boolean contains(double mouseX, double mouseY);

    /** True while the user is dragging inside the overlay (e.g. picker sliders). */
    default boolean isDragging() {
        return false;
    }

    /** Screen-side owner of the currently open overlay. */
    interface Host {
        void openOverlay(Md3Overlay overlay);

        void closeOverlay();

        int screenWidth();

        int screenHeight();

        /** Right edge of the window in screen coordinates (for overlay clamping). */
        int rightEdge();

        /** Bottom edge of the window in screen coordinates (for overlay clamping). */
        int bottomEdge();
    }
}

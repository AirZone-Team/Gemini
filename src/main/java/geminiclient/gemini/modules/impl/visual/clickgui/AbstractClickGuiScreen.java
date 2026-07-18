package geminiclient.gemini.modules.impl.visual.clickgui;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.modules.impl.visual.ClickGui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shared base for all ClickGui screen modes (Classic / Material3).
 * Handles closing behavior: closing the screen also disables the ClickGui module.
 *
 * <p>While a ClickGui screen is open, {@code MixinGameRenderer} defers the screen's
 * render-state extraction until after the client's HUD modules (ArrayList etc.)
 * have been submitted, and places a blur stratum boundary in between. This keeps
 * the ClickGui on top of every other HUD element with the world and HUD blurred
 * underneath. For that to work this base class must never request the vanilla
 * menu blur itself (only one blur boundary is allowed per frame), so
 * {@link #extractBackground} is pinned to the transparent in-game style.</p>
 */
public abstract class AbstractClickGuiScreen extends Screen {

    /** Duration of the blur fade-in when the screen opens. */
    private static final long BLUR_FADE_IN_MS = 300L;

    private final long openedAtMs = System.currentTimeMillis();

    protected AbstractClickGuiScreen(Component title) {
        super(title);
    }

    /**
     * Blur fade-in progress in [0, 1]; ramps up over {@link #BLUR_FADE_IN_MS}
     * after the screen opens so the background blur doesn't pop in abruptly.
     */
    public float getBlurFade() {
        return Math.min(1.0f, (System.currentTimeMillis() - this.openedAtMs) / (float) BLUR_FADE_IN_MS);
    }

    /**
     * Always use the transparent in-game background and never request the vanilla
     * blurred background — the blur boundary for the ClickGui is placed explicitly
     * by {@code MixinGameRenderer} after the HUD submissions, and
     * {@code GuiRenderState} permits only one blur boundary per frame.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.extractTransparentBackground(graphics);
        this.minecraft.gui.extractDeferredSubtitles();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
        Gemini.moduleManager.getModules().stream()
                .filter(m -> m instanceof ClickGui)
                .findFirst()
                .ifPresent(m -> m.setEnabled(false));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

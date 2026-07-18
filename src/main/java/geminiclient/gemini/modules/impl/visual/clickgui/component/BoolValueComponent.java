package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.modules.impl.visual.clickgui.ClassicTheme;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.utils.animation.SpringAnimation;
import geminiclient.gemini.values.impl.BoolValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class BoolValueComponent extends ValueComponent {

    // ── Spring animations ───────────────────────────────
    private final SpringAnimation toggleSpring = SpringAnimation.snappy();
    private final SpringAnimation hoverSpring  = SpringAnimation.smooth();

    public BoolValueComponent(BoolValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16);
        toggleSpring.snap(value.enabled ? 1.0f : 0.0f);
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        BoolValue boolValue = (BoolValue) this.value;

        // ── Hover spring ────────────────────────────────
        boolean hovered = isHovered(mouseX, mouseY);
        hoverSpring.setTarget(hovered ? 1.0f : 0.0f);
        hoverSpring.update(partialTicks);
        float hoverT = hoverSpring.getValue();

        // ── Row background + border ──────────────────────
        ClassicTheme.drawRow(guiGraphics, x, y, width, height, hoverT);

        // ── Label ───────────────────────────────────────
        guiGraphics.text(mc.font, boolValue.getName(), x + 7, y + 4, ClassicTheme.TEXT, true);

        // ── iOS-style toggle ────────────────────────────
        int switchW = 26;
        int switchH = 14;
        int trackR  = switchH / 2;
        int switchX = x + width - switchW - 6;
        int switchY = y + (height - switchH) / 2;

        // Handle — spring + easeOutCubic
        toggleSpring.setTarget(boolValue.enabled ? 1.0f : 0.0f);
        toggleSpring.update(partialTicks);
        float rawT = toggleSpring.getValue();
        float toggleT = SpringAnimation.easeOutCubic(rawT);

        // Track — colour cross-fades with the toggle state
        int trackColor = ClassicTheme.lerpColor(ClassicTheme.TRACK, ClassicTheme.ACCENT, toggleT);
        CustomRoundedRectRenderer.drawRoundedRect(
                guiGraphics, switchX, switchY, switchW, switchH, trackR, trackColor);

        // Track border
        CustomRoundedRectRenderer.drawRoundedOutline(
                guiGraphics, switchX, switchY, switchW, switchH,
                trackR, ClassicTheme.BORDER, 1);

        int handleSize = switchH - 4;
        int handleY    = switchY + 2;
        float handleMinX = switchX + 2;
        float handleMaxX = switchX + switchW - handleSize - 2;
        float handleX = handleMinX + (handleMaxX - handleMinX) * toggleT;

        // Handle shadow
        CustomRoundedRectRenderer.drawRoundedRect(
                guiGraphics, (int) handleX + 1, handleY + 1,
                handleSize, handleSize, handleSize / 2, ClassicTheme.HANDLE_SHADOW);

        // Handle
        CustomRoundedRectRenderer.drawRoundedRect(
                guiGraphics, (int) handleX, handleY,
                handleSize, handleSize, handleSize / 2, ClassicTheme.HANDLE);

        // Active track glow
        if (toggleT > 0.15f) {
            int glowColor = ClassicTheme.modulateAlpha(ClassicTheme.ACCENT_GLOW, toggleT);
            CustomRoundedRectRenderer.drawRoundedRect(
                    guiGraphics, switchX - 1, switchY - 1,
                    switchW + 2, switchH + 2, trackR + 1, glowColor);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        BoolValue boolValue = (BoolValue) this.value;
        if (isHovered(mouseX, mouseY) && button == 0) {
            boolValue.setEnabled(!boolValue.enabled);
            return true;
        }
        return false;
    }
}

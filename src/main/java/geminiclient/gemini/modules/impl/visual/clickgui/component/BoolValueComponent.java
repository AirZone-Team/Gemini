package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.utils.animation.SpringAnimation;
import geminiclient.gemini.values.impl.BoolValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class BoolValueComponent extends ValueComponent {

    // ── Modern palette ──────────────────────────────────
    private static final int ACTIVE_TRACK    = new Color(139, 92, 246).getRGB();   // #8B5CF6
    private static final int ACTIVE_GLOW     = new Color(139, 92, 246, 45).getRGB();
    private static final int INACTIVE_TRACK  = new Color(52, 52, 58).getRGB();
    private static final int HANDLE_COLOR    = Color.WHITE.getRGB();
    private static final int HANDLE_SHADOW   = new Color(0, 0, 0, 25).getRGB();
    private static final int BASE_BG         = new Color(20, 20, 28, 200).getRGB();
    private static final int TEXT_COLOR      = new Color(210, 210, 225).getRGB();
    private static final int BORDER_COLOR    = new Color(255, 255, 255, 10).getRGB();
    private static final int BORDER_HOVER    = new Color(255, 255, 255, 22).getRGB();

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

        // ── Background ───────────────────────────────────
        int bgAlpha = (int) (200 + (18 * hoverT));
        int bgColor = new Color(20, 20, 28, bgAlpha).getRGB();
        CustomRoundedRectRenderer.drawRoundedRect(
                guiGraphics, x, y, width, height, 4, bgColor);

        // ── Dynamic border ───────────────────────────────
        int borderColor = hoverT > 0.01f
                ? lerpColor(BORDER_COLOR, BORDER_HOVER, hoverT)
                : BORDER_COLOR;
        CustomRoundedRectRenderer.drawRoundedOutline(
                guiGraphics, x, y, width, height, 4, borderColor, 1);

        // ── Label ───────────────────────────────────────
        guiGraphics.text(mc.font, boolValue.getName(), x + 7, y + 4, TEXT_COLOR, true);

        // ── iOS-style toggle ────────────────────────────
        int switchW = 26;
        int switchH = 14;
        int trackR  = switchH / 2;
        int switchX = x + width - switchW - 6;
        int switchY = y + (height - switchH) / 2;

        // Track
        int trackColor = boolValue.enabled ? ACTIVE_TRACK : INACTIVE_TRACK;
        CustomRoundedRectRenderer.drawRoundedRect(
                guiGraphics, switchX, switchY, switchW, switchH, trackR, trackColor);

        // Track border
        CustomRoundedRectRenderer.drawRoundedOutline(
                guiGraphics, switchX, switchY, switchW, switchH,
                trackR, new Color(255, 255, 255, 15).getRGB(), 1);

        // Handle — spring + easeOutCubic
        toggleSpring.setTarget(boolValue.enabled ? 1.0f : 0.0f);
        toggleSpring.update(partialTicks);
        float rawT = toggleSpring.getValue();
        float toggleT = SpringAnimation.easeOutCubic(rawT);

        int handleSize = switchH - 4;
        int handleY    = switchY + 2;
        float handleMinX = switchX + 2;
        float handleMaxX = switchX + switchW - handleSize - 2;
        float handleX = handleMinX + (handleMaxX - handleMinX) * toggleT;

        // Handle shadow
        CustomRoundedRectRenderer.drawRoundedRect(
                guiGraphics, (int) handleX + 1, handleY + 1,
                handleSize, handleSize, handleSize / 2, HANDLE_SHADOW);

        // Handle
        CustomRoundedRectRenderer.drawRoundedRect(
                guiGraphics, (int) handleX, handleY,
                handleSize, handleSize, handleSize / 2, HANDLE_COLOR);

        // Active track glow
        if (boolValue.enabled && toggleT > 0.15f) {
            int glowAlpha = (int) (45 * toggleT);
            int glowColor = new Color(139, 92, 246, glowAlpha).getRGB();
            CustomRoundedRectRenderer.drawRoundedRect(
                    guiGraphics, switchX - 1, switchY - 1,
                    switchW + 2, switchH + 2, trackR + 1, glowColor);
        }
    }

    private int lerpColor(int colorA, int colorB, float t) {
        if (t <= 0.0f) return colorA;
        if (t >= 1.0f) return colorB;
        int aA = (colorA >> 24) & 0xFF, rA = (colorA >> 16) & 0xFF;
        int gA = (colorA >> 8) & 0xFF,  bA = colorA & 0xFF;
        int aB = (colorB >> 24) & 0xFF, rB = (colorB >> 16) & 0xFF;
        int gB = (colorB >> 8) & 0xFF,  bB = colorB & 0xFF;
        int a = (int) (aA + (aB - aA) * t);
        int r = (int) (rA + (rB - rA) * t);
        int g = (int) (gA + (gB - gA) * t);
        int b = (int) (bA + (bB - bA) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
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

package geminiclient.gemini.modules.impl.visual.clickgui.md3.component;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Fonts;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Theme;
import geminiclient.gemini.values.impl.ColorValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

/**
 * MD3 color control: row with a rounded swatch; clicking opens an MD3 color
 * picker overlay (SV square, hue slider, alpha slider, preset swatches).
 */
public class Md3ColorValueComponent extends Md3ValueComponent {

    private static final int[] PRESET_COLORS = {
            0xFFF5F5F5, 0xFF43E096, 0xFFFF5B5B, 0xFF3B82F6,
            0xFFF59E0B, 0xFFA855F7, 0xFFEC4899, 0xFF06B6D4,
            0xFFFFD700, 0xFF00FF00, 0xFFFF0000, 0xFF0000FF,
    };

    private final ColorValue colorValue;

    public Md3ColorValueComponent(ColorValue value, int width, Md3Overlay.Host host) {
        super(value, width, 36, host);
        this.colorValue = value;
    }

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        var bodyFont = Md3Fonts.body();
        float lh = Md3Fonts.lineHeight(bodyFont);
        float textY = y + (height - lh) / 2f;

        // Hover state layer (consistent across all MD3 rows)
        drawHoverState(gui, mouseX, mouseY);

        Md3Fonts.drawText(gui, bodyFont, colorValue.getName(), x, textY, Md3Theme.ON_SURFACE);

        // Hex text
        String hex = String.format("#%06X", colorValue.getRGB());
        var labelFont = Md3Fonts.label();
        float hw = Md3Fonts.width(labelFont, hex);

        // Swatch (right)
        int sw = 22, sh = 14;
        int swX = x + width - sw;
        int swY = y + (height - sh) / 2;
        CustomRoundedRectRenderer.drawRoundedRect(gui, swX, swY, sw, sh, 4, colorValue.getColor());
        CustomRoundedRectRenderer.drawRoundedOutline(gui, swX, swY, sw, sh, 4,
                Md3Theme.OUTLINE_VARIANT, 1);

        Md3Fonts.drawText(gui, labelFont, hex, swX - hw - 8,
                y + (height - Md3Fonts.lineHeight(labelFont)) / 2f, Md3Theme.ON_SURFACE_VARIANT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            overlayHost.openOverlay(new Md3ColorPicker(x + width, y + height / 2));
            return true;
        }
        return false;
    }

    // ── Color picker overlay ────────────────────────────

    private final class Md3ColorPicker implements Md3Overlay {

        private static final int PICKER_W = 216;
        private static final int PAD = 12;
        private static final int SV_H = 120;
        private static final int SLIDER_H = 12;
        private static final int GAP = 10;
        private static final int SWATCH = 18;
        private static final int SWATCH_GAP = 6;

        private final int px, py, ph;
        private final int pickerW = PICKER_W;

        // HSV state derived from the current color
        private float hue, sat, bri, alpha;

        private int dragTarget = 0; // 0 none, 1 SV, 2 hue, 3 alpha

        Md3ColorPicker(int anchorRight, int anchorCenterY) {
            int pickerH = PAD + SV_H + GAP + SLIDER_H + GAP + SLIDER_H + GAP + SWATCH * 2 + SWATCH_GAP + PAD;
            this.ph = pickerH;
            this.px = Math.max(8, Math.min(anchorRight - pickerW, overlayHost.rightEdge() - pickerW - 8));
            this.py = Math.max(8, Math.min(anchorCenterY - pickerH / 2, overlayHost.bottomEdge() - pickerH - 8));

            int c = colorValue.getColor();
            float[] hsb = Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
            this.hue = hsb[0];
            this.sat = hsb[1];
            this.bri = hsb[2];
            this.alpha = ((c >> 24) & 0xFF) / 255f;
        }

        private int svX() { return px + PAD; }
        private int svY() { return py + PAD; }
        private int svW() { return pickerW - PAD * 2; }
        private int hueY() { return svY() + SV_H + GAP; }
        private int alphaY() { return hueY() + SLIDER_H + GAP; }
        private int swatchY() { return alphaY() + SLIDER_H + GAP; }

        @Override
        public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
            // Continue an in-progress drag (mouse position arrives with the frame)
            if (dragTarget == 1) updateSV(mouseX, mouseY);
            else if (dragTarget == 2) updateHue(mouseX);
            else if (dragTarget == 3) updateAlpha(mouseX);

            int r = Md3Theme.R_CARD;
            Md3Theme.elevation2(gui, px, py, pickerW, ph, r);
            CustomRoundedRectRenderer.drawRoundedRect(gui, px, py, pickerW, ph, r,
                    Md3Theme.SURFACE_CONTAINER);

            // ── SV square: white -> hue horizontally, transparent -> black vertically
            int hueColor = Color.HSBtoRGB(hue, 1f, 1f);
            CustomRoundedRectRenderer.drawRoundedRectHorizGrad(gui, svX(), svY(), svW(), SV_H, 6,
                    0xFFFFFFFF, hueColor);
            CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, svX(), svY(), svW(), SV_H, 6,
                    0x00000000, 0xFF000000);

            // ── SV square outlines (subtle) ─────────────────
            CustomRoundedRectRenderer.drawRoundedOutline(gui, svX(), svY(), svW(), SV_H, 6,
                    Md3Theme.OUTLINE_VARIANT, 1);

            // SV cursor
            int cursorX = svX() + Math.round(sat * svW());
            int cursorY = svY() + Math.round((1f - bri) * SV_H);
            CustomRoundedRectRenderer.drawRoundedOutline(gui, cursorX - 5, cursorY - 5, 10, 10, 5,
                    0xFFFFFFFF, 2);

            // ── Hue slider ──────────────────────────────────
            int segW = svW() / 6;
            for (int i = 0; i < 6; i++) {
                int c1 = Color.HSBtoRGB(i / 6f, 1f, 1f);
                int c2 = Color.HSBtoRGB((i + 1) / 6f, 1f, 1f);
                int sx = svX() + i * segW;
                int w = (i == 5) ? svX() + svW() - sx : segW;
                CustomRoundedRectRenderer.drawRoundedRectHorizGrad(gui, sx, hueY(), w, SLIDER_H,
                        i == 0 ? 6 : 0, c1, c2);
            }
            CustomRoundedRectRenderer.drawRoundedOutline(gui, svX(), hueY(), svW(), SLIDER_H, 6,
                    Md3Theme.OUTLINE_VARIANT, 1);
            int hueCursorX = svX() + Math.round(hue * svW());
            CustomRoundedRectRenderer.drawRoundedOutline(gui, hueCursorX - 4, hueY() - 2, 8,
                    SLIDER_H + 4, 4, 0xFFFFFFFF, 2);

            // ── Alpha slider ────────────────────────────────
            int rgbNoAlpha = 0xFF000000 | (currentRGB() & 0xFFFFFF);
            CustomRoundedRectRenderer.drawRoundedRectHorizGrad(gui, svX(), alphaY(), svW(), SLIDER_H, 6,
                    rgbNoAlpha & 0x00FFFFFF, rgbNoAlpha);
            CustomRoundedRectRenderer.drawRoundedOutline(gui, svX(), alphaY(), svW(), SLIDER_H, 6,
                    Md3Theme.OUTLINE_VARIANT, 1);
            int alphaCursorX = svX() + Math.round(alpha * svW());
            CustomRoundedRectRenderer.drawRoundedOutline(gui, alphaCursorX - 4, alphaY() - 2, 8,
                    SLIDER_H + 4, 4, 0xFFFFFFFF, 2);

            // ── Preset swatches ─────────────────────────────
            for (int i = 0; i < PRESET_COLORS.length; i++) {
                int col = i % 6, row = i / 6;
                int sx = svX() + col * (SWATCH + SWATCH_GAP + 6);
                int sy = swatchY() + row * (SWATCH + SWATCH_GAP);
                CustomRoundedRectRenderer.drawRoundedRect(gui, sx, sy, SWATCH, SWATCH, 5,
                        PRESET_COLORS[i]);
                if (PRESET_COLORS[i] == colorValue.getColor()) {
                    CustomRoundedRectRenderer.drawRoundedOutline(gui, sx - 1, sy - 1,
                            SWATCH + 2, SWATCH + 2, 6, Md3Theme.PRIMARY, 2);
                } else {
                    CustomRoundedRectRenderer.drawRoundedOutline(gui, sx, sy, SWATCH, SWATCH, 5,
                            Md3Theme.OUTLINE_VARIANT, 1);
                }
            }

            // ── Hex label
            var font = Md3Fonts.label();
            Md3Fonts.drawText(gui, font, String.format("#%08X", currentRGB()),
                    svX(), py + ph - PAD - Md3Fonts.lineHeight(font) + 2, Md3Theme.ON_SURFACE_VARIANT);
        }

        private int currentRGB() {
            int rgb = Color.HSBtoRGB(hue, sat, bri) & 0xFFFFFF;
            return (Math.round(alpha * 255) << 24) | rgb;
        }

        private void apply() {
            colorValue.setColor(currentRGB());
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return false;

            if (inRect(mouseX, mouseY, svX(), svY(), svW(), SV_H)) {
                dragTarget = 1;
                updateSV(mouseX, mouseY);
                return true;
            }
            if (inRect(mouseX, mouseY, svX() - 4, hueY() - 2, svW() + 8, SLIDER_H + 4)) {
                dragTarget = 2;
                updateHue(mouseX);
                return true;
            }
            if (inRect(mouseX, mouseY, svX() - 4, alphaY() - 2, svW() + 8, SLIDER_H + 4)) {
                dragTarget = 3;
                updateAlpha(mouseX);
                return true;
            }
            for (int i = 0; i < PRESET_COLORS.length; i++) {
                int col = i % 6, row = i / 6;
                int sx = svX() + col * (SWATCH + SWATCH_GAP + 6);
                int sy = swatchY() + row * (SWATCH + SWATCH_GAP);
                if (inRect(mouseX, mouseY, sx, sy, SWATCH, SWATCH)) {
                    int preset = PRESET_COLORS[i];
                    float[] hsb = Color.RGBtoHSB((preset >> 16) & 0xFF, (preset >> 8) & 0xFF,
                            preset & 0xFF, null);
                    hue = hsb[0];
                    sat = hsb[1];
                    bri = hsb[2];
                    alpha = ((preset >> 24) & 0xFF) / 255f;
                    apply();
                    return true;
                }
            }
            return true; // consume clicks inside the picker
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0 && dragTarget != 0) {
                dragTarget = 0;
                return true;
            }
            return false;
        }

        private void updateSV(double mouseX, double mouseY) {
            sat = clamp((float) (mouseX - svX()) / svW());
            bri = 1f - clamp((float) (mouseY - svY()) / SV_H);
            apply();
        }

        private void updateHue(double mouseX) {
            hue = clamp((float) (mouseX - svX()) / svW());
            apply();
        }

        private void updateAlpha(double mouseX) {
            alpha = clamp((float) (mouseX - svX()) / svW());
            apply();
        }

        @Override
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= px && mouseX <= px + pickerW && mouseY >= py && mouseY <= py + ph;
        }

        @Override
        public boolean isDragging() {
            return dragTarget != 0;
        }

        private boolean inRect(double mx, double my, int rx, int ry, int rw, int rh) {
            return mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh;
        }

        private float clamp(float v) {
            return Math.max(0f, Math.min(1f, v));
        }
    }
}

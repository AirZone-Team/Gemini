package geminiclient.gemini.modules.impl.visual.clickgui.md3.component;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Anim;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Fonts;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3RenderUtils;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Theme;
import geminiclient.gemini.values.impl.ColorValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

/**
 * Material 3 colour control with a trailing tonal chip and an elevated colour
 * picker containing an SV field, hue/opacity tracks and tonal presets.
 */
public class Md3ColorValueComponent extends Md3ValueComponent {

    private static final int[] PRESET_COLORS = {
            0xFF6750A4, 0xFF625B71, 0xFF7D5260, 0xFFB3261E,
            0xFF3F51B5, 0xFF006A6A, 0xFF386A20, 0xFF5F5E62,
    };

    private final ColorValue colorValue;

    public Md3ColorValueComponent(ColorValue value, int width, Md3Overlay.Host host) {
        super(value, width, 36, host);
        this.colorValue = value;
    }

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        var bodyFont = Md3Fonts.body();
        float lineHeight = Md3Fonts.lineHeight(bodyFont);
        boolean hovered = isHovered(mouseX, mouseY);

        drawHoverState(gui, mouseX, mouseY);
        Md3Fonts.drawText(gui, bodyFont, colorValue.getName(), x,
                y + (height - lineHeight) / 2f, Md3Theme.ON_SURFACE);

        // Treat the swatch and value as one trailing action.
        String hex = formatHex(colorValue.getColor());
        var labelFont = Md3Fonts.label();
        float hexWidth = Md3Fonts.width(labelFont, hex);
        int chipH = 24;
        int chipW = Math.max(76, Math.round(hexWidth) + 38);
        int chipX = x + width - chipW;
        int chipY = y + (height - chipH) / 2;
        int chipColor = hovered
                ? Md3Theme.PRIMARY_CONTAINER
                : Md3Theme.SURFACE_CONTAINER_HIGHEST;
        int contentColor = hovered
                ? Md3Theme.ON_PRIMARY_CONTAINER
                : Md3Theme.ON_SURFACE_VARIANT;

        CustomRoundedRectRenderer.drawRoundedRect(gui, chipX, chipY, chipW, chipH,
                Md3Theme.R_FULL, chipColor);
        drawColorDot(gui, chipX + 13, chipY + chipH / 2, 14, colorValue.getColor());
        Md3Fonts.drawText(gui, labelFont, hex, chipX + 26,
                chipY + (chipH - Md3Fonts.lineHeight(labelFont)) / 2f, contentColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            overlayHost.openOverlay(new Md3ColorPicker(x + width, y + height / 2));
            return true;
        }
        return false;
    }

    private static String formatHex(int color) {
        return ((color >>> 24) & 0xFF) == 0xFF
                ? String.format("#%06X", color & 0xFFFFFF)
                : String.format("#%08X", color);
    }

    private static void drawColorDot(GuiGraphicsExtractor gui, int cx, int cy,
                                     int diameter, int color) {
        CustomRoundedRectRenderer.drawCircle(gui, cx, cy, diameter + 2,
                Md3Theme.OUTLINE_VARIANT);
        CustomRoundedRectRenderer.drawCircle(gui, cx, cy, diameter,
                Md3Theme.SURFACE_CONTAINER_LOWEST);
        CustomRoundedRectRenderer.drawCircle(gui, cx, cy, diameter - 2, color);
    }

    // ── Colour picker overlay ────────────────────────────────────────────

    private final class Md3ColorPicker implements Md3Overlay {

        private static final int PICKER_W = 240;
        private static final int PAD = 16;
        private static final int HEADER_H = 30;
        private static final int SV_H = 96;
        private static final int SLIDER_H = 12;
        private static final int GAP = 10;
        private static final int LABEL_H = 14;
        private static final int SWATCH = 19;
        private static final int SWATCH_GAP = 8;
        private static final int FOOTER_H = 30;

        private final int px;
        private final int py;
        private final int ph;
        private final long openedAtMs = System.currentTimeMillis();

        private float hue;
        private float sat;
        private float bri;
        private float alpha;

        // 0 none, 1 SV, 2 hue, 3 alpha
        private int dragTarget;

        Md3ColorPicker(int anchorRight, int anchorCenterY) {
            ph = PAD + HEADER_H + SV_H
                    + GAP + LABEL_H + SLIDER_H
                    + GAP + LABEL_H + SLIDER_H
                    + GAP + LABEL_H + SWATCH
                    + 12 + FOOTER_H + PAD;
            px = Math.max(8, Math.min(anchorRight - PICKER_W,
                    overlayHost.rightEdge() - PICKER_W - 8));
            py = Math.max(8, Math.min(anchorCenterY - ph / 2,
                    overlayHost.bottomEdge() - ph - 8));

            int color = colorValue.getColor();
            float[] hsb = Color.RGBtoHSB(
                    (color >> 16) & 0xFF,
                    (color >> 8) & 0xFF,
                    color & 0xFF,
                    null);
            hue = hsb[0];
            sat = hsb[1];
            bri = hsb[2];
            alpha = ((color >> 24) & 0xFF) / 255f;
        }

        private int svX() {
            return px + PAD;
        }

        private int svY() {
            return py + PAD + HEADER_H;
        }

        private int svW() {
            return PICKER_W - PAD * 2;
        }

        private int hueLabelY() {
            return svY() + SV_H + GAP;
        }

        private int hueY() {
            return hueLabelY() + LABEL_H;
        }

        private int alphaLabelY() {
            return hueY() + SLIDER_H + GAP;
        }

        private int alphaY() {
            return alphaLabelY() + LABEL_H;
        }

        private int swatchLabelY() {
            return alphaY() + SLIDER_H + GAP;
        }

        private int swatchY() {
            return swatchLabelY() + LABEL_H;
        }

        private int footerY() {
            return swatchY() + SWATCH + 12;
        }

        @Override
        public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
            if (dragTarget == 1) {
                updateSV(mouseX, mouseY);
            } else if (dragTarget == 2) {
                updateHue(mouseX);
            } else if (dragTarget == 3) {
                updateAlpha(mouseX);
            }

            float openT = Math.min(1f, (System.currentTimeMillis() - openedAtMs) / 180f);
            int lift = Math.round((1f - Md3Anim.easeOutCubic(openT)) * 5);
            int drawY = py + lift;

            Md3Theme.elevation2(gui, px, drawY, PICKER_W, ph, Md3Theme.R_CARD);
            CustomRoundedRectRenderer.drawRoundedRect(gui, px, drawY, PICKER_W, ph,
                    Md3Theme.R_CARD, Md3Theme.SURFACE_CONTAINER_HIGH);

            drawHeader(gui, drawY);
            drawSaturationValueField(gui, lift);
            drawHueControl(gui, lift);
            drawAlphaControl(gui, lift);
            drawPresetColors(gui, mouseX, mouseY, lift);
            drawFooter(gui, mouseX, mouseY, lift);
        }

        private void drawHeader(GuiGraphicsExtractor gui, int drawY) {
            var titleFont = Md3Fonts.title();
            Md3Fonts.drawText(gui, titleFont, "Choose color", px + PAD, drawY + PAD,
                    Md3Theme.ON_SURFACE);

            int previewW = 38;
            int previewH = 24;
            int previewX = px + PICKER_W - PAD - previewW;
            int previewY = drawY + PAD - 5;
            CustomRoundedRectRenderer.drawRoundedRect(gui, previewX, previewY,
                    previewW, previewH, Md3Theme.R_FULL,
                    Md3Theme.SURFACE_CONTAINER_LOWEST);
            CustomRoundedRectRenderer.drawRoundedRect(gui, previewX + 2, previewY + 2,
                    previewW - 4, previewH - 4, Md3Theme.R_FULL, currentRGB());
            CustomRoundedRectRenderer.drawRoundedOutline(gui, previewX, previewY,
                    previewW, previewH, Md3Theme.R_FULL,
                    Md3Theme.OUTLINE_VARIANT, 1);
        }

        private void drawSaturationValueField(GuiGraphicsExtractor gui, int lift) {
            int fieldY = svY() + lift;
            int hueColor = Color.HSBtoRGB(hue, 1f, 1f);
            CustomRoundedRectRenderer.drawRoundedRectHorizGrad(gui, svX(), fieldY,
                    svW(), SV_H, Md3Theme.R_SMALL, 0xFFFFFFFF, hueColor);
            CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, svX(), fieldY,
                    svW(), SV_H, Md3Theme.R_SMALL, 0x00000000, 0xFF000000);
            CustomRoundedRectRenderer.drawRoundedOutline(gui, svX(), fieldY,
                    svW(), SV_H, Md3Theme.R_SMALL, Md3Theme.OUTLINE_VARIANT, 1);

            int cursorX = svX() + Math.round(sat * svW());
            int cursorY = fieldY + Math.round((1f - bri) * SV_H);
            int selectedColor = 0xFF000000
                    | (Color.HSBtoRGB(hue, sat, bri) & 0xFFFFFF);
            drawPickerHandle(gui, cursorX, cursorY, selectedColor);
        }

        private void drawHueControl(GuiGraphicsExtractor gui, int lift) {
            var labelFont = Md3Fonts.label();
            float lineHeight = Md3Fonts.lineHeight(labelFont);
            Md3Fonts.drawText(gui, labelFont, "Hue", svX(),
                    hueLabelY() + lift + (LABEL_H - lineHeight) / 2f,
                    Md3Theme.ON_SURFACE_VARIANT);

            int trackY = hueY() + lift;
            int segmentW = svW() / 6;
            for (int i = 0; i < 6; i++) {
                int leftColor = Color.HSBtoRGB(i / 6f, 1f, 1f);
                int rightColor = Color.HSBtoRGB((i + 1) / 6f, 1f, 1f);
                int segmentX = svX() + i * segmentW;
                int width = i == 5 ? svX() + svW() - segmentX : segmentW;
                CustomRoundedRectRenderer.drawRoundedRectHorizGrad(gui,
                        segmentX, trackY, width, SLIDER_H,
                        i == 0 ? SLIDER_H / 2 : 0,
                        leftColor, rightColor);
            }
            CustomRoundedRectRenderer.drawRoundedOutline(gui, svX(), trackY,
                    svW(), SLIDER_H, SLIDER_H / 2,
                    Md3Theme.OUTLINE_VARIANT, 1);

            int cursorX = svX() + Math.round(hue * svW());
            drawTrackHandle(gui, cursorX, trackY + SLIDER_H / 2,
                    Color.HSBtoRGB(hue, 1f, 1f));
        }

        private void drawAlphaControl(GuiGraphicsExtractor gui, int lift) {
            var labelFont = Md3Fonts.label();
            float lineHeight = Md3Fonts.lineHeight(labelFont);
            String opacity = "Opacity  " + Math.round(alpha * 100f) + "%";
            Md3Fonts.drawText(gui, labelFont, opacity, svX(),
                    alphaLabelY() + lift + (LABEL_H - lineHeight) / 2f,
                    Md3Theme.ON_SURFACE_VARIANT);

            int trackY = alphaY() + lift;
            int opaqueRgb = 0xFF000000 | (currentRGB() & 0xFFFFFF);
            drawCheckerTrack(gui, svX(), trackY, svW(), SLIDER_H);
            CustomRoundedRectRenderer.drawRoundedRectHorizGrad(gui, svX(), trackY,
                    svW(), SLIDER_H, SLIDER_H / 2,
                    opaqueRgb & 0x00FFFFFF, opaqueRgb);
            CustomRoundedRectRenderer.drawRoundedOutline(gui, svX(), trackY,
                    svW(), SLIDER_H, SLIDER_H / 2,
                    Md3Theme.OUTLINE_VARIANT, 1);

            int cursorX = svX() + Math.round(alpha * svW());
            drawTrackHandle(gui, cursorX, trackY + SLIDER_H / 2, currentRGB());
        }

        private void drawPresetColors(GuiGraphicsExtractor gui, int mouseX,
                                      int mouseY, int lift) {
            var labelFont = Md3Fonts.label();
            float lineHeight = Md3Fonts.lineHeight(labelFont);
            Md3Fonts.drawText(gui, labelFont, "Material colors", svX(),
                    swatchLabelY() + lift + (LABEL_H - lineHeight) / 2f,
                    Md3Theme.ON_SURFACE_VARIANT);

            for (int i = 0; i < PRESET_COLORS.length; i++) {
                int swatchX = svX() + i * (SWATCH + SWATCH_GAP);
                int swatchY = swatchY() + lift;
                boolean hovered = inRect(mouseX, mouseY,
                        swatchX - 3, swatchY - 3, SWATCH + 6, SWATCH + 6);
                boolean selected = alpha >= 0.995f
                        && (currentRGB() & 0xFFFFFF)
                        == (PRESET_COLORS[i] & 0xFFFFFF);

                if (hovered || selected) {
                    CustomRoundedRectRenderer.drawCircle(gui,
                            swatchX + SWATCH / 2f, swatchY + SWATCH / 2f,
                            SWATCH + 8,
                            selected
                                    ? Md3Theme.PRIMARY_CONTAINER
                                    : Md3Theme.hoverState(Md3Theme.ON_SURFACE));
                }
                CustomRoundedRectRenderer.drawCircle(gui,
                        swatchX + SWATCH / 2f, swatchY + SWATCH / 2f,
                        SWATCH, PRESET_COLORS[i]);
                if (selected) {
                    Md3RenderUtils.drawCheck(gui, swatchX + 4, swatchY + 4,
                            11, 0xFFFFFFFF);
                }
            }
        }

        private void drawFooter(GuiGraphicsExtractor gui, int mouseX,
                                int mouseY, int lift) {
            int y = footerY() + lift;
            var labelFont = Md3Fonts.label();
            float lineHeight = Md3Fonts.lineHeight(labelFont);

            CustomRoundedRectRenderer.drawRoundedRect(gui, svX(), y,
                    104, FOOTER_H, Md3Theme.R_SMALL,
                    Md3Theme.SURFACE_CONTAINER_HIGHEST);
            drawColorDot(gui, svX() + 15, y + FOOTER_H / 2,
                    14, currentRGB());
            Md3Fonts.drawText(gui, labelFont, formatHex(currentRGB()), svX() + 28,
                    y + (FOOTER_H - lineHeight) / 2f,
                    Md3Theme.ON_SURFACE_VARIANT);

            int doneW = 58;
            int doneX = svX() + svW() - doneW;
            boolean hovered = inRect(mouseX, mouseY,
                    doneX, y, doneW, FOOTER_H);
            CustomRoundedRectRenderer.drawRoundedRect(gui, doneX, y,
                    doneW, FOOTER_H, Md3Theme.R_FULL, Md3Theme.PRIMARY);
            if (hovered) {
                CustomRoundedRectRenderer.drawRoundedRect(gui, doneX, y,
                        doneW, FOOTER_H, Md3Theme.R_FULL,
                        Md3Theme.hoverState(Md3Theme.ON_PRIMARY));
            }
            float textWidth = Md3Fonts.width(labelFont, "Done");
            Md3Fonts.drawText(gui, labelFont, "Done",
                    doneX + (doneW - textWidth) / 2f,
                    y + (FOOTER_H - lineHeight) / 2f,
                    Md3Theme.ON_PRIMARY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return false;
            }

            int doneW = 58;
            int doneX = svX() + svW() - doneW;
            if (inRect(mouseX, mouseY, doneX, footerY(), doneW, FOOTER_H)) {
                overlayHost.closeOverlay();
                return true;
            }
            if (inRect(mouseX, mouseY, svX(), svY(), svW(), SV_H)) {
                dragTarget = 1;
                updateSV(mouseX, mouseY);
                return true;
            }
            if (inRect(mouseX, mouseY,
                    svX() - 4, hueY() - 3, svW() + 8, SLIDER_H + 6)) {
                dragTarget = 2;
                updateHue(mouseX);
                return true;
            }
            if (inRect(mouseX, mouseY,
                    svX() - 4, alphaY() - 3, svW() + 8, SLIDER_H + 6)) {
                dragTarget = 3;
                updateAlpha(mouseX);
                return true;
            }
            for (int i = 0; i < PRESET_COLORS.length; i++) {
                int swatchX = svX() + i * (SWATCH + SWATCH_GAP);
                if (inRect(mouseX, mouseY,
                        swatchX - 3, swatchY() - 3, SWATCH + 6, SWATCH + 6)) {
                    selectPreset(PRESET_COLORS[i]);
                    return true;
                }
            }
            return true;
        }

        private void selectPreset(int preset) {
            float[] hsb = Color.RGBtoHSB(
                    (preset >> 16) & 0xFF,
                    (preset >> 8) & 0xFF,
                    preset & 0xFF,
                    null);
            hue = hsb[0];
            sat = hsb[1];
            bri = hsb[2];
            alpha = ((preset >> 24) & 0xFF) / 255f;
            apply();
        }

        private void drawPickerHandle(GuiGraphicsExtractor gui, int cx,
                                      int cy, int color) {
            CustomRoundedRectRenderer.drawCircle(gui, cx, cy, 16,
                    Md3Theme.withAlpha(Md3Theme.ON_SURFACE, 0.55f));
            CustomRoundedRectRenderer.drawCircle(gui, cx, cy, 13, 0xFFFFFFFF);
            CustomRoundedRectRenderer.drawCircle(gui, cx, cy, 9, color);
        }

        private void drawTrackHandle(GuiGraphicsExtractor gui, int cx,
                                     int cy, int color) {
            CustomRoundedRectRenderer.drawCircle(gui, cx, cy, 18,
                    Md3Theme.withAlpha(Md3Theme.ON_SURFACE, 0.35f));
            CustomRoundedRectRenderer.drawCircle(gui, cx, cy, 15,
                    Md3Theme.SURFACE_CONTAINER_LOWEST);
            CustomRoundedRectRenderer.drawCircle(gui, cx, cy, 11, color);
        }

        private void drawCheckerTrack(GuiGraphicsExtractor gui, int x, int y,
                                      int width, int height) {
            CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, width, height,
                    height / 2, Md3Theme.SURFACE_CONTAINER_LOWEST);
            int cell = height / 2;
            int startX = x + height / 2;
            int endX = x + width - height / 2;
            for (int cellX = startX, column = 0;
                 cellX < endX;
                 cellX += cell, column++) {
                int cellWidth = Math.min(cell, endX - cellX);
                int top = (column & 1) == 0
                        ? Md3Theme.SURFACE_CONTAINER_HIGHEST
                        : Md3Theme.SURFACE_CONTAINER_LOWEST;
                int bottom = (column & 1) == 0
                        ? Md3Theme.SURFACE_CONTAINER_LOWEST
                        : Md3Theme.SURFACE_CONTAINER_HIGHEST;
                CustomRoundedRectRenderer.drawRoundedRect(gui,
                        cellX, y, cellWidth, cell, 0, top);
                CustomRoundedRectRenderer.drawRoundedRect(gui,
                        cellX, y + cell, cellWidth, height - cell, 0, bottom);
            }
        }

        private int currentRGB() {
            int rgb = Color.HSBtoRGB(hue, sat, bri) & 0xFFFFFF;
            return (Math.round(alpha * 255) << 24) | rgb;
        }

        private void apply() {
            colorValue.setColor(currentRGB());
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
            return mouseX >= px && mouseX <= px + PICKER_W
                    && mouseY >= py && mouseY <= py + ph;
        }

        @Override
        public boolean isDragging() {
            return dragTarget != 0;
        }

        private boolean inRect(double mouseX, double mouseY,
                               int x, int y, int width, int height) {
            return mouseX >= x && mouseX <= x + width
                    && mouseY >= y && mouseY <= y + height;
        }

        private float clamp(float value) {
            return Math.max(0f, Math.min(1f, value));
        }
    }
}

package geminiclient.gemini.modules.impl.visual.osu4k.screen;

import geminiclient.gemini.base.I18n;
import geminiclient.gemini.modules.impl.visual.Osu4k;
import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kKeyConfig;
import geminiclient.gemini.modules.impl.visual.clickgui.ModuleComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Per-column keybind editor, one key set per lane count (1K-10K).
 *
 * <p>Click a column row to enter rebind mode (the row highlights and waits for
 * the next key press). Pressing any key immediately binds it; Esc cancels.
 * Each column's bound key is live-tested — pressing it lights the lane up in
 * the column preview strip. Changes are written to {@code osu4k.json} on the
 * spot and applied to the shared key set of the selected count.</p>
 *
 * <p>The chip row at the top switches which lane count is being edited; the
 * row list scrolls when a count has more lanes than the panel fits.</p>
 */
public final class Osu4kKeybindScreen extends Osu4kScreen {

    private static final int PANEL_W = 440;
    private static final int PANEL_H = 400;
    private static final int ROW_H = 42;
    private static final int ROW_GAP = 6;

    // Panel-relative layout: selector chips, then the (scrolling) row list,
    // then the live-test strip and the Esc footer line.
    private static final int SELECTOR_Y = 66;
    private static final int CHIP_W = 34;
    private static final int CHIP_H = 26;
    private static final int CHIP_GAP = 4;
    private static final int ROWS_TOP = 100;
    private static final int ROWS_BOTTOM = PANEL_H - 88;
    private static final int LEGEND_Y = PANEL_H - 74;

    private int panelX, panelY;
    private int rebindingColumn = -1;
    private int selectedColumns;
    private int scrollOffset;
    private int[] keys;
    private final long[] laneFlashAt = new long[Osu4kKeyConfig.MAX_KEYS];
    private final boolean[] keysDown = new boolean[Osu4kKeyConfig.MAX_KEYS];
    private long openAtMs;

    public Osu4kKeybindScreen(Screen parent, int initialColumns) {
        super(parent, "OSU4k Keybinds");
        this.selectedColumns = clampColumns(initialColumns);
        this.keys = Osu4k.keysFor(selectedColumns).clone();
    }

    @Override
    protected void init() {
        super.init();
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;
        openAtMs = System.currentTimeMillis();
        clampScroll();
    }

    private static int clampColumns(int columns) {
        return Math.max(Osu4kKeyConfig.MIN_KEYS, Math.min(Osu4kKeyConfig.MAX_KEYS, columns));
    }

    // ---------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        fillBackground(gui);
        long now = System.currentTimeMillis();
        float reveal = easeOutCubic(clamp01((now - openAtMs) / 380f));
        int py = panelY + Math.round((1f - reveal) * 12f);

        drawGlassPanel(gui, panelX, py, PANEL_W, PANEL_H, 14);

        int textColor = scaleAlpha(COLOR_TEXT, reveal);
        int dimColor = scaleAlpha(COLOR_TEXT_DIM, reveal);
        int accentColor = scaleAlpha(COLOR_ACCENT, reveal);

        drawCentered(gui, titleFont, I18n.tr("Custom Keybinds"), panelX + PANEL_W / 2f, py + 30, textColor);
        drawAccentGlow(gui, panelX + PANEL_W / 2f, py + 48, 110, 2);

        // Lane-count selector chips (1K..10K).
        int chipsTotal = CHIP_W * 10 + CHIP_GAP * 9;
        int chipStart = panelX + (PANEL_W - chipsTotal) / 2;
        for (int k = Osu4kKeyConfig.MIN_KEYS; k <= Osu4kKeyConfig.MAX_KEYS; k++) {
            int cx = chipStart + (k - Osu4kKeyConfig.MIN_KEYS) * (CHIP_W + CHIP_GAP);
            boolean selected = k == selectedColumns;
            boolean hovered = inRect(mouseX, mouseY, cx, py + SELECTOR_Y, CHIP_W, CHIP_H);
            int fill = selected ? 0x4A2A6C8F : hovered ? 0x3A2C3A54 : 0x2A232D40;
            geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer.drawRoundedRect(
                    gui, cx, py + SELECTOR_Y, CHIP_W, CHIP_H, 6, scaleAlpha(fill, reveal));
            geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer.drawRoundedOutline(
                    gui, cx, py + SELECTOR_Y, CHIP_W, CHIP_H, 6,
                    scaleAlpha(selected ? COLOR_ACCENT : GLASS_OUTLINE_SOFT, reveal), selected ? 2 : 1);
            drawCentered(gui, smallFont, k + "K", cx + CHIP_W / 2f, py + SELECTOR_Y + CHIP_H / 2f,
                    selected ? accentColor : textColor);
        }

        // Column rows, scrolled and clipped to the list region.
        int listTop = py + ROWS_TOP;
        int listBottom = py + ROWS_BOTTOM;
        int rows = visibleRows();
        gui.enableScissor(panelX + 16, listTop, panelX + PANEL_W - 16, listBottom);
        for (int i = scrollOffset; i < selectedColumns && i < scrollOffset + rows; i++) {
            int rowY = listTop + (i - scrollOffset) * (ROW_H + ROW_GAP);
            drawLaneRow(gui, i, rowY, mouseX, mouseY, reveal);
        }
        gui.disableScissor();

        // Scrollbar for counts whose rows overflow the panel.
        if (selectedColumns > rows) {
            int trackX = panelX + PANEL_W - 18;
            int trackH = ROWS_BOTTOM - ROWS_TOP;
            int maxScroll = selectedColumns - rows;
            geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer.drawRoundedRect(
                    gui, trackX, listTop, 3, trackH, 2, 0x3340495E);
            float thumbH = Math.max(24, trackH * rows / (float) selectedColumns);
            float thumbY = listTop + (trackH - thumbH) * scrollOffset / maxScroll;
            geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer.drawRoundedRect(
                    gui, trackX, Math.round(thumbY), 3, Math.round(thumbH), 2, scaleAlpha(COLOR_ACCENT, reveal));
        }

        // Live-test strip.
        int legendY = py + LEGEND_Y;
        drawCentered(gui, smallFont, I18n.tr("PRESS A KEY TO TEST IT LIVE"), panelX + PANEL_W / 2f, legendY, dimColor);
        int laneGap = 10;
        int laneW = Math.min(64, (PANEL_W - 48 - (selectedColumns - 1) * laneGap) / selectedColumns);
        int totalLaneW = laneW * selectedColumns + laneGap * (selectedColumns - 1);
        int startX = panelX + (PANEL_W - totalLaneW) / 2;
        for (int c = 0; c < selectedColumns; c++) {
            int lx = startX + c * (laneW + laneGap);
            int flash = laneGlow(c);
            int fill = flash > 0 ? blend(Osu4k.laneColor(c), 0xFF000000, flash) : scaleAlpha(0xAA1C2330, reveal);
            geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer.drawRoundedRect(
                    gui, lx, legendY + 14, laneW, 28, 6, fill);
            if (flash > 0) {
                geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer.drawRoundedOutline(
                        gui, lx - 2, legendY + 12, laneW + 4, 32, 8, scaleAlpha(Osu4k.laneColor(c), reveal), 2);
            }
        }

        drawCentered(gui, smallFont, I18n.tr("Esc: back"), panelX + PANEL_W / 2f, py + PANEL_H - 14, dimColor);
    }

    private void drawLaneRow(GuiGraphicsExtractor gui, int column, int rowY, int mouseX, int mouseY, float reveal) {
        boolean hovered = inRect(mouseX, mouseY, panelX + 20, rowY, PANEL_W - 40, ROW_H);
        boolean rebinding = rebindingColumn == column;

        int top = rebinding ? 0x402A4C6E : hovered ? 0x3A2C3A54 : 0x2A232D40;
        int bottom = rebinding ? 0x30203A55 : hovered ? 0x28222A3C : 0x201B2230;
        int outline = rebinding ? COLOR_ACCENT : hovered ? 0x88FFFFFF : GLASS_OUTLINE_SOFT;
        geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer.drawRoundedRectVertGrad(
                gui, panelX + 20, rowY, PANEL_W - 40, ROW_H, 8,
                scaleAlpha(top, reveal), scaleAlpha(bottom, reveal));
        geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer.drawRoundedOutline(
                gui, panelX + 20, rowY, PANEL_W - 40, ROW_H, 8, scaleAlpha(outline, reveal), rebinding ? 2 : 1);

        int dotX = panelX + 40;
        int dotY = rowY + ROW_H / 2;
        geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer.drawCircle(
                gui, dotX, dotY, 12, scaleAlpha(Osu4k.laneColor(column), reveal));

        drawStringCenteredY(gui, itemFont, I18n.trf("Column %d", column + 1), panelX + 58, rowY + ROW_H / 2f, scaleAlpha(COLOR_TEXT, reveal));

        String keyName = ModuleComponent.getKeyName(keys[column]);
        String label = rebinding ? I18n.tr("Press a key...") : keyName;
        int keyColor = rebinding ? scaleAlpha(COLOR_ACCENT, reveal) : scaleAlpha(COLOR_SUCCESS, reveal);
        int keyX = panelX + PANEL_W - 128;
        geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer.drawRoundedRect(
                gui, keyX, rowY + 7, 92, ROW_H - 14, 7, scaleAlpha(0x552A3145, reveal));
        geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer.drawRoundedOutline(
                gui, keyX, rowY + 7, 92, ROW_H - 14, 7, scaleAlpha(rebinding ? COLOR_ACCENT : GLASS_OUTLINE_SOFT, reveal), 1);
        drawCentered(gui, itemFont, label, keyX + 46, rowY + ROW_H / 2f, keyColor);
    }

    private int laneGlow(int column) {
        long elapsed = System.currentTimeMillis() - laneFlashAt[column];
        if (elapsed > 400 || elapsed < 0) {
            return 0;
        }
        return 255 - (int) (255 * elapsed / 400);
    }

    // ---------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean idk) {
        if (mouse.button() != 0) {
            return super.mouseClicked(mouse, idk);
        }
        int py = panelY + Math.round((1f - easeOutCubic(clamp01((System.currentTimeMillis() - openAtMs) / 380f))) * 12f);

        // Selector chips switch which lane count is being edited.
        int chipsTotal = CHIP_W * 10 + CHIP_GAP * 9;
        int chipStart = panelX + (PANEL_W - chipsTotal) / 2;
        for (int k = Osu4kKeyConfig.MIN_KEYS; k <= Osu4kKeyConfig.MAX_KEYS; k++) {
            int cx = chipStart + (k - Osu4kKeyConfig.MIN_KEYS) * (CHIP_W + CHIP_GAP);
            if (inRect(mouse.x(), mouse.y(), cx, py + SELECTOR_Y, CHIP_W, CHIP_H)) {
                if (k != selectedColumns) {
                    selectedColumns = k;
                    keys = Osu4k.keysFor(selectedColumns).clone();
                    rebindingColumn = -1;
                    scrollOffset = 0;
                    clampScroll();
                }
                return true;
            }
        }

        // Column rows (only the visible ones).
        int listTop = py + ROWS_TOP;
        int rows = visibleRows();
        for (int i = scrollOffset; i < selectedColumns && i < scrollOffset + rows; i++) {
            int rowY = listTop + (i - scrollOffset) * (ROW_H + ROW_GAP);
            if (inRect(mouse.x(), mouse.y(), panelX + 20, rowY, PANEL_W - 40, ROW_H)) {
                rebindingColumn = rebindingColumn == i ? -1 : i;
                return true;
            }
        }
        if (inRect(mouse.x(), mouse.y(), panelX + 20, py + PANEL_H - 32, PANEL_W - 40, 24)) {
            closeToParent();
            return true;
        }
        return super.mouseClicked(mouse, idk);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inRect(mouseX, mouseY, panelX, panelY, PANEL_W, PANEL_H)) {
            if (scrollY > 0) {
                scrollOffset--;
            } else if (scrollY < 0) {
                scrollOffset++;
            }
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (rebindingColumn >= 0) {
            if (event.isEscape()) {
                rebindingColumn = -1;
                return true;
            }
            keys[rebindingColumn] = key;
            persist();
            laneFlashAt[rebindingColumn] = System.currentTimeMillis();
            rebindingColumn = -1;
            return true;
        }
        if (event.isEscape()) {
            closeToParent();
            return true;
        }
        int col = columnForKey(key);
        if (col >= 0) {
            if (!keysDown[col]) {
                keysDown[col] = true;
                laneFlashAt[col] = System.currentTimeMillis();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        int col = columnForKey(event.key());
        if (col >= 0) {
            keysDown[col] = false;
            return true;
        }
        return super.keyReleased(event);
    }

    private int columnForKey(int key) {
        for (int c = 0; c < selectedColumns; c++) {
            if (keys[c] == key) {
                return c;
            }
        }
        return -1;
    }

    private void persist() {
        Osu4k.setKeys(selectedColumns, keys);
    }

    private void closeToParent() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------------
    // Scrolling helpers
    // ---------------------------------------------------------------------

    private int visibleRows() {
        return Math.max(1, (ROWS_BOTTOM - ROWS_TOP) / (ROW_H + ROW_GAP));
    }

    private void clampScroll() {
        int max = Math.max(0, selectedColumns - visibleRows());
        scrollOffset = Math.max(0, Math.min(scrollOffset, max));
    }
}

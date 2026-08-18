package geminiclient.gemini.modules.impl.visual.osu4k.screen;

import geminiclient.gemini.base.I18n;
import geminiclient.gemini.modules.impl.visual.Osu4k;
import geminiclient.gemini.modules.impl.visual.osu4k.model.BeatmapData;
import geminiclient.gemini.modules.impl.visual.osu4k.model.OszArchive;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Beatmap selection screen: opens a {@code .osz} via the native file dialog,
 * lists every playable mania difficulty (1K-10K), and offers entry to keybinds.
 *
 * <p>Errors from a corrupt or unsupported archive are shown inline in the
 * panel (never thrown). Loading is done synchronously — set sizes are small
 * and parsing a few hundred KB of text is well under a frame budget.</p>
 */
public final class Osu4kSelectScreen extends Osu4kScreen {

    private static final int PANEL_W = 540;
    private static final int PANEL_H = 400;
    private static final int ROW_H = 36;
    private static final int ROW_GAP = 6;
    /** Difficulty list region (panel-relative): below the header, above the footer. */
    private static final int LIST_TOP = 138;
    private static final int LIST_BOTTOM = 372;

    private int panelX, panelY;
    private String errorText;
    private String setTitle = "";
    private List<BeatmapData> maps = List.of();
    private int scrollOffset;
    private final List<String> messages = new ArrayList<>();
    private long messagesUntil;
    private long openAtMs;

    public Osu4kSelectScreen(Screen parent) {
        super(parent, "OSU4k");
    }

    @Override
    protected void init() {
        super.init();
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;
        openAtMs = System.currentTimeMillis();
        // Show the difficulties of a set already loaded (e.g. returning from
        // the library screen after picking an entry).
        refreshFromCurrentArchive();
    }

    // ---------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------

    private void openFileChooser() {
        Thread t = new Thread(() -> {
            String selected = TinyFileDialogs.tinyfd_openFileDialog(
                    I18n.tr("Select .osz beatmap"),
                    System.getProperty("user.home"),
                    null,
                    "osu! beatmap set (*.osz)",
                    false);
            if (selected != null) {
                loadArchive(Path.of(selected));
            }
        }, "OSU4K-FileChooser");
        t.setDaemon(true);
        t.start();
    }

    /** Loads an archive synchronously (file dialog thread or caller). */
    private void loadArchive(Path path) {
        String error = Osu4k.openBeatmapSet(path);
        if (error != null) {
            errorText = error;
            maps = List.of();
            setTitle = "";
            return;
        }
        refreshFromCurrentArchive();
        showMessage(I18n.trf("Loaded %d difficulties", maps.size()));
    }

    /** Rebuilds the difficulty list from the currently loaded beatmap set. */
    private void refreshFromCurrentArchive() {
        if (Osu4k.currentArchive == null) {
            maps = List.of();
            setTitle = "";
            clampScroll();
            return;
        }
        List<BeatmapData> playable = Osu4k.currentArchive.playableMaps();
        if (playable.isEmpty()) {
            List<String> reasons = new ArrayList<>();
            for (OszArchive.MapFile mf : Osu4k.currentArchive.mapFiles()) {
                reasons.add(mf.entryName() + "  " + mf.error());
            }
            errorText = I18n.tr("No playable mania map in this .osz") + "\n" + String.join("\n", reasons);
            maps = List.of();
            setTitle = "";
        } else {
            maps = playable;
            setTitle = playable.get(0).artist() + " - " + playable.get(0).title();
            errorText = null;
        }
        clampScroll();
    }

    private void openLibrary() {
        this.minecraft.gui.setScreen(new Osu4kLibraryScreen(this));
    }

    private void startGame(BeatmapData map) {
        // The game screen returns to this screen on Exit, so another
        // difficulty can be picked without re-opening the archive.
        this.minecraft.gui.setScreen(new Osu4kGameScreen(this, map));
    }

    private void openKeybinds() {
        this.minecraft.gui.setScreen(new Osu4kKeybindScreen(this, 4));
    }

    private void showMessage(String text) {
        messages.add(text);
        if (messages.size() > 4) {
            messages.remove(0);
        }
        messagesUntil = System.currentTimeMillis() + 3000;
    }

    // ---------------------------------------------------------------------
    // List layout helpers
    // ---------------------------------------------------------------------

    private int visibleRows() {
        return Math.max(1, (LIST_BOTTOM - LIST_TOP) / (ROW_H + ROW_GAP));
    }

    private void clampScroll() {
        int max = Math.max(0, maps.size() - visibleRows());
        scrollOffset = Math.max(0, Math.min(scrollOffset, max));
    }

    // ---------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        fillBackground(gui);
        long now = System.currentTimeMillis();
        float reveal = easeOutCubic(clamp01((now - openAtMs) / 400f));

        // Panel with a soft entrance offset.
        int py = panelY + Math.round((1f - reveal) * 14f);
        drawGlassPanel(gui, panelX, py, PANEL_W, PANEL_H, 14);

        int alpha = Math.round(reveal * 255);
        int textColor = scaleAlpha(COLOR_TEXT, reveal);
        int dimColor = scaleAlpha(COLOR_TEXT_DIM, reveal);
        int accentColor = scaleAlpha(COLOR_ACCENT, reveal);

        // Title block.
        drawCentered(gui, titleFont, "OSU4k", panelX + PANEL_W / 2f, py + 30, accentColor);
        drawAccentGlow(gui, panelX + PANEL_W / 2f, py + 48, 90, 2);
        String subtitle = setTitle.isEmpty() ? I18n.tr("Mania Rhythm Game") : setTitle;
        drawCentered(gui, itemFont, subtitle, panelX + PANEL_W / 2f, py + 56, dimColor);

        // Action row.
        int btnY = py + 74;
        int openW = 128;
        int libW = 128;
        int keyW = 150;
        int gap = 10;
        int openX = panelX + 24;
        int libX = openX + openW + gap;
        int keyX = panelX + PANEL_W - 24 - keyW;
        boolean openHover = inRect(mouseX, mouseY, openX, btnY, openW, 32);
        boolean libHover = inRect(mouseX, mouseY, libX, btnY, libW, 32);
        boolean keyHover = inRect(mouseX, mouseY, keyX, btnY, keyW, 32);
        drawButton(gui, openX, btnY, openW, 32, I18n.tr("Open .osz"), openHover, true);
        drawButton(gui, libX, btnY, libW, 32, I18n.tr("Library"), libHover, true);
        drawButton(gui, keyX, btnY, keyW, 32, I18n.tr("Custom Keybinds"), keyHover, true);

        // Difficulty list header.
        int listHeaderY = btnY + 46;
        if (!maps.isEmpty()) {
            String difficultiesLabel = I18n.tr("DIFFICULTIES");
            drawStringCenteredY(gui, smallFont, difficultiesLabel, panelX + 32, listHeaderY, dimColor);
            String count = "(" + maps.size() + ")";
            float hw = textWidth(difficultiesLabel, 12.5f);
            drawStringCenteredY(gui, smallFont, count, panelX + 32 + hw + 6, listHeaderY, accentColor);
            geminiclient.gemini.customRenderer.cpu.CustomRectRenderer.drawRect(
                    gui, panelX + 30, listHeaderY + 8, PANEL_W - 60, 1, GLASS_OUTLINE_SOFT);
        }

        // Difficulty rows, scrolled and clipped to the list region: entries
        // beyond the visible window are hidden until they scroll into place.
        int listX = panelX + 24;
        int listW = PANEL_W - 48;
        int listTop = py + LIST_TOP;
        int listBottom = py + LIST_BOTTOM;
        int rows = visibleRows();
        gui.enableScissor(panelX + 16, listTop, panelX + PANEL_W - 16, listBottom);
        for (int i = scrollOffset; i < maps.size() && i < scrollOffset + rows; i++) {
            int rowY = listTop + (i - scrollOffset) * (ROW_H + ROW_GAP);
            if (rowY + ROW_H < listTop || rowY > listBottom) {
                continue;
            }
            drawDifficultyRow(gui, maps.get(i), listX, rowY, listW, mouseX, mouseY, reveal, i);
        }
        gui.disableScissor();

        // Scrollbar.
        if (maps.size() > rows) {
            int trackX = panelX + PANEL_W - 18;
            int trackH = LIST_BOTTOM - LIST_TOP;
            CustomRoundedRectRenderer.drawRoundedRect(gui, trackX, listTop, 3, trackH, 2, 0x3340495E);
            float thumbH = Math.max(24, trackH * rows / (float) maps.size());
            float thumbY = listTop + (trackH - thumbH) * scrollOffset / (float) Math.max(1, maps.size() - rows);
            CustomRoundedRectRenderer.drawRoundedRect(gui, trackX, Math.round(thumbY), 3, Math.round(thumbH), 2,
                    scaleAlpha(COLOR_ACCENT, reveal));
        }

        // Empty state.
        if (maps.isEmpty() && errorText == null) {
            drawCentered(gui, itemFont, I18n.tr("Click \"Open .osz\" to load a beatmap set"),
                    panelX + PANEL_W / 2f, py + PANEL_H / 2f + 20, dimColor);
        }

        // Error area.
        if (errorText != null) {
            int errY = py + PANEL_H - 84;
            CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, panelX + 24, errY - 8, PANEL_W - 48, 56, 8,
                    scaleAlpha(0x33FF6E6E, reveal), scaleAlpha(0x14FF6E6E, reveal));
            CustomRoundedRectRenderer.drawRoundedOutline(gui, panelX + 24, errY - 8, PANEL_W - 48, 56, 8,
                    scaleAlpha(0x66FF6E6E, reveal), 1);
            drawCentered(gui, itemFont, errorText, panelX + PANEL_W / 2f, errY + 20, scaleAlpha(COLOR_ERROR, reveal));
        }

        // Toast messages.
        if (now < messagesUntil) {
            for (int i = 0; i < messages.size(); i++) {
                drawCentered(gui, itemFont, messages.get(i), panelX + PANEL_W / 2f,
                        py + PANEL_H - 30 - i * 16, scaleAlpha(COLOR_SUCCESS, reveal));
            }
        }

        drawCentered(gui, smallFont, I18n.tr("Esc: back"), panelX + PANEL_W / 2f, py + PANEL_H - 12, dimColor);
    }

    private void drawDifficultyRow(GuiGraphicsExtractor gui, BeatmapData m, int x, int y, int w,
                                   int mouseX, int mouseY, float reveal, int index) {
        boolean hovered = inRect(mouseX, mouseY, x, y, w, ROW_H);
        int top = hovered ? 0x3A2C3A54 : 0x2A232D40;
        int bottom = hovered ? 0x28222A3C : 0x201B2230;
        CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, x, y, w, ROW_H, 7,
                scaleAlpha(top, reveal), scaleAlpha(bottom, reveal));
        CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, w, ROW_H, 7,
                scaleAlpha(hovered ? 0x88FFFFFF : GLASS_OUTLINE_SOFT, reveal), 1);

        // Lane-colour dot.
        int dotColor = Osu4k.laneColor(index);
        CustomRoundedRectRenderer.drawCircle(gui, x + 18, y + ROW_H / 2f, 10, scaleAlpha(dotColor, reveal));

        drawStringCenteredY(gui, itemFont, m.displayLabel(), x + 34, y + ROW_H / 2f, scaleAlpha(COLOR_TEXT, reveal));
        String notes = I18n.trf("%d notes", m.hitObjects().size());
        drawCentered(gui, smallFont, notes, x + w - 64, y + ROW_H / 2f, scaleAlpha(COLOR_TEXT_DIM, reveal));
        // Key-count badge ("4K", "6K", ...) between the label and the notes.
        drawStringCenteredY(gui, smallFont, m.keyCount() + "K", x + 34 + textWidth(m.displayLabel(), 12.5f) + 10,
                y + ROW_H / 2f, scaleAlpha(COLOR_ACCENT, reveal));
        if (hovered) {
            drawCentered(gui, itemFont, ">", x + w - 18, y + ROW_H / 2f, scaleAlpha(COLOR_ACCENT, reveal));
        }
    }

    // ---------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent mouse, boolean idk) {
        if (mouse.button() != 0) {
            return super.mouseClicked(mouse, idk);
        }
        int py = panelY + Math.round((1f - easeOutCubic(clamp01((System.currentTimeMillis() - openAtMs) / 400f))) * 14f);
        int btnY = py + 74;
        int openX = panelX + 24;
        int libX = openX + 128 + 10;
        int keyX = panelX + PANEL_W - 24 - 150;
        if (inRect(mouse.x(), mouse.y(), openX, btnY, 128, 32)) {
            openFileChooser();
            return true;
        }
        if (inRect(mouse.x(), mouse.y(), libX, btnY, 128, 32)) {
            openLibrary();
            return true;
        }
        if (inRect(mouse.x(), mouse.y(), keyX, btnY, 150, 32)) {
            openKeybinds();
            return true;
        }
        int listTop = py + LIST_TOP;
        int rows = visibleRows();
        for (int i = scrollOffset; i < maps.size() && i < scrollOffset + rows; i++) {
            int rowY = listTop + (i - scrollOffset) * (ROW_H + ROW_GAP);
            if (inRect(mouse.x(), mouse.y(), panelX + 24, rowY, PANEL_W - 48, ROW_H)) {
                startGame(maps.get(i));
                return true;
            }
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
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.isEscape()) {
            // Leaving the select screen exits the OSU4K flow and turns the
            // module off (parent is null when the module opened this screen).
            closeTo(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

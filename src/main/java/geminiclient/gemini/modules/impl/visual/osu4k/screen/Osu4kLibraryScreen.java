package geminiclient.gemini.modules.impl.visual.osu4k.screen;

import geminiclient.gemini.base.I18n;
import geminiclient.gemini.modules.impl.visual.osu4k.Osu4k;
import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kLibrary;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Beatmap library screen: browses every {@code .osz} the player has opened
 * (persisted in {@code osu4k_library.json}), opens one at a time, removes
 * entries, and bulk-imports multiple files at once.
 *
 * <p>Import paths: the native multi-file dialog ("Add .osz") or drag & drop
 * ({@link #onFilesDrop}). Clicking an entry loads it as the current beatmap
 * set and returns to the difficulty selection screen.</p>
 */
public final class Osu4kLibraryScreen extends Osu4kScreen {

    private static final int PANEL_W = 580;
    private static final int PANEL_H = 440;
    private static final int ROW_H = 44;
    private static final int ROW_GAP = 6;
    private static final int BTN_Y = 62;
    private static final int LIST_TOP = 112;
    private static final int LIST_BOTTOM = 372;

    private int panelX, panelY;
    private List<Osu4kLibrary.Entry> entries = new ArrayList<>();
    private int scrollOffset;
    private long openAtMs;
    private String toastText;
    private long toastUntil;

    public Osu4kLibraryScreen(Screen parent) {
        super(parent, "OSU4k Library");
    }

    @Override
    protected void init() {
        super.init();
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;
        openAtMs = System.currentTimeMillis();
        entries = Osu4kLibrary.load(this.minecraft.gameDirectory.toPath());
        clampScroll();
    }

    // ---------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------

    private void openFileChooser() {
        Thread t = new Thread(() -> {
            String selected = TinyFileDialogs.tinyfd_openFileDialog(
                    I18n.tr("Add .osz beatmaps"),
                    System.getProperty("user.home"),
                    null,
                    "osu! beatmap set (*.osz)",
                    true); // allow multiple selection
            if (selected == null || selected.isBlank()) {
                return;
            }
            List<Path> paths = new ArrayList<>();
            for (String part : selected.split("[;\\r\\n]+")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    paths.add(Path.of(trimmed));
                }
            }
            addPaths(paths);
        }, "OSU4K-LibraryAdd");
        t.setDaemon(true);
        t.start();
    }

    /** Adds every valid {@code .osz} from {@code paths} and persists. */
    private int addPaths(List<Path> paths) {
        int added = 0;
        for (Path p : paths) {
            if (!Files.isRegularFile(p)) {
                continue;
            }
            if (!p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".osz")) {
                continue;
            }
            entries = Osu4kLibrary.withAdded(entries, p);
            added++;
        }
        if (added > 0) {
            Osu4kLibrary.save(this.minecraft.gameDirectory.toPath(), entries);
            clampScroll();
            toast(added == 1 ? I18n.tr("Added 1 beatmap set") : I18n.trf("Added %d beatmap sets", added));
        }
        return added;
    }

    private void openEntry(Osu4kLibrary.Entry entry) {
        if (!entry.exists()) {
            toast(I18n.trf("File not found: %s", entry.displayName()));
            return;
        }
        String error = Osu4k.openBeatmapSet(entry.path());
        if (error != null) {
            toast(error);
            return;
        }
        this.minecraft.gui.setScreen(new Osu4kSelectScreen(parent));
    }

    private void removeEntry(Osu4kLibrary.Entry entry) {
        entries = Osu4kLibrary.withRemoved(entries, entry.path());
        Osu4kLibrary.save(this.minecraft.gameDirectory.toPath(), entries);
        clampScroll();
        toast(I18n.trf("Removed \"%s\"", entry.displayName()));
    }

    private void clearAll() {
        entries = List.of();
        Osu4kLibrary.save(this.minecraft.gameDirectory.toPath(), entries);
        scrollOffset = 0;
        toast(I18n.tr("Library cleared"));
    }

    private void toast(String text) {
        toastText = text;
        toastUntil = System.currentTimeMillis() + 2800;
    }

    // ---------------------------------------------------------------------
    // Layout helpers
    // ---------------------------------------------------------------------

    private int visibleRows() {
        int available = LIST_BOTTOM - LIST_TOP;
        return Math.max(1, available / (ROW_H + ROW_GAP));
    }

    private void clampScroll() {
        int max = Math.max(0, entries.size() - visibleRows());
        scrollOffset = Math.max(0, Math.min(scrollOffset, max));
    }

    private int rowY(int index) {
        return panelY + LIST_TOP + (index - scrollOffset) * (ROW_H + ROW_GAP);
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

        drawCentered(gui, titleFont, I18n.tr("Beatmap Library"), panelX + PANEL_W / 2f, py + 30, textColor);
        drawAccentGlow(gui, panelX + PANEL_W / 2f, py + 48, 130, 2);
        String stats = entries.isEmpty() ? I18n.tr("no beatmaps yet")
                : entries.size() == 1 ? I18n.tr("1 beatmap set")
                : I18n.trf("%d beatmap sets", entries.size());
        drawCentered(gui, smallFont, stats, panelX + PANEL_W / 2f, py + 58, dimColor);

        // Action buttons.
        boolean addHover = inRect(mouseX, mouseY, panelX + 24, py + BTN_Y, 130, 32);
        boolean clearHover = inRect(mouseX, mouseY, panelX + 162, py + BTN_Y, 110, 32);
        boolean backHover = inRect(mouseX, mouseY, panelX + PANEL_W - 24 - 110, py + BTN_Y, 110, 32);
        drawButton(gui, panelX + 24, py + BTN_Y, 130, 32, I18n.tr("Add .osz"), addHover, true);
        drawButton(gui, panelX + 162, py + BTN_Y, 110, 32, I18n.tr("Clear All"), clearHover, !entries.isEmpty());
        drawButton(gui, panelX + PANEL_W - 24 - 110, py + BTN_Y, 110, 32, I18n.tr("Back"), backHover, true);

        // Entry list, clipped to the list region so rows that scroll past the
        // top/bottom edge are hidden until they scroll back into view.
        int listX = panelX + 24;
        int listW = PANEL_W - 48;
        int rows = visibleRows();
        int pyOffset = py - panelY; // keep the same relative layout as hover/hit tests
        int listTop = panelY + LIST_TOP + pyOffset;
        int listBottom = panelY + LIST_BOTTOM + pyOffset;
        gui.enableScissor(panelX + 16, listTop, panelX + PANEL_W - 16, listBottom);
        for (int i = scrollOffset; i < entries.size() && i < scrollOffset + rows; i++) {
            int y = panelY + LIST_TOP + (i - scrollOffset) * (ROW_H + ROW_GAP) + pyOffset;
            if (y + ROW_H < listTop || y > listBottom) {
                continue;
            }
            drawEntryRow(gui, entries.get(i), listX, y, listW, mouseX, mouseY, reveal);
        }
        gui.disableScissor();

        // Empty state.
        if (entries.isEmpty()) {
            drawCentered(gui, itemFont, I18n.tr("No beatmaps saved yet"), panelX + PANEL_W / 2f, panelY + (LIST_TOP + LIST_BOTTOM) / 2f + pyOffset, dimColor);
            drawCentered(gui, smallFont, I18n.tr("Click \"Add .osz\" or drop .osz files here"), panelX + PANEL_W / 2f, panelY + (LIST_TOP + LIST_BOTTOM) / 2f + 18 + pyOffset, dimColor);
        }

        // Scrollbar.
        if (entries.size() > rows) {
            int trackX = panelX + PANEL_W - 18;
            int trackTop = panelY + LIST_TOP + pyOffset;
            int trackH = LIST_BOTTOM - LIST_TOP;
            CustomRoundedRectRenderer.drawRoundedRect(gui, trackX, trackTop, 3, trackH, 2, 0x3340495E);
            float thumbH = Math.max(24, trackH * rows / (float) entries.size());
            float thumbY = trackTop + (trackH - thumbH) * scrollOffset / (float) Math.max(1, entries.size() - rows);
            CustomRoundedRectRenderer.drawRoundedRect(gui, trackX, Math.round(thumbY), 3, Math.round(thumbH), 2, scaleAlpha(COLOR_ACCENT, reveal));
        }

        // Toast.
        if (toastText != null && now < toastUntil) {
            drawCentered(gui, itemFont, toastText, panelX + PANEL_W / 2f, panelY + LIST_BOTTOM + 24 + pyOffset, scaleAlpha(COLOR_SUCCESS, reveal));
        }

        drawCentered(gui, smallFont, I18n.tr("Drop .osz files here to add   •   Esc: back"), panelX + PANEL_W / 2f, py + PANEL_H - 14, dimColor);
    }

    private void drawEntryRow(GuiGraphicsExtractor gui, Osu4kLibrary.Entry entry, int x, int y, int w,
                              int mouseX, int mouseY, float reveal) {
        boolean exists = entry.exists();
        boolean hovered = inRect(mouseX, mouseY, x, y, w, ROW_H);
        boolean removeHover = inRect(mouseX, mouseY, x + w - 30, y + (ROW_H - 22) / 2, 22, 22);
        boolean current = Osu4k.currentOszPath != null
                && Osu4k.currentOszPath.toAbsolutePath().normalize().equals(entry.path().toAbsolutePath().normalize());

        int top = hovered ? 0x3A2C3A54 : 0x2A232D40;
        int bottom = hovered ? 0x28222A3C : 0x201B2230;
        CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, x, y, w, ROW_H, 7,
                scaleAlpha(top, reveal), scaleAlpha(bottom, reveal));
        CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, w, ROW_H, 7,
                scaleAlpha(current ? COLOR_ACCENT : (hovered ? 0x88FFFFFF : GLASS_OUTLINE_SOFT), reveal),
                current ? 2 : 1);

        // Leading accent dot (dim for missing files).
        int dotColor = exists ? (current ? COLOR_ACCENT : 0xFF8FA3C8) : COLOR_TEXT_DIM;
        CustomRoundedRectRenderer.drawCircle(gui, x + 16, y + ROW_H / 2f, 8, scaleAlpha(dotColor, reveal));

        int nameColor = exists ? COLOR_TEXT : COLOR_TEXT_DIM;
        String name = truncate(entry.displayName(), 34);
        drawStringCenteredY(gui, itemFont, name, x + 30, y + (ROW_H / 2f) - 7, scaleAlpha(nameColor, reveal));
        String path = truncate(entry.path().toString(), 52);
        drawStringCenteredY(gui, smallFont, path, x + 30, y + (ROW_H / 2f) + 9, scaleAlpha(COLOR_TEXT_DIM, reveal));

        // Right side: remove button, missing tag, current marker.
        int cx = x + w - 18;
        if (removeHover) {
            CustomRoundedRectRenderer.drawCircle(gui, cx, y + ROW_H / 2f, 18, scaleAlpha(0x33FF6E6E, reveal));
        }
        drawCentered(gui, smallFont, "\u2715", cx, y + ROW_H / 2f, scaleAlpha(0xCCFF6E6E, reveal));
        if (!exists) {
            drawCentered(gui, smallFont, I18n.tr("missing"), x + w - 62, y + ROW_H / 2f, scaleAlpha(COLOR_ERROR, reveal));
        } else if (current) {
            drawCentered(gui, smallFont, I18n.tr("loaded"), x + w - 62, y + ROW_H / 2f, scaleAlpha(COLOR_ACCENT, reveal));
        }
    }

    private static String truncate(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars - 1) + "\u2026";
    }

    // ---------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean idk) {
        if (mouse.button() != 0) {
            return super.mouseClicked(mouse, idk);
        }
        double mx = mouse.x();
        double my = mouse.y();
        int py = panelY + Math.round((1f - easeOutCubic(clamp01((System.currentTimeMillis() - openAtMs) / 380f))) * 12f);

        if (inRect(mx, my, panelX + 24, py + BTN_Y, 130, 32)) {
            openFileChooser();
            return true;
        }
        if (inRect(mx, my, panelX + 162, py + BTN_Y, 110, 32) && !entries.isEmpty()) {
            clearAll();
            return true;
        }
        if (inRect(mx, my, panelX + PANEL_W - 24 - 110, py + BTN_Y, 110, 32)) {
            this.minecraft.gui.setScreen(parent);
            return true;
        }

        // Entry rows: remove button has priority over opening.
        int listX = panelX + 24;
        int listW = PANEL_W - 48;
        int rows = visibleRows();
        int pyOffset = py - panelY;
        for (int i = scrollOffset; i < entries.size() && i < scrollOffset + rows; i++) {
            int y = panelY + LIST_TOP + (i - scrollOffset) * (ROW_H + ROW_GAP) + pyOffset;
            if (inRect(mx, my, listX + listW - 30, y + (ROW_H - 22) / 2, 22, 22)) {
                removeEntry(entries.get(i));
                return true;
            }
            if (inRect(mx, my, listX, y, listW, ROW_H)) {
                openEntry(entries.get(i));
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
    public void onFilesDrop(List<Path> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        addPaths(files);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            this.minecraft.gui.setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------------
    // Easing
    // ---------------------------------------------------------------------

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }
}

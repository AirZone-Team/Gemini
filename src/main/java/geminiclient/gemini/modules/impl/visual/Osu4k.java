package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.I18n;
import geminiclient.gemini.base.MinecraftInstance;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.visual.osu4k.audio.FfmpegAudioPlayer;
import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kGameState;
import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kKeyConfig;
import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kLibrary;
import geminiclient.gemini.modules.impl.visual.osu4k.model.OszArchive;
import geminiclient.gemini.modules.impl.visual.osu4k.screen.Osu4kScreen;
import geminiclient.gemini.modules.impl.visual.osu4k.screen.Osu4kSelectScreen;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Function;

/**
 * OSU4k — an osu!mania style rhythm game inside the client (1K-10K lanes).
 *
 * <p>Enabling the module opens the beatmap selection screen; from there the
 * player can open a {@code .osz}, pick a difficulty, rebind keys and play.
 * Each beatmap's key count (read from the file) drives the number of lanes.
 * The module values (offset, volume, scroll speed, debug overlay) are
 * persisted by the regular module config system.</p>
 */
public class Osu4k extends Module implements MinecraftInstance {

    /** Shared, module-wide audio player. */
    public static final FfmpegAudioPlayer AUDIO = new FfmpegAudioPlayer();

    // Keybinds shared across the screens, one set per lane count (1K-10K);
    // loaded once at init, persisted to osu4k.json on change/disable.
    private static final java.util.Map<Integer, int[]> KEY_SETS = new java.util.HashMap<>();

    /** Currently open beatmap set, owned by the select screen. */
    public static OszArchive currentArchive;
    public static Path currentOszPath;

    /** Default per-lane note colours (ARGB); the "Default" lane colour mode.
     *  The first four hues keep the original 4K look; entries cover 10K. */
    public static final int[] LANE_COLORS = {
            0xFF5FA8FF, 0xFF4FC3F7, 0xFFFFD54F, 0xFFFF8A65,
            0xFF7EE081, 0xFFB388FF, 0xFFFF80AB, 0xFF80DEEA,
            0xFFFFB74D, 0xFFA1887F
    };

    /** Single colour used for every lane in the "Same" lane colour mode. */
    private static final int SAME_LANE_COLOR = 0xFFE6E9F2;

    /** Random palette, generated once per client launch so runs stay stable. */
    private static final int[] RANDOM_LANE_COLORS = randomPalette();

    private static int[] randomPalette() {
        java.util.Random rnd = new java.util.Random(System.nanoTime());
        int[] out = new int[LANE_COLORS.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = 0xFF000000 | hsvToRgb(rnd.nextFloat(), 0.8f, 1f);
        }
        return out;
    }

    /** HSV -> 0xRRGGBB, kept dependency-free (AWT is unavailable here). */
    private static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1f - Math.abs((h * 6f) % 2f - 1f));
        float m = v - c;
        float r = 0, g = 0, b = 0;
        int sext = (int) (h * 6f) % 6;
        switch (sext) {
            case 0 -> { r = c; g = x; }
            case 1 -> { r = x; g = c; }
            case 2 -> { g = c; b = x; }
            case 3 -> { g = x; b = c; }
            case 4 -> { r = x; b = c; }
            default -> { r = c; b = x; }
        }
        return (Math.round((r + m) * 255) << 16)
                | (Math.round((g + m) * 255) << 8)
                | Math.round((b + m) * 255);
    }

    private final IntValue offsetMs = new IntValue("Offset", 0, -100, 100);
    private final FloatValue volume = new FloatValue("Volume", 0.8f, 0f, 1f);
    private final FloatValue scrollSpeed = new FloatValue("Scroll Speed", 2.0f, 0.5f, 4f);
    private final BoolValue debug = new BoolValue("Debug", false);
    private final BoolValue hitEffects = new BoolValue("Hit Effects", true);
    private final BoolValue laneHighlight = new BoolValue("Lane Highlight", true);
    private final ListValue judgement = new ListValue("Judgement", "Moderate",
            new String[]{"Very Lenient", "Lenient", "Moderate", "Strict", "Very Strict"});
    private final ListValue laneColors = new ListValue("Lane Colors", "Default",
            new String[]{"Default", "Same", "Random"});

    public Osu4k() {
        super("OSU4k", ModuleEnum.Visual);
        this.key = 0; // unbound by default; bind in ClickGUI
        addValue(offsetMs, volume, scrollSpeed, debug, judgement, hitEffects, laneHighlight, laneColors);
        KEY_SETS.putAll(Osu4kKeyConfig.loadKeySets(mc.gameDirectory.toPath()));
    }

    public int getOffsetMs() {
        return offsetMs.getValue();
    }

    public float getVolume() {
        return volume.getValue();
    }

    public float getScrollSpeed() {
        return scrollSpeed.getValue();
    }

    public boolean isDebug() {
        return debug.enabled;
    }

    // ---------------------------------------------------------------------
    // Static accessors (used by the screens)
    // ---------------------------------------------------------------------

    private static Osu4k module() {
        return Gemini.moduleManager == null ? null : Gemini.moduleManager.getModule(Osu4k.class);
    }

    /** Reads a module value, falling back when the module is not available. */
    private static <T> T cfg(Function<Osu4k, T> getter, T fallback) {
        Osu4k m = module();
        return m == null ? fallback : getter.apply(m);
    }

    /** Selected judgement-window preset, used when a game state is created. */
    public static Osu4kGameState.Preset judgementPreset() {
        Osu4k m = module();
        if (m == null) {
            return Osu4kGameState.Preset.MODERATE;
        }
        try {
            String name = m.judgement.get().toUpperCase(Locale.ROOT).replace(' ', '_');
            return Osu4kGameState.Preset.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Osu4kGameState.Preset.MODERATE;
        }
    }

    /** Effective hit-timing offset in ms. */
    public static int offset() {
        return cfg(Osu4k::getOffsetMs, 0);
    }

    /** Effective playback volume (0..1). */
    public static float volume() {
        return cfg(Osu4k::getVolume, 0.8f);
    }

    /** Effective note scroll speed in px per ms. */
    public static float scrollSpeed() {
        return cfg(Osu4k::getScrollSpeed, 2.0f);
    }

    /** Whether the debug overlay should render. */
    public static boolean debug() {
        return cfg(Osu4k::isDebug, false);
    }

    /** Whether judgement-line hit effects (rings, hold pulse) should render. */
    public static boolean hitEffects() {
        return cfg(m -> m.hitEffects.enabled, true);
    }

    /** Whether pressing a lane key should highlight that lane (press glow). */
    public static boolean laneHighlight() {
        return cfg(m -> m.laneHighlight.enabled, true);
    }

    /**
     * Effective colour of a lane's notes, honouring the Lane Colors mode:
     * {@code Default} keeps the fixed lane palette, {@code Same} paints
     * every lane in one colour and {@code Random} uses a per-launch palette.
     */
    public static int laneColor(int column) {
        String mode = cfg(m -> m.laneColors.get(), "Default");
        int idx = Math.floorMod(column, LANE_COLORS.length);
        if ("Same".equals(mode)) {
            return SAME_LANE_COLOR;
        }
        if ("Random".equals(mode)) {
            return RANDOM_LANE_COLORS[idx];
        }
        return LANE_COLORS[idx];
    }

    /**
     * Effective lane keys for a key count, falling back to that count's
     * default layout when it has no stored set. Callers must not mutate the
     * returned array (clone it first, as the keybind screen does).
     */
    public static int[] keysFor(int columns) {
        int c = Math.max(Osu4kKeyConfig.MIN_KEYS, Math.min(Osu4kKeyConfig.MAX_KEYS, columns));
        int[] keys = KEY_SETS.get(c);
        return keys != null ? keys : Osu4kKeyConfig.defaultKeys(c);
    }

    /** Replaces the key set for one lane count and persists every set. */
    public static void setKeys(int columns, int[] keys) {
        int c = Math.max(Osu4kKeyConfig.MIN_KEYS, Math.min(Osu4kKeyConfig.MAX_KEYS, columns));
        if (keys == null || keys.length != c) {
            return;
        }
        KEY_SETS.put(c, keys.clone());
        Osu4kKeyConfig.saveKeySets(mc.gameDirectory.toPath(), KEY_SETS);
    }

    /**
     * Opens a {@code .osz} and makes it the current beatmap set, validating
     * that it contains at least one playable 4K map. The set is persisted into
     * the library for quick re-opening.
     *
     * @return {@code null} on success, or a user-facing error message
     */
    public static String openBeatmapSet(Path path) {
        if (!Files.isRegularFile(path)) {
            return I18n.trf("File not found: %s", path.getFileName());
        }
        try {
            if (currentArchive != null) {
                currentArchive.close();
            }
            OszArchive archive = OszArchive.open(path);
            archive.requirePlayableSet();
            currentArchive = archive;
            currentOszPath = path;
            Osu4kLibrary.addEntry(mc.gameDirectory.toPath(), path);
            return null;
        } catch (OszArchive.OszError e) {
            return e.getMessage();
        } catch (Exception e) {
            return I18n.trf("Failed to read .osz: %s", e.getMessage());
        }
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        AUDIO.setVolume(getVolume());
        if (mc.gui.screen() == null) {
            mc.gui.setScreen(new Osu4kSelectScreen(null));
        }
    }

    /**
     * Disables the module if it is currently enabled. Used by the screens when
     * the OSU4K flow is closed (Esc on the select screen), so the module never
     * stays on in the background with no UI open.
     */
    public static void requestDisable() {
        Osu4k m = module();
        if (m != null && m.enabled) {
            m.setEnabled(false);
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        // Return to the game screen if one of the OSU4K screens is open. This
        // must run on the render thread; the heavier teardown below does not.
        if (mc.gui.screen() instanceof Osu4kScreen) {
            mc.gui.setScreen(null);
        }
        // Teardown (audio decode-thread join, FFmpeg release, key-set + config
        // JSON writes) runs on a background thread so toggling the module off
        // never stalls a frame. Skipped when the module was re-enabled before
        // it ran, so a quick re-toggle cannot kill the new session's audio.
        OszArchive archive = currentArchive;
        currentArchive = null;
        currentOszPath = null;
        Thread cleanup = new Thread(() -> {
            if (enabled) {
                return;
            }
            Osu4kKeyConfig.saveKeySets(mc.gameDirectory.toPath(), KEY_SETS);
            AUDIO.unload();
            if (archive != null) {
                try {
                    archive.close();
                } catch (Exception ignored) {
                    // best effort
                }
            }
            Gemini.fileSystem.saveConfig();
        }, "OSU4K-Disable");
        cleanup.setDaemon(true);
        cleanup.start();
    }
}

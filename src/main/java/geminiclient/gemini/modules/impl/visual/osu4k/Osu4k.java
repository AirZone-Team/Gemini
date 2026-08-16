package geminiclient.gemini.modules.impl.visual.osu4k;

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
import geminiclient.gemini.modules.impl.visual.osu4k.screen.Osu4kSelectScreen;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * OSU4k — a 4K (osu!mania style) rhythm game inside the client.
 *
 * <p>Enabling the module opens the beatmap selection screen; from there the
 * player can open a {@code .osz}, pick a difficulty, rebind keys and play.
 * The module values (offset, volume, scroll speed, debug overlay) are
 * persisted by the regular module config system.</p>
 */
public class Osu4k extends Module implements MinecraftInstance {

    /** Shared, module-wide audio player. */
    public static final FfmpegAudioPlayer AUDIO = new FfmpegAudioPlayer();

    // Keybinds shared across the screens; loaded once at init.
    public static int[] KEYS = Osu4kKeyConfig.DEFAULT_KEYS.clone();

    /** Currently open beatmap set, owned by the select screen. */
    public static OszArchive currentArchive;
    public static Path currentOszPath;

    /** Default per-lane note colours (ARGB); the "Default" lane colour mode. */
    public static final int[] LANE_COLORS = {
            0xFF5FA8FF, 0xFF4FC3F7, 0xFFFFD54F, 0xFFFF8A65
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
        Osu4k.KEYS = Osu4kKeyConfig.loadKeys(mc.gameDirectory.toPath());
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

    /** Effective hit-timing offset in ms. */
    public static int offset() {
        Osu4k m = module();
        return m == null ? 0 : m.getOffsetMs();
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

    /** Effective playback volume (0..1). */
    public static float volume() {
        Osu4k m = module();
        return m == null ? 0.8f : m.getVolume();
    }

    /** Effective note scroll speed in px per ms. */
    public static float scrollSpeed() {
        Osu4k m = module();
        return m == null ? 2.0f : m.getScrollSpeed();
    }

    /** Whether the debug overlay should render. */
    public static boolean debug() {
        Osu4k m = module();
        return m != null && m.isDebug();
    }

    /** Whether judgement-line hit effects (rings, hold pulse) should render. */
    public static boolean hitEffects() {
        Osu4k m = module();
        return m == null || m.hitEffects.enabled;
    }

    /** Whether pressing a lane key should highlight that lane (press glow). */
    public static boolean laneHighlight() {
        Osu4k m = module();
        return m == null || m.laneHighlight.enabled;
    }

    /**
     * Effective colour of a lane's notes, honouring the Lane Colors mode:
     * {@code Default} keeps the fixed four-lane palette, {@code Same} paints
     * every lane in one colour and {@code Random} uses a per-launch palette.
     */
    public static int laneColor(int column) {
        Osu4k m = module();
        String mode = m == null ? "Default" : m.laneColors.get();
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

    @Override
    public void onDisabled() {
        super.onDisabled();
        Osu4kKeyConfig.saveKeys(mc.gameDirectory.toPath(), Osu4k.KEYS);
        AUDIO.unload();
        if (currentArchive != null) {
            try {
                currentArchive.close();
            } catch (Exception ignored) {
                // best effort
            }
            currentArchive = null;
            currentOszPath = null;
        }
        // Return to the game screen if one of the OSU4K screens is open.
        if (mc.gui.screen() instanceof geminiclient.gemini.modules.impl.visual.osu4k.screen.Osu4kScreen) {
            mc.gui.setScreen(null);
        }
        Gemini.fileSystem.saveConfig();
    }
}

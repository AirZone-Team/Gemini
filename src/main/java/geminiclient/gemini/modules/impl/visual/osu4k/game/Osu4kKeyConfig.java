package geminiclient.gemini.modules.impl.visual.osu4k.game;

import com.mojang.blaze3d.platform.InputConstants;
import geminiclient.gemini.modules.impl.visual.osu4k.model.BeatmapData;
import geminiclient.gemini.utils.KeyUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persistent per-key-count lane keybinds (and nothing else — module values
 * such as offset/volume/scroll speed live in the regular module config).
 *
 * <p>Stored as {@code <gameDir>/gemini/configs/osu4k.json}, written atomically.
 * The file holds one binding set per key count (1K-10K) plus the key-code domain
 * marker (see {@link KeyUtils} for the domain itself):</p>
 * <pre>
 * {
 *   "keyDomain": "sdl",
 *   "keySets": {
 *     "4": [7, 9, 13, 14],
 *     "6": [22, 7, 9, 13, 14, 15]
 *   }
 * }
 * </pre>
 * <p>A legacy {@code {"keys": [...]}} file (the old single 4K array) is
 * migrated to the {@code "4"} set on load. A file without {@code keyDomain}
 * predates the Minecraft 26.3 GLFW→SDL switch, so its codes are translated once
 * and rewritten with the marker. Counts without a stored set fall back to
 * {@link #defaultKeys(int)}. A corrupt or missing file silently falls back to
 * the defaults.</p>
 */
public final class Osu4kKeyConfig {

    private static final Logger LOGGER = Logger.getLogger(Osu4kKeyConfig.class.getName());

    /** 键码域标记；缺省表示文件写于 GLFW 时代。 */
    private static final String JSON_KEY_DOMAIN = "keyDomain";
    private static final String KEY_DOMAIN_SDL = "sdl";

    /** Smallest configurable lane count (1K). */
    public static final int MIN_KEYS = BeatmapData.MIN_KEY_COUNT;
    /** Largest configurable lane count (10K). */
    public static final int MAX_KEYS = BeatmapData.MAX_KEY_COUNT;

    private Osu4kKeyConfig() {}

    /**
     * Default key layout for a given lane count, following the classic mania
     * spreads (4K keeps D / F / J / K). No SPACE anywhere, so the pause key
     * never collides with a lane.
     */
    public static int[] defaultKeys(int columns) {
        return switch (columns) {
            case 1 -> new int[]{InputConstants.KEY_D};
            case 2 -> new int[]{InputConstants.KEY_D, InputConstants.KEY_J};
            case 3 -> new int[]{InputConstants.KEY_D, InputConstants.KEY_F, InputConstants.KEY_J};
            case 5 -> new int[]{InputConstants.KEY_S, InputConstants.KEY_D, InputConstants.KEY_F,
                    InputConstants.KEY_J, InputConstants.KEY_K};
            case 6 -> new int[]{InputConstants.KEY_S, InputConstants.KEY_D, InputConstants.KEY_F,
                    InputConstants.KEY_J, InputConstants.KEY_K, InputConstants.KEY_L};
            case 7 -> new int[]{InputConstants.KEY_S, InputConstants.KEY_D, InputConstants.KEY_F,
                    InputConstants.KEY_G, InputConstants.KEY_J, InputConstants.KEY_K, InputConstants.KEY_L};
            case 8 -> new int[]{InputConstants.KEY_A, InputConstants.KEY_S, InputConstants.KEY_D, InputConstants.KEY_F,
                    InputConstants.KEY_J, InputConstants.KEY_K, InputConstants.KEY_L, InputConstants.KEY_SEMICOLON};
            case 9 -> new int[]{InputConstants.KEY_A, InputConstants.KEY_S, InputConstants.KEY_D, InputConstants.KEY_F,
                    InputConstants.KEY_G, InputConstants.KEY_J, InputConstants.KEY_K, InputConstants.KEY_L, InputConstants.KEY_SEMICOLON};
            case 10 -> new int[]{InputConstants.KEY_A, InputConstants.KEY_S, InputConstants.KEY_D, InputConstants.KEY_F,
                    InputConstants.KEY_G, InputConstants.KEY_H, InputConstants.KEY_J, InputConstants.KEY_K,
                    InputConstants.KEY_L, InputConstants.KEY_SEMICOLON};
            default -> new int[]{InputConstants.KEY_D, InputConstants.KEY_F, InputConstants.KEY_J, InputConstants.KEY_K};
        };
    }

    public static Path configFile(Path gameDirectory) {
        return gameDirectory.resolve("gemini").resolve("configs").resolve("osu4k.json");
    }

    /**
     * Loads every stored key set, keyed by lane count. Missing or out-of-range
     * counts are absent from the map (callers fall back to {@link #defaultKeys}).
     */
    public static Map<Integer, int[]> loadKeySets(Path gameDirectory) {
        Map<Integer, int[]> sets = new HashMap<>();
        boolean needsMigration = false;
        Path file = configFile(gameDirectory);
        if (!Files.isRegularFile(file)) {
            return sets;
        }
        try (InputStream in = Files.newInputStream(file)) {
            JSONObject root = new JSONObject(new JSONTokener(in));
            needsMigration = !KEY_DOMAIN_SDL.equals(root.optString(JSON_KEY_DOMAIN, ""));
            JSONObject setsObj = root.optJSONObject("keySets");
            if (setsObj != null) {
                for (String key : setsObj.keySet()) {
                    try {
                        int columns = Integer.parseInt(key);
                        if (columns < MIN_KEYS || columns > MAX_KEYS) {
                            continue;
                        }
                        JSONArray arr = setsObj.optJSONArray(key);
                        if (arr == null) {
                            continue;
                        }
                        sets.put(columns, readKeys(arr, defaultKeys(columns)));
                    } catch (NumberFormatException ignored) {
                        // non-numeric key set id
                    }
                }
            } else {
                // Legacy single-array format: migrate {"keys": [...]} to the 4K set.
                JSONArray arr = root.optJSONArray("keys");
                if (arr != null) {
                    sets.put(4, readKeys(arr, defaultKeys(4)));
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to load OSU4K keybinds, using defaults", e);
        }

        if (needsMigration && !sets.isEmpty()) {
            // 文件写于 GLFW 时代：翻译一次键码，并连同域标记重写，保证只迁移一次
            sets.replaceAll((columns, keys) -> migrateFromGlfw(keys));
            saveKeySets(gameDirectory, sets);
        }
        return sets;
    }

    /** 把一组 GLFW 域键码换算成当前的 SDL 域（鼠标码/未识别值原样保留）。 */
    private static int[] migrateFromGlfw(int[] keys) {
        int[] migrated = keys.clone();
        for (int i = 0; i < migrated.length; i++) {
            if (migrated[i] > 0) {
                migrated[i] = KeyUtils.migrateLegacyGlfwKey(migrated[i]);
            }
        }
        return migrated;
    }

    /** Saves every key set (only counts with a correctly sized array). */
    public static void saveKeySets(Path gameDirectory, Map<Integer, int[]> sets) {
        if (sets == null) {
            return;
        }
        JSONObject setsObj = new JSONObject();
        for (Map.Entry<Integer, int[]> e : sets.entrySet()) {
            int columns = e.getKey();
            int[] keys = e.getValue();
            if (columns < MIN_KEYS || columns > MAX_KEYS || keys == null || keys.length != columns) {
                continue;
            }
            setsObj.put(String.valueOf(columns), new JSONArray(keys));
        }
        JSONObject root = new JSONObject();
        root.put(JSON_KEY_DOMAIN, KEY_DOMAIN_SDL);
        root.put("keySets", setsObj);
        Path file = configFile(gameDirectory);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = Files.createTempFile(file.getParent(), "osu4k", ".tmp");
            Files.writeString(tmp, root.toString(4), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save OSU4K keybinds", e);
        }
    }

    /** Merges the stored array over the defaults (unknown entries keep defaults). */
    private static int[] readKeys(JSONArray arr, int[] defaults) {
        int[] keys = defaults.clone();
        for (int i = 0; i < keys.length && i < arr.length(); i++) {
            int key = arr.optInt(i, keys[i]);
            if (key > 0) {
                keys[i] = key;
            }
        }
        return keys;
    }
}

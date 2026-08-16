package geminiclient.gemini.modules.impl.visual.osu4k.game;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persistent storage for the four lane keybinds (and nothing else — module
 * values such as offset/volume/scroll speed live in the regular module config).
 *
 * <p>Stored as {@code <gameDir>/gemini/configs/osu4k.json}, written atomically.
 * Defaults follow the classic mania layout D / F / J / K. A corrupt or missing
 * file silently falls back to the defaults.</p>
 */
public final class Osu4kKeyConfig {

    private static final Logger LOGGER = Logger.getLogger(Osu4kKeyConfig.class.getName());

    public static final int COLUMNS = 4;
    public static final int[] DEFAULT_KEYS = {GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K};

    private Osu4kKeyConfig() {}

    public static Path configFile(Path gameDirectory) {
        return gameDirectory.resolve("gemini").resolve("configs").resolve("osu4k.json");
    }

    public static int[] loadKeys(Path gameDirectory) {
        int[] keys = DEFAULT_KEYS.clone();
        Path file = configFile(gameDirectory);
        if (!Files.isRegularFile(file)) {
            return keys;
        }
        try (InputStream in = Files.newInputStream(file)) {
            JSONObject root = new JSONObject(new JSONTokener(in));
            JSONArray arr = root.optJSONArray("keys");
            if (arr != null) {
                for (int i = 0; i < COLUMNS && i < arr.length(); i++) {
                    int key = arr.optInt(i, keys[i]);
                    if (key > 0) {
                        keys[i] = key;
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to load OSU4K keybinds, using defaults", e);
        }
        return keys;
    }

    public static void saveKeys(Path gameDirectory, int[] keys) {
        if (keys == null || keys.length < COLUMNS) {
            return;
        }
        JSONObject root = new JSONObject();
        root.put("keys", new JSONArray(Arrays.copyOf(keys, COLUMNS)));
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
}

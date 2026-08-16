package geminiclient.gemini.modules.impl.visual.osu4k.game;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persistent record of the {@code .osz} beatmap sets the player has opened.
 *
 * <p>Stored as {@code <gameDir>/gemini/configs/osu4k_library.json} (atomic
 * write, same pattern as {@link Osu4kKeyConfig}). Entries keep their absolute
 * path and are ordered most-recently-opened first; duplicates are merged and
 * the list is capped at {@value #MAX_ENTRIES}.</p>
 */
public final class Osu4kLibrary {

    private static final Logger LOGGER = Logger.getLogger(Osu4kLibrary.class.getName());

    public static final int MAX_ENTRIES = 64;

    /** One saved beatmap set. */
    public record Entry(Path path, String displayName, long addedAtMs) {
        public boolean exists() {
            return Files.isRegularFile(path);
        }
    }

    private Osu4kLibrary() {}

    public static Path configFile(Path gameDirectory) {
        return gameDirectory.resolve("gemini").resolve("configs").resolve("osu4k_library.json");
    }

    /** Reads the saved library; missing/corrupt files yield an empty list. */
    public static List<Entry> load(Path gameDirectory) {
        List<Entry> entries = new ArrayList<>();
        Path file = configFile(gameDirectory);
        if (!Files.isRegularFile(file)) {
            return entries;
        }
        try (InputStream in = Files.newInputStream(file)) {
            JSONObject root = new JSONObject(new JSONTokener(in));
            JSONArray arr = root.optJSONArray("oszFiles");
            if (arr != null) {
                for (int i = 0; i < arr.length() && entries.size() < MAX_ENTRIES; i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) {
                        continue;
                    }
                    String pathStr = o.optString("path", "").trim();
                    if (pathStr.isEmpty()) {
                        continue;
                    }
                    Path path = Path.of(pathStr);
                    String name = o.optString("name", "").trim();
                    if (name.isEmpty()) {
                        name = displayName(path);
                    }
                    entries.add(new Entry(path, name, o.optLong("addedAt", 0)));
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to load OSU4K library, starting empty", e);
        }
        return entries;
    }

    /** Writes the library atomically. */
    public static void save(Path gameDirectory, List<Entry> entries) {
        JSONObject root = new JSONObject();
        JSONArray arr = new JSONArray();
        int n = Math.min(entries.size(), MAX_ENTRIES);
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            arr.put(new JSONObject()
                    .put("path", e.path().toString())
                    .put("name", e.displayName())
                    .put("addedAt", e.addedAtMs()));
        }
        root.put("oszFiles", arr);
        Path file = configFile(gameDirectory);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = Files.createTempFile(file.getParent(), "osu4k_lib", ".tmp");
            Files.writeString(tmp, root.toString(4), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save OSU4K library", e);
        }
    }

    /** Returns the list with {@code path} moved to the front (dedup + cap). */
    public static List<Entry> withAdded(List<Entry> entries, Path path) {
        List<Entry> out = new ArrayList<>();
        out.add(new Entry(path, displayName(path), System.currentTimeMillis()));
        Path target = path.toAbsolutePath().normalize();
        for (Entry e : entries) {
            if (out.size() >= MAX_ENTRIES) {
                break;
            }
            if (!e.path().toAbsolutePath().normalize().equals(target)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Returns the list without {@code path}. */
    public static List<Entry> withRemoved(List<Entry> entries, Path path) {
        List<Entry> out = new ArrayList<>();
        Path target = path.toAbsolutePath().normalize();
        for (Entry e : entries) {
            if (!e.path().toAbsolutePath().normalize().equals(target)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Loads, adds {@code path} to the front and persists — used on successful open. */
    public static void addEntry(Path gameDirectory, Path path) {
        save(gameDirectory, withAdded(load(gameDirectory), path));
    }

    /** Human-readable name for an entry, derived from the file name. */
    public static String displayName(Path path) {
        String name = path.getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(".osz")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }
}

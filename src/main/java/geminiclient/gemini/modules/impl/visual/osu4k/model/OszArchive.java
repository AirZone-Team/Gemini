package geminiclient.gemini.modules.impl.visual.osu4k.model;

import geminiclient.gemini.base.I18n;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Read-only view of a {@code .osz} beatmap set (a ZIP archive).
 *
 * <p>Provides three things:</p>
 * <ul>
 *   <li>difficulty discovery — every {@code .osu} entry is parsed and reported
 *       together with its error, so the UI can list all available difficulties
 *       (and say why an entry is unplayable);</li>
 *   <li>validation — a usable set must contain at least one playable 4K map
 *       and the audio file that map references;</li>
 *   <li>audio extraction — the referenced audio is copied into a per-set cache
 *       directory under {@code <gameDir>/gemini/osu4k/cache/}.</li>
 * </ul>
 *
 * <p>The archive itself is never modified. Instances must be {@link #close()}
 * d to release the underlying file handle.</p>
 */
public final class OszArchive implements Closeable {

    /** Maximum uncompressed size accepted for a single extracted audio file. */
    private static final long MAX_EXTRACT_BYTES = 256L * 1024 * 1024;

    /** User-facing error produced by a failed load attempt. */
    public static final class OszError extends Exception {
        public OszError(String message) {
            super(message);
        }
    }

    /** One {@code .osu} entry inside the set: either a parsed map or an error. */
    public record MapFile(String entryName, BeatmapData map, String error) {
        public boolean isPlayable() {
            return map != null;
        }
    }

    private final Path oszPath;
    private final ZipFile zip;
    private final String setFolderName;

    private OszArchive(Path oszPath, ZipFile zip) {
        this.oszPath = oszPath;
        this.zip = zip;
        String name = oszPath.getFileName().toString();
        String base = name;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        this.setFolderName = sanitizeFolderName(base);
    }

    /**
     * Opens the archive. A {@code ZipException} (not a ZIP at all) is translated
     * into an {@link OszError} with a friendly message; other I/O problems are
     * reported with the file name.
     */
    public static OszArchive open(Path oszPath) throws OszError {
        try {
            ZipFile zip = new ZipFile(oszPath.toFile(), StandardCharsets.UTF_8);
            return new OszArchive(oszPath, zip);
        } catch (ZipException e) {
            throw new OszError(I18n.trf("Not a valid .osz archive: %s", oszPath.getFileName()));
        } catch (IOException e) {
            throw new OszError(I18n.trf("Cannot read .osz file: %s", oszPath.getFileName()));
        }
    }

    /** Parses every {@code .osu} entry. A single broken map never aborts the scan. */
    public List<MapFile> mapFiles() {
        List<MapFile> result = new ArrayList<>();
        zip.stream()
                .filter(entry -> !entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".osu"))
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .forEach(entry -> {
                    try (InputStream in = zip.getInputStream(entry)) {
                        String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        BeatmapData map = OsuParser.parse(text, entry.getName());
                        result.add(new MapFile(entry.getName(), map, null));
                    } catch (IllegalArgumentException e) {
                        result.add(new MapFile(entry.getName(), null, e.getMessage()));
                    } catch (IOException e) {
                        result.add(new MapFile(entry.getName(), null, I18n.trf("Unreadable entry: %s", e.getMessage())));
                    }
                });
        return result;
    }

    /** Convenience: only the playable 4K maps. */
    public List<BeatmapData> playableMaps() {
        List<BeatmapData> maps = new ArrayList<>();
        for (MapFile mf : mapFiles()) {
            if (mf.isPlayable()) {
                maps.add(mf.map());
            }
        }
        return maps;
    }

    /**
     * Extracts the audio referenced by {@code map} into {@code cacheRoot/setFolder/}
     * and returns the extracted file path. Extraction is idempotent — an already
     * extracted, non-empty file is reused.
     *
     * @throws OszError if the audio entry is missing or cannot be extracted
     */
    public Path extractAudio(BeatmapData map, Path cacheRoot) throws OszError {
        ZipEntry audioEntry = findEntry(map.audioFileName());
        if (audioEntry == null) {
            throw new OszError(I18n.trf("Map \"%s\" references missing audio: %s", map.displayLabel(), map.audioFileName()));
        }

        Path folder = cacheRoot.resolve(setFolderName);
        Path target = folder.resolve(audioEntry.getName().replace('/', '_'));
        try {
            Files.createDirectories(folder);
            if (Files.isRegularFile(target) && Files.size(target) > 0) {
                return target; // already extracted
            }
            Path tmp = Files.createTempFile(folder, "audio", ".tmp");
            try (InputStream in = zip.getInputStream(audioEntry);
                 OutputStream out = Files.newOutputStream(tmp)) {
                copyBounded(in, out, audioEntry.getSize());
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new OszError(I18n.trf("Failed to extract audio: %s", e.getMessage()));
        }
    }

    /**
     * Validates the whole set: at least one playable 4K map and its audio present.
     * Used to fail fast with a clear message before opening the game screen.
     *
     * @return the chosen (first) playable map, or throws
     */
    public BeatmapData requirePlayableSet() throws OszError {
        List<BeatmapData> maps = playableMaps();
        if (maps.isEmpty()) {
            List<String> reasons = new ArrayList<>();
            for (MapFile mf : mapFiles()) {
                reasons.add(mf.entryName() + " -> " + mf.error());
            }
            throw new OszError(I18n.tr("No playable 4K map in this .osz") + (reasons.isEmpty() ? ""
                    : "\n" + String.join("\n", reasons)));
        }
        BeatmapData first = maps.get(0);
        if (findEntry(first.audioFileName()) == null) {
            throw new OszError(I18n.trf("Map \"%s\" is missing its audio file: %s", first.displayLabel(), first.audioFileName()));
        }
        return first;
    }

    public String setFolderName() {
        return setFolderName;
    }

    public Path oszPath() {
        return oszPath;
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private ZipEntry findEntry(String audioFileName) {
        String wanted = normalizePath(audioFileName);
        ZipEntry byFileName = null;
        for (ZipEntry e : zip.stream().toList()) {
            String name = normalizePath(e.getName());
            if (name.equals(wanted)) {
                return e;
            }
            // Fallback: match on the trailing path segment only (some sets store
            // audio under a subfolder while the map references a bare filename).
            if (byFileName == null && !name.isEmpty() && name.endsWith("/" + wanted)) {
                byFileName = e;
            }
        }
        return byFileName;
    }

    private static String normalizePath(String p) {
        String s = p.replace('\\', '/');
        while (s.startsWith("./")) {
            s = s.substring(2);
        }
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private static void copyBounded(InputStream in, OutputStream out, long declaredSize) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) >= 0) {
            total += n;
            if (total > MAX_EXTRACT_BYTES) {
                throw new IOException(I18n.trf("audio file exceeds the %d MB limit", MAX_EXTRACT_BYTES >> 20));
            }
            out.write(buffer, 0, n);
        }
        if (declaredSize >= 0 && total != declaredSize) {
            throw new IOException(I18n.trf("audio file truncated (%d bytes declared, %d read)", declaredSize, total));
        }
    }

    /** Keeps the cache folder name safe as a single path segment. */
    private static String sanitizeFolderName(String name) {
        String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (clean.isEmpty()) {
            clean = "beatmapset";
        }
        return clean;
    }

    @Override
    public void close() {
        try {
            zip.close();
        } catch (IOException ignored) {
            // Nothing to report when closing a read-only archive.
        }
    }
}

package geminiclient.gemini.modules.impl.visual.osu4k.model;

import geminiclient.gemini.base.I18n;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal parser for the osu! beatmap ({@code .osu}) text format.
 *
 * <p>Only the data the mania player consumes is extracted: metadata
 * ({@code [General]} / {@code [Metadata]}), key count ({@code [Mania]} /
 * legacy {@code [Difficulty] CircleSize}) and the {@code [HitObjects]}
 * section. Unknown or malformed lines are skipped defensively — a malformed
 * single object never aborts the whole parse.</p>
 *
 * <p>Format reference: <a href="https://osu.ppy.sh/wiki/en/Client/File_formats/osu_%28file_format%29">osu file format</a>.</p>
 */
public final class OsuParser {

    private OsuParser() {}

    /**
     * Parses a {@code .osu} file from disk.
     *
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if the file has no hit objects or no audio reference
     */
    public static BeatmapData parse(Path osuFile) throws IOException {
        return parse(new String(Files.readAllBytes(osuFile), StandardCharsets.UTF_8), osuFile.getFileName().toString());
    }

    /**
     * Parses a {@code .osu} file from raw text.
     *
     * @param text    the file contents
     * @param fileName the file name, used only for error messages
     */
    public static BeatmapData parse(String text, String fileName) {
        String title = "";
        String artist = "";
        String version = "";
        int keyCount = 0; // 0 = not declared yet; resolved to 4K (spec default) below
        boolean keysDeclared = false; // a map that declares Keys: 0 is invalid, not "undeclared"
        double circleSize = 0.0; // legacy mania key count ([Difficulty] CircleSize)
        String audioFile = "";
        double audioLeadIn = 0.0;
        double sliderVelocity = 1.0;
        int mode = -1;

        List<HitObject> hitObjects = new ArrayList<>();

        String section = "";
        String[] lines = text.split("\\r?\\n", -1);
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1);
                continue;
            }

            switch (section) {
                case "General" -> {
                    if (line.startsWith("AudioFilename:")) {
                        audioFile = valueAfterColon(line);
                    } else if (line.startsWith("AudioLeadIn:")) {
                        audioLeadIn = parseDouble(valueAfterColon(line), 0.0);
                    } else if (line.startsWith("Mode:")) {
                        mode = (int) parseDouble(valueAfterColon(line), -1);
                    }
                }
                case "Metadata" -> {
                    if (line.startsWith("Title:")) {
                        title = stripMetadataQuotes(valueAfterColon(line));
                    } else if (line.startsWith("Artist:")) {
                        artist = stripMetadataQuotes(valueAfterColon(line));
                    } else if (line.startsWith("Version:")) {
                        version = stripMetadataQuotes(valueAfterColon(line));
                    }
                }
                case "Difficulty" -> {
                    if (line.startsWith("SliderMultiplier:")) {
                        sliderVelocity = parseDouble(valueAfterColon(line), 1.0);
                    } else if (line.startsWith("CircleSize:")) {
                        circleSize = parseDouble(valueAfterColon(line), 0.0);
                    }
                }
                case "Mania" -> {
                    if (line.startsWith("Mania Speed:") || line.startsWith("Mania_Speed:")) {
                        sliderVelocity = parseDouble(valueAfterColon(line), 1.0);
                    } else if (line.startsWith("Keys:") || line.startsWith("KeyCount:")) {
                        keyCount = (int) Math.round(parseDouble(valueAfterColon(line), 0.0));
                        keysDeclared = true;
                    }
                    // SpecialStyle is a 0/1 scratch-column flag, NOT a key count.
                }
                case "HitObjects" -> {
                    HitObject obj = parseHitObject(line, effectiveKeyCount(keyCount, keysDeclared, circleSize));
                    if (obj != null) {
                        hitObjects.add(obj);
                    }
                }
                default -> {
                    // Ignore all other sections ([Events], [TimingPoints], [Colours], ...)
                }
            }
        }

        // Key count resolution: [Mania] "Keys:" is the modern format; before it
        // existed mania maps stored the key count in [Difficulty] CircleSize
        // (as this sample set does); per the spec Mode 3 defaults to 4K when
        // neither declares a count. Resolved both mid-parse (so hit objects are
        // mapped with the right lane count) and again here for validation.
        keyCount = effectiveKeyCount(keyCount, keysDeclared, circleSize);
        if (keyCount < BeatmapData.MIN_KEY_COUNT || keyCount > BeatmapData.MAX_KEY_COUNT) {
            throw new IllegalArgumentException(I18n.trf("[%s] is a %dK map, only 1K-10K is supported",
                    fileName, keyCount));
        }
        if (audioFile.isEmpty()) {
            throw new IllegalArgumentException(I18n.trf("[%s] has no AudioFilename", fileName));
        }
        if (hitObjects.isEmpty()) {
            throw new IllegalArgumentException(I18n.trf("[%s] has no hit objects", fileName));
        }
        // Every real .osu carries Mode:; only mode 3 (osu!mania) is playable.
        // A missing Mode line is tolerated for hand-written test maps.
        if (mode != -1 && mode != 3) {
            throw new IllegalArgumentException(I18n.trf("[%s] is not an osu!mania beatmap (Mode=%d)", fileName, mode));
        }

        return new BeatmapData(title, artist, version, keyCount, audioFile,
                audioLeadIn, sliderVelocity, hitObjects);
    }

    /**
     * Resolves the effective lane count: {@code Keys} (modern) wins; a legacy
     * {@code CircleSize} fills in when no {@code Keys} line was seen; otherwise
     * the spec default of 4K applies.
     */
    private static int effectiveKeyCount(int keyCount, boolean keysDeclared, double circleSize) {
        if (!keysDeclared) {
            if (circleSize > 0) {
                return (int) Math.round(circleSize);
            }
            return 4;
        }
        return keyCount;
    }

    /**
     * Parses one {@code [HitObjects]} line.
     *
     * <p>Format: {@code x,y,time,type,hitSound,objectParams,hitSample}</p>
     * Mania stores the column number in {@code x} ({@code x / 512 * keyCount}),
     * and holds carry their tail length in {@code objectParams} (ms).
     */
    private static HitObject parseHitObject(String line, int keyCount) {
        String[] fields = line.split(",");
        if (fields.length < 5) {
            return null;
        }
        int x;
        int y;
        int time;
        int type;
        try {
            x = Integer.parseInt(fields[0].trim());
            y = Integer.parseInt(fields[1].trim());
            time = Integer.parseInt(fields[2].trim());
            type = Integer.parseInt(fields[3].trim());
        } catch (NumberFormatException e) {
            return null;
        }

        // Column: mania uses x with 512 logical pixels across keyCount columns.
        int column = Math.min(keyCount - 1, Math.max(0, (int) ((long) x * keyCount / 512L)));

        int durationMs = 0;
        if ((type & (1 << 7)) != 0) { // hold note (type bit 7)
            // objectParams (field 5) is "endTime:volume:...". Modern maps store
            // the absolute end time; very old maps stored the duration instead.
            if (fields.length > 5) {
                double param = parseLeadingNumber(fields[5].trim(), 0.0);
                if (param > 0) {
                    if (param >= time) {
                        durationMs = (int) Math.round(param) - time; // end time
                    } else {
                        durationMs = (int) Math.round(param);        // legacy duration
                    }
                }
            }
            durationMs = Math.max(0, durationMs);
        }
        return new HitObject(time, column, durationMs);
    }

    private static String valueAfterColon(String line) {
        int colon = line.indexOf(':');
        return colon < 0 ? "" : line.substring(colon + 1).trim();
    }

    private static String stripMetadataQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value.trim();
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Parses the leading numeric token of a value that may be a compound string
     * such as {@code "35100:2:0:1:80:"} (mania hold parameters). Returns the
     * fallback when no leading number is present.
     */
    private static double parseLeadingNumber(String value, double fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.substring(0, end));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Case-insensitive audio extension check (covered by {@code OsuParserTest}). */
    public static boolean isAudioFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".ogg") || lower.endsWith(".wav");
    }
}

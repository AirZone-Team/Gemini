package geminiclient.gemini.modules.impl.visual.osu4k.model;

import java.util.List;
import java.util.Objects;

/**
 * A parsed mania beatmap (one {@code .osu} file).
 *
 * <p>Only the subset of the osu! file format needed by the player is kept:
 * metadata, audio reference, key count and the hit objects. Everything else
 * (timing points, storyboards, etc.) is intentionally discarded.</p>
 */
public final class BeatmapData {

    /** Smallest playable key count (1K). */
    public static final int MIN_KEY_COUNT = 1;
    /** Largest playable key count (10K). */
    public static final int MAX_KEY_COUNT = 10;

    private final String title;
    private final String artist;
    private final String version;      // difficulty name, e.g. "Insane"
    private final int keyCount;
    private final String audioFileName;
    private final double audioLeadInMs;
    private final double sliderVelocity; // SliderMultiplier fallback; unused for mania
    private final List<HitObject> hitObjects;

    public BeatmapData(String title, String artist, String version, int keyCount,
                       String audioFileName, double audioLeadInMs, double sliderVelocity,
                       List<HitObject> hitObjects) {
        this.title = Objects.requireNonNullElse(title, "").trim();
        this.artist = Objects.requireNonNullElse(artist, "").trim();
        this.version = Objects.requireNonNullElse(version, "").trim();
        this.keyCount = keyCount;
        this.audioFileName = Objects.requireNonNullElse(audioFileName, "").trim();
        this.audioLeadInMs = audioLeadInMs;
        this.sliderVelocity = sliderVelocity;
        this.hitObjects = List.copyOf(Objects.requireNonNull(hitObjects, "hitObjects"));
    }

    public String title() { return title; }
    public String artist() { return artist; }
    public String version() { return version; }
    public int keyCount() { return keyCount; }
    public String audioFileName() { return audioFileName; }
    public double audioLeadInMs() { return audioLeadInMs; }
    public double sliderVelocity() { return sliderVelocity; }
    public List<HitObject> hitObjects() { return hitObjects; }

    /** True if this map is playable (1K-10K, with audio and hit objects). */
    public boolean isPlayable() {
        return keyCount >= MIN_KEY_COUNT && keyCount <= MAX_KEY_COUNT
                && !audioFileName.isEmpty() && !hitObjects.isEmpty();
    }

    /** Total song length in ms (last hit object end), used for the progress bar. */
    public int songLengthMs() {
        int end = 0;
        for (HitObject ho : hitObjects) {
            end = Math.max(end, ho.endTimeMs());
        }
        return end;
    }

    /** Display label used in the difficulty list, e.g. "Insane (1:12)". */
    public String displayLabel() {
        int secs = songLengthMs() / 1000;
        String len = String.format("%d:%02d", secs / 60, secs % 60);
        return version.isEmpty() ? "Unknown (" + len + ")" : version + " (" + len + ")";
    }
}

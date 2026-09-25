package geminiclient.gemini.modules.impl.visual.osu4k;

import geminiclient.gemini.modules.impl.visual.osu4k.model.BeatmapData;
import geminiclient.gemini.modules.impl.visual.osu4k.model.HitObject;
import geminiclient.gemini.modules.impl.visual.osu4k.model.OsuParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsing tests for the {@code .osu} reader against hand-written maps. */
class OsuParserTest {

    private static final String HEADER = """
            osu file format v14

            [General]
            AudioFilename: audio.mp3
            Mode: 3

            [Metadata]
            Title:Sendan Life
            TitleUnicode:Sendan Life
            Artist:Remo Prototype (CV: Hanamori Yumiri)
            Version:Insane

            [Difficulty]
            SliderMultiplier:1.8

            [Mania]
            Keys: 4

            """;

    private static String withObjects(String... lines) {
        StringBuilder sb = new StringBuilder(HEADER).append("\n[HitObjects]\n");
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    @Test
    void parsesMetadataAndObjects() {
        BeatmapData map = OsuParser.parse(withObjects(
                "64,192,1000,1,0,0:0:0:0:",
                "192,192,1016,1,0,0:0:0:0:",
                "448,192,4000,128,0,4500:0:0:0:0:" // hold 500ms
        ), "test.osu");

        assertEquals("Sendan Life", map.title());
        assertEquals("Remo Prototype (CV: Hanamori Yumiri)", map.artist());
        assertEquals("Insane", map.version());
        assertEquals("audio.mp3", map.audioFileName());
        assertEquals(4, map.keyCount());
        assertEquals(3, map.hitObjects().size());
        assertTrue(map.isPlayable());
    }

    @Test
    void mapsXCoordinatesToColumns() {
        BeatmapData map = OsuParser.parse(withObjects(
                "64,192,1000,1,0,0:0:0:0:",   // col 0
                "192,192,1000,1,0,0:0:0:0:",  // col 1
                "320,192,1000,1,0,0:0:0:0:",  // col 2
                "448,192,1000,1,0,0:0:0:0:",  // col 3
                "511,192,2000,1,0,0:0:0:0:"   // clamps to col 3
        ), "cols.osu");
        assertEquals(0, map.hitObjects().get(0).column());
        assertEquals(1, map.hitObjects().get(1).column());
        assertEquals(2, map.hitObjects().get(2).column());
        assertEquals(3, map.hitObjects().get(3).column());
        assertEquals(3, map.hitObjects().get(4).column());
    }

    @Test
    void parsesHoldDurationFromEndTime() {
        BeatmapData map = OsuParser.parse(withObjects(
                "192,192,4000,128,0,4500:2:0:1:80:", // tail 4500 -> 500ms hold
                "64,192,6000,128,0,7000:0:0:0:0:"
        ), "holds.osu");
        HitObject hold = map.hitObjects().get(0);
        assertTrue(hold.isHold());
        assertEquals(500, hold.durationMs());
        assertEquals(4500, hold.endTimeMs());
    }

    @Test
    void skipsMalformedLinesWithoutAborting() {
        BeatmapData map = OsuParser.parse(withObjects(
                "garbage,line,here",
                "64,192,1000,1,0,0:0:0:0:",
                "192,notanumber,1000,1,0,0:0:0:0:"
        ), "dirty.osu");
        assertEquals(1, map.hitObjects().size());
    }

    @Test
    void rejectsOutOfRangeKeyCounts() {
        IllegalArgumentException e11 = assertThrows(IllegalArgumentException.class,
                () -> OsuParser.parse(withObjects("64,192,1000,1,0,0:0:0:0:").replace("Keys: 4", "Keys: 11"),
                        "elevenk.osu"));
        assertTrue(e11.getMessage().contains("11K"));
        assertTrue(e11.getMessage().contains("1K-10K"));

        IllegalArgumentException e0 = assertThrows(IllegalArgumentException.class,
                () -> OsuParser.parse(withObjects("64,192,1000,1,0,0:0:0:0:").replace("Keys: 4", "Keys: 0"),
                        "zerok.osu"));
        assertTrue(e0.getMessage().contains("0K"));
    }

    @Test
    void acceptsSevenKeyMaps() {
        BeatmapData map = OsuParser.parse(
                withObjects("64,192,1000,1,0,0:0:0:0:").replace("Keys: 4", "Keys: 7"), "sevenk.osu");
        assertEquals(7, map.keyCount());
        assertTrue(map.isPlayable());
    }

    /** Legacy maps (pre-[Mania] section) store the key count in CircleSize. */
    private static final String LEGACY_HEADER = """
            osu file format v14

            [General]
            AudioFilename: audio.mp3
            Mode: 3

            [Metadata]
            Title:Sendan Life
            TitleUnicode:Sendan Life
            Artist:Remo Prototype (CV: Hanamori Yumiri)
            Version:Insane

            [Difficulty]
            SliderMultiplier:1.8
            CircleSize:6

            """;

    private static String withLegacyObjects(String... lines) {
        StringBuilder sb = new StringBuilder(LEGACY_HEADER).append("\n[HitObjects]\n");
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    @Test
    void readsKeyCountFromLegacyCircleSize() {
        BeatmapData map = OsuParser.parse(withLegacyObjects(
                "42,192,1000,1,0,0:0:0:0:",   // col 0
                "128,192,1000,1,0,0:0:0:0:",  // col 1
                "213,192,1000,1,0,0:0:0:0:",  // col 2
                "298,192,1000,1,0,0:0:0:0:",  // col 3
                "384,192,1000,1,0,0:0:0:0:",  // col 4
                "469,192,1000,1,0,0:0:0:0:",  // col 5
                "511,192,2000,1,0,0:0:0:0:"   // clamps to col 5
        ), "legacy6k.osu");
        assertEquals(6, map.keyCount());
        for (int c = 0; c < 6; c++) {
            assertEquals(c, map.hitObjects().get(c).column());
        }
        assertEquals(5, map.hitObjects().get(6).column());
        assertTrue(map.isPlayable());
    }

    @Test
    void acceptsOneAndTenKeyMaps() {
        BeatmapData one = OsuParser.parse(
                withObjects("256,192,1000,1,0,0:0:0:0:").replace("Keys: 4", "Keys: 1"), "onek.osu");
        assertEquals(1, one.keyCount());
        assertEquals(0, one.hitObjects().get(0).column());
        assertTrue(one.isPlayable());

        BeatmapData ten = OsuParser.parse(
                withObjects("511,192,1000,1,0,0:0:0:0:").replace("Keys: 4", "Keys: 10"), "tenk.osu");
        assertEquals(10, ten.keyCount());
        assertEquals(9, ten.hitObjects().get(0).column());
        assertTrue(ten.isPlayable());
    }

    @Test
    void specialStyleDoesNotAffectKeyCount() {
        // Modern format: Keys wins, SpecialStyle is a 0/1 scratch flag.
        BeatmapData modern = OsuParser.parse(withObjects("42,192,1000,1,0,0:0:0:0:")
                .replace("Keys: 4", "Keys: 6")
                .replace("[Mania]\nKeys: 6", "[Mania]\nKeys: 6\nSpecialStyle: 1"), "scratch.osu");
        assertEquals(6, modern.keyCount());

        // Legacy format: no Keys line, SpecialStyle must not override CircleSize
        // ([Mania] sits after [Difficulty] in the file order).
        BeatmapData legacy = OsuParser.parse(withLegacyObjects("42,192,1000,1,0,0:0:0:0:")
                .replace("CircleSize:6", "CircleSize:6\n[Mania]\nSpecialStyle: 1"),
                "scratchlegacy.osu");
        assertEquals(6, legacy.keyCount());
    }

    @Test
    void rejectsMapsWithoutAudio() {
        String text = withObjects("64,192,1000,1,0,0:0:0:0:").replace("AudioFilename: audio.mp3", "");
        assertThrows(IllegalArgumentException.class, () -> OsuParser.parse(text, "noaudio.osu"));
    }

    @Test
    void rejectsNonManiaModes() {
        String text = withObjects("64,192,1000,1,0,0:0:0:0:").replace("Mode: 3", "Mode: 0");
        assertThrows(IllegalArgumentException.class, () -> OsuParser.parse(text, "std.osu"));
    }

    @Test
    void rejectsEmptyObjectList() {
        assertThrows(IllegalArgumentException.class,
                () -> OsuParser.parse(withObjects(), "empty.osu"));
    }

    @Test
    void audioExtensionDetection() {
        assertTrue(OsuParser.isAudioFile("music.mp3"));
        assertTrue(OsuParser.isAudioFile("music.ogg"));
        assertTrue(OsuParser.isAudioFile("music.WAV"));
        assertFalse(OsuParser.isAudioFile("background.jpg"));
        assertFalse(OsuParser.isAudioFile("song.mp3.ini"));
    }
}

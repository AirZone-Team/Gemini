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
        assertTrue(map.isPlayable4K());
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
    void rejectsNonFourKeyMaps() {
        String text = withObjects("64,192,1000,1,0,0:0:0:0:").replace("Keys: 4", "Keys: 7");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> OsuParser.parse(text, "sevenk.osu"));
        assertTrue(e.getMessage().contains("7K"));
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

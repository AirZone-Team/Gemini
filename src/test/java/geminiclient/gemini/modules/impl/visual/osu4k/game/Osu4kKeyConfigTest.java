package geminiclient.gemini.modules.impl.visual.osu4k.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Persistence tests for the per-key-count lane keybinds. */
class Osu4kKeyConfigTest {

    @TempDir
    Path tempDir;

    private static final int D = GLFW.GLFW_KEY_D;
    private static final int F = GLFW.GLFW_KEY_F;
    private static final int J = GLFW.GLFW_KEY_J;
    private static final int K = GLFW.GLFW_KEY_K;
    private static final int S = GLFW.GLFW_KEY_S;
    private static final int L = GLFW.GLFW_KEY_L;

    @Test
    void missingFileLoadsEmptySets() {
        assertTrue(Osu4kKeyConfig.loadKeySets(tempDir).isEmpty());
    }

    @Test
    void defaultKeysCoverEveryCount() {
        assertArrayEquals(new int[]{D, F, J, K}, Osu4kKeyConfig.defaultKeys(4));
        assertArrayEquals(new int[]{S, D, F, J, K, L}, Osu4kKeyConfig.defaultKeys(6));
        assertEquals(1, Osu4kKeyConfig.defaultKeys(1).length);
        assertEquals(10, Osu4kKeyConfig.defaultKeys(10).length);
        // No lane default is the pause key (space).
        for (int c = 1; c <= 10; c++) {
            for (int key : Osu4kKeyConfig.defaultKeys(c)) {
                assertTrue(key != GLFW.GLFW_KEY_SPACE, "lane default must not be space");
            }
        }
    }

    @Test
    void legacyKeysArrayMigratesToFourKeySet() throws Exception {
        Files.createDirectories(Osu4kKeyConfig.configFile(tempDir).getParent());
        Files.writeString(Osu4kKeyConfig.configFile(tempDir), "{\"keys\": [68, 70, 74, 75]}",
                StandardCharsets.UTF_8);

        Map<Integer, int[]> sets = Osu4kKeyConfig.loadKeySets(tempDir);
        assertEquals(1, sets.size());
        assertArrayEquals(new int[]{D, F, J, K}, sets.get(4));
    }

    @Test
    void keySetsRoundTrip() throws Exception {
        Map<Integer, int[]> original = new HashMap<>();
        original.put(4, new int[]{D, F, J, K});
        original.put(6, new int[]{S, D, F, J, K, L});

        Osu4kKeyConfig.saveKeySets(tempDir, original);
        Map<Integer, int[]> loaded = Osu4kKeyConfig.loadKeySets(tempDir);

        assertEquals(2, loaded.size());
        assertArrayEquals(new int[]{D, F, J, K}, loaded.get(4));
        assertArrayEquals(new int[]{S, D, F, J, K, L}, loaded.get(6));
    }

    @Test
    void partialArrayKeepsDefaultsForUnknownEntries() throws Exception {
        Files.createDirectories(Osu4kKeyConfig.configFile(tempDir).getParent());
        Files.writeString(Osu4kKeyConfig.configFile(tempDir),
                "{\"keySets\": {\"4\": [0, 71, 0, 0]}}", StandardCharsets.UTF_8);

        Map<Integer, int[]> sets = Osu4kKeyConfig.loadKeySets(tempDir);
        int[] keys = sets.get(4);
        assertEquals(D, keys[0]);           // 0 keeps the default
        assertEquals(GLFW.GLFW_KEY_G, keys[1]); // custom G binding
        assertEquals(J, keys[2]);
        assertEquals(K, keys[3]);
    }

    @Test
    void corruptFileFallsBackToEmpty() throws Exception {
        Files.createDirectories(Osu4kKeyConfig.configFile(tempDir).getParent());
        Files.writeString(Osu4kKeyConfig.configFile(tempDir), "not json at all {{{", StandardCharsets.UTF_8);

        assertTrue(Osu4kKeyConfig.loadKeySets(tempDir).isEmpty());
    }

    @Test
    void outOfRangeKeySetsAreIgnored() throws Exception {
        Files.createDirectories(Osu4kKeyConfig.configFile(tempDir).getParent());
        Files.writeString(Osu4kKeyConfig.configFile(tempDir),
                "{\"keySets\": {\"4\": [68, 70, 74, 75], \"11\": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]}}",
                StandardCharsets.UTF_8);

        Map<Integer, int[]> sets = Osu4kKeyConfig.loadKeySets(tempDir);
        assertEquals(1, sets.size());
        assertTrue(sets.containsKey(4));
    }
}

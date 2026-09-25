package geminiclient.gemini.modules.impl.visual.osu4k.game;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    // 键码域 = InputConstants（Minecraft 26.3 起为 SDL scancode），见 KeyUtils 的说明。
    private static final int D = InputConstants.KEY_D;
    private static final int F = InputConstants.KEY_F;
    private static final int J = InputConstants.KEY_J;
    private static final int K = InputConstants.KEY_K;
    private static final int S = InputConstants.KEY_S;
    private static final int L = InputConstants.KEY_L;

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
                assertTrue(key != InputConstants.KEY_SPACE, "lane default must not be space");
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

    /** 没有 keyDomain 标记的文件写于 GLFW 时代，读入时翻译一次并重写标记。 */
    @Test
    void unmarkedFileMigratesGlfwCodesOnce() throws Exception {
        Files.createDirectories(Osu4kKeyConfig.configFile(tempDir).getParent());
        Path file = Osu4kKeyConfig.configFile(tempDir);
        // GLFW 的 D/F/J/K = 68/70/74/75
        Files.writeString(file, "{\"keySets\": {\"4\": [68, 70, 74, 75]}}", StandardCharsets.UTF_8);

        assertArrayEquals(new int[]{D, F, J, K}, Osu4kKeyConfig.loadKeySets(tempDir).get(4));

        String saved = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(saved.contains("keyDomain") && saved.contains("sdl"),
                "migration must mark the domain: " + saved);
        // 再读一次必须稳定（否则说明发生了二次迁移）
        assertArrayEquals(new int[]{D, F, J, K}, Osu4kKeyConfig.loadKeySets(tempDir).get(4));
    }

    /** 已标记为 sdl 的文件里，数值一律原样保留，即使它恰好等于某个 GLFW 键码。 */
    @Test
    void markedFileIsNeverRemigrated() throws Exception {
        Files.createDirectories(Osu4kKeyConfig.configFile(tempDir).getParent());
        Files.writeString(Osu4kKeyConfig.configFile(tempDir),
                "{\"keyDomain\": \"sdl\", \"keySets\": {\"4\": [68, 70, 74, 75]}}",
                StandardCharsets.UTF_8);

        assertArrayEquals(new int[]{68, 70, 74, 75}, Osu4kKeyConfig.loadKeySets(tempDir).get(4));
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
                "{\"keyDomain\": \"sdl\", \"keySets\": {\"4\": [0, 71, 0, 0]}}", StandardCharsets.UTF_8);

        Map<Integer, int[]> sets = Osu4kKeyConfig.loadKeySets(tempDir);
        int[] keys = sets.get(4);
        assertEquals(D, keys[0]);           // 0 keeps the default
        assertEquals(71, keys[1]);          // a stored binding wins over the default
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

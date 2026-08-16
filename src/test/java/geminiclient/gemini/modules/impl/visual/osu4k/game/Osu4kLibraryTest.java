package geminiclient.gemini.modules.impl.visual.osu4k.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Persistence tests for the saved .osz library. */
class Osu4kLibraryTest {

    @TempDir
    Path tempDir;

    private Path libFile() {
        return Osu4kLibrary.configFile(tempDir);
    }

    @Test
    void missingFileLoadsEmpty() {
        assertTrue(Osu4kLibrary.load(tempDir).isEmpty());
    }

    @Test
    void saveRoundTripPreservesEntries() {
        Path a = tempDir.resolve("A.osz");
        Path b = tempDir.resolve("B.osz");
        Osu4kLibrary.save(tempDir, List.of(
                new Osu4kLibrary.Entry(a, "A", 1000),
                new Osu4kLibrary.Entry(b, "B", 2000)));

        List<Osu4kLibrary.Entry> loaded = Osu4kLibrary.load(tempDir);
        assertEquals(2, loaded.size());
        assertEquals("A", loaded.get(0).displayName());
        assertEquals("B", loaded.get(1).displayName());
        assertTrue(loaded.get(0).path().endsWith("A.osz"));
    }

    @Test
    void withAddedDedupesAndMovesToFront() {
        Path a = tempDir.resolve("A.osz");
        Path b = tempDir.resolve("B.osz");
        List<Osu4kLibrary.Entry> list = List.of(
                new Osu4kLibrary.Entry(a, "A", 1000),
                new Osu4kLibrary.Entry(b, "B", 2000));

        // Re-adding A moves it to the front instead of duplicating.
        List<Osu4kLibrary.Entry> updated = Osu4kLibrary.withAdded(list, a);
        assertEquals(2, updated.size());
        assertEquals("A", updated.get(0).displayName());
        assertEquals("B", updated.get(1).displayName());
    }

    @Test
    void withAddedCapsAtMaxEntries() {
        // Entries are ordered most-recently-opened first (index 0 = newest).
        List<Osu4kLibrary.Entry> list = new java.util.ArrayList<>();
        for (int i = 0; i < Osu4kLibrary.MAX_ENTRIES; i++) {
            list.add(new Osu4kLibrary.Entry(tempDir.resolve("f" + i + ".osz"), "f" + i, i));
        }
        List<Osu4kLibrary.Entry> updated = Osu4kLibrary.withAdded(list, tempDir.resolve("new.osz"));
        assertEquals(Osu4kLibrary.MAX_ENTRIES, updated.size());
        assertEquals("new", updated.get(0).displayName());
        // The oldest entry (the tail) is evicted.
        assertFalse(updated.stream().anyMatch(e -> e.displayName().equals("f63")));
    }

    @Test
    void withRemovedDropsTheEntry() {
        Path a = tempDir.resolve("A.osz");
        Path b = tempDir.resolve("B.osz");
        List<Osu4kLibrary.Entry> list = List.of(
                new Osu4kLibrary.Entry(a, "A", 1000),
                new Osu4kLibrary.Entry(b, "B", 2000));
        List<Osu4kLibrary.Entry> updated = Osu4kLibrary.withRemoved(list, a);
        assertEquals(1, updated.size());
        assertEquals("B", updated.get(0).displayName());
    }

    @Test
    void displayNameStripsExtension() {
        assertEquals("My Beatmap", Osu4kLibrary.displayName(Path.of("C:/maps/My Beatmap.osz")));
    }

    @Test
    void addEntryPersistsAndLoadsBack() {
        Path a = tempDir.resolve("A.osz");
        Osu4kLibrary.addEntry(tempDir, a);
        assertEquals(1, Osu4kLibrary.load(tempDir).size());
    }
}

package gemini.build.slang;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlangManifestTest {
    @Test
    void parsesVariantsInOrder() {
        Map<String, List<String>> result = SlangManifest.parse("""
                {
                  "core/pass.frag.slang": ["FIRST_PASS", "SECOND_PASS"]
                }
                """);

        assertEquals(List.of("FIRST_PASS", "SECOND_PASS"), result.get("core/pass.frag.slang"));
    }

    @Test
    void rejectsInvalidAndDuplicateDefines() {
        assertThrows(GradleException.class, () -> SlangManifest.parse("""
                { "core/pass.frag.slang": ["bad-name"] }
                """));
        assertThrows(GradleException.class, () -> SlangManifest.parse("""
                { "core/pass.frag.slang": ["PASS", "PASS"] }
                """));
    }
}

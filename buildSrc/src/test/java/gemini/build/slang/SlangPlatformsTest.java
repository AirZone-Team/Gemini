package gemini.build.slang;

import groovy.lang.Closure;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlangPlatformsTest {
    private static Closure<?> classifierFor;

    @BeforeAll
    static void loadSharedMappingScript() {
        File repositoryRoot = new File(System.getProperty("gemini.repositoryRoot"));
        File script = new File(repositoryRoot, "gradle/slang-platforms.gradle");
        Project project = ProjectBuilder.builder().build();
        project.apply(java.util.Map.of("from", script));
        classifierFor = (Closure<?>) project.getExtensions().getExtraProperties()
                .get("slangNativeClassifierFor");
    }

    @Test
    void mapsOsAndArchitectureAliases() {
        assertEquals("windows-x86_64", classify("Windows 10", "amd64", null));
        assertEquals("windows-aarch64", classify("win64", "arm64", null));
        assertEquals("linux-x86_64", classify("GNU/Linux", "x64", null));
        assertEquals("linux-aarch64", classify("linux", "aarch64", null));
        assertEquals("macos-x86_64", classify("Mac OS X", "x86-64", null));
        assertEquals("macos-aarch64", classify("darwin", "arm64-v8a", null));
    }

    @Test
    void explicitClassifierOverridesDetection() {
        assertEquals("linux-aarch64", classify("unsupported-os", "unsupported-arch", "LINUX-AARCH64"));
    }

    @Test
    void rejectsUnsupportedValuesClearly() {
        GradleException os = assertThrows(GradleException.class,
                () -> classify("plan9", "amd64", null));
        assertTrue(os.getMessage().contains("Unsupported Slang native operating system 'plan9'"));

        GradleException arch = assertThrows(GradleException.class,
                () -> classify("linux", "riscv64", null));
        assertTrue(arch.getMessage().contains("Unsupported Slang native architecture 'riscv64'"));

        GradleException override = assertThrows(GradleException.class,
                () -> classify("linux", "amd64", "linux-riscv64"));
        assertTrue(override.getMessage().contains("Unsupported -PslangNativeClassifier='linux-riscv64'"));
    }

    private static String classify(String os, String arch, String override) {
        return (String) classifierFor.call(os, arch, override);
    }
}

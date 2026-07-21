package gemini.build.toolkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildToolkitVerifierTest {
    private static final String CLASSIFIER = "windows-x86_64";
    private static final String VERSION = "1.0-SNAPSHOT";
    private static final String SLANG_VERSION = "0.0.6";
    private static final String RUNTIME_JAR = "slang-java-0.0.6.jar";
    private static final String NATIVE_JAR = "slang-java-natives-0.0.6-windows-x86_64.jar";

    @TempDir
    Path tempDirectory;

    @Test
    void acceptsExpectedBuildkit() throws IOException {
        assertDoesNotThrow(() -> BuildToolkitVerifier.verify(buildkit(Map.of(), false)));
    }

    @Test
    void rejectsDisallowedEntriesAndChecksumMismatch() throws IOException {
        Path archive = buildkit(Map.of(".gradle/cache.bin", new byte[]{1}), true);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BuildToolkitVerifier.verify(archive));
        assertTrue(error.getMessage().contains("disallowed entry"));
        assertTrue(error.getMessage().contains("SHA-256 mismatch"));
    }

    @Test
    void rejectsWrongNativeClassifierAndMissingMetadata() throws IOException {
        Map<String, byte[]> entries = baseEntries();
        entries.remove("BUILD-INFO.properties");
        entries.put("vendor/slang/" + NATIVE_JAR, zip(Map.of(
                "META-INF/natives/linux/x86_64/index.txt",
                "slang 2026.13 hash\nF lib/libslang.so\n".getBytes(StandardCharsets.UTF_8))));
        addChecksums(entries, false);
        Path archive = writeZip(entries);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BuildToolkitVerifier.verify(archive));
        assertTrue(error.getMessage().contains("missing required entry: BUILD-INFO.properties"));
    }

    @Test
    void rejectsExtraNativeJarAndWrongBuildTag() throws IOException {
        Map<String, byte[]> additions = new LinkedHashMap<>();
        additions.put("vendor/slang/slang-java-natives-0.0.6-linux-x86_64.jar", new byte[]{1});
        Path archive = buildkit(additions, false);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BuildToolkitVerifier.verify(archive));
        assertTrue(error.getMessage().contains("expected exactly vendor JARs"));
        assertTrue(error.getMessage().contains("expected exactly one slang-java-natives JAR"));
    }

    private Path buildkit(Map<String, byte[]> additions, boolean corruptChecksum) throws IOException {
        Map<String, byte[]> entries = baseEntries();
        entries.putAll(additions);
        addChecksums(entries, corruptChecksum);
        return writeZip(entries);
    }

    private Map<String, byte[]> baseEntries() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (String name : new String[]{
                "build.gradle", "settings.gradle", "gradle.properties", "gradlew", "gradlew.bat",
                "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
                "gradle/slang-platforms.gradle", "buildSrc/build.gradle", "README.md", "LICENSE",
                "BUILDING.md", "THIRD-PARTY-NOTICES", "src/main/slang/variants.json"}) {
            entries.put(name, name.getBytes(StandardCharsets.UTF_8));
        }
        entries.put("buildSrc/src/main/java/example/Plugin.java", new byte[]{1});
        entries.put("src/main/java/example/Main.java", new byte[]{1});
        entries.put("src/main/resources/example.txt", new byte[]{1});
        entries.put("src/main/slang/gemini/example.slang", new byte[]{1});
        entries.put("vendor/slang/" + RUNTIME_JAR, new byte[]{1, 2, 3});
        entries.put("vendor/slang/" + NATIVE_JAR, zip(Map.of(
                "META-INF/natives/windows/x86_64/index.txt",
                "slang 2026.13 hash\nF lib/slang.dll\n".getBytes(StandardCharsets.UTF_8))));
        entries.put("BUILD-INFO.properties", ("formatVersion=1\n"
                + "project=gemini\nprojectVersion=" + VERSION + "\nclassifier=" + CLASSIFIER + "\n"
                + "slangJavaVersion=" + SLANG_VERSION + "\nslangBuildTag=2026.13\n"
                + "slangRuntimeJars=" + RUNTIME_JAR + "\nslangNativeJar=" + NATIVE_JAR + "\n"
                + "gradleVersion=9.2.1\njavaVersion=25\n").getBytes(StandardCharsets.ISO_8859_1));
        return entries;
    }

    private static void addChecksums(Map<String, byte[]> entries, boolean corrupt) {
        StringBuilder checksums = new StringBuilder();
        entries.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("vendor/slang/") && entry.getKey().endsWith(".jar"))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> checksums.append(corrupt ? "0".repeat(64) : sha256(entry.getValue()))
                        .append("  ").append(entry.getKey()).append('\n'));
        entries.put("DEPENDENCIES.sha256", checksums.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Path writeZip(Map<String, byte[]> entries) throws IOException {
        Path archive = tempDirectory.resolve("gemini-buildkit-" + VERSION + "-" + CLASSIFIER + ".zip");
        Files.write(archive, zip(entries));
        return archive;
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}

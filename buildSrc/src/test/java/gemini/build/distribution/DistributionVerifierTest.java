package gemini.build.distribution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributionVerifierTest {
    @TempDir
    Path tempDirectory;

    @Test
    void acceptsExpectedJarJarDistribution() throws IOException {
        Path archive = distribution(Map.of());
        assertDoesNotThrow(() -> DistributionVerifier.verify(archive));
    }

    @Test
    void rejectsForbiddenTopLevelAndShaderEntries() throws IOException {
        Path archive = distribution(Map.of(
                "org/json/JSONObject.class", new byte[]{1},
                "net/minecraft/Client.class", new byte[]{1},
                "assets/gemini/bad.spv", new byte[]{1},
                "io/github/refux/slang/Slang.class", new byte[]{1}));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DistributionVerifier.verify(archive));
        assertTrue(error.getMessage().contains("org.json was unpacked"));
        assertTrue(error.getMessage().contains("platform class packaged"));
        assertTrue(error.getMessage().contains("source/intermediate shader packaged"));
        assertTrue(error.getMessage().contains("Slang class/native packaged"));
    }

    @Test
    void rejectsUnexpectedNestedDependencyAndShaderCount() throws IOException {
        Map<String, byte[]> outer = new LinkedHashMap<>();
        outer.put("META-INF/jarjar/not-json.jar", zip(Map.of("example/Other.class", new byte[]{1})));
        outer.put("META-INF/jarjar/metadata.json", metadata("META-INF/jarjar/not-json.jar").getBytes(StandardCharsets.UTF_8));
        Path archive = writeZip("bad.jar", outer);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DistributionVerifier.verify(archive));
        assertTrue(error.getMessage().contains("expected exactly 111"));
        assertTrue(error.getMessage().contains("not the expected org.json:json artifact"));
    }

    private Path distribution(Map<String, byte[]> additions) throws IOException {
        Map<String, byte[]> outer = new LinkedHashMap<>();
        for (int i = 0; i < 111; i++) {
            outer.put("assets/gemini/shaders/generated/shader-" + i + (i % 2 == 0 ? ".vsh" : ".fsh"), new byte[]{1});
        }
        String nestedPath = "META-INF/jarjar/json-test.jar";
        outer.put(nestedPath, zip(Map.of(
                "org/json/JSONObject.class", new byte[]{1},
                "META-INF/maven/org.json/json/pom.properties",
                "groupId=org.json\nartifactId=json\nversion=test\n".getBytes(StandardCharsets.UTF_8))));
        outer.put("META-INF/jarjar/metadata.json", metadata(nestedPath).getBytes(StandardCharsets.UTF_8));
        outer.putAll(additions);
        return writeZip("distribution.jar", outer);
    }

    private static String metadata(String path) {
        return "{\"jars\":[{\"identifier\":{\"group\":\"org.json\",\"artifact\":\"json\"},\"path\":\"" + path + "\"}]}";
    }

    private Path writeZip(String name, Map<String, byte[]> entries) throws IOException {
        Path path = tempDirectory.resolve(name);
        java.nio.file.Files.write(path, zip(entries));
        return path;
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
}

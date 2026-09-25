package gemini.build.distribution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
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
    void acceptsVideoOnlyJarJarDistribution() throws IOException {
        assertDoesNotThrow(() -> DistributionVerifier.verify(distribution(Map.of()), generatedShaders()));
    }

    @Test
    void reportsShadersMissingFromAndStrayInTheDistribution() throws IOException {
        Path root = generatedShaders();
        java.nio.file.Files.write(root.resolve("assets/gemini/shaders/generated/only-in-sources.vsh"),
                new byte[]{1});
        Path archive = distribution(Map.of("assets/gemini/shaders/generated/stray.fsh", new byte[]{1}));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DistributionVerifier.verify(archive, root));
        assertTrue(error.getMessage().contains("generated shaders missing from the distribution"));
        assertTrue(error.getMessage().contains("only-in-sources.vsh"));
        assertTrue(error.getMessage().contains("shaders packaged without a generated source"));
        assertTrue(error.getMessage().contains("stray.fsh"));
    }

    @Test
    void rejectsDistributionVerifiedAgainstAnEmptySlangOutput() throws IOException {
        Path root = java.nio.file.Files.createDirectories(tempDirectory.resolve("empty-slang-output"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DistributionVerifier.verify(distribution(Map.of()), root));
        assertTrue(error.getMessage().contains("no generated .vsh/.fsh resources"));
    }

    @Test
    void rejectsForbiddenTopLevelAndShaderEntries() throws IOException {
        Path archive = distribution(Map.of(
                "org/json/JSONObject.class", new byte[]{1},
                "net/minecraft/Client.class", new byte[]{1},
                "assets/gemini/bad.spv", new byte[]{1},
                "io/github/refux/slang/Slang.class", new byte[]{1}));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DistributionVerifier.verify(archive, generatedShaders()));
        assertTrue(error.getMessage().contains("unpacked at the archive top level"));
        assertTrue(error.getMessage().contains("platform class packaged"));
        assertTrue(error.getMessage().contains("source/intermediate shader packaged"));
        assertTrue(error.getMessage().contains("Slang class/native packaged"));
    }

    @Test
    void rejectsOpenCvAndUnexpectedDependencies() throws IOException {
        Map<String, byte[]> additions = Map.of(
                "META-INF/jarjar/opencv-4.13.0.jar",
                zip(Map.of("org/bytedeco/opencv/global/opencv_core.class", new byte[]{1})));
        Path archive = distribution(additions, List.of(
                dependency("org.bytedeco", "opencv", "META-INF/jarjar/opencv-4.13.0.jar")));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DistributionVerifier.verify(archive, generatedShaders()));
        assertTrue(error.getMessage().contains("unexpected JarJar dependency set"));
        assertTrue(error.getMessage().contains("OpenCV payload packaged"));
    }

    private Path distribution(Map<String, byte[]> additions) throws IOException {
        return distribution(additions, List.of());
    }

    /** Stand-in for the compileSlangShaders output the JAR is checked against. */
    private Path generatedShaders() throws IOException {
        Path root = tempDirectory.resolve("slang-output");
        for (String shader : shaderResources()) {
            Path file = root.resolve(shader);
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.write(file, new byte[]{1});
        }
        return root;
    }

    private static List<String> shaderResources() {
        List<String> shaders = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            shaders.add("assets/gemini/shaders/generated/shader-" + i + (i % 2 == 0 ? ".vsh" : ".fsh"));
        }
        return shaders;
    }

    private Path distribution(Map<String, byte[]> additions, List<String> extraMetadata) throws IOException {
        Map<String, byte[]> outer = new LinkedHashMap<>();
        for (String shader : shaderResources()) {
            outer.put(shader, new byte[]{1});
        }
        for (String className : List.of(
                "Frame", "Frame$Type",
                "FrameGrabber", "FrameGrabber$1", "FrameGrabber$Array", "FrameGrabber$Exception",
                "FrameGrabber$ImageMode", "FrameGrabber$PropertyEditor", "FrameGrabber$SampleMode",
                "FFmpegFrameGrabber", "FFmpegFrameGrabber$1", "FFmpegFrameGrabber$Exception",
                "FFmpegFrameGrabber$ReadCallback", "FFmpegFrameGrabber$SeekCallback",
                "FFmpegLogCallback")) {
            outer.put("org/bytedeco/javacv/" + className + ".class", new byte[]{1});
        }

        // Flat native stack (javacpp + ffmpeg classes and platform DLLs).
        outer.put("org/bytedeco/javacpp/Loader.class", new byte[]{1});
        outer.put("org/bytedeco/javacpp/windows-x86_64/jnijavacpp.dll", new byte[]{1});
        outer.put("org/bytedeco/ffmpeg/global/avcodec.class", new byte[]{1});
        outer.put("org/bytedeco/ffmpeg/windows-x86_64/jniavcodec.dll", new byte[]{1});

        Map<String, byte[]> nested = expectedNestedJars();
        outer.putAll(nested);
        outer.putAll(additions);

        List<String> metadata = new java.util.ArrayList<>(List.of(
                dependency("org.json", "json", "META-INF/jarjar/json-test.jar")));
        metadata.addAll(extraMetadata);
        outer.put("META-INF/jarjar/metadata.json",
                ("{\"jars\":[" + String.join(",", metadata) + "]}").getBytes(StandardCharsets.UTF_8));
        return writeZip("distribution.jar", outer);
    }

    private static Map<String, byte[]> expectedNestedJars() throws IOException {
        // The JavaCV/FFmpeg stack is unpacked flat into the JAR root; only
        // org.json stays nested under META-INF/jarjar/.
        Map<String, byte[]> nested = new LinkedHashMap<>();
        nested.put("META-INF/jarjar/json-test.jar", zip(Map.of("org/json/JSONObject.class", new byte[]{1})));
        return nested;
    }

    private static String dependency(String group, String artifact, String path) {
        return "{\"identifier\":{\"group\":\"" + group + "\",\"artifact\":\"" + artifact
                + "\"},\"path\":\"" + path + "\"}";
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

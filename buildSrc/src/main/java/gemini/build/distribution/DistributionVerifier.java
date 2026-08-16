package gemini.build.distribution;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class DistributionVerifier {
    private static final String JARJAR_PREFIX = "META-INF/jarjar/";
    private static final String JARJAR_METADATA = JARJAR_PREFIX + "metadata.json";
    private static final Pattern IDENTIFIER = Pattern.compile(
            "\\\"group\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"artifact\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    // Only org.json stays nested. The JavaCV/FFmpeg native stack is unpacked
    // flat into the JAR root (see build.gradle installableJar) so JavaCPP can
    // load its natives under the FML modular classloader.
    private static final Map<String, Integer> EXPECTED_DEPENDENCIES = Map.of(
            "org.json:json", 1);
    /** JavaCV classes FFmpegFrameGrabber needs at runtime (must be present). */
    private static final Set<String> REQUIRED_JAVACV_CLASSES = Set.of(
            "Frame", "Frame$Type",
            "FrameGrabber", "FrameGrabber$1", "FrameGrabber$Array", "FrameGrabber$Exception",
            "FrameGrabber$ImageMode", "FrameGrabber$PropertyEditor", "FrameGrabber$SampleMode",
            "FFmpegFrameGrabber", "FFmpegFrameGrabber$1", "FFmpegFrameGrabber$Exception",
            "FFmpegFrameGrabber$ReadCallback", "FFmpegFrameGrabber$SeekCallback",
            "FFmpegLogCallback");

    private DistributionVerifier() {
    }

    public static void verify(Path archive) throws IOException {
        List<String> errors = new ArrayList<>();
        Map<String, byte[]> nestedJars = new LinkedHashMap<>();
        Set<String> javaCvClasses = new java.util.HashSet<>();
        boolean hasJavaCppClasses = false;
        boolean hasJavaCppNative = false;
        boolean hasFfmpegClasses = false;
        boolean hasFfmpegNative = false;
        String metadata = null;
        int shaderCount = 0;

        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                String javaCvClass = javaCvClassName(name);
                if (javaCvClass != null) {
                    javaCvClasses.add(javaCvClass);
                }
                if (name.startsWith("org/bytedeco/javacpp/") && lower.endsWith(".class")) {
                    hasJavaCppClasses = true;
                }
                if (name.startsWith("org/bytedeco/ffmpeg/") && lower.endsWith(".class")) {
                    hasFfmpegClasses = true;
                }
                boolean nativeLib = lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib");
                if (nativeLib && name.startsWith("org/bytedeco/javacpp/")) {
                    hasJavaCppNative = true;
                }
                if (nativeLib && name.startsWith("org/bytedeco/ffmpeg/")) {
                    hasFfmpegNative = true;
                }
                if (name.endsWith(".vsh") || name.endsWith(".fsh")) {
                    shaderCount++;
                }
                rejectTopLevel(name, errors);
                if (name.equals(JARJAR_METADATA)) {
                    metadata = readString(zip.getInputStream(entry));
                } else if (name.startsWith(JARJAR_PREFIX) && name.endsWith(".jar")) {
                    nestedJars.put(name, zip.getInputStream(entry).readAllBytes());
                }
            }
        }

        if (shaderCount != 113) {
            errors.add("expected exactly 113 .vsh/.fsh resources, found " + shaderCount);
        }
        if (metadata == null) {
            errors.add("missing " + JARJAR_METADATA);
        } else {
            verifyMetadata(metadata, nestedJars.keySet(), errors);
        }
        if (!javaCvClasses.containsAll(REQUIRED_JAVACV_CLASSES)) {
            errors.add("missing runtime-critical JavaCV classes: "
                    + javaCvClasses + ", required " + REQUIRED_JAVACV_CLASSES);
        }
        verifyNestedJars(nestedJars, errors);
        verifyFlatNativeStack(hasJavaCppClasses, hasJavaCppNative, hasFfmpegClasses, hasFfmpegNative, errors);

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Distribution verification failed for " + archive + ":\n - "
                    + String.join("\n - ", errors));
        }
    }

    private static void verifyFlatNativeStack(boolean hasJavaCppClasses, boolean hasJavaCppNative,
                                              boolean hasFfmpegClasses, boolean hasFfmpegNative,
                                              List<String> errors) {
        if (!hasJavaCppClasses) {
            errors.add("JavaCPP classes are not unpacked in the JAR root");
        }
        if (!hasFfmpegClasses) {
            errors.add("FFmpeg classes are not unpacked in the JAR root");
        }
        if (!hasJavaCppNative) {
            errors.add("JavaCPP native libraries are not unpacked in the JAR root");
        }
        if (!hasFfmpegNative) {
            errors.add("FFmpeg native libraries are not unpacked in the JAR root");
        }
    }

    private static void rejectTopLevel(String name, List<String> errors) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".spv") || lower.endsWith(".slang")) {
            errors.add("source/intermediate shader packaged: " + name);
        }
        // The JavaCV/FFmpeg stack is deliberately unpacked flat (see
        // verifyFlatNativeStack); only stray org/json content is rejected.
        if (name.startsWith("org/json/")) {
            errors.add("nested dependency was unpacked at the archive top level: " + name);
        }
        if (name.startsWith("net/minecraft/") || name.startsWith("net/neoforged/")) {
            errors.add("platform class packaged: " + name);
        }
        if (name.startsWith("buildSrc/") || name.contains("/buildSrc/")) {
            errors.add("buildSrc content packaged: " + name);
        }
        if (isSlangPayload(name, lower)) {
            errors.add("Slang class/native packaged: " + name);
        }
    }

    private static String javaCvClassName(String name) {
        String prefix = "org/bytedeco/javacv/";
        if (!name.startsWith(prefix) || !name.endsWith(".class")) {
            return null;
        }
        return name.substring(prefix.length(), name.length() - ".class".length());
    }

    private static boolean isSlangPayload(String name, String lower) {
        if (name.startsWith("io/github/refux/slang/") && lower.endsWith(".class")) {
            return true;
        }
        if (lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib")) {
            // The JavaCV/FFmpeg platform DLLs under org/bytedeco/ are the
            // intended flat native stack, not Slang payload.
            return !name.startsWith("org/bytedeco/");
        }
        return lower.contains("slang-java") || lower.contains("slang_glsl")
                || lower.contains("slang-llvm") || lower.contains("slang_rt");
    }

    private static void verifyMetadata(String metadata, Set<String> nestedPaths, List<String> errors) {
        String compact = metadata.replaceAll("\\s+", "");
        for (String path : nestedPaths) {
            if (!compact.contains("\"path\":\"" + path + "\"")) {
                errors.add("JarJar metadata does not reference nested path " + path);
            }
        }

        Map<String, Integer> actual = new LinkedHashMap<>();
        Matcher matcher = IDENTIFIER.matcher(metadata);
        while (matcher.find()) {
            actual.merge(matcher.group(1) + ":" + matcher.group(2), 1, Integer::sum);
        }
        if (!EXPECTED_DEPENDENCIES.equals(actual)) {
            errors.add("unexpected JarJar dependency set: " + actual + ", expected " + EXPECTED_DEPENDENCIES);
        }
        if (nestedPaths.size() != EXPECTED_DEPENDENCIES.values().stream().mapToInt(Integer::intValue).sum()) {
            errors.add("unexpected nested JarJar count: " + nestedPaths.size());
        }
    }

    private static void verifyNestedJars(Map<String, byte[]> nestedJars, List<String> errors) throws IOException {
        for (Map.Entry<String, byte[]> nested : nestedJars.entrySet()) {
            String path = nested.getKey();
            NestedContents contents = inspect(nested.getValue());
            boolean valid;
            if (path.matches(".*/json-[^/]+\\.jar")) {
                valid = contents.entries.contains("org/json/JSONObject.class");
            } else if (path.matches(".*/javacpp-[^/]+-(windows|linux|macosx)-[^/]+\\.jar")) {
                valid = contents.hasNative && contents.hasJavaCppPayload;
            } else if (path.matches(".*/javacpp-[^/]+\\.jar")) {
                valid = contents.entries.contains("org/bytedeco/javacpp/Loader.class");
            } else if (path.matches(".*/ffmpeg-[^/]+-(windows|linux|macosx)-[^/]+\\.jar")) {
                valid = contents.hasNative && contents.hasFfmpegPayload;
            } else if (path.matches(".*/ffmpeg-[^/]+\\.jar")) {
                valid = contents.entries.contains("org/bytedeco/ffmpeg/global/avcodec.class");
            } else {
                valid = false;
            }

            if (!valid) {
                errors.add("unexpected or invalid nested dependency: " + path);
            }
            if (contents.hasOpenCvPayload) {
                errors.add("OpenCV payload packaged in video-only distribution: " + path);
            }
        }
    }

    private static NestedContents inspect(byte[] bytes) throws IOException {
        Set<String> entries = new java.util.HashSet<>();
        boolean hasNative = false;
        boolean hasJavaCppPayload = false;
        boolean hasFfmpegPayload = false;
        boolean hasOpenCvPayload = false;
        try (ZipInputStream nested = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = nested.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                entries.add(name);
                hasNative |= lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib");
                hasJavaCppPayload |= name.startsWith("org/bytedeco/javacpp/");
                hasFfmpegPayload |= name.startsWith("org/bytedeco/ffmpeg/");
                hasOpenCvPayload |= name.startsWith("org/bytedeco/opencv/") || lower.contains("opencv");
            }
        }
        return new NestedContents(entries, hasNative, hasJavaCppPayload, hasFfmpegPayload, hasOpenCvPayload);
    }

    private static String readString(InputStream input) throws IOException {
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record NestedContents(Set<String> entries, boolean hasNative, boolean hasJavaCppPayload,
                                  boolean hasFfmpegPayload, boolean hasOpenCvPayload) {
    }
}

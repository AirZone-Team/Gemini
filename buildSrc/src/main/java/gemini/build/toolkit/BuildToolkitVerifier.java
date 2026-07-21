package gemini.build.toolkit;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class BuildToolkitVerifier {
    private static final Set<String> SUPPORTED_CLASSIFIERS = Set.of(
            "windows-x86_64", "windows-aarch64",
            "linux-x86_64", "linux-aarch64",
            "macos-x86_64", "macos-aarch64");
    private static final Set<String> REQUIRED_ENTRIES = Set.of(
            "build.gradle", "settings.gradle", "gradle.properties",
            "gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties", "gradle/slang-platforms.gradle",
            "buildSrc/build.gradle", "README.md", "LICENSE", "BUILDING.md",
            "THIRD-PARTY-NOTICES", "BUILD-INFO.properties", "DEPENDENCIES.sha256",
            "src/main/slang/variants.json");
    private static final List<String> DISALLOWED_SEGMENTS = List.of(
            ".git/", ".gradle/", ".gradle-local/", ".idea/", ".claude/",
            "build/", "run/", "repo/", "tools/", "secrets/");

    private BuildToolkitVerifier() {
    }

    public static void verify(Path archive) throws IOException {
        List<String> errors = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Map<String, byte[]> vendorJars = new HashMap<>();
        Properties metadata = null;
        String checksums = null;

        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                String name = normalize(entry.getName());
                if (!names.add(name)) {
                    errors.add("duplicate ZIP entry: " + name);
                }
                rejectDisallowed(name, errors);
                if (entry.isDirectory()) {
                    continue;
                }
                if (name.equals("BUILD-INFO.properties")) {
                    metadata = loadProperties(zip.getInputStream(entry));
                } else if (name.equals("DEPENDENCIES.sha256")) {
                    checksums = readString(zip.getInputStream(entry));
                } else if (name.startsWith("vendor/slang/") && name.endsWith(".jar")) {
                    vendorJars.put(name, zip.getInputStream(entry).readAllBytes());
                }
            }
        }

        for (String required : REQUIRED_ENTRIES) {
            if (!names.contains(required)) {
                errors.add("missing required entry: " + required);
            }
        }
        if (names.stream().noneMatch(name -> name.startsWith("buildSrc/src/main/"))) {
            errors.add("missing buildSrc source tree");
        }
        if (names.stream().noneMatch(name -> name.startsWith("src/main/java/"))) {
            errors.add("missing main Java source tree");
        }
        if (names.stream().noneMatch(name -> name.startsWith("src/main/resources/"))) {
            errors.add("missing main resource tree");
        }
        if (names.stream().noneMatch(name -> name.endsWith(".slang") && name.startsWith("src/main/slang/"))) {
            errors.add("missing Slang shader sources");
        }

        if (metadata == null) {
            errors.add("missing or unreadable BUILD-INFO.properties");
        } else {
            verifyMetadata(archive, metadata, vendorJars, checksums, errors);
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Build toolkit verification failed for " + archive + ":\n - "
                    + String.join("\n - ", errors));
        }
    }

    private static void verifyMetadata(Path archive, Properties metadata, Map<String, byte[]> vendorJars,
                                       String checksums, List<String> errors) {
        String classifier = required(metadata, "classifier", errors);
        String projectVersion = required(metadata, "projectVersion", errors);
        String slangVersion = required(metadata, "slangJavaVersion", errors);
        String slangBuildTag = required(metadata, "slangBuildTag", errors);
        String runtimeJars = required(metadata, "slangRuntimeJars", errors);
        String nativeJar = required(metadata, "slangNativeJar", errors);
        String formatVersion = required(metadata, "formatVersion", errors);
        if (!"1".equals(formatVersion)) {
            errors.add("unsupported buildkit formatVersion: " + formatVersion);
        }
        if (!SUPPORTED_CLASSIFIERS.contains(classifier)) {
            errors.add("unsupported metadata classifier: " + classifier);
            return;
        }
        String expectedArchiveSuffix = "-" + projectVersion + "-" + classifier + ".zip";
        if (!archive.getFileName().toString().endsWith(expectedArchiveSuffix)) {
            errors.add("archive filename does not match metadata version/classifier: " + archive.getFileName());
        }

        Set<String> expectedJarNames = new TreeSet<>();
        if (!runtimeJars.isBlank()) {
            for (String jar : runtimeJars.split(",")) {
                expectedJarNames.add("vendor/slang/" + jar.trim());
            }
        }
        expectedJarNames.add("vendor/slang/" + nativeJar);
        if (!vendorJars.keySet().equals(expectedJarNames)) {
            errors.add("expected exactly vendor JARs " + expectedJarNames + ", found " + new TreeSet<>(vendorJars.keySet()));
        }
        long nativeCount = vendorJars.keySet().stream()
                .filter(name -> name.startsWith("vendor/slang/slang-java-natives-"))
                .count();
        if (nativeCount != 1) {
            errors.add("expected exactly one slang-java-natives JAR, found " + nativeCount);
        }
        String expectedNative = "slang-java-natives-" + slangVersion + "-" + classifier + ".jar";
        if (!expectedNative.equals(nativeJar)) {
            errors.add("native JAR metadata does not match version/classifier: " + nativeJar);
        }

        verifyChecksums(checksums, vendorJars, errors);
        byte[] nativeBytes = vendorJars.get("vendor/slang/" + nativeJar);
        if (nativeBytes != null) {
            verifyNativeJar(nativeBytes, classifier, slangBuildTag, errors);
        }
    }

    private static void verifyChecksums(String text, Map<String, byte[]> vendorJars, List<String> errors) {
        if (text == null) {
            errors.add("missing DEPENDENCIES.sha256");
            return;
        }
        Map<String, String> declared = new HashMap<>();
        for (String line : text.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.trim().split("\\s+", 2);
            if (fields.length != 2 || !fields[0].matches("[0-9a-fA-F]{64}")) {
                errors.add("invalid dependency checksum line: " + line);
                continue;
            }
            declared.put(normalize(fields[1].replaceFirst("^\\*", "")), fields[0].toLowerCase(Locale.ROOT));
        }
        if (!declared.keySet().equals(vendorJars.keySet())) {
            errors.add("checksum entries do not exactly match vendor JARs");
        }
        vendorJars.forEach((name, bytes) -> {
            String expected = declared.get(name);
            if (expected != null && !expected.equals(sha256(bytes))) {
                errors.add("SHA-256 mismatch for " + name);
            }
        });
    }

    private static void verifyNativeJar(byte[] bytes, String classifier, String slangBuildTag,
                                        List<String> errors) {
        String[] parts = classifier.split("-", 2);
        String os = parts[0].equals("macos") ? "macos" : parts[0];
        String arch = parts[1];
        String expectedIndex = "META-INF/natives/" + os + "/" + arch + "/index.txt";
        int indexCount = 0;
        String index = null;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = normalize(entry.getName());
                if (name.matches("META-INF/natives/[^/]+/[^/]+/index\\.txt")) {
                    indexCount++;
                    if (name.equals(expectedIndex)) {
                        index = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (IOException exception) {
            errors.add("cannot inspect native JAR: " + exception.getMessage());
            return;
        }
        if (indexCount != 1 || index == null) {
            errors.add("native JAR must contain exactly matching " + expectedIndex + ", found " + indexCount + " native indexes");
            return;
        }
        String firstLine = index.lines().findFirst().orElse("");
        if (!firstLine.startsWith("slang " + slangBuildTag + " ")) {
            errors.add("native index Slang build tag does not match " + slangBuildTag + ": " + firstLine);
        }
    }

    private static void rejectDisallowed(String name, List<String> errors) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/") || lower.contains("../") || lower.matches("^[a-z]:.*")) {
            errors.add("unsafe ZIP entry: " + name);
        }
        for (String segment : DISALLOWED_SEGMENTS) {
            if (lower.startsWith(segment) || lower.contains("/" + segment)) {
                errors.add("disallowed entry: " + name);
                break;
            }
        }
        if (lower.endsWith(".spv") || lower.contains("generated/slang")) {
            errors.add("generated shader output packaged: " + name);
        }
        if (lower.endsWith(".env") || lower.contains("credentials") || lower.contains("secret")) {
            errors.add("possible secret material packaged: " + name);
        }
    }

    private static String required(Properties properties, String name, List<String> errors) {
        String value = properties.getProperty(name, "").trim();
        if (value.isEmpty()) {
            errors.add("missing metadata property: " + name);
        }
        return value;
    }

    private static Properties loadProperties(InputStream input) throws IOException {
        try (input) {
            Properties properties = new Properties();
            properties.load(input);
            return properties;
        }
    }

    private static String readString(InputStream input) throws IOException {
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

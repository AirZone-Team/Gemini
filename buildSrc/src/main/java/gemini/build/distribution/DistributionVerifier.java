package gemini.build.distribution;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class DistributionVerifier {
    private static final String JARJAR_PREFIX = "META-INF/jarjar/";
    private static final String JARJAR_METADATA = JARJAR_PREFIX + "metadata.json";

    private DistributionVerifier() {
    }

    public static void verify(Path archive) throws IOException {
        List<String> errors = new ArrayList<>();
        byte[] nestedJar = null;
        String nestedPath = null;
        String metadata = null;
        int shaderCount = 0;

        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.endsWith(".vsh") || name.endsWith(".fsh")) {
                    shaderCount++;
                }
                reject(name, errors);
                if (name.equals(JARJAR_METADATA)) {
                    metadata = readString(zip.getInputStream(entry));
                } else if (name.startsWith(JARJAR_PREFIX) && name.endsWith(".jar")) {
                    if (nestedJar != null) {
                        errors.add("more than one nested JarJar dependency: " + nestedPath + ", " + name);
                    } else {
                        nestedPath = name;
                        nestedJar = zip.getInputStream(entry).readAllBytes();
                    }
                }
            }
        }

        if (shaderCount != 111) {
            errors.add("expected exactly 111 .vsh/.fsh resources, found " + shaderCount);
        }
        if (metadata == null) {
            errors.add("missing " + JARJAR_METADATA);
        }
        if (nestedJar == null) {
            errors.add("missing nested JarJar dependency");
        } else {
            verifyNestedJson(nestedJar, errors);
        }
        if (metadata != null && nestedPath != null) {
            verifyMetadata(metadata, nestedPath, errors);
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Distribution verification failed for " + archive + ":\n - "
                    + String.join("\n - ", errors));
        }
    }

    private static void reject(String name, List<String> errors) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".spv") || lower.endsWith(".slang")) {
            errors.add("source/intermediate shader packaged: " + name);
        }
        if (name.startsWith("org/json/")) {
            errors.add("org.json was unpacked at the archive top level: " + name);
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

    private static boolean isSlangPayload(String name, String lower) {
        if (name.startsWith("io/github/refux/slang/") && lower.endsWith(".class")) {
            return true;
        }
        return lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib")
                || lower.contains("slang-java") || lower.contains("slang_glsl")
                || lower.contains("slang-llvm") || lower.contains("slang_rt");
    }

    private static void verifyMetadata(String metadata, String nestedPath, List<String> errors) {
        String compact = metadata.replaceAll("\\s+", "");
        if (!compact.contains("\"group\":\"org.json\"")
                || !compact.contains("\"artifact\":\"json\"")) {
            errors.add("JarJar metadata does not identify org.json:json");
        }
        if (!compact.contains("\"path\":\"" + nestedPath + "\"")) {
            errors.add("JarJar metadata does not reference nested path " + nestedPath);
        }
    }

    private static void verifyNestedJson(byte[] bytes, List<String> errors) throws IOException {
        boolean hasJsonClass = false;
        boolean identifiedAsJson = false;
        int fileCount = 0;
        try (ZipInputStream nested = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = nested.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                fileCount++;
                String name = entry.getName();
                if (name.equals("org/json/JSONObject.class")) {
                    hasJsonClass = true;
                }
                if (name.endsWith("/pom.properties")) {
                    Properties properties = new Properties();
                    properties.load(nested);
                    if ("org.json".equals(properties.getProperty("groupId"))
                            && "json".equals(properties.getProperty("artifactId"))) {
                        identifiedAsJson = true;
                    }
                }
            }
        }
        if (fileCount == 0) {
            errors.add("nested dependency is empty");
        }
        if (!hasJsonClass || !identifiedAsJson) {
            errors.add("nested dependency is not the expected org.json:json artifact");
        }
    }

    private static String readString(InputStream input) throws IOException {
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

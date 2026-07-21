package gemini.build.slang;

import org.gradle.api.GradleException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SlangManifest {
    private static final Pattern DEFINE = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Pattern ENTRY = Pattern.compile("\\s*\\\"([^\\\"]+)\\\"\\s*:\\s*\\[(.*?)]\\s*(?:,|$)", Pattern.DOTALL);
    private static final Pattern STRING = Pattern.compile("\\\"([^\\\"]+)\\\"");

    record ShaderEntry(Path source, String moduleName, String stage, String outputName, String define) {
        String extension() {
            return stage.equals("vertex") ? ".vsh" : ".fsh";
        }
    }

    private SlangManifest() {
    }

    static List<ShaderEntry> load(Path sourceRoot, Path manifestPath) throws IOException {
        Map<String, List<String>> variants = parse(Files.readString(manifestPath, StandardCharsets.UTF_8));
        List<Path> sources;
        try (var stream = Files.walk(sourceRoot)) {
            sources = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".slang"))
                    .sorted()
                    .toList();
        }

        List<ShaderEntry> entries = new ArrayList<>();
        Set<String> sourceKeys = new HashSet<>();
        Set<String> outputs = new HashSet<>();
        for (Path source : sources) {
            String relative = sourceRoot.relativize(source).toString().replace('\\', '/');
            String suffix;
            String stage;
            if (relative.endsWith(".vert.slang")) {
                suffix = ".vert.slang";
                stage = "vertex";
            } else if (relative.endsWith(".frag.slang")) {
                suffix = ".frag.slang";
                stage = "fragment";
            } else {
                throw new GradleException("Slang shader must end in .vert.slang or .frag.slang: " + source);
            }
            sourceKeys.add(relative);
            String baseName = relative.substring(0, relative.length() - suffix.length());
            add(entries, outputs, source, baseName, stage, baseName, null);
            for (String define : variants.getOrDefault(relative, List.of())) {
                add(entries, outputs, source, baseName, stage,
                        baseName + "__" + define.toLowerCase(Locale.ROOT), define);
            }
        }
        for (String key : variants.keySet()) {
            if (!sourceKeys.contains(key)) {
                throw new GradleException("Variant manifest references a missing shader: " + key);
            }
        }
        return List.copyOf(entries);
    }

    private static void add(List<ShaderEntry> entries, Set<String> outputs, Path source,
                            String moduleName, String stage, String outputName, String define) {
        String extension = stage.equals("vertex") ? ".vsh" : ".fsh";
        if (!outputs.add(outputName + extension)) {
            throw new GradleException("Multiple Slang entries map to " + outputName + extension);
        }
        entries.add(new ShaderEntry(source, moduleName.replace('/', '_'), stage, outputName, define));
    }

    static Map<String, List<String>> parse(String json) {
        String stripped = json.replaceAll("(?s)/\\*.*?\\*/|//[^\\n]*", "").trim();
        if (!stripped.startsWith("{") || !stripped.endsWith("}")) {
            throw new GradleException("Variant manifest must be a JSON object");
        }
        String body = stripped.substring(1, stripped.length() - 1).trim();
        Map<String, List<String>> result = new LinkedHashMap<>();
        Matcher entries = ENTRY.matcher(body);
        int consumed = 0;
        while (entries.find()) {
            if (!body.substring(consumed, entries.start()).trim().isEmpty()) {
                throw new GradleException("Unsupported variant manifest JSON near: " + body.substring(consumed));
            }
            String key = entries.group(1);
            if (result.containsKey(key)) {
                throw new GradleException("Duplicate variant manifest key: " + key);
            }
            List<String> defines = new ArrayList<>();
            Set<String> unique = new HashSet<>();
            Matcher strings = STRING.matcher(entries.group(2));
            int valuesConsumed = 0;
            while (strings.find()) {
                String gap = entries.group(2).substring(valuesConsumed, strings.start()).replace(",", "").trim();
                if (!gap.isEmpty()) {
                    throw new GradleException("Variant values must be strings for " + key);
                }
                String define = strings.group(1);
                if (!DEFINE.matcher(define).matches()) {
                    throw new GradleException("Invalid Slang variant define: " + define);
                }
                if (!unique.add(define)) {
                    throw new GradleException("Duplicate Slang variant define " + define + " for " + key);
                }
                defines.add(define);
                valuesConsumed = strings.end();
            }
            if (!entries.group(2).substring(valuesConsumed).replace(",", "").trim().isEmpty()) {
                throw new GradleException("Variant values must be strings for " + key);
            }
            result.put(key, List.copyOf(defines));
            consumed = entries.end();
        }
        if (!body.substring(consumed).trim().isEmpty()) {
            throw new GradleException("Unsupported variant manifest JSON near: " + body.substring(consumed));
        }
        return result;
    }
}

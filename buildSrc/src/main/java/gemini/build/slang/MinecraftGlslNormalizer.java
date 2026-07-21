package gemini.build.slang;

import org.gradle.api.GradleException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MinecraftGlslNormalizer {
    static final String VERSION = "2";

    enum ShaderStage {
        VERTEX,
        FRAGMENT
    }

    private static final Pattern VERSION_LINE = Pattern.compile("(?m)^#version\\s+\\d+\\s*$");
    private static final Pattern DRAW_PARAMETERS_EXTENSION = Pattern.compile(
            "(?m)^#extension\\s+GL_ARB_shader_draw_parameters\\s*:\\s*require\\s*\\n");
    private static final Pattern BINDING_LAYOUT = Pattern.compile(
            "(?m)^layout\\(binding\\s*=\\s*\\d+\\)\\s*\\n");
    private static final Pattern SET_BINDING_LAYOUT = Pattern.compile(
            "(?m)^layout\\(set\\s*=\\s*\\d+\\s*,\\s*binding\\s*=\\s*\\d+\\)\\s*\\n");
    private static final Pattern MATRIX_LAYOUT = Pattern.compile(
            "(?m)^layout\\((?:row|column)_major\\)\\s+(?:uniform|buffer);\\s*\\n");
    private static final Pattern PARAMETER_BLOCK = Pattern.compile(
            "block_SLANG_ParameterGroup_([A-Za-z_][A-Za-z0-9_]*)_\\d+");
    private static final Pattern UNIFORM_BLOCK = Pattern.compile(
            "(?s)(layout\\([^)]*\\)\\s+uniform\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{)(.*?)(\\}\\s*)([A-Za-z_][A-Za-z0-9_]*)\\s*;");
    private static final Pattern BLOCK_MEMBER = Pattern.compile(
            "(?m)^\\s*(?:[A-Za-z_][A-Za-z0-9_]*\\s+)+([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\[[^]]+])?\\s*;");
    private static final Pattern INTERFACE = Pattern.compile(
            "(?m)^((?:(?:flat|smooth|noperspective|centroid|sample)\\s+)*)layout\\(location\\s*=\\s*(\\d+)\\)\\s*\\n\\s*((?:(?:flat|smooth|noperspective|centroid|sample)\\s+)*)(in|out)\\s+([^;\\n]+?)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;");
    private static final Pattern VERTEX_INDEX = Pattern.compile(
            "uint\\(gl_VertexIndex\\s*-\\s*gl_BaseVertex\\)");
    private static final Pattern INSTANCE_INDEX = Pattern.compile(
            "uint\\(gl_InstanceIndex\\s*-\\s*gl_BaseInstance\\)");
    private static final Pattern LEVEL_DECLARATION = Pattern.compile(
            "(?m)^\\s*int\\s+numberOfLevels_\\d+;\\s*\\n");
    private static final Pattern LEVEL_ASSIGNMENT = Pattern.compile(
            ",\\s*\\(\\(numberOfLevels_\\d+\\)\\s*=\\s*textureQueryLevels\\([^\\n]+\\)\\);");
    private static final Pattern FORBIDDEN = Pattern.compile(
            "layout\\(location|layout\\(set\\s*=|block_SLANG_ParameterGroup_|gl_BaseVertex|gl_BaseInstance|numberOfLevels_\\d+");

    private MinecraftGlslNormalizer() {
    }

    static String normalize(String source, ShaderStage stage) {
        String text = source.replace("\r\n", "\n").replace('\r', '\n');
        text = VERSION_LINE.matcher(text).replaceFirst("#version 330 core");
        if (!text.startsWith("#version 330 core")) {
            throw new GradleException("Generated GLSL does not declare a version");
        }
        text = DRAW_PARAMETERS_EXTENSION.matcher(text).replaceAll("");
        text = BINDING_LAYOUT.matcher(text).replaceAll("");
        text = SET_BINDING_LAYOUT.matcher(text).replaceAll("");
        text = MATRIX_LAYOUT.matcher(text).replaceAll("");
        text = PARAMETER_BLOCK.matcher(text).replaceAll("$1");
        text = normalizeUniformBlocks(text);
        text = normalizeInterfaces(text, stage);
        text = VERTEX_INDEX.matcher(text).replaceAll("uint(gl_VertexID)");
        text = INSTANCE_INDEX.matcher(text).replaceAll("uint(gl_InstanceID)");
        text = LEVEL_DECLARATION.matcher(text).replaceAll("");
        text = LEVEL_ASSIGNMENT.matcher(text).replaceAll(";");
        Matcher forbidden = FORBIDDEN.matcher(text);
        if (forbidden.find()) {
            throw new GradleException("Generated GLSL still contains unsupported Slang pattern: "
                    + forbidden.group());
        }
        return text.endsWith("\n") ? text : text + "\n";
    }

    private static String normalizeUniformBlocks(String source) {
        Matcher matcher = UNIFORM_BLOCK.matcher(source);
        StringBuffer output = new StringBuffer();
        List<String> instances = new ArrayList<>();
        Map<String, String> promotedMembers = new HashMap<>();
        while (matcher.find()) {
            String blockName = matcher.group(2);
            String instanceName = matcher.group(5);
            if (!blockName.equals(instanceName)) {
                continue;
            }
            Matcher members = BLOCK_MEMBER.matcher(matcher.group(3));
            while (members.find()) {
                String member = members.group(1);
                String previous = promotedMembers.putIfAbsent(member, blockName);
                if (previous != null && !previous.equals(blockName)) {
                    throw new GradleException("Uniform blocks " + previous + " and " + blockName
                            + " both promote member " + member);
                }
            }
            instances.add(instanceName);
            matcher.appendReplacement(output, Matcher.quoteReplacement(
                    matcher.group(1) + matcher.group(3) + matcher.group(4) + ";"));
        }
        matcher.appendTail(output);
        String text = output.toString();
        for (String instance : instances) {
            text = replaceIdentifierPrefix(text, instance, "");
            if (Pattern.compile("\\b" + Pattern.quote(instance) + "\\s*\\.").matcher(text).find()) {
                throw new GradleException("Uniform block instance access remains for " + instance);
            }
        }
        return text;
    }

    private static String normalizeInterfaces(String source, ShaderStage stage) {
        Matcher matcher = INTERFACE.matcher(source);
        StringBuffer output = new StringBuffer();
        Map<String, String> renames = new HashMap<>();
        Set<String> declarations = new HashSet<>();
        int fragmentOutputs = 0;
        while (matcher.find()) {
            int location = Integer.parseInt(matcher.group(2));
            String interpolation = matcher.group(1) + matcher.group(3);
            String direction = matcher.group(4);
            String type = matcher.group(5).trim();
            String oldName = matcher.group(6);
            String newName;
            if (stage == ShaderStage.VERTEX && direction.equals("in")) {
                newName = minecraftAttributeName(oldName);
            } else if ((stage == ShaderStage.VERTEX && direction.equals("out"))
                    || (stage == ShaderStage.FRAGMENT && direction.equals("in"))) {
                newName = "gemini_varying_" + location;
            } else if (stage == ShaderStage.FRAGMENT && direction.equals("out")) {
                fragmentOutputs++;
                if (fragmentOutputs > 1 || location != 0) {
                    throw new GradleException("GLSL 330 supports one unbound fragment output at location 0");
                }
                newName = oldName;
            } else {
                throw new GradleException("Unsupported shader interface: " + matcher.group());
            }
            String declarationKey = direction + ':' + newName;
            if (!declarations.add(declarationKey)) {
                throw new GradleException("Duplicate shader interface " + declarationKey);
            }
            String previous = renames.putIfAbsent(oldName, newName);
            if (previous != null && !previous.equals(newName)) {
                throw new GradleException("Shader interface " + oldName + " maps to multiple names");
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(
                    interpolation + direction + " " + type + " " + newName + ";"));
        }
        matcher.appendTail(output);
        String text = output.toString();
        for (Map.Entry<String, String> rename : renames.entrySet()) {
            if (!rename.getKey().equals(rename.getValue())) {
                text = replaceIdentifier(text, rename.getKey(), rename.getValue());
            }
        }
        return text;
    }

    private static String minecraftAttributeName(String generatedName) {
        String base = generatedName.endsWith("_0")
                ? generatedName.substring(0, generatedName.length() - 2) : generatedName;
        return switch (base.toUpperCase(Locale.ROOT)) {
            case "POSITION" -> "Position";
            case "COLOR" -> "Color";
            case "UV", "UV0" -> "UV0";
            case "UV1" -> "UV1";
            case "UV2" -> "UV2";
            case "NORMAL" -> "Normal";
            case "LINEWIDTH" -> "LineWidth";
            default -> throw new GradleException("Unsupported Minecraft vertex attribute: " + generatedName);
        };
    }

    private static String replaceIdentifier(String source, String identifier, String replacement) {
        return Pattern.compile("\\b" + Pattern.quote(identifier) + "\\b")
                .matcher(source).replaceAll(Matcher.quoteReplacement(replacement));
    }

    private static String replaceIdentifierPrefix(String source, String identifier, String replacement) {
        return Pattern.compile("\\b" + Pattern.quote(identifier) + "\\s*\\.\\s*")
                .matcher(source).replaceAll(Matcher.quoteReplacement(replacement));
    }
}

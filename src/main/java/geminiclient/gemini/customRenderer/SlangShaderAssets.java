package geminiclient.gemini.customRenderer;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Resolves offline-compiled Slang shader variants without depending on a
 * graphics backend. Both Minecraft's OpenGL and Vulkan devices consume the
 * same generated resource name.
 */
public final class SlangShaderAssets {
    private static final Pattern DEFINE_NAME = Pattern.compile("[A-Z][A-Z0-9_]*");

    private SlangShaderAssets() {
    }

    public static String variant(String shader, String define) {
        Objects.requireNonNull(shader, "shader");
        Objects.requireNonNull(define, "define");
        if (!DEFINE_NAME.matcher(define).matches()) {
            throw new IllegalArgumentException("Invalid Slang variant define: " + define);
        }
        return shader + "__" + define.toLowerCase(Locale.ROOT);
    }
}

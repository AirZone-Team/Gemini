package gemini.build.slang;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinecraftGlslNormalizerTest {
    @Test
    void normalizesSlangOutputForMinecraft() {
        String input = """
                #version 460
                #extension GL_ARB_shader_draw_parameters : require
                layout(column_major) uniform;
                layout(column_major) buffer;
                layout(binding = 4)
                layout(set = 1, binding = 2)
                uniform block_SLANG_ParameterGroup_GeminiParams_3 { vec4 color; };
                uint vertex = uint(gl_VertexIndex - gl_BaseVertex);
                uint instance = uint(gl_InstanceIndex - gl_BaseInstance);
                int numberOfLevels_1;
                value, ((numberOfLevels_1) = textureQueryLevels(tex));
                """;

        String normalized = MinecraftGlslNormalizer.normalize(
                input, MinecraftGlslNormalizer.ShaderStage.VERTEX);

        assertEquals("#version 330 core\nuniform GeminiParams { vec4 color; };\n"
                + "uint vertex = uint(gl_VertexID);\n"
                + "uint instance = uint(gl_InstanceID);\nvalue;\n", normalized);
        assertFalse(normalized.contains("\r"));
    }

    @Test
    void makesUniformBlocksAnonymousAndPromotesMembers() {
        String input = """
                #version 460
                layout(std140) uniform Projection
                {
                    mat4x4 ProjMat;
                } Projection;
                layout(std140) uniform DynamicTransforms
                {
                    mat4x4 ModelViewMat;
                    vec4 ColorModulator;
                }DynamicTransforms;
                void main() {
                    vec4 value = Projection.ProjMat * DynamicTransforms.ModelViewMat * DynamicTransforms.ColorModulator;
                }
                """;

        String normalized = MinecraftGlslNormalizer.normalize(
                input, MinecraftGlslNormalizer.ShaderStage.VERTEX);

        assertFalse(normalized.contains("} Projection;"));
        assertFalse(normalized.contains("}DynamicTransforms;"));
        assertFalse(normalized.contains("Projection.ProjMat"));
        assertFalse(normalized.contains("DynamicTransforms."));
        assertFalse(normalized.contains("unrelated.member"));
        assertEquals(true, normalized.contains("ProjMat * ModelViewMat * ColorModulator"));
    }

    @Test
    void normalizesVertexAttributesAndVaryings() {
        String input = """
                #version 460
                layout(location = 0)
                out vec4 entryPointParam_main_vertexColor_0;
                layout(location = 1)
                out vec2 entryPointParam_main_uvCoord_0;
                layout(location = 0)
                in vec3 Position_0;
                layout(location = 2)
                in vec4 Color_0;
                layout(location = 1)
                in vec2 UV0_0;
                void main() {
                    gl_Position = vec4(Position_0, 1.0);
                    entryPointParam_main_vertexColor_0 = Color_0;
                    entryPointParam_main_uvCoord_0 = UV0_0;
                }
                """;

        String normalized = MinecraftGlslNormalizer.normalize(
                input, MinecraftGlslNormalizer.ShaderStage.VERTEX);

        assertFalse(normalized.contains("layout(location"));
        assertEquals(true, normalized.contains("in vec3 Position;"));
        assertEquals(true, normalized.contains("in vec4 Color;"));
        assertEquals(true, normalized.contains("in vec2 UV0;"));
        assertEquals(true, normalized.contains("out vec4 gemini_varying_0;"));
        assertEquals(true, normalized.contains("out vec2 gemini_varying_1;"));
        assertEquals(true, normalized.contains("gemini_varying_0 = Color;"));
    }

    @Test
    void normalizesFragmentVaryingsAndOutput() {
        String input = """
                #version 460
                layout(location = 0)
                in vec4 vertexColor_0;
                layout(location = 1)
                in vec2 uvCoord_0;
                layout(location = 0)
                out vec4 entryPointParam_main_fragColor_0;
                void main() {
                    entryPointParam_main_fragColor_0 = vertexColor_0 * uvCoord_0.x;
                }
                """;

        String normalized = MinecraftGlslNormalizer.normalize(
                input, MinecraftGlslNormalizer.ShaderStage.FRAGMENT);

        assertFalse(normalized.contains("layout(location"));
        assertEquals(true, normalized.contains("in vec4 gemini_varying_0;"));
        assertEquals(true, normalized.contains("in vec2 gemini_varying_1;"));
        assertEquals(true, normalized.contains("out vec4 entryPointParam_main_fragColor_0;"));
        assertEquals(true, normalized.contains(
                "entryPointParam_main_fragColor_0 = gemini_varying_0 * gemini_varying_1.x;"));
    }

    @Test
    void rejectsUnsafeInterfaceAndUniformBlockShapes() {
        assertThrows(GradleException.class, () -> MinecraftGlslNormalizer.normalize("""
                #version 460
                layout(location = 0)
                in vec3 Unknown_0;
                """, MinecraftGlslNormalizer.ShaderStage.VERTEX));
        assertThrows(GradleException.class, () -> MinecraftGlslNormalizer.normalize("""
                #version 460
                layout(location = 0)
                out vec4 first_0;
                layout(location = 1)
                out vec4 second_0;
                """, MinecraftGlslNormalizer.ShaderStage.FRAGMENT));
        assertThrows(GradleException.class, () -> MinecraftGlslNormalizer.normalize("""
                #version 460
                layout(std140) uniform First { vec4 Shared; } First;
                layout(std140) uniform Second { vec4 Shared; } Second;
                """, MinecraftGlslNormalizer.ShaderStage.FRAGMENT));
    }
}

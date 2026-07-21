// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Composite pass: blends the blurred glow with the original scene.
// Uses additive blending (base + glow * intensity).

uniform sampler2D InSampler;    // blurred glow
uniform sampler2D BaseSampler;  // original scene

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

[shader("fragment")]

void main() {
    vec4 base = texture(BaseSampler, texCoord);
    vec4 glow = texture(InSampler, texCoord);

    // Additive blend
    fragColor.rgb = base.rgb + glow.rgb;
    fragColor.a = base.a;
}

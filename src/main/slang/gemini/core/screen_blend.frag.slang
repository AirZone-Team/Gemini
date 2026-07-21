// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Screen blend: 1 - (1-base) * (1-bloom).
// Softer than additive, produces less clipping.

uniform sampler2D InSampler;    // bloom/glow layer
uniform sampler2D BaseSampler;  // original base layer

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlendConfig {
    float Intensity;
    float _pad0;
    float _pad1;
    float _pad2;
};

in vec2 texCoord;

out vec4 fragColor;

[shader("fragment")]

void main() {
    vec4 base = texture(BaseSampler, texCoord);
    vec4 bloom = texture(InSampler, texCoord);

    float fi = clamp(Intensity, 0.0, 1.0);

    // Screen blend per channel
    vec3 blended = 1.0 - (1.0 - base.rgb) * (1.0 - bloom.rgb * fi);
    float alpha = min(1.0, base.a + bloom.a * fi);

    fragColor = vec4(blended, alpha);
}

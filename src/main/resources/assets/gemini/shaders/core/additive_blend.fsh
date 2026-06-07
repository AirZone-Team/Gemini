#version 330

// Additive blend: base + bloom * intensity.
// Clamps to [0,1] to avoid HDR overflow.

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

void main() {
    vec4 base = texture(BaseSampler, texCoord);
    vec4 bloom = texture(InSampler, texCoord);

    float fi = clamp(Intensity, 0.0, 1.0);

    // Additive blend with saturation
    vec3 blended = min(vec3(1.0), base.rgb + bloom.rgb * fi);
    float alpha = min(1.0, base.a + bloom.a * fi);

    fragColor = vec4(blended, alpha);
}

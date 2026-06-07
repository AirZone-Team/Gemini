#version 330

// Drop shadow fragment shader.
// Reads the alpha channel from the mask texture, applies Gaussian
// blur, offsets the result, and tints with a shadow color.

uniform sampler2D MaskSampler;

layout(std140) uniform ShadowConfig {
    vec2 ShadowOffset;   // offset in pixels (normalized to texcoords internally)
    vec4 ShadowColor;    // shadow tint with alpha
    float ShadowRadius;  // blur radius in pixels
    float _pad;
};

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 oneTexel = 1.0 / InSize;

    // Sample the mask alpha at the offset position
    vec2 offsetCoord = texCoord - ShadowOffset * oneTexel;
    float maskAlpha = texture(MaskSampler, offsetCoord).a;

    // Simple box-blur on the alpha channel
    // For a true Gaussian, this should be done as a two-pass separable blur,
    // but for GUI drop shadows this single-pass approximation is sufficient
    // when combined with the alpha falloff.
    float shadow = 0.0;
    float r = max(1.0, round(ShadowRadius));
    float weightSum = 0.0;
    int iRadius = int(r);

    for (int dy = -iRadius; dy <= iRadius; dy++) {
        for (int dx = -iRadius; dx <= iRadius; dx++) {
            float dist = sqrt(float(dx * dx + dy * dy));
            if (dist <= r) {
                float w = exp(-0.5 * (dist * dist) / (r * r * 0.25));
                vec2 sampleCoord = offsetCoord + vec2(dx, dy) * oneTexel;
                shadow += texture(MaskSampler, sampleCoord).a * w;
                weightSum += w;
            }
        }
    }

    shadow = shadow / max(0.001, weightSum);
    shadow *= maskAlpha; // Modulate by original mask alpha at offset position

    fragColor = vec4(ShadowColor.rgb, shadow * ShadowColor.a);
}

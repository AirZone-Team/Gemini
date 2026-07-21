// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Single-pass soft glow for GUI elements.
//
// Designed to be used with a custom pipeline where the glow color
// comes from vertexColor and the glow spread is encoded in how far
// the rendered quad extends beyond the original element bounds.
//
// This samples the source texture and computes a weighted sum
// in the local neighborhood to approximate a blurred glow.

uniform sampler2D InSampler;

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(std140) uniform GlowConfig {
    float GlowRadius;    // blur radius in pixels
    float GlowIntensity; // 0-1 blend strength
    float GlowThreshold; // brightness threshold 0-1
    float _pad;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

// ITU-R BT.601 luminance
float luminance(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

[shader("fragment")]

void main() {
    vec2 oneTexel = 1.0 / textureSize(InSampler, 0);

    // Unpack glow params
    float r = max(1.0, GlowRadius);
    float intensity = clamp(GlowIntensity, 0.0, 1.0);
    float threshold = clamp(GlowThreshold, 0.0, 1.0);
    int iRadius = int(ceil(r));

    vec4 blurred = vec4(0.0);
    float weightSum = 0.0;
    float sigma = r * 0.4;

    for (int dy = -iRadius; dy <= iRadius; dy++) {
        for (int dx = -iRadius; dx <= iRadius; dx++) {
            vec2 sampleCoord = uvCoord + vec2(dx, dy) * oneTexel;
            vec4 sampleColor = texture(InSampler, sampleCoord);
            float lum = luminance(sampleColor.rgb);

            // Brightness extraction
            float bright = smoothstep(threshold - 0.05, threshold + 0.05, lum);

            // Gaussian weight
            float dist = float(dx * dx + dy * dy);
            float weight = exp(-dist / (2.0 * sigma * sigma)) * bright;

            blurred += sampleColor * weight;
            weightSum += weight;
        }
    }

    vec4 glow = blurred / max(0.0001, weightSum);

    // Tint with vertexColor and apply intensity
    fragColor = vec4(glow.rgb * vertexColor.rgb * intensity, glow.a * vertexColor.a * intensity);
}

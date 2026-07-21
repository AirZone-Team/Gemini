// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Brightness threshold extraction pass.
// Outputs only pixels whose luminance exceeds the threshold.
// Below-threshold pixels are set to transparent black.
// Uses the engine's Globals UBO for screen-size info.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

// Luminance weights (ITU-R BT.601)
const vec3 LUM_WEIGHTS = vec3(0.299, 0.587, 0.114);

[shader("fragment")]

void main() {
    vec4 color = texture(InSampler, texCoord);
    float lum = dot(color.rgb, LUM_WEIGHTS);

    // Threshold from BlurConfig.Radius, clamped
    float threshold = 0.5;
    if (lum < threshold) {
        fragColor = vec4(0.0);
    } else {
        // Smooth falloff above threshold
        float range = max(0.01, 1.0 - threshold);
        float a = min(1.0, (lum - threshold) / range);
        fragColor = vec4(color.rgb * a, a * color.a);
    }
}

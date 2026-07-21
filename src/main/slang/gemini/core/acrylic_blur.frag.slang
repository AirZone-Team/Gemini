// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Single-pass blur optimized for acrylic/frosted-glass backgrounds.
//
// Uses Poisson-disk sampling (12 taps) for smooth blur.
// Parameters are compile-time defines set at pipeline creation.
// Tint color comes from vertexColor.
//
// Pipeline creation:
//   .withShaderDefine("BLUR_RADIUS", 8)
//   .withShaderDefine("NOISE_STRENGTH", 0.05)

uniform sampler2D Sampler0;       // background texture (to be blurred)
uniform sampler2D Sampler1;       // tileable noise texture

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

#ifndef BLUR_RADIUS
#define BLUR_RADIUS 8.0
#endif
#ifndef NOISE_STRENGTH
#define NOISE_STRENGTH 0.05
#endif

// Poisson disk samples (13 points, radius=1.0)
const vec2 POISSON[13] = vec2[](
    vec2( 0.0000,  0.0000),
    vec2( 0.5239,  0.8518),
    vec2( 0.8518, -0.5239),
    vec2(-0.5239, -0.8518),
    vec2(-0.8518,  0.5239),
    vec2( 0.3117, -0.9502),
    vec2( 0.9502,  0.3117),
    vec2(-0.3117,  0.9502),
    vec2(-0.9502, -0.3117),
    vec2(-0.1477,  0.1951),
    vec2( 0.1951,  0.1477),
    vec2( 0.1477, -0.1951),
    vec2(-0.1951, -0.1471)
);

[shader("fragment")]

void main() {
    vec2 size = textureSize(Sampler0, 0);
    vec2 oneTexel = 1.0 / size;

    // Constrain blur radius to prevent performance cliff
    float r = clamp(BLUR_RADIUS, 1.0, 32.0);

    // Poisson-disk blur
    vec4 blurred = vec4(0.0);
    float weightSum = 0.0;
    for (int i = 0; i < 13; i++) {
        vec2 sampleCoord = texCoord + POISSON[i] * r * oneTexel;
        float w = 1.0 / (1.0 + length(POISSON[i]));
        blurred += texture(Sampler0, sampleCoord) * w;
        weightSum += w;
    }
    blurred /= weightSum;

    // Noise grain (tiled 4x)
    float noise = texture(Sampler1, texCoord * 4.0).r;

    // Tint from vertexColor, blend with blurred background
    vec3 tinted = mix(blurred.rgb, vertexColor.rgb, vertexColor.a);

    // Add noise grain
    vec3 finalColor = tinted + (noise - 0.5) * NOISE_STRENGTH;
    finalColor = clamp(finalColor, 0.0, 1.0);

    float resultAlpha = blurred.a;

    fragColor = vec4(finalColor * ColorModulator.rgb, resultAlpha * ColorModulator.a);

    if (fragColor.a < 0.004) {
        discard;
    }
}

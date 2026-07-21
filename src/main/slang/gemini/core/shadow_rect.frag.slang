// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Single-pass drop shadow for rectangular GUI elements.
//
// Same UV convention as glow_rect: [0,1] is the element area.
// The shadow offset is baked into the UV coordinates by the
// Java-side quad setup, so the shader simply computes the
// signed distance to the [0,1]^2 rectangle and applies a
// Gaussian falloff with a dark tint from vertexColor.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

// Default shadow softness in UV space.
// Override via shader define at pipeline creation time.
#ifndef SHADOW_SIGMA
#define SHADOW_SIGMA 0.12
#endif

const float TWO_SIGMA2 = 2.0 * SHADOW_SIGMA * SHADOW_SIGMA;

[shader("fragment")]

void main() {
    float d;

    bool inside = uvCoord.x >= 0.0 && uvCoord.x <= 1.0
               && uvCoord.y >= 0.0 && uvCoord.y <= 1.0;

    if (inside) {
        d = 0.0;
    } else {
        vec2 dmin = -uvCoord;
        vec2 dmax = uvCoord - 1.0;
        vec2 outside = max(vec2(0.0), max(dmin, dmax));
        d = length(outside);
    }

    float shadow = exp(-d * d / TWO_SIGMA2);
    shadow = smoothstep(0.01, 0.0, 1.0 - shadow) * shadow;

    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb * shadow, color.a * shadow);

    if (fragColor.a < 0.004) {
        discard;
    }
}

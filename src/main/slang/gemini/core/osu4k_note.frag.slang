// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// OSU4k note glow bar.
//
// UV convention:
//   [0,1] x [0,1] = the note bar area -> full glow intensity
//   Outside [0,1] = glow spread region -> Gaussian falloff
//
// vertexColor.rgb = note tint
// vertexColor.a   = glow intensity multiplier (0-1)
//
// The bar is brightest at its leading edge (uv.y = 0) and tapers toward the
// trailing edge, so falling notes read as luminous beams.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

// Gaussian sigma in UV space. Larger = softer glow.
#ifndef GLOW_SIGMA
#define GLOW_SIGMA 0.10
#endif

const float TWO_SIGMA2 = 2.0 * GLOW_SIGMA * GLOW_SIGMA;

[shader("fragment")]

void main() {
    // Signed distance to [0,1]^2 rect in UV space.
    vec2 dmin = -uvCoord;
    vec2 dmax = uvCoord - 1.0;
    vec2 outside = max(vec2(0.0), max(dmin, dmax));
    float d = length(outside);

    float glow = exp(-d * d / TWO_SIGMA2);
    glow = smoothstep(0.01, 0.0, 1.0 - glow) * glow;

    // Leading-edge emphasis (uv.y = 0 is the head of the note).
    float edge = smoothstep(1.0, 0.0, uvCoord.y);

    vec4 color = vertexColor * ColorModulator;
    float alpha = color.a * glow * (0.55 + 0.45 * edge);
    vec3 rgb = color.rgb * (0.7 + 0.3 * edge);

    fragColor = vec4(rgb, alpha);
    if (fragColor.a < 0.004) {
        discard;
    }
}

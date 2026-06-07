#version 330

// Single-pass soft glow for rectangular GUI elements.
//
// UV convention:
//   [0,1] x [0,1] = the element area → full glow intensity
//   Outside [0,1] = glow spread region → Gaussian falloff
//
// vertexColor.rgb = glow tint color
// vertexColor.a   = glow intensity multiplier (0-1)

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

// Gaussian sigma in UV space. Larger = softer glow.
// Tune via shader define at pipeline creation time.
#ifndef GLOW_SIGMA
#define GLOW_SIGMA 0.15
#endif

const float TWO_SIGMA2 = 2.0 * GLOW_SIGMA * GLOW_SIGMA;

void main() {
    float d;

    // Inside the element rect?  Check all four edges.
    bool inside = uvCoord.x >= 0.0 && uvCoord.x <= 1.0
               && uvCoord.y >= 0.0 && uvCoord.y <= 1.0;

    if (inside) {
        d = 0.0;
    } else {
        // Signed distance to [0,1]^2 rectangle in UV space
        vec2 dmin = -uvCoord;
        vec2 dmax = uvCoord - 1.0;
        vec2 outside = max(vec2(0.0), max(dmin, dmax));
        d = length(outside);
    }

    float glow = exp(-d * d / TWO_SIGMA2);
    // Smoothstep the tail to avoid hard cutoff at large distances
    glow = smoothstep(0.01, 0.0, 1.0 - glow) * glow;

    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb * glow, color.a * glow);

    if (fragColor.a < 0.004) {
        discard;
    }
}

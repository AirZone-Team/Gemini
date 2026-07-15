#version 330

// SDF-based rounded rectangle fill with adaptive anti-aliasing.
//
// UV convention:
//   [0,1] x [0,1] = the element rectangle (full fill)
//   Outside [0,1]   = transparent (clipped)
//
// The corner radius is baked in as a shader define (RADIUS_PX).
// vertexColor.rgb = fill color
// vertexColor.a   = fill alpha

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

// Corner radius in pixel-equivalent UV units.
// Tune via shader define at pipeline creation time.
#ifndef RADIUS_UV
#define RADIUS_UV 0.12
#endif

// SDF: signed distance to a rounded box.
// CenterPosition = frag pos relative to center, in [0,1] space.
// HalfSize       = half the box dimensions in [0,1] space  (0.5, 0.5).
// Radius         = corner radius in the same space.
float roundedBoxSDF(vec2 centerPos, vec2 halfSize, float radius) {
    vec2 q = abs(centerPos) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    // Map UV [0,1] to center-relative coordinates in [-0.5, 0.5]
    vec2 centerPos = uvCoord - 0.5;
    vec2 halfSize  = vec2(0.5);

    float dist = roundedBoxSDF(centerPos, halfSize, RADIUS_UV);

    // Adaptive anti-aliasing: smoothstep edge width scales with
    // screen-space partial derivatives of the distance field.
    float edgeWidth = fwidth(dist);
    float alpha = 1.0 - smoothstep(0.0, edgeWidth, dist);

    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * alpha);

    if (fragColor.a < 0.004) {
        discard;
    }
}

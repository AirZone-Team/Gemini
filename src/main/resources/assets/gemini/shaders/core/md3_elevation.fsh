#version 330

// ── Material Design 3 elevation shadow ──
//
// Soft penumbra around a rounded box, computed with an exact signed
// distance field in pixel space. Unlike glow_rect.fsh (which treats the
// element as a sharp rectangle in UV space), the SDF hugs the rounded
// corners, so MD3 cards with large corner radii (12 px+) get a uniform
// penumbra band instead of squared-off glow blobs at the corners.
//
// Vertex colour encoding (same convention as target_display.fsh):
//   vertexColor.r → element width    (0–512 px, mapped 0–1)
//   vertexColor.g → element height   (0–512 px, mapped 0–1)
//   vertexColor.b → corner radius    (0–64  px, mapped 0–1)
//   vertexColor.a → shadow strength  (0–1)
//
// UV: pixel coordinates relative to the element origin —
// [0,w] x [0,h] is the card itself, values outside are the penumbra.
// The Java side positions the quad with the MD3 key-light Y offset
// already applied.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

// Penumbra softness (Gaussian sigma) in pixels — MD3 elevation level 1
// uses a small, tight blur; bake at pipeline creation time if needed.
#ifndef SHADOW_SOFTNESS
#define SHADOW_SOFTNESS 5.0
#endif

const float TWO_SIGMA2 = 2.0 * SHADOW_SOFTNESS * SHADOW_SOFTNESS;

// Signed distance to a rounded box.
// p = position relative to box centre, halfSize = (w/2, h/2), r = radius.
float roundedRectSDF(vec2 p, vec2 halfSize, float r) {
    vec2 d = abs(p) - halfSize + vec2(r);
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - r;
}

void main() {
    float w = vertexColor.r * 512.0;
    float h = vertexColor.g * 512.0;
    float r = min(vertexColor.b * 64.0, min(w, h) * 0.5);
    float strength = vertexColor.a;

    vec2 halfSize = vec2(w, h) * 0.5;
    float dist = roundedRectSDF(uvCoord - halfSize, halfSize, r);

    // Inside the card the shadow is occluded by the (opaque) surface drawn
    // on top; keeping the umbra at full strength lets the penumbra start
    // at maximum density exactly at the card edge, like a real key light.
    float penumbra = dist <= 0.0 ? 1.0 : exp(-dist * dist / TWO_SIGMA2);

    float a = strength * penumbra;
    if (a < 0.004) discard;

    // MD3 elevation shadows are pure black; strength controls opacity.
    fragColor = vec4(vec3(0.0), a) * ColorModulator;
}

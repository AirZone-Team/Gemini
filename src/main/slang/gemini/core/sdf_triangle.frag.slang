// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Rounded equilateral triangle with its apex pointing right, drawn from an
// exact signed distance field. Shares the SDF vertex format with the other
// sdf_rounded pipelines (see core/sdf_rounded.vert.slang):
//
//   pixelPos   element-local px in [-AA_MARGIN, size+AA_MARGIN]
//   elemSize   (size, size)
//   shapeParams.x  corner rounding, percent of circumradius (0..40)
//   shapeParams.y  unused
//
// The triangle is defined by its three vertices in NDC-ish space
// (p = uv*2-1, y flipped so +y is up). The distance to the polygon outline
// is the min of the three edge-segment distances (exact for a convex
// polygon both inside and outside); the interior sign is taken from the
// winding tests. Subtracting the rounding radius yields the classic
// Minkowski sum: a triangle with circularly rounded corners.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

in vec4 vertexColor;
in vec2 pixelPos;
in vec2 elemSize;
in vec2 shapeParams;

out vec4 fragColor;

float segmentDistance(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-5), 0.0, 1.0);
    return length(pa - ba * h);
}

float cross2(vec2 a, vec2 b) {
    return a.x * b.y - a.y * b.x;
}

[shader("fragment")]

void main() {
    vec2 uv = pixelPos / elemSize;
    // y-down pixel space → y-up math space, then to [-1, 1].
    vec2 p = vec2(uv.x, 1.0 - uv.y) * 2.0 - 1.0;

    // Apex-right equilateral triangle, circumradius 0.92 (balanced padding).
    const float R = 0.92;
    vec2 v0 = vec2(R, 0.0);              // right apex
    vec2 v1 = vec2(-R * 0.5, R * 0.866); // top-left
    vec2 v2 = vec2(-R * 0.5, -R * 0.866);// bottom-left

    float outline = min(
        segmentDistance(p, v0, v1),
        min(segmentDistance(p, v1, v2), segmentDistance(p, v2, v0)));

    bool inside = cross2(v1 - v0, p - v0) >= 0.0
               && cross2(v2 - v1, p - v1) >= 0.0
               && cross2(v0 - v2, p - v2) >= 0.0;

    float dist = inside ? -outline : outline;

    // Round the corners: offset the outline by the rounding radius.
    float roundPct = clamp(shapeParams.x, 0.0, 40.0);
    dist -= (roundPct / 100.0) * R;

    float edge = max(fwidth(dist), 1e-4);
    float alpha = 1.0 - smoothstep(0.0, edge, dist);
    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * alpha);

    if (fragColor.a < 0.004) {
        discard;
    }
}

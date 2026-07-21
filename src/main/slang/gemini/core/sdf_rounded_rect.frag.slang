// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// SDF rounded rectangle: anti-aliased fill (shapeParams.y == 0) or
// anti-aliased inner border ring (shapeParams.y = border thickness).
//
// The quad is expanded by ~1.5px beyond the element bounds so the
// fwidth()-wide AA transition happens inside the rasterized area.
//
// vertexColor = per-corner ARGB (bilinear gradient across the quad).

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 pixelPos;
in vec2 elemSize;
in vec2 shapeParams;

out vec4 fragColor;

// Signed distance to a rounded box.
// p = position relative to box centre, halfSize = (w/2, h/2), r = radius.
float roundedRectSDF(vec2 p, vec2 halfSize, float r) {
    vec2 d = abs(p) - halfSize + vec2(r);
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - r;
}

[shader("fragment")]

void main() {
    float w = elemSize.x;
    float h = elemSize.y;
    float r = min(shapeParams.x, min(w, h) * 0.5);
    float thickness = shapeParams.y;

    vec2 halfSize = vec2(w, h) * 0.5;
    float dist = roundedRectSDF(pixelPos - halfSize, halfSize, r);

    // Screen-space AA width (~1 framebuffer pixel), guard against degenerate quads.
    float edge = max(fwidth(dist), 1e-4);

    float alpha;
    if (thickness > 0.0) {
        // Inner border ring: dist in [-thickness, 0], both edges anti-aliased.
        float outerEdge = 1.0 - smoothstep(0.0, edge, dist);
        float innerEdge = smoothstep(-thickness - edge, -thickness, dist);
        alpha = outerEdge * innerEdge;
    } else {
        alpha = 1.0 - smoothstep(0.0, edge, dist);
    }

    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * alpha);

    if (fragColor.a < 0.004) {
        discard;
    }
}

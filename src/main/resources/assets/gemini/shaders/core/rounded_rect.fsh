#version 330

// SDF-based rounded rectangle with per-corner radii, fill and border.
// All coordinates use the framebuffer's bottom-left origin.
//
// Uniform layout:
//   rect       = vec4(left, bottom, width, height) in pixels
//   radii      = vec4(top-left, top-right, bottom-right, bottom-left) in pixels
//   fillColor  = base fill color (RGBA)
//   borderColor= base border color (RGBA)
//   params     = vec4(borderThickness, useGradientFill, useGradientBorder, unused)
//   fillTL/TR/BR/BL   = per-corner fill colors for gradients
//   borderTL/TR/BR/BL = per-corner border colors for gradients

layout(std140) uniform RoundedRectUniforms {
    vec4 rect;
    vec4 radii;
    vec4 fillColor;
    vec4 borderColor;
    vec4 params;
    vec4 fillTL;
    vec4 fillTR;
    vec4 fillBR;
    vec4 fillBL;
    vec4 borderTL;
    vec4 borderTR;
    vec4 borderBR;
    vec4 borderBL;
};

in vec2 uvCoord;
out vec4 fragColor;

// Signed distance to a rounded box with per-corner radii.
// position: fragment coordinate (bottom-left origin)
// bounds:   vec4(left, bottom, right, top)
// radius:   vec4(top-left, top-right, bottom-right, bottom-left)
float roundRectDistance(vec2 position, vec4 bounds, vec4 radius) {
    vec2 halfSize = (bounds.zw - bounds.xy) * 0.5;
    vec2 center = (bounds.xy + bounds.zw) * 0.5;
    vec2 p = position - center;

    // In bottom-left coords: p.y > 0 means top half.
    vec2 s = step(0.0, p);
    float rCurrent = mix(
        mix(radius.w, radius.x, s.y), // left half: bottom-left / top-left
        mix(radius.z, radius.y, s.y), // right half: bottom-right / top-right
        s.x
    );

    vec2 q = abs(p) - halfSize + rCurrent;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rCurrent;
}

// Bilinear interpolation of four corner colors.
// uv (0,0) = bottom-left, (1,1) = top-right.
vec4 bilinearColor(vec4 tl, vec4 tr, vec4 br, vec4 bl, vec2 uv) {
    vec4 bottom = mix(bl, br, uv.x);
    vec4 top    = mix(tl, tr, uv.x);
    return mix(bottom, top, uv.y);
}

void main() {
    vec2 fragPos = gl_FragCoord.xy;
    vec4 bounds  = vec4(rect.xy, rect.xy + rect.zw);

    float dist = roundRectDistance(fragPos, bounds, radii);

    float edge = fwidth(dist);
    float fillAlpha = 1.0 - smoothstep(0.0, edge, dist);

    vec4 fill = (params.y > 0.5)
        ? bilinearColor(fillTL, fillTR, fillBR, fillBL, uvCoord)
        : fillColor;

    float borderThickness = params.x;
    vec4 border = (params.z > 0.5)
        ? bilinearColor(borderTL, borderTR, borderBR, borderBL, uvCoord)
        : borderColor;

    float borderAlpha = 0.0;
    if (borderThickness > 0.0) {
        float outerEdge = 1.0 - smoothstep(0.0, edge, dist);
        float innerEdge = smoothstep(-borderThickness - edge, -borderThickness, dist);
        borderAlpha = outerEdge * innerEdge;
    }

    vec4 fillLayer   = vec4(fill.rgb,   fill.a   * fillAlpha);
    vec4 borderLayer = vec4(border.rgb, border.a * borderAlpha);

    fragColor = mix(fillLayer, borderLayer, borderLayer.a);

    if (fragColor.a < 0.001) {
        discard;
    }
}

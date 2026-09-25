// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Vertex shader for SLUG glyph rendering.
// Uses POSITION_TEX_COLOR format. UV0 is not a texture coordinate here — the
// glyph triangles are drawn straight from the vertex buffer, so its channels
// carry the two per-vertex quantities the fragment stage needs:
//
//   x = phi   signed distance to the contour in logical units, negative
//             outside it and positive inside
//   y = kind  0 for a solid triangle, 1 for an anti-aliasing fringe triangle
//             on either side of the contour
//
// See geminiclient.gemini.customRenderer.glsl.slug.SlugGeometry for how the
// triangle sets are produced, and font.frag.slang for the coverage.
// Color carries the per-vertex ARGB tint, blended from the glyph box's four
// corner colors on the CPU.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec4 vertexColor;
out float phi;
out float kind;

[shader("vertex")]

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    phi = UV0.x;
    kind = UV0.y;
}

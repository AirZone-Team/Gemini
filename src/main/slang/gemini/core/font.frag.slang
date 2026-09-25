// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// SLUG font fragment shader: turns a glyph triangle's interpolated signed
// distance into coverage. Nothing is sampled — the whole step is arithmetic on
// the varyings, so there is no atlas, no field range to decode and no metadata
// texel to read.
//
// Geometry contract (geminiclient.gemini.customRenderer.glsl.slug.SlugGeometry):
//
//   phi   signed distance to the contour in logical units: 0 exactly on the
//         outline, down to -AA_MARGIN at the outer edge of the fringe and up to
//         half that at its inner edge.
//   kind  0 for the triangles covering the outline eroded by the inner fringe,
//         1 for either side of the fringe. The three sets are disjoint — blaze3d
//         gives the GUI no stencil buffer and GuiRenderer replays a batch with
//         one blend state, so coverage cannot be accumulated and each fragment
//         must be owned by exactly one kind of triangle.
//
// Anti-aliasing comes from phi's own screen-space derivative: within a fringe
// triangle the distance is affine in position, so length(vec2(dFdx(phi),
// dFdy(phi))) is exactly the number of logical units spanned by one output
// pixel, and the ramp keeps its ~1 px width at any zoom or GUI scale.
//
// The ramp straddles the contour, so a fragment only reaches full alpha half a
// pixel inside it. That is what keeps a stem's weight true: a fringe confined
// to the outside has to saturate at the contour itself, which adds half a
// device pixel to every side of every stroke and closes a counter at UI sizes.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in float phi;
in float kind;

out vec4 fragColor;

[shader("fragment")]

void main() {
    // Units of phi per output pixel. Solid triangles hold phi at a constant,
    // which would make this zero; the floor keeps the unused quotient finite so
    // a GPU that evaluates both branches can't produce a NaN.
    float unitsPerPixel = max(length(vec2(dFdx(phi), dFdy(phi))), 1e-7);
    float alpha = kind < 0.5 ? 1.0 : clamp(0.5 + phi / unitsPerPixel, 0.0, 1.0);

    // Apply vertex color (includes per-character tint/gradient)
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * alpha) * ColorModulator;

    // Skip the outer fringe entirely: it contributes nothing, and blending a
    // zero-alpha fragment over the background still costs a read-modify-write.
    if (color.a <= 0.0) {
        discard;
    }

    fragColor = color;
}

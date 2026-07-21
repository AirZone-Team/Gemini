// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// SDF rounded-rect drop shadow: Gaussian penumbra that hugs the corner
// radius instead of squaring off like the legacy UV-rectangle glow.
//
// shapeParams = (corner radius, gaussian sigma in pixels)
// vertexColor = shadow tint (alpha = peak opacity)
//
// The quad covers the element expanded by ~3*sigma on every side; the
// interior stays at full strength because the (opaque) surface drawn on
// top occludes it, and the tail is faded out smoothly before the quad
// boundary to avoid a hard cutoff.

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

float roundedRectSDF(vec2 p, vec2 halfSize, float r) {
    vec2 d = abs(p) - halfSize + vec2(r);
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - r;
}

[shader("fragment")]

void main() {
    float w = elemSize.x;
    float h = elemSize.y;
    float r = min(shapeParams.x, min(w, h) * 0.5);
    float sigma = max(shapeParams.y, 0.001);

    vec2 halfSize = vec2(w, h) * 0.5;
    float dist = roundedRectSDF(pixelPos - halfSize, halfSize, r);

    float penumbra = dist <= 0.0 ? 1.0 : exp(-dist * dist / (2.0 * sigma * sigma));
    // Smooth tail cutoff so the penumbra reaches zero before the quad edge.
    penumbra *= 1.0 - smoothstep(2.7 * sigma, 3.0 * sigma, dist);

    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * penumbra);

    if (fragColor.a < 0.004) {
        discard;
    }
}

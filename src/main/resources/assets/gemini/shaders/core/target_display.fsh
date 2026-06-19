#version 330

// ── TargetDisplay rounded rectangle background ──
//
// Renders a rounded rectangle with gradient fill and subtle border
// using a signed-distance-field (SDF) approach.
//
// Vertex colour encoding (decoded in the shader):
//   vertexColor.r → element width   (0–512 px, mapped 0–1)
//   vertexColor.g → element height  (0–512 px, mapped 0–1)
//   vertexColor.b → corner radius   (0–64  px, mapped 0–1)
//   vertexColor.a → master alpha    (0–1)

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

// ── Decode element dimensions ──
float elemWidth()   { return vertexColor.r * 512.0; }
float elemHeight()  { return vertexColor.g * 512.0; }
float cornerRadius() { return vertexColor.b * 64.0; }
float masterAlpha()  { return vertexColor.a; }

// ── Rounded rectangle SDF ──
// p = position in pixel space relative to element centre
// halfSize = (width/2, height/2) in pixels
// r = corner radius in pixels
float roundedRectSDF(vec2 p, vec2 halfSize, float r) {
    vec2 d = abs(p) - halfSize + vec2(r);
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - r;
}

void main() {
    float w = elemWidth();
    float h = elemHeight();
    float r = min(cornerRadius(), min(w, h) * 0.5);
    float alpha = masterAlpha();

    // UV → centre-relative position in pixel space
    vec2 halfSize = vec2(w, h) * 0.5;
    vec2 pixelPos = (uvCoord * 2.0 - 1.0) * halfSize;

    // SDF value: negative = inside, positive = outside
    float dist = roundedRectSDF(pixelPos, halfSize, r);

    // Anti-aliased shape mask
    float shape = 1.0 - smoothstep(0.0, 1.5, dist);

    // ── Background: subtle vertical gradient (dark → slightly darker) ──
    float grad = mix(0.82, 0.72, uvCoord.y); // lighter at top
    vec3 bgColor = vec3(0.08, 0.08, 0.12) * grad; // #14141F with gradient
    float bgAlpha = 0.78;

    vec4 color = vec4(bgColor, bgAlpha * shape * alpha);

    // ── Subtle top accent line ──
    float accentH = 2.0 / h;
    if (uvCoord.y < accentH && shape > 0.0) {
        float accent = 1.0 - smoothstep(0.0, accentH, uvCoord.y);
        vec3 accentColor = vec3(0.35, 0.55, 1.0); // soft blue
        color.rgb = mix(color.rgb, accentColor, accent * 0.6);
    }

    // ── Border (1px inner, subtle) ──
    float borderDist = abs(dist) - 1.0;
    float borderShape = 1.0 - smoothstep(0.0, 1.0, borderDist);
    vec4 borderColor = vec4(0.25, 0.30, 0.45, 0.4);
    color = mix(color, borderColor, borderShape * (1.0 - step(dist, 0.0)));

    if (color.a < 0.004) discard;
    fragColor = color * ColorModulator;
}

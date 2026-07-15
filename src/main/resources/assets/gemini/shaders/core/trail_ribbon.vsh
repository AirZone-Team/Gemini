#version 330

// ── Ribbon Trail Vertex Shader ───────────────────────────────────
// Position is camera-relative (CPU-baked).
// ModelOffset.x carries animation time (set by Java).

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV;
layout(location = 2) in vec4 Color;

out vec2 vUv;
out vec4 vColor;

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

void main() {
    vUv    = UV;
    vColor = Color;

    // Position is camera-relative (CPU-baked), ModelViewMat is identity.
    // ModelOffset.x carries animation time but is NOT added to position
    // (doing so would shift geometry by 1.7 billion units).
    // The fragment shader reads ModelOffset.x purely as a time value.
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}

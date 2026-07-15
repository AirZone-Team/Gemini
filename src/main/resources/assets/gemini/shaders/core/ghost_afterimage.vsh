#version 330

// ── Ghost AfterImage Vertex Shader ───────────────────────────────
// Camera-facing billboard. Color: R=fade, G=tintR, B=tintG, A=tintB.
// ModelOffset.x carries animation time (set by Java, unused in VS).

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
    // ModelOffset carries animation time in .x but is NOT added to position
    // (epoch seconds ≈ 1.7e9 would shift geometry into oblivion).
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}

#version 330

// ── Sweep Attack Arc / Ring / Lightning Vertex Shader ───────────
// Position is camera-relative (CPU-baked).
// ModelOffset.x carries animation time.
//
// Vertex encoding:
//   UV0.xy   = effect-specific UV data
//   Color.r  = glow / brightness
//   Color.g  = unused
//   Color.b  = unused
//   Color.a  = mode selector: 0.0-0.99 = arc, 1.0 = ring/lightning
//              (values < 1.0 are arc, == 1.0 is ring/lightning)

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
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
    vUv    = UV0;
    vColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}

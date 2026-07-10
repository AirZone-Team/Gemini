#version 330 core

// Volumetric Glow Ball vertex shader — camera-facing billboards.
//
// vertexColor.r = time progress (0→1) within stage
// vertexColor.g = glow layer selector:
//   0.0 = dense core     (ultra-bright inner sphere)
//   0.5 = mid glow       (intense halo)
//   1.0 = outer glow     (soft volumetric aura)
//   1.5 = ambient sphere (far-reaching subtle glow)
// vertexColor.b = intensity multiplier
// vertexColor.a = master alpha

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
out vec2 uvCoord;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    uvCoord     = UV0;
}

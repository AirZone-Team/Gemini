#version 330

// Vertex shader for single-pass rectangular glow / drop shadow.
// Uses POSITION_TEX_COLOR format.
// UV coordinates are encoded such that [0,1] maps to the element
// area, with values <0 or >1 extending into the glow spread region.

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
    uvCoord = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
}

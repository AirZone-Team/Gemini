#version 330 core

// Magic Circle / Tower vertex shader — camera-facing billboards.
//
// vertexColor.r = time progress within stage (0→1)
// vertexColor.g = stage ID (1=circle, 2=tower)
// vertexColor.b = intensity
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

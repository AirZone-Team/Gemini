#version 330 core

// Magic Halo (魔法光环) vertex shader — horizontal billboard pass-through.
//
// vertexColor.r = 1.0 (reserved, time is now passed via TimeUniforms)
// vertexColor.g = spike count / 16.0 (normalised, fragment shader uses this)
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

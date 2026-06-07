#version 330

// GUI-compatible vertex shader for glow/shadow passes.
// Mirrors core/gui.vsh layout but adds texCoord output for
// texture-based glow rendering.

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
in vec4 Color;
in vec2 UV0;

out vec4 vertexColor;
out vec2 texCoord;
out vec2 uvCoord;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    texCoord = UV0;
    uvCoord = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
}

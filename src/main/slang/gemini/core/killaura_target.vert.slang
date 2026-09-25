// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Vertex stage for the Sorcery Array target indicator. Per-target scalars
// (health, acquire flash, variety seed, material page) ride in one flat
// integer varying, following the JumpCircle packed-words pattern.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;

out vec4 vertexColor;
out vec2 uvCoord;
flat out ivec2 targetWords;

[shader("vertex")]

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    uvCoord = UV0;
    targetWords = UV1;
}

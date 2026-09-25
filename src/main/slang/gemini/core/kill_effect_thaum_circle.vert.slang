// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Thaumaturgy Strike sky sigil — sigil plane vertex stage.
//
// vertexColor.r = reveal (0→1 birth sweep)
// vertexColor.g = animation phase (0→1 loop) for the boil + radial pulse
// vertexColor.b = energy / 4 (HDR, rescaled in the fragment stage)
// vertexColor.a = master alpha
//
// The renderer hands in a world-space plane basis, so this stage is a plain
// pass-through: no billboard math, no rotation of its own.

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
in vec2 UV0;
in vec4 Color;

out vec4 vertexColor;
out vec2 uvCoord;

[shader("vertex")]

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    uvCoord     = UV0;
}

// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Summoning-array vertex shader — one world-space plane of the array.
//
// The renderer orients and turns every plane in its own geometry, so nothing
// here rotates: the vertex colour only carries bounded values.
//
// vertexColor.r = reveal (0→1 summon-script progress)
// vertexColor.g = animation phase (0→1, wraps seamlessly)
// vertexColor.b = energy / 4 (HDR)
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

[shader("vertex")]

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    uvCoord     = UV0;
}

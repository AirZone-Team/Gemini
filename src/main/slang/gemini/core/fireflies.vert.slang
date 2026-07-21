// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Particle + 3D sphere vertex shader (POSITION_COLOR format).
// Passes view-space position and per-vertex color to fragment shader.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

in  vec3 Position;
in  vec4 Color;

out vec4 vertexColor;
out vec3 viewPos;

[shader("vertex")]

void main() {
    vec4 viewPos4 = ModelViewMat * vec4(Position, 1.0);
    viewPos       = viewPos4.xyz;
    vertexColor   = Color;
    gl_Position   = ProjMat * viewPos4;
}

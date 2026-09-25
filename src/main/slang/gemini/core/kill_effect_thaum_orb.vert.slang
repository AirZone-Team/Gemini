// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Thaumaturgy Strike nova sphere — real 3D sphere mesh (NOT a billboard).
//
// Unit-sphere tessellation emitted in effect-local space; the pose stack
// carries the translation to the impact centre, so the fragment stage
// recovers the sphere centre from ModelViewMat * vec4(0,0,0,1).
//
// vertexColor.r = progress through the sphere's life (0→1)
// vertexColor.g = heat (1 = white-hot, 0 = cooling violet)
// vertexColor.b = intensity / 4 (HDR, rescaled in the fragment stage)
// vertexColor.a = master alpha
// UV0.x         = sphere radius in blocks

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
out vec3 vViewPos;

[shader("vertex")]

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;
    vViewPos    = viewPos.xyz;
    vertexColor = Color;
    uvCoord     = UV0;
}

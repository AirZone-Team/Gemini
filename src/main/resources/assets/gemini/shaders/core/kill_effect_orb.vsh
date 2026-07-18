#version 330 core

// Volumetric Orb vertex shader — real 3D sphere mesh (NOT a billboard).
//
// The mesh is a unit sphere tessellation emitted in effect-local space;
// the pose stack carries the translation to the effect center, so
// ModelViewMat * vec4(0,0,0,1) in the fragment shader recovers the
// sphere center in view space.
//
// vertexColor.r = time progress within stage (0→1)
// vertexColor.g = heat (1 = white-hot, 0 = cool ember)
// vertexColor.b = intensity / 4 (HDR, rescaled in shader)
// vertexColor.a = master alpha
// UV0.x         = sphere radius in blocks

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
out vec3 vViewPos;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;
    vViewPos    = viewPos.xyz;
    vertexColor = Color;
    uvCoord     = UV0;
}

// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Thaumaturgy Strike lightning ribbon vertex stage.
//
// One quad per bolt span, oriented by ThaumaturgyRenderer#emitRibbon so the
// arc keeps its screen width however the span is aimed.
//
// UV.x = across the ribbon (0 = one edge, 0.5 = the arc itself, 1 = other)
// UV.y = along the whole bolt, 0 at the sigil end and 1 at what it strikes
// vertexColor.r = per-bolt seed (0→1)
// vertexColor.g = flow phase (0→1); only integer rates are applied to it
// vertexColor.b = energy / 4 (HDR)
// vertexColor.a = master alpha

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

#version 330

// Shared vertex shader for the SDF rounded-rect / rounded-shadow pipelines.
// Custom vertex format: Position(FLOAT3) + Color(UBYTE4) + UV0(FLOAT2)
//                       + UV1(SHORT2) + UV2(SHORT2)
//
//   UV0 → element-local pixel coordinates (may exceed [0,w]x[0,h] on the
//         1.5px AA margin / the shadow penumbra margin)
//   UV1 → element size in pixels (width, height)
//   UV2 → shape params: x = corner radius, y = aux
//         (fill: 0, outline: border thickness, shadow: gaussian sigma)

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
// SHORT elements use integer attributes (glVertexAttribIPointer) — must be ivec2.
in ivec2 UV1;
in ivec2 UV2;

out vec4 vertexColor;
out vec2 pixelPos;
out vec2 elemSize;
out vec2 shapeParams;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    pixelPos = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
    elemSize = vec2(UV1);
    shapeParams = vec2(UV2);
}

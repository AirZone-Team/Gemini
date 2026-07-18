#version 330

// Vertex shader for the MD3 elevation shadow.
// POSITION_TEX_COLOR format.
// UV0: pixel coordinates relative to the element origin —
//      [0,w] x [0,h] is the card, values outside are the penumbra region.
// Color: encodes (width, height, cornerRadius, strength) for the fragment shader.

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
    uvCoord = UV0;
}

#version 330

// Trajectory prediction line — vertex colour carries the gradient
// encoded as RGBA: RGB = line tint, A = alpha (fades near endpoint).

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;
    if (color.a < 0.004) discard;
    fragColor = color;
}

#version 330 core

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Projection { mat4 ProjMat; };

in vec3 Position;
in vec4 Color;

out vec4 vertexColor;
out vec2 localPos; // 传递给片段着色器的 UV 坐标

void main() {
    vec4 viewPos4 = ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    gl_Position = ProjMat * viewPos4;

    // 根据 Quad 的 4 个顶点生成 -1.0 到 1.0 的局部坐标，用于画圆
    vec2 uvs[4] = vec2[4](
            vec2(-1.0, -1.0),
            vec2(-1.0,  1.0),
            vec2( 1.0,  1.0),
            vec2( 1.0, -1.0)
    );
    localPos = uvs[gl_VertexID % 4];
}
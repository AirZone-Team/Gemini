#version 330

uniform sampler2D Sampler0;

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

void main() {
    // 1. 获取从 Java 端生成的标准字体位图纹理颜色
    vec4 texel = texture(Sampler0, texCoord);

    // 2. 标准的栅格化字体渲染：直接将纹理颜色与顶点颜色进行乘法混合
    vec4 color = texel * vertexColor * ColorModulator;

    // 3. 丢弃几乎完全透明的像素以优化性能
    if (color.a < 0.004) {
        discard;
    }

    fragColor = color;
}
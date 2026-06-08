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
    vec4 texel = texture(Sampler0, texCoord);
    float glyph = texel.a;

    // 基于屏幕空间导数的自适应边缘宽度
    // 在任何缩放级别下边缘都恰好过渡 1 像素
    float edge = fwidth(glyph);
    glyph = smoothstep(0.5 - edge, 0.5 + edge, glyph);

    // 颜色调制
    vec4 modulated = vertexColor * ColorModulator;

    // 正确预乘 alpha，避免半透明黑边
    float finalAlpha = modulated.a * glyph;
    fragColor = vec4(modulated.rgb * finalAlpha, finalAlpha);

    if (fragColor.a < 0.004) {
        discard;
    }
}
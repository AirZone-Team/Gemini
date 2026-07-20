#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 localPos;
out vec4 fragColor;

void main() {
    // 计算离中心的距离 (0.0 到 1.0+)
    float dist = length(localPos);

    // 柔和的径向渐变，越靠近边缘越透明
    float alphaFade = smoothstep(1.0, 0.0, dist);

    // pow(x, 1.5) 让核心更亮，边缘衰减更自然
    alphaFade = pow(alphaFade, 2.2);

    fragColor = vec4(vertexColor.rgb, vertexColor.a * alphaFade) * ColorModulator;

    if (fragColor.a < 0.001) {
        discard;
    }
}

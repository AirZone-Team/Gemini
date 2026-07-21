// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 uvCoord;
flat in ivec2 styleWords;
flat in ivec2 materialWords;
out vec4 fragColor;

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
        mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

[shader("fragment")]

void main() {
    int opacityWord = styleWords.x & 65535;
    vec2 p = uvCoord * 2.0 - 1.0;
    float distanceValue = length(p);
    float life = clamp(vertexColor.a, 0.0, 1.0);
    float opacity = clamp(float(opacityWord) / 255.0, 0.0, 1.0);

    float radius = mix(0.12, 0.82, 1.0 - pow(1.0 - life, 3.0));
    float body = 1.0 - smoothstep(radius * 0.20, radius + 0.20, distanceValue);
    float wave = 1.0 - smoothstep(0.06, 0.19, abs(distanceValue - radius * 0.76));
    float breakup = 0.78 + noise(p * 7.0 + life * 2.4) * 0.22;
    float fade = smoothstep(0.0, 0.04, life) * (1.0 - smoothstep(0.52, 1.0, life));
    float alpha = (body * 0.54 + wave * 0.46) * breakup * fade * opacity;

    fragColor = vec4(vertexColor.rgb, alpha) * ColorModulator;
    if (fragColor.a < 0.001) discard;
}

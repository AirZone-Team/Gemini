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

out vec4 fragColor;

float hash21(vec2 point) {
    return fract(sin(dot(point, vec2(127.1, 311.7))) * 43758.5453);
}

[shader("fragment")]

void main() {
    float across = abs(uvCoord.y);
    if (across > 1.0 || vertexColor.a < 0.002) discard;

    float core = exp(-across * 7.5);
    float aura = exp(-across * 2.4) * 0.34;
    float flow = 0.72 + 0.28 * sin(uvCoord.x * 6.2831853);
    float filament = smoothstep(0.45, 1.0,
        hash21(vec2(floor(uvCoord.x * 5.0), floor(across * 7.0))));
    float energy = core * (1.05 + flow * 0.65) + aura + filament * core * 0.18;

    vec3 color = vertexColor.rgb * energy;
    color += vec3(1.0) * core * core * 0.38;
    float alpha = vertexColor.a * clamp(core + aura, 0.0, 1.0);
    if (alpha < 0.003) discard;

    fragColor = vec4(color, alpha) * ColorModulator;
}

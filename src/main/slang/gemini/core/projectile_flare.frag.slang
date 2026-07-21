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

[shader("fragment")]

void main() {
    vec2 point = uvCoord - 0.5;
    point.x = fract(uvCoord.x) - 0.5;
    float radius = length(point) * 2.0;

    float halo = exp(-radius * 3.8) * 0.58;
    float core = exp(-radius * 13.0) * 1.8;
    float raysA = exp(-abs(point.x) * 42.0) * exp(-abs(point.y) * 3.5);
    float raysB = exp(-abs(point.y) * 42.0) * exp(-abs(point.x) * 3.5);
    float diagonalA = exp(-abs(point.x + point.y) * 35.0) * exp(-radius * 3.0);
    float diagonalB = exp(-abs(point.x - point.y) * 35.0) * exp(-radius * 3.0);
    float ring = exp(-abs(radius - 0.47) * 42.0) * 0.72;

    float energy = halo + core + (raysA + raysB) * 0.72
                 + (diagonalA + diagonalB) * 0.36 + ring;
    float alpha = clamp(energy, 0.0, 1.0) * vertexColor.a;
    if (alpha < 0.004 || radius > 1.42) discard;

    vec3 color = vertexColor.rgb * energy;
    color += vec3(1.0) * core * 0.65;
    fragColor = vec4(color, alpha) * ColorModulator;
}

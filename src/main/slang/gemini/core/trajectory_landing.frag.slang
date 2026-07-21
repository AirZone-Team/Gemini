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

float ring(float distanceFromCenter, float radius, float width) {
    return 1.0 - smoothstep(width, width + 0.012, abs(distanceFromCenter - radius));
}

[shader("fragment")]

void main() {
    int style = int(floor(uvCoord.x + 0.0001));
    vec2 point = vec2(fract(uvCoord.x), uvCoord.y) - 0.5;
    float distanceFromCenter = length(point);
    float shape = ring(distanceFromCenter, 0.39, 0.025);
    float glow = exp(-abs(distanceFromCenter - 0.39) * 18.0) * 0.42;

    if (style >= 1) {
        float crosshair = max(
            step(abs(point.x), 0.013) * step(0.18, abs(point.y)),
            step(abs(point.y), 0.013) * step(0.18, abs(point.x))
        );
        float corners = ring(distanceFromCenter, 0.29, 0.012)
            * step(0.72, abs(sin(atan(point.y, point.x) * 4.0)));
        shape = max(shape, max(crosshair, corners));
    }

    if (style >= 2) {
        float angle = atan(point.y, point.x);
        float runes = ring(distanceFromCenter, 0.24, 0.010)
            * step(0.48, sin(angle * 12.0) * 0.5 + 0.5);
        float spokes = step(0.93, cos(angle * 8.0))
            * smoothstep(0.12, 0.20, distanceFromCenter)
            * (1.0 - smoothstep(0.28, 0.34, distanceFromCenter));
        float center = exp(-distanceFromCenter * 18.0) * 0.78;
        shape = max(shape, max(runes, max(spokes, center)));
    }

    float alpha = clamp(shape + glow, 0.0, 1.0) * vertexColor.a;
    if (alpha < 0.004 || distanceFromCenter > 0.52) discard;

    vec3 color = vertexColor.rgb * (shape * 1.4 + glow);
    color += vec3(1.0) * shape * shape * 0.28;
    fragColor = vec4(color, alpha) * ColorModulator;
}

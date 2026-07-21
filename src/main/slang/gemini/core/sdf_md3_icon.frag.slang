// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Analytic Material 3 icon primitives. shapeParams.x selects:
// 0 filled heart, 1 outlined heart, 2 chevron down, 3 chevron up,
// 4 check, 5 close.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 pixelPos;
in vec2 elemSize;
in vec2 shapeParams;

out vec4 fragColor;

float segmentSDF(vec2 p, vec2 a, vec2 b, float radius) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-5), 0.0, 1.0);
    return length(pa - ba * h) - radius;
}

// Approximate signed distance to the classic implicit heart curve.
float heartSDF(vec2 p) {
    float r = dot(p, p) - 1.0;
    float f = r * r * r - p.x * p.x * p.y * p.y * p.y;
    vec2 gradient = vec2(
        6.0 * p.x * r * r - 2.0 * p.x * p.y * p.y * p.y,
        6.0 * p.y * r * r - 3.0 * p.x * p.x * p.y * p.y
    );
    return f / max(length(gradient), 0.18);
}

[shader("fragment")]

void main() {
    vec2 uv = pixelPos / elemSize;
    vec2 p = uv * 2.0 - 1.0;
    int icon = int(shapeParams.x + 0.5);

    float dist;
    if (icon == 0 || icon == 1) {
        // The implicit equation uses y-up coordinates. Slightly reduce and
        // shift it so the lobes and lower point share balanced padding.
        vec2 hp = vec2(p.x * 1.16, -p.y * 1.16 + 0.03);
        float heart = heartSDF(hp);
        dist = icon == 0 ? heart : abs(heart) - 0.075;
    } else if (icon == 2) {
        dist = min(
            segmentSDF(p, vec2(-0.55, -0.28), vec2(0.0, 0.28), 0.105),
            segmentSDF(p, vec2(0.0, 0.28), vec2(0.55, -0.28), 0.105)
        );
    } else if (icon == 3) {
        dist = min(
            segmentSDF(p, vec2(-0.55, 0.28), vec2(0.0, -0.28), 0.105),
            segmentSDF(p, vec2(0.0, -0.28), vec2(0.55, 0.28), 0.105)
        );
    } else if (icon == 4) {
        dist = min(
            segmentSDF(p, vec2(-0.55, -0.02), vec2(-0.14, 0.38), 0.105),
            segmentSDF(p, vec2(-0.14, 0.38), vec2(0.58, -0.38), 0.105)
        );
    } else {
        dist = min(
            segmentSDF(p, vec2(-0.46, -0.46), vec2(0.46, 0.46), 0.10),
            segmentSDF(p, vec2(-0.46, 0.46), vec2(0.46, -0.46), 0.10)
        );
    }

    float edge = max(fwidth(dist), 1e-4);
    float alpha = 1.0 - smoothstep(0.0, edge, dist);
    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * alpha);

    if (fragColor.a < 0.004) {
        discard;
    }
}

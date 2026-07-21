// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Procedural KillAura glyph atlas. UV.x selects a two-unit material page;
// no textures are sampled, so every shape and its bloom halo are generated
// directly in GLSL.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

float lineMask(float value, float width) {
    float aa = max(fwidth(value), 0.006);
    return 1.0 - smoothstep(width, width + aa, abs(value));
}

float fillMask(float signedDistance) {
    float aa = max(fwidth(signedDistance), 0.006);
    return 1.0 - smoothstep(0.0, aa, signedDistance);
}

float polygonRadius(vec2 point, float sides) {
    float angle = atan(point.y, point.x);
    float sector = TAU / sides;
    return cos(floor(0.5 + angle / sector) * sector - angle) * length(point);
}

[shader("fragment")]

void main() {
    int material = int(floor(uvCoord.x * 0.5 + 0.0001));
    vec2 localUv = vec2(mod(uvCoord.x, 2.0), uvCoord.y);
    vec2 p = (localUv - 0.5) * 2.0;
    float radius = length(p);
    float angle = atan(p.y, p.x);
    float core = 0.0;
    float glow = 0.0;
    float whiteHot = 0.0;

    if (material == 0) {
        // Lit energy pearl.
        if (radius > 1.0) discard;
        float z = sqrt(max(0.0, 1.0 - radius * radius));
        vec3 normal = normalize(vec3(p, z));
        float diffuse = max(dot(normal, normalize(vec3(-0.38, 0.52, 0.76))), 0.0);
        core = (1.0 - smoothstep(0.70, 0.90, radius)) * (0.72 + diffuse * 0.52);
        glow = exp(-radius * 3.2) * 0.46;
        whiteHot = pow(max(z, 0.0), 9.0) * 0.38;
    } else if (material == 1) {
        // Four-point comet flare with a diamond nucleus.
        float vertical = exp(-abs(p.x) * 25.0) * exp(-abs(p.y) * 2.2);
        float horizontal = exp(-abs(p.y) * 31.0) * exp(-abs(p.x) * 4.2);
        float diamond = fillMask(abs(p.x) + abs(p.y) - 0.25);
        core = max(diamond, max(vertical, horizontal) * 0.82);
        glow = exp(-radius * 3.8) * 0.42 + max(vertical, horizontal) * 0.22;
        whiteHot = diamond * 0.72;
    } else if (material == 2) {
        // Faceted crystal diamond.
        float diamondDistance = abs(p.x) * 0.82 + abs(p.y) - 0.66;
        float shell = lineMask(diamondDistance, 0.055);
        float inner = lineMask(abs(p.x) * 0.82 + abs(p.y) - 0.34, 0.025);
        float facets = lineMask(abs(p.x) - abs(p.y) * 0.66, 0.018)
            * fillMask(diamondDistance);
        core = max(shell, max(inner * 0.78, facets * 0.56));
        glow = exp(-abs(diamondDistance) * 7.5) * 0.42;
        whiteHot = shell * 0.46;
    } else if (material == 3) {
        // Arcane rune: broken rings, spokes and a glowing core.
        float outer = lineMask(radius - 0.67, 0.040)
            * step(0.16, abs(sin(angle * 4.0 + 0.55)));
        float inner = lineMask(radius - 0.38, 0.028)
            * step(0.35, abs(cos(angle * 6.0)));
        float spokes = pow(max(cos(angle * 3.0), 0.0), 28.0)
            * smoothstep(0.18, 0.28, radius)
            * (1.0 - smoothstep(0.48, 0.62, radius));
        float center = fillMask(radius - 0.105);
        core = max(max(outer, inner), max(spokes, center));
        glow = exp(-abs(radius - 0.55) * 6.0) * 0.25
            + exp(-radius * 5.5) * 0.30;
        whiteHot = center * 0.65;
    } else if (material == 4) {
        // Crescent blade.
        float outer = fillMask(radius - 0.73);
        float cutout = fillMask(length(p - vec2(0.29, 0.04)) - 0.63);
        float crescent = outer * (1.0 - cutout);
        float edge = lineMask(radius - 0.73, 0.035) * (1.0 - cutout);
        core = max(crescent * 0.88, edge);
        glow = exp(-abs(radius - 0.64) * 5.5) * crescent * 0.55;
        whiteHot = edge * 0.42;
    } else if (material == 5) {
        // Large horizontal summoning array.
        float rings = max(lineMask(radius - 0.83, 0.020),
            max(lineMask(radius - 0.71, 0.012), lineMask(radius - 0.45, 0.014)));
        float hex = lineMask(polygonRadius(p, 6.0) - 0.55, 0.015);
        float spokes = pow(abs(cos(angle * 6.0)), 45.0)
            * smoothstep(0.18, 0.26, radius)
            * (1.0 - smoothstep(0.68, 0.76, radius));
        float runeBand = smoothstep(0.56, 0.61, radius)
            * (1.0 - smoothstep(0.65, 0.70, radius))
            * step(0.58, abs(sin(angle * 12.0)));
        float center = max(lineMask(abs(p.x) + abs(p.y) - 0.18, 0.018),
            fillMask(radius - 0.035));
        core = max(max(rings, hex * 0.86), max(spokes * 0.78, max(runeBand, center)));
        glow = exp(-abs(radius - 0.78) * 12.0) * 0.28
            + exp(-abs(radius - 0.48) * 9.0) * 0.18;
        whiteHot = center * 0.82 + rings * 0.16;
    } else {
        // Watcher eye used at the heart of the Arcane Array.
        float lensDistance = abs(p.y) - (0.38 - p.x * p.x * 0.34);
        float lens = lineMask(lensDistance, 0.032) * step(abs(p.x), 0.82);
        float iris = lineMask(radius - 0.25, 0.038);
        float pupil = fillMask(radius - 0.09);
        float lashes = pow(abs(cos(angle * 5.0)), 42.0)
            * smoothstep(0.42, 0.56, radius)
            * (1.0 - smoothstep(0.70, 0.82, radius));
        core = max(lens, max(iris, max(pupil, lashes * 0.72)));
        glow = exp(-abs(lensDistance) * 8.0) * 0.25
            + exp(-radius * 4.0) * 0.25;
        whiteHot = pupil * 0.86;
    }

    float alpha = clamp(core + glow, 0.0, 1.0) * vertexColor.a;
    if (alpha < 0.004) discard;

    vec3 color = vertexColor.rgb * (core * 1.36 + glow * 0.78);
    color += vec3(1.0) * (whiteHot + core * core * 0.22);
    fragColor = vec4(color, alpha) * ColorModulator;
}

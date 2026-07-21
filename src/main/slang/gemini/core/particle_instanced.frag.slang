// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════════
//  Instanced Particle Fragment Shader — 5-type SDF particles
//
//  Color: R=ageRatio, G=type, B=intensity, A=alpha
//  Time: ModelOffset.x (set by Java each frame)
//
//  Types (from vColor.g):
//    <0.1 = RUNE      — diamond + stroke, gold/purple
//    <0.3 = HEXAGON   — hexagonal tile, cyan/teal
//    <0.5 = TRIANGLE  — sharp shard, orange/red
//    <0.7 = FEATHER   — soft wisp, pink/purple
//    else = STARLIGHT — 4-point cross, white/silver
// ═══════════════════════════════════════════════════════════════════════

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

#define u_time ModelOffset.x

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

// ── Type 0: RUNE ────────────────────────────────────────────────
float runeShape(vec2 p) {
    float diamond = abs(p.x) + abs(p.y);
    float d1 = 1.0 - smoothstep(0.55, 0.75, diamond);
    float stroke = 1.0 - smoothstep(0.08, 0.12, abs(p.x));
    float bar = 1.0 - smoothstep(0.06, 0.10, abs(p.y - 0.15));
    return max(max(d1 * 0.7, stroke * 0.9), bar * 0.6);
}

// ── Type 1: HEXAGON ─────────────────────────────────────────────
float hexagonShape(vec2 p) {
    p = abs(p);
    float h = p.x * 0.866025 + p.y * 0.5;
    return 1.0 - smoothstep(0.55, 0.60, max(h, p.y));
}

// ── Type 2: TRIANGLE ────────────────────────────────────────────
float triangleShape(vec2 p) {
    float h = 0.6 * 0.866025;
    float top = 1.0 - smoothstep(-0.02, 0.03, -(p.y - h * 0.7));
    float left  = 1.0 - smoothstep(-0.02, 0.03, p.y + h * 0.35 + p.x * 1.6);
    float right = 1.0 - smoothstep(-0.02, 0.03, p.y + h * 0.35 - p.x * 1.6);
    float s = min(top, min(left, right));
    s *= 1.0 - smoothstep(h * 0.68, h * 0.72, p.y);
    return s;
}

// ── Type 3: FEATHER ─────────────────────────────────────────────
float featherShape(vec2 p) {
    float ellipse = p.x * p.x * 2.5 + p.y * p.y * 0.8;
    float n = hash(floor(p * 12.0 + u_time * 0.5)) * 0.15;
    float radius = 0.4 + n;
    return 1.0 - smoothstep(radius - 0.08, radius + 0.05, sqrt(ellipse));
}

// ── Type 4: STARLIGHT ───────────────────────────────────────────
float starlightShape(vec2 p) {
    float k = 0.55;
    float star = pow(abs(p.x), k) + pow(abs(p.y), k);
    float crossH = 1.0 - smoothstep(0.04, 0.08, abs(p.y))
                 * (1.0 - smoothstep(0.7, 0.9, abs(p.x)));
    float crossV = 1.0 - smoothstep(0.04, 0.08, abs(p.x))
                 * (1.0 - smoothstep(0.7, 0.9, abs(p.y)));
    float core = 1.0 - smoothstep(0.15, 0.25, star);
    return max(max(core * 0.8, crossH * 0.6), crossV * 0.6);
}

[shader("fragment")]

void main() {
    float ageRatio  = vColor.r;
    float typeVal   = vColor.g;
    float intensity = vColor.b;
    float alpha     = vColor.a;

    if (alpha < 0.002) discard;

    vec2 p = (vUv - 0.5) * 2.0;

    // ── Select shape ────────────────────────────────────────────
    float shape;
    vec3 baseColor;

    if (typeVal < 0.1)       { shape = runeShape(p);       baseColor = vec3(1.00, 0.75, 0.30); }
    else if (typeVal < 0.3)  { shape = hexagonShape(p);    baseColor = vec3(0.30, 0.85, 1.00); }
    else if (typeVal < 0.5)  { shape = triangleShape(p);   baseColor = vec3(1.00, 0.45, 0.10); }
    else if (typeVal < 0.7)  { shape = featherShape(p);    baseColor = vec3(0.80, 0.40, 0.90); }
    else                     { shape = starlightShape(p);   baseColor = vec3(0.95, 0.95, 1.00); }

    // ── Glow + pulse ───────────────────────────────────────────
    float dist = length(p);
    float coreGlow = shape;
    float outerGlow = exp(-dist * 3.5) * 0.3;
    float combined = clamp(coreGlow + outerGlow, 0.0, 1.0);

    float pulse = 0.8 + 0.2 * sin(u_time * 12.0 + ageRatio * 20.0);
    combined *= pulse;

    vec3 col = baseColor * combined * intensity * alpha;

    // HDR boost
    float hdr = 1.0 + coreGlow * 3.0;
    col *= hdr;

    fragColor = vec4(col, combined * alpha);
}

// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  Hell's Hand — ground rift (地狱之门)
//
//  Horizontal quad on the death spot. Renders a molten circular tear:
//  a pitch-black void interior, a boiling flame lip, rotating runic
//  dashes, and an iris-style pinch-shut closing animation.
//
//  vertexColor.r = open  (0→1 easeOutBack, may overshoot ~1.1)
//  vertexColor.g = close (0→1 iris pinch during the final stage)
//  vertexColor.b = boil  (time 0→1 fraction, drives all animation)
//  vertexColor.a = master alpha (intensity × fades)
//
//  Blend: TRANSLUCENT — the void actually darkens the ground while the
//  flame lip paints bright molten color over it.
// ═══════════════════════════════════════════════════════════════════

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

// ── Noise helpers ─────────────────────────────────────────────────

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i+vec2(1,0)), f.x),
               mix(hash(i+vec2(0,1)), hash(i+vec2(1,1)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) { v += a * noise(p); p *= 2.03; a *= 0.5; }
    return v;
}

float smoothstep01(float x) {
    x = clamp(x, 0.0, 1.0);
    return x * x * (3.0 - 2.0 * x);
}

[shader("fragment")]

void main() {
    float open    = vertexColor.r;
    float close   = vertexColor.g;
    float boil    = vertexColor.b;          // 0→1, one lap of a rolling cycle
    float alpha   = vertexColor.a;

    // Seconds of boiling time (cycle length baked into the pack on the CPU:
    // boil = fract(elapsed / 3.0)). Fast enough for flame flicker.
    float tb = boil * 20.0;

    // ── Coordinates ───────────────────────────────────────────────
    vec2 p = (uvCoord - 0.5) * 2.0;         // [-1,1] ground plane
    float r = length(p);
    float ang = atan(p.y, p.x);

    // ── Rift radius evolution ─────────────────────────────────────
    // Full opening ~0.64 of the quad half-size; the iris closing drags the
    // lip inward with jagged "teeth" until it pinches to the center.
    float baseR = 0.64 * clamp(open, 0.0, 1.15);

    // Jagged closing: per-angle tooth noise scaled by close
    float teeth = fbm(vec2(ang * 2.8 + 11.0, tb * 0.35));
    float lipR = baseR * (1.0 - close * (0.78 + teeth * 0.42));
    lipR = max(lipR, baseR * 0.045 * (1.0 - smoothstep01((close - 0.82) / 0.18)));

    // ── Flame lip ─────────────────────────────────────────────────
    float w = 0.14 * (1.0 - close * 0.55) + 0.015;
    // Tongue modulation: flames lick higher on random azimuths, boiling
    float tongue = 0.55 + 0.75 * fbm(vec2(ang * 2.2 - tb * 1.6, 2.3 + tb * 0.9));
    float band = exp(-((r - lipR) * (r - lipR)) / (w * w));
    band *= tongue;
    band += 0.35 * exp(-abs(r - lipR) / (w * 2.6)); // soft halo

    // Flicker
    band *= 0.82 + 0.18 * sin(tb * 9.0 + ang * 7.0 + teeth * 6.0);

    // ── Rotating runic dashes just outside the lip ───────────────
    float runeR = lipR + 0.14;
    float runeRing = exp(-((r - runeR) * (r - runeR)) / 0.0022);
    float dash = step(0.52, fract((ang / 6.2831853 + tb * 0.02) * 12.0));
    float runes = runeRing * (0.30 + 0.70 * dash) * (1.0 - close);

    // ── Void interior ─────────────────────────────────────────────
    float inside = 1.0 - smoothstep(lipR - 0.02, lipR + 0.006, r);
    // Deep-red core breathing + swirling smoke specks
    float swirl = fbm(vec2(ang * 3.0 + tb * 0.9 + r * 4.0, r * 6.0 - tb * 0.7));
    float coreGlow = exp(-r * r / max(lipR * lipR * 0.28, 0.002))
                   * (0.55 + 0.45 * sin(tb * 3.1));
    vec3 voidCol = vec3(0.010, 0.004, 0.006)
                 + vec3(0.42, 0.05, 0.01) * coreGlow * 0.55
                 + vec3(0.65, 0.16, 0.02) * swirl * 0.10 * (1.0 - r / max(lipR, 0.001));
    float voidA = inside * 0.88;

    // ── Lip color: dark red → orange → yellow-white hottest ──────
    float heatT = clamp(band * 1.6, 0.0, 1.0);
    vec3 lipCol = mix(vec3(0.75, 0.14, 0.02), vec3(1.0, 0.48, 0.08), smoothstep(0.3, 0.75, heatT));
    lipCol = mix(lipCol, vec3(1.0, 0.88, 0.55), smoothstep(0.75, 1.0, heatT) * 0.85);

    // ── Pinch flash: white-hot point when the iris snaps shut ─────
    float pinching = smoothstep01((close - 0.80) / 0.20);
    float flash = pinching * exp(-r * r / 0.004) * 6.0
                + pinching * exp(-((r - lipR) * (r - lipR)) / (w * w * 0.35)) * 3.0;

    // ── Composite (over-composite lip + runes onto the void) ─────
    float lipA = clamp(band + runes * 0.85, 0.0, 1.0);
    vec3 col = mix(voidCol, lipCol, lipA);
    col += vec3(1.0, 0.75, 0.40) * flash;
    float a = mix(voidA, 1.0, lipA);
    a += flash;

    // ── Edge fade (hide the square quad boundary) ─────────────────
    float outerFade = 1.0 - smoothstep(0.80, 1.0, r);
    a *= outerFade;

    // Open-in ramp: the tear should not exist at open≈0
    a *= smoothstep01(open * 6.0);

    a *= alpha;
    fragColor = vec4(col, a) * ColorModulator;

    if (fragColor.a < 0.004) {
        discard;
    }
}

#version 330

// ═══════════════════════════════════════════════════════════════════════
//  Ribbon Trail Fragment Shader
//
//  UV: u=along trail(0→1), v=cross-section(-1→1)
//  Color: R=age(0→1), G=motionBlend, B=intensity, A=alpha
//  Time: ModelOffset.x (set by Java each frame)
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

// ── Noise ──────────────────────────────────────────────────────

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i), hash(i + vec2(1,0)), f.x),
        mix(hash(i + vec2(0,1)), hash(i + vec2(1,1)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    float freq = 1.0;
    for (int j = 0; j < 4; j++) {
        v += a * vnoise(p * freq);
        freq *= 2.2;
        a *= 0.45;
    }
    return v;
}

vec3 rainbow(float t) {
    return 0.5 + 0.5 * cos(6.28318 * (t + vec3(0.0, 0.33, 0.66)));
}

void main() {
    float ageRatio   = vColor.r;
    float motionBlend = vColor.g;
    float intensity  = vColor.b;
    float alpha      = vColor.a;

    if (alpha < 0.002) discard;

    float u = vUv.x;
    float v = vUv.y;

    // ════════════════════════════════════════════════════════════
    //  1. Energy flow
    // ════════════════════════════════════════════════════════════

    float flowU = u * 8.0 - u_time * 3.0;
    float flowV = v * 4.0;
    float energy = fbm(vec2(flowU, flowV));
    energy = pow(energy, 2.5) * 1.4;

    float fineFlow = vnoise(vec2(u * 20.0 - u_time * 6.0, v * 10.0));
    energy = mix(energy, energy * fineFlow, 0.5);

    // ════════════════════════════════════════════════════════════
    //  2. Cross-section glow
    // ════════════════════════════════════════════════════════════

    float glow = exp(-abs(v) * 6.0);
    float wideGlow = exp(-abs(v) * 2.5) * 0.5;

    // ════════════════════════════════════════════════════════════
    //  3. Rainbow colour
    // ════════════════════════════════════════════════════════════

    float hue = u_time * 0.15 + u * 1.2 + fbm(vec2(u * 3.0, u_time * 0.3)) * 0.4;
    vec3 trailColor = rainbow(hue);
    float centerBoost = glow * 0.6;
    trailColor = mix(trailColor, vec3(1.0), centerBoost);

    // ════════════════════════════════════════════════════════════
    //  4. Age fade
    // ════════════════════════════════════════════════════════════

    float ageFade = 1.0 - smoothstep(0.5, 1.0, ageRatio);
    float tailFade = 1.0 - smoothstep(0.85, 1.0, u);

    // ════════════════════════════════════════════════════════════
    //  5. Composite + HDR
    // ════════════════════════════════════════════════════════════

    float core = energy * glow * ageFade * tailFade;
    float aura = wideGlow * ageFade * tailFade * 0.35;

    vec3 col = trailColor * (core + aura) * intensity * alpha;

    // HDR boost — pushes values above 1.0 for bloom extraction
    float hdr = 1.0 + energy * 2.0 + glow * 1.5;
    col *= hdr;
    col *= 1.0 + motionBlend * 0.8;

    fragColor = vec4(col, alpha);
}

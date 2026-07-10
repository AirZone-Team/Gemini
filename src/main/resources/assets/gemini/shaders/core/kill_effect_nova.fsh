#version 330

// ═══════════════════════════════════════════════════════════════════
//  Hypernova + Flash fragment shader
//
//  vertexColor.r = time progress (0→1)
//  vertexColor.g = mode flag: 0.0=flash, 0.5=hypernova/afterglow
//  vertexColor.b = intensity
//  vertexColor.a = master alpha
//
//  Mode detection: vertexColor.g < 0.25 → Flash
//                  vertexColor.g ≥ 0.25 → Hypernova / Afterglow
//
//  Performance: FBM reduced to 3 octaves, star count 7→4,
//  wisp count 3→2, fireball FBMs 4→3, if-else → mix for fire color.
// ═══════════════════════════════════════════════════════════════════

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

// ── Hash & noise ──────────────────────────────────────────────────

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
        mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

/** 3-octave FBM — reduced from 5 for ~40% noise cost savings. */
float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * noise(p);
        p *= 2.1;
        a *= 0.5;
    }
    return v;
}

float voronoi(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float minDist = 1.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 neighbor = vec2(float(x), float(y));
            vec2 point = vec2(hash(i + neighbor), hash(i + neighbor + vec2(31.0)));
            vec2 diff = neighbor + point - f;
            float d = dot(diff, diff);
            minDist = min(minDist, d);
        }
    }
    return sqrt(minDist);
}

// ── Fireball temperature color (branchless mix chain) ─────────────

vec3 fireColor(float t) {
    vec3 fc0 = vec3(1.0, 1.0, 0.95);
    vec3 fc1 = vec3(1.0, 0.82, 0.3);
    vec3 fc2 = vec3(1.0, 0.48, 0.06);
    vec3 fc3 = vec3(0.8, 0.12, 0.01);
    vec3 fc4 = vec3(0.2, 0.02, 0.0);
    float s1 = smoothstep(0.0,  0.25, t);
    float s2 = smoothstep(0.25, 0.5,  t);
    float s3 = smoothstep(0.5,  0.75, t);
    float s4 = smoothstep(0.75, 1.0,  t);
    vec3 c = mix(fc0, fc1, s1);
    c = mix(c, fc2, s2);
    c = mix(c, fc3, s3);
    return mix(c, fc4, s4);
}

// ── Nebula color (branchless mix chain) ───────────────────────────

vec3 nebulaColor(vec2 uv, float time) {
    vec3 nc1 = vec3(0.2, 0.06, 0.6);
    vec3 nc2 = vec3(0.7, 0.15, 0.4);
    vec3 nc3 = vec3(0.06, 0.2, 0.8);
    vec3 nc4 = vec3(0.85, 0.6, 0.1);
    float nv = fbm(uv * 7.0 + 0.5) * 0.5 + 0.5;
    vec3 c = mix(nc1, nc2, nv);
    c = mix(c, nc3, fbm(uv * 3.0 + 2.0) * 0.5 + 0.5);
    return mix(c, nc4, nv * 0.15);
}

// ── Stars (pre-computed positions via hash) ───────────────────────
// Only 4 stars instead of 7 — roughly comparable visual coverage
// since the outer stars are tiny and easily masked by other layers.

float drawStars(vec2 uv, float time) {
    float stars = 0.0;
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        vec2 sp = vec2(hash(vec2(fi, 0.0)) * 2.0 - 1.0,
                       hash(vec2(fi, 1.0)) * 2.0 - 1.0);
        float sd = length(uv - sp);
        float sb = hash(vec2(fi, 2.0));
        float ss = 0.005 + sb * 0.022;
        stars += exp(-sd * sd / (ss * ss)) * sb;
        stars += exp(-sd * sd * 0.25 / (ss * ss)) * sb * 0.2;
    }
    float starFade = smoothstep(0.08, 0.4, time);
    return stars * starFade * 0.65;
}

// ── Energy wisps ──────────────────────────────────────────────────

float drawWisps(vec2 uv, float time) {
    float angle = atan(uv.y, uv.x);
    float d = length(uv);
    float wisp = 0.0;
    for (int j = 0; j < 2; j++) {
        float fj = float(j);
        float wa = angle + time * (1.1 + fj * 0.5) + fj * 2.0;
        float wr = d * (2.0 + fj * 1.5);
        float wn = fbm(vec2(wr, wa * 2.0));
        wisp += exp(-abs(wn - 0.5) * 9.0) * exp(-d * (1.0 + fj * 0.6));
    }
    return wisp * smoothstep(0.15, 0.4, time);
}

// ══════════════════════════════════════════════════════════════════
//  Shared "edge mask" function
// ══════════════════════════════════════════════════════════════════

float edgeMask(float d) {
    return 1.0 - smoothstep(1.05, 1.35, d);
}

// ── Main ──────────────────────────────────────────────────────────

void main() {
    float time      = vertexColor.r;
    float modeFlag  = vertexColor.g;    // 0.0=flash, 0.5=nova
    float intensity = vertexColor.b;
    float alpha     = vertexColor.a;

    // Early-out: skip all computation if master alpha is negligible
    if (alpha < 0.002) { discard; return; }

    vec2 uv = (uvCoord - 0.5) * 2.0;
    float d = length(uv);
    float angle = atan(uv.y, uv.x);

    // ══════════════════════════════════════════════════════════════
    //  Flash mode (modeFlag < 0.25)
    // ══════════════════════════════════════════════════════════════
    if (modeFlag < 0.25) {
        // Edge mask — only used in flash mode; not computed for hypernova path
        float em = 1.0 - smoothstep(1.05, 1.35, d);
        vec3 flashRgb = vec3(0.0);

        // Bell-curve flash envelope (wider, smoother)
        float t = time;
        float peak = 0.35;
        float sigmaRise = 0.06, sigmaFall = 0.18;
        float env = t < peak
            ? exp(-((t - peak) * (t - peak)) / sigmaRise)
            : exp(-((t - peak) * (t - peak)) / sigmaFall);
        // Ensure envelope never goes fully to zero at t=0
        if (t < 0.01) env = t / 0.01 * env;

        // ── Core: intense central spot, wider than before ─────────
        float core = exp(-d * d * 50.0) * env * 200.0;
        flashRgb += vec3(1.0, 1.0, 1.0) * core;

        // ── Inner glow: soft, fills the billboard ─────────────────
        float inner = exp(-d * 8.0) * env * 30.0;
        flashRgb += vec3(1.0, 0.95, 0.9) * inner;

        // ── Expanding ring (wider than before: σ=0.08) ────────────
        float ringR = t * 0.8;
        float ring = exp(-abs(d - ringR) * 18.0) * env * 8.0;
        // Ring echo (inner)
        ring += exp(-abs(d - ringR * 0.5) * 16.0) * env * 3.0;
        // Ring precursor (outer)
        ring += exp(-abs(d - ringR * 1.5) * 12.0) * env * 1.5;

        vec3 ringCol = mix(vec3(1.0, 1.0, 1.0), vec3(0.4, 0.6, 1.0), smoothstep(0.0, 0.5, t));
        ringCol = mix(ringCol, vec3(0.6, 0.2, 1.0), smoothstep(0.5, 1.0, t));
        flashRgb += ringCol * ring;

        // ── Afterglow: wide, soft, fading ─────────────────────────
        float after = (1.0 - t) * (1.0 - t) * exp(-d * 3.5) * 3.0;
        vec3 afterCol = vec3(0.4, 0.12, 0.85);
        flashRgb += afterCol * after;

        // ── Global faint screen brightening ───────────────────────
        flashRgb += vec3(0.2, 0.15, 0.6) * env * 0.5;

        flashRgb *= intensity * 2.0 * em;

        // Luminance-based alpha for HDR additive blending
        float flashAlpha = dot(flashRgb, vec3(0.299, 0.587, 0.114)) * alpha;
        fragColor = vec4(flashRgb, flashAlpha) * ColorModulator;
        if (fragColor.a < 0.001) discard;
        return;
    }

    // ══════════════════════════════════════════════════════════════
    //  Hypernova / Afterglow mode (modeFlag >= 0.25)
    // ══════════════════════════════════════════════════════════════

    vec3 rgb = vec3(0.0);

    // easeOutCubic: fast start, slow end
    float et = 1.0 - pow(1.0 - time, 3.0);

    // ── Shockwave ──────────────────────────────────────────────────
    float shockR = et * 1.8;
    float shock = exp(-abs(d - shockR) * 50.0);
    shock += exp(-abs(d - shockR * 0.6) * 35.0) * 0.55;
    shock += exp(-abs(d - shockR * 1.5) * 20.0) * 0.2;
    shock += exp(-d * d / (shockR * shockR + 0.02)) * 0.35;
    float shockFade = smoothstep(0.0, 0.08, time) * (1.0 - smoothstep(0.8, 1.0, time));
    shock *= shockFade;
    vec3 shockColor = mix(vec3(0.45, 0.7, 1.0), vec3(1.0, 0.9, 0.75), et * 0.15);
    rgb += shockColor * shock * 1.5 * intensity;

    // ── Fireball ──────────────────────────────────────────────────
    // Reduced from 4 to 3 FBM calls: fbm4 (fine detail) merged into fbm2.
    float fireR = et * 3.0;
    float fbm1 = fbm(uv * 6.0 + time * 1.6);
    float fbm2 = fbm(uv * 13.0 - time * 2.0 + 2.5);
    float fbm3 = fbm(uv * 3.0 + time * 0.5 + 4.0);
    float fireProfile = exp(-d * d / (fireR * fireR + 0.06));
    float fireball = fireProfile * (0.4 + fbm1 * 0.35 + fbm2 * 0.35 + fbm3 * 0.25);
    float fireFade = smoothstep(0.02, 0.15, time);
    fireball *= fireFade;

    float fireTemp = d / max(fireR, 0.01);
    vec3 fireCol = fireColor(fireTemp);
    rgb += fireCol * fireball * 1.1 * intensity;

    // ── Lightning ──────────────────────────────────────────────────
    float boltN = 13.0;
    float boltNoise = fbm(vec2(angle * boltN / 6.28, time * 1.3)) * 0.45;
    float boltPat = abs(sin(angle * boltN + boltNoise * 6.28));
    float boltThick = 0.02 + fbm(vec2(angle * 3.5, d * 7.0)) * 0.04;
    float bolt = 1.0 - smoothstep(0.0, boltThick, boltPat);
    float boltPat2 = abs(sin(angle * boltN * 1.8 + boltNoise * 3.14 + 1.2));
    bolt += (1.0 - smoothstep(0.0, boltThick * 0.55, boltPat2)) * 0.35;
    float boltExtent = smoothstep(fireR * 1.4, fireR * 0.3, d);
    bolt *= boltExtent * fireFade;
    bolt *= 0.35 + hash(vec2(floor(angle * boltN / 6.28), 2.0)) * 1.5;
    vec3 boltCol = mix(vec3(0.35, 0.55, 1.0), vec3(1.0, 0.9, 1.0), hash(vec2(angle * boltN, 0.0)));
    rgb += boltCol * bolt * 0.85 * intensity;

    // ── Nebula ─────────────────────────────────────────────────────
    float nebFbm = fbm(uv * 4.5 + time * 0.3);
    float nebVor = voronoi(uv * 5.5 + time * 0.2);
    float nebula = nebFbm * 0.5 + (1.0 - nebVor) * 0.5;
    float nebR = 0.5 + et * 4.0;
    nebula *= exp(-d / nebR) * 0.65;
    nebula *= smoothstep(0.1, 0.35, time);
    vec3 nebCol = nebulaColor(uv, time);
    rgb += nebCol * nebula * 0.45;

    // ── Stars (4 sprites, reduced from 7) ──────────────────────────
    float starsTotal = drawStars(uv, time);
    vec3 starCol = mix(vec3(0.6, 0.75, 1.0), vec3(1.0, 0.88, 0.6), fbm(uv * 15.0));
    rgb += starCol * starsTotal * intensity;

    // ── Volumetric glow ────────────────────────────────────────────
    float glow = exp(-d * 0.9 / (1.0 + et * 3.5)) * 0.35;
    glow *= 1.0 + 0.2 * sin(time * 5.0 + d * 2.0);
    glow *= smoothstep(0.0, 0.2, time);
    vec3 glowCol = mix(vec3(1.0, 0.5, 0.15), vec3(0.35, 0.12, 0.8), et * 0.4);
    rgb += glowCol * glow * intensity;

    // ── Energy wisps (2 curves, reduced from 3) ────────────────────
    float wisp = drawWisps(uv, time);
    vec3 wispCol = mix(vec3(0.5, 0.7, 1.0), vec3(0.9, 0.35, 0.8), fbm(uv * 5.0 + time));
    rgb += wispCol * wisp * 0.28 * intensity;

    // ── Polar jets ─────────────────────────────────────────────────
    float jetX = abs(uv.x);
    float jetY = abs(uv.y);
    float jet = exp(-jetX * 10.0 / (1.0 + et * 2.5)) * exp(-abs(jetY - et * 0.9) * 5.0);
    jet += exp(-jetX * 6.0 / (1.0 + et * 2.5)) * exp(-abs(jetY - et * 1.5) * 2.5) * 0.4;
    jet *= smoothstep(0.2, 0.55, time);
    vec3 jetCol = mix(vec3(0.6, 0.75, 1.0), vec3(0.4, 0.2, 0.95), jetY * 0.4);
    rgb += jetCol * jet * 0.15 * intensity;

    // ── Depth approximation ────────────────────────────────────────
    float depthFade = exp(-d * 0.5);
    rgb *= 0.4 + depthFade * 0.6;

    // ── Composite alpha ────────────────────────────────────────────
    float finalAlpha = (shock * 0.7 + fireball * 0.5 + bolt * 0.6
                      + nebula * 0.3 + glow * 0.5 + wisp * 0.3
                      + starsTotal * 0.75 + jet * 0.25) * alpha;

    fragColor = vec4(rgb, finalAlpha) * ColorModulator;
    if (fragColor.a < 0.0005) discard;
}

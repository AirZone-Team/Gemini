#version 330

// Dynamic jump/landing ring effect — six animation systems driven by u_LifeTime.
//
//   #ifdef HEAVY   → orange-red warm gradient (heavy landing)
//   #ifdef NORMAL  → gray-tan neutral tones   (normal landing)
//   (no define)    → user theme color from vertexColor.rgb (jump takeoff)
//
// vertexColor.a carries the normalised lifetime progress (0→1).
// All animation curves are computed procedurally from this value.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

// ═══════════════════════════════════════════════════════════════
//  Noise / hash primitives
// ═══════════════════════════════════════════════════════════════

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
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

float fbm4(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    float f = 1.0;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p * f);
        f *= 2.3;
        a *= 0.45;
    }
    return v;
}

float fbm5(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    float f = 1.0;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p * f);
        f *= 2.4;
        a *= 0.42;
    }
    return v;
}

// ═══════════════════════════════════════════════════════════════
//  Animation curves — all driven by u_LifeTime (0→1)
// ═══════════════════════════════════════════════════════════════

// Radius scale: impact→rebound→expansion
float animRadius(float t) {
    if (t < 0.05) return mix(1.0, 1.2, t / 0.05);           // 0→0.05: 1.0→1.2
    if (t < 0.12) return mix(1.2, 1.0, (t - 0.05) / 0.07);  // 0.05→0.12: 1.2→1.0
    return mix(1.0, 1.5, (t - 0.12) / 0.88);                 // 0.12→1.0: 1.0→1.5
}

// Thickness: burst thick→rebound thin→dissipate to zero
float animThickness(float t) {
    if (t < 0.03) return mix(0.8, 1.3, t / 0.03);            // 0→0.03: 0.8→1.3
    if (t < 0.15) return mix(1.3, 0.8, (t - 0.03) / 0.12);   // 0.03→0.15: 1.3→0.8
    return mix(0.8, 0.0, (t - 0.15) / 0.85);                  // 0.15→1.0: 0.8→0.0
}

// Whiteness: pure white flash at burst, transitions to theme colour
float animWhiteness(float t) {
    if (t < 0.02) return 1.0;
    if (t < 0.10) return 1.0 - (t - 0.02) / 0.08;            // 0.02→0.10: 1→0
    return 0.0;
}

// Emissive intensity: overexposure at burst → sustain → fade
float animEmissive(float t) {
    if (t < 0.04) return mix(1.0, 3.0, t / 0.04);            // 0→0.04: 1→3
    if (t < 0.12) return mix(3.0, 1.0, (t - 0.04) / 0.08);   // 0.04→0.12: 3→1
    if (t < 0.60) return 1.0;                                 // hold
    return mix(1.0, 0.0, (t - 0.60) / 0.40);                  // 0.60→1.0: 1→0
}

// Master alpha: instant on → hold → fade
float animAlpha(float t) {
    if (t < 0.03) return t / 0.03;                            // fast fade-in
    if (t < 0.45) return 1.0;                                 // hold
    return 1.0 - smoothstep(0.45, 1.0, t);                    // soft fade-out
}

// Noise amplitude: high→lower→higher (edge chaos)
float animNoiseAmp(float t) {
    if (t < 0.05) return mix(0.3, 1.0, t / 0.05);            // ramp up to burst
    if (t < 0.18) return mix(1.0, 0.55, (t - 0.05) / 0.13);  // tighten
    return mix(0.55, 1.1, (t - 0.18) / 0.82);                 // rising chaos
}

// Noise frequency: increases toward dissipation (fine grain)
float animNoiseFreq(float t) {
    if (t < 0.50) return 8.0;
    return mix(8.0, 22.0, (t - 0.50) / 0.50);                // 0.5→1.0: 8→22
}

// Noise flow speed: ramps up over time
float animNoiseFlow(float t) {
    return t * 5.0;
}

// Dissolve threshold: 0 until halfway, then ramps to 1
float animDissolve(float t) {
    if (t < 0.45) return 0.0;
    return smoothstep(0.45, 0.95, t);                         // 0.45→0.95: 0→1
}

// ═══════════════════════════════════════════════════════════════
//  Ring builder — evaluates one ring layer
// ═══════════════════════════════════════════════════════════════

// Returns (colour, alpha) for a ring at the given parameters.
vec4 evalRing(vec2 center, float t, float angle,
              float ringCenter, float ringHalfWidth,
              vec3 colorBasis,
              float noiseAmp, float noiseFreq, float noiseTime,
              float dissolve, float whiteness, float emissive,
              float ringAlphaMul, float lifetime) {

    // ── Ring mask from radius / thickness ─────────────────
    float innerEdge = smoothstep(ringCenter - ringHalfWidth - 0.03,
                                  ringCenter - ringHalfWidth, t);
    float outerEdge = 1.0 - smoothstep(ringCenter + ringHalfWidth,
                                        ringCenter + ringHalfWidth + 0.04, t);
    float ringMask = innerEdge * outerEdge;

    if (ringMask < 0.001) return vec4(0.0);

    // ── Brightness curve: peaks at ring centre ────────────
    float brightness = 1.0 - abs(t - ringCenter) / ringHalfWidth;
    brightness = clamp(brightness, 0.0, 1.0);
    brightness = brightness * brightness;

    // ── Rotating energy streaks (decaying rotation speed) ─
    // ω(t) = ω0·exp(-t·8), ∫ω = ω0/8·(1−exp(−8t)) ≈ 12.57·(1−exp(−8t))
    float rotAccum = 12.57 * (1.0 - exp(-lifetime * 8.0));
    float rotAngle = angle + rotAccum;
    float streaks = sin(rotAngle * 9.0 + fbm(center * 6.0 + noiseTime) * 4.0);
    streaks = streaks * 0.42 + 0.58;  // 0.16→1.0, bright streaks on dim base
    brightness *= (0.7 + 0.3 * streaks);

    // ── Edge noise distortion ─────────────────────────────
    float edgeNoise = fbm4(center * noiseFreq + vec2(noiseTime * 0.7, noiseTime * 0.3));
    float noiseFactor = 1.0 + (edgeNoise - 0.5) * noiseAmp * 1.4;
    ringMask *= noiseFactor;

    // ── Dissolve (burning-paper edge erosion) ─────────────
    if (dissolve > 0.001) {
        float dissNoise = fbm4(center * 11.0 + dissolve * 3.5);
        float dissEdge = dissNoise - dissolve;
        ringMask *= smoothstep(0.0, 0.09, dissEdge);
    }

    ringMask = clamp(ringMask, 0.0, 1.0);

    // ── Colour: whiteness flash → theme colour ────────────
    vec3 col = mix(colorBasis, vec3(1.0), whiteness);
    col *= brightness;

    return vec4(col * emissive, ringMask * ringAlphaMul);
}

// ═══════════════════════════════════════════════════════════════
//  Mathematical light-particle spot
// ═══════════════════════════════════════════════════════════════

// Evaluates one pseudo-random light speckle that drifts outward and fades.
float evalSpot(vec2 center, float lf, float masterAlpha,
               vec2 seedA, vec2 seedB, vec2 seedC, vec2 seedD) {

    float spotAngle  = hash(seedA) * 6.28318;
    float spotRadius0 = 0.50 + hash(seedB) * 0.32;           // starts just outside main ring
    float spotRadius  = spotRadius0 + lf * 0.40;              // radial drift outward
    float spotSize    = 0.010 + hash(seedC) * 0.030;
    float spotBright  = 0.30 + hash(seedD) * 0.45;

    vec2  spotPos  = vec2(cos(spotAngle), sin(spotAngle)) * spotRadius;
    float d        = length(center - spotPos);

    // Organic noise halo — fbm5 on high-frequency coords around the spot
    float n = fbm5((center - spotPos) * 38.0 + lf * 2.3);
    float mask = 1.0 - smoothstep(0.0, spotSize, d);
    mask *= (0.55 + 0.45 * n);

    // Subtle twinkle
    float twinkle = 0.75 + 0.25 * sin(lf * 18.0 + hash(seedA) * 12.0);

    // Fade with lifetime — spots dim and vanish as ring dissipates
    float spotLife = 1.0 - smoothstep(0.0, 0.72, lf);

    return mask * spotBright * twinkle * spotLife * masterAlpha;
}

// ═══════════════════════════════════════════════════════════════
//  Main
// ═══════════════════════════════════════════════════════════════

void main() {
    vec2  center = uvCoord - 0.5;
    float dist   = length(center);
    float angle  = atan(center.y, center.x) * 0.15915494 + 0.5;  // 0→1

    // Normalised radius: 0 at centre, ~1.0 at quad-edge midpoint
    float t = dist / 0.5;

    // ── Read lifetime from vertex colour alpha ────────────
    float lifetime = vertexColor.a;
    float lf = clamp(lifetime, 0.0, 1.0);

    // ── Pick base ring colour per type ────────────────────
    vec3 baseColor;
#ifdef HEAVY
    // Orange→red warm gradient along radius
    vec3 innerWarm = vec3(1.0, 0.55, 0.05);
    vec3 outerWarm = vec3(1.0, 0.15, 0.02);
    baseColor = mix(innerWarm, outerWarm, clamp((t - 0.2) / 0.6, 0.0, 1.0));
#elif defined(NORMAL)
    // Gray-tan neutral tones
    baseColor = mix(vec3(0.82, 0.78, 0.70), vec3(0.65, 0.60, 0.52), clamp((t - 0.2) / 0.6, 0.0, 1.0));
#else
    // Jump takeoff — user theme colour from vertexColor.rgb
    baseColor = vertexColor.rgb;
#endif

    // ── Compute animation curve values ────────────────────
    float rScale    = animRadius(lf);
    float thickness = animThickness(lf);
    float whiteness = animWhiteness(lf);
    float emissive  = animEmissive(lf);
    float masterAlpha = animAlpha(lf);
    float noiseAmp  = animNoiseAmp(lf);
    float noiseFreq = animNoiseFreq(lf);
    float noiseFlow = animNoiseFlow(lf);
    float dissolve  = animDissolve(lf);

    // Ring geometry: centre and half-width in normalised-t space
    float ringCenter = 0.45 * rScale;
    float ringHalfWidth = 0.28 * thickness;

    // ── Primary ring ──────────────────────────────────────
    vec4 primary = evalRing(center, t, angle,
                            ringCenter, ringHalfWidth,
                            baseColor,
                            noiseAmp, noiseFreq, noiseFlow,
                            dissolve, whiteness, emissive,
                            masterAlpha, lf);

    vec3 finalRgb = primary.rgb;
    float finalAlpha = primary.a;

    // ── Afterglow / ghost ring (aftershock ripple) ────────
    // Appears ~0.1 s after the main ring, thinner, dimmer, faster expansion.
    const float AG_DELAY = 0.12;
    if (lf > AG_DELAY) {
        float agLocal   = (lf - AG_DELAY) / (1.0 - AG_DELAY);
        float agRScale  = rScale * (1.0 + agLocal * 0.32);         // overtakes main ring radially
        float agCenter  = 0.48 * agRScale;
        float agHalfW   = ringHalfWidth * 0.25;                     // extremely thin
        float agAlpha   = masterAlpha * 0.18;                       // ghost-level brightness

        vec4 ag = evalRing(center, t, angle,
                           agCenter, agHalfW,
                           baseColor,
                           noiseAmp * 0.45, noiseFreq * 1.6, noiseFlow * 1.35,
                           dissolve * 0.35, whiteness * 0.25, emissive * 0.35,
                           agAlpha, agLocal);

        finalRgb   += ag.rgb;
        finalAlpha  = max(finalAlpha, ag.a);
    }

    // ── Free light-points / mathematical particles ────────
    // 4 pseudo-random speckles that drift radially outward and fade.
    const int SPOT_COUNT = 4;
    for (int i = 0; i < SPOT_COUNT; i++) {
        float fi = float(i);
        vec2 sa = vec2(fi * 12.9898 + 1.0,  78.233  + fi);
        vec2 sb = vec2(fi * 45.1643 + 3.0,  93.7612 + fi);
        vec2 sc = vec2(fi * 78.3512 + 5.0, 112.4891 + fi);
        vec2 sd = vec2(fi * 33.7821 + 7.0,  55.9134 + fi);

        float spotAlpha = evalSpot(center, lf, masterAlpha, sa, sb, sc, sd);

        finalRgb   += baseColor * spotAlpha * 0.75;
        finalAlpha  = max(finalAlpha, spotAlpha);
    }

    // ── Colour modulation & output ────────────────────────
    fragColor = vec4(finalRgb, finalAlpha) * ColorModulator;

    if (fragColor.a < 0.0005) {
        discard;
    }
}

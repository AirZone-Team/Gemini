// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  Orbiting halo array (魔法光环)
//
//  The same discharge language as jump_circle, but running continuously
//  instead of over one life cycle:
//    · a crest line with a hairline riding just inside it, dashed per style
//    · counter-rotating echo rings and dashed satellites
//    · a rune band that ignites cell by cell as a sweep head passes over it
//    · tapered energy flares outside the crest
//    · pressure rings that are emitted at the crest and run outward
//    · an inscribed polygon lattice turning in the middle
//    · a core iris throwing rotating rays
//    · orbiting sparks stretched along their own motion, plus a starfield
//
//  Three sweep heads ride the crest in opposite directions and every element
//  brightens as one passes over it, which is what makes the array look driven
//  round rather than simply faded in place. Nothing shares a rotation rate:
//  the frame stays still and each element carries its own phase, so the whole
//  ornament reads as mechanism with depth instead of one spinning decal.
//
//  Clarity comes from the same two rules used throughout:
//    1. every stroke is an analytic line whose width widens to one screen pixel
//       (px, from fwidth), so the foreshortened far side of a tilted halo stops
//       turning into mush;
//    2. colour is tone-mapped on its PEAK, not per channel, so an over-driven
//       stroke rolls off towards white without the palette washing out — the
//       old per-channel clamp is exactly what turned this halo into a white bulb.
//
//  HaloReserved.x = dynamics, HaloReserved.y = accent pass (0 or 1)
// ═══════════════════════════════════════════════════════════════════

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

layout(std140) uniform HaloUniforms {
    vec4 HaloMeta;       // time, style, color mode, feature flags
    vec4 HaloPrimary;    // custom primary rgb, opacity
    vec4 HaloSecondary;  // custom secondary rgb, brightness
    vec4 HaloAccent;     // custom accent rgb, glow
    vec4 HaloGeometry;   // radius, thickness, flare length, flare count
    vec4 HaloDetail;     // layers, rune detail, star density, sharpness
    vec4 HaloMotion;     // rotation, pulse, distortion, rainbow speed
    vec4 HaloReserved;   // dynamics, accent pass, unused, unused
};

in vec2 uvCoord;
out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

float hash11(float p) {
    return fract(sin(p * 127.1) * 43758.5453123);
}

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
        mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

float fbm(vec2 p) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 3; i++) {
        sum += valueNoise(p) * amplitude;
        p = p * 2.07 + 3.17;
        amplitude *= 0.5;
    }
    return sum * 1.142857;
}

// Analytic stroke: a core that never goes sub-pixel plus a gaussian halo.
// `gain` trades brightness for the extra coverage a widened stroke needs, on a
// square root so a foreshortened line stays hot enough to read as light.
float neon(float dist, float w, float px, float haloAmp, float haloSq) {
    float ww = max(w, px);
    float gain = sqrt(w / ww);
    float a = abs(dist);
    return smoothstep(ww, 0.0, a) * gain
         + exp(-a * a * haloSq / (ww * ww)) * haloAmp * gain;
}

// AA'd interval on a periodic coordinate. The feather is capped below half the
// interval so a collapsing window cannot flip the smoothstep edges.
float window(float x, float lo, float hi, float feather) {
    float w = clamp(feather, 1e-5, max((hi - lo) * 0.45, 1e-5));
    return smoothstep(lo - w, lo + w, x) * (1.0 - smoothstep(hi - w, hi + w, x));
}

// AA'd interval on a radius.
float band(float x, float lo, float hi, float feather) {
    float w = max(feather, 1e-5);
    return smoothstep(lo - w, lo + w, x) * (1.0 - smoothstep(hi - w, hi + w, x));
}

// Shortest signed angular offset, used by anything anchored to a direction.
float angleDelta(float a, float b) {
    return abs(mod(a - b + PI, TAU) - PI);
}

bool hasFeature(int bit) {
    return (int(HaloMeta.w + 0.5) & bit) != 0;
}

void stylePalette(int styleId, out vec3 primary, out vec3 secondary, out vec3 accent) {
    if (styleId == 1) {
        primary = vec3(0.56, 0.27, 1.00);
        secondary = vec3(0.12, 0.88, 1.00);
        accent = vec3(1.00, 0.80, 0.34);
    } else if (styleId == 2) {
        primary = vec3(0.08, 0.92, 1.00);
        secondary = vec3(1.00, 0.12, 0.64);
        accent = vec3(0.88, 1.00, 1.00);
    } else if (styleId == 3) {
        primary = vec3(0.31, 0.08, 0.76);
        secondary = vec3(0.94, 0.04, 0.30);
        accent = vec3(0.82, 0.68, 1.00);
    } else if (styleId == 4) {
        primary = vec3(1.00, 0.28, 0.015);
        secondary = vec3(1.00, 0.72, 0.06);
        accent = vec3(1.00, 0.96, 0.62);
    } else if (styleId == 5) {
        primary = vec3(0.22, 0.72, 1.00);
        secondary = vec3(0.56, 0.96, 1.00);
        accent = vec3(0.96, 1.00, 1.00);
    } else if (styleId == 6) {
        primary = vec3(1.00, 0.22, 0.68);
        secondary = vec3(0.18, 0.82, 1.00);
        accent = vec3(1.00, 0.88, 0.30);
    } else {
        primary = vec3(1.00, 0.66, 0.18);
        secondary = vec3(1.00, 0.28, 0.70);
        accent = vec3(1.00, 0.98, 0.82);
    }
}

vec3 spectralColor(float phase) {
    return 0.58 + 0.42 * cos(TAU * (phase + vec3(0.00, 0.33, 0.67)));
}

[shader("fragment")]

void main() {
    float time = HaloMeta.x;
    int styleId = clamp(int(HaloMeta.y + 0.5), 0, 6);
    int colorMode = clamp(int(HaloMeta.z + 0.5), 0, 2);

    float opacity = HaloPrimary.a;
    float brightness = HaloSecondary.a;
    float glow = HaloAccent.a;

    vec2 p = uvCoord * 2.0 - 1.0;
    float d = length(p);
    float ang = atan(p.y, p.x);

    // One screen pixel measured in UV units, taken before any discard so the
    // derivatives are always evaluated in uniform control flow.
    float px = max(length(vec2(fwidth(p.x), fwidth(p.y))), 1e-6);
    if (d > 1.06 || opacity < 0.001 || brightness < 0.001) discard;

    float ringRadius = HaloGeometry.x;
    float thickness = HaloGeometry.y;
    float flareLength = HaloGeometry.z;
    float flareCount = max(4.0, floor(HaloGeometry.w + 0.5));
    int layerCount = clamp(int(HaloDetail.x + 0.5), 1, 5);
    int runeDetail = clamp(int(HaloDetail.y + 0.5), 0, 3);
    int starDensity = clamp(int(HaloDetail.z + 0.5), 0, 3);
    float sharpness = clamp(HaloDetail.w, 0.0, 1.0);
    float rotation = HaloMotion.x;
    float pulse = HaloMotion.y;
    float distortion = HaloMotion.z;
    float dynamics = clamp(HaloReserved.x, 0.0, 2.0);
    bool accentPass = HaloReserved.y > 0.5;

    // Sharpness drives clarity: narrow strokes, a tighter halo, and a lower
    // pixel floor before a line is widened back out.
    float clarity = mix(0.55, 2.10, sharpness);
    float lw = 1.0 / clarity;
    float haloGain = clamp(0.035 + glow * 0.085, 0.02, 0.30) * (1.45 - clarity * 0.22);
    float haloSq = mix(1.0 / 11.0, 1.0 / 34.0, clamp((clarity - 0.4) / 1.8, 0.0, 1.0));
    float aaFloor = px * mix(1.7, 0.75, clamp((clarity - 0.4) / 1.8, 0.0, 1.0));
    float edge = max(px * 0.9, aaFloor * 0.35);

    // ── Frame ─────────────────────────────────────────────────────
    float breathe = 0.5 + 0.5 * sin(time * 2.1);
    float r = ringRadius * (1.0 + (breathe - 0.5) * pulse * 0.055)
            * (accentPass ? 1.045 : 1.0);
    float w0 = (0.0035 + thickness * 0.42) * lw * (accentPass ? 0.58 : 1.0);

    // Spiral shear: the array twists against its own radius as it turns, so the
    // spokes bend instead of staying radially straight.
    float twist = clamp(rotation, -1.0, 1.0) * (0.012 + dynamics * 0.030) * (1.0 - d)
                * sin(time * 0.9);
    float ua = fract(ang / TAU + 0.5 + twist);
    float au = px / max(d, 0.06) / TAU;

    float noiseField = fbm(p * mix(3.0, 7.0, distortion)
                           + vec2(31.7) + time * rotation * 0.55);
    float dw = d + (noiseField - 0.5) * distortion * 0.050;

    // ── Sweep heads ───────────────────────────────────────────────
    // Three of them, at incommensurate rates and opposite directions, so the
    // pattern never visibly loops and the ring always has something arriving.
    float sweep = (rotation * 0.55 + 0.40) * (0.35 + dynamics * 0.85);
    float headA = fract(time * sweep * 0.110);
    float headB = fract(0.5 - time * sweep * 0.075);
    float headC = fract(0.31 + time * sweep * 0.160);
    float tailK = mix(13.0, 5.0, clamp(dynamics * 0.5, 0.0, 1.0));
    float comet = max(exp(-fract(headA - ua) * tailK),
                  max(exp(-fract(ua - headB) * tailK) * 0.78,
                      exp(-fract(headC - ua) * tailK * 1.7) * 0.42));

    // ── Style gate: how the crest is broken up ────────────────────
    float cellScale = accentPass ? 1.37 : 1.0;
    float gate = 1.0;
    if (styleId == 0) {
        float cells = 9.0 * cellScale;
        float t = fract(ua * cells);
        gate = 0.34 + 0.66 * window(t, 0.05, 0.95, au * cells * 0.5);
    } else if (styleId == 1) {
        float cells = 12.0 * cellScale;
        float t = fract(ua * cells);
        gate = 0.32 + 0.68 * window(t, 0.07, 0.93, au * cells * 0.5);
    } else if (styleId == 2) {
        float cells = 28.0 * cellScale;
        float idx = floor(ua * cells);
        float t = fract(ua * cells);
        float hi = 0.42 + hash11(idx + 7.0) * 0.45;
        gate = 0.16 + 0.84 * window(t, 0.06, hi, au * cells * 0.5);
    } else if (styleId == 3) {
        float cells = 22.0 * cellScale;
        float idx = floor(ua * cells);
        float t = fract(ua * cells);
        float on = step(0.34, hash11(idx + floor(time * 0.9) * 0.17));
        gate = 0.10 + 0.90 * on * window(t, 0.04, 0.96, au * cells * 0.5);
    } else if (styleId == 4) {
        gate = 0.40 + 0.85 * noiseField;
    } else if (styleId == 5) {
        gate = 0.52 + 0.48 * (0.5 + 0.5 * cos(ua * TAU * 6.0 - time * rotation * 3.0));
    } else {
        float cells = 16.0 * cellScale;
        float idx = floor(ua * cells);
        float t = fract(ua * cells);
        float hi = 0.30 + hash11(idx * 1.7 + 4.0) * 0.62;
        gate = 0.22 + 0.78 * window(t, 0.08, hi, au * cells * 0.45);
    }

    // ── Crest: main line plus a hairline riding just inside it ────
    float crest = neon(dw - r, w0, aaFloor, haloGain, haloSq) * (0.42 + 1.30 * comet) * gate;
    crest += neon(dw - (r - w0 * 1.7), w0 * 0.20, aaFloor, haloGain, haloSq)
           * (0.28 + 0.90 * comet) * gate;
    // A continuous spine through the dash gaps keeps the ring reading as one
    // closed circuit rather than a scatter of unrelated arcs.
    crest += neon(dw - r, w0 * 0.13, aaFloor, haloGain, haloSq) * 0.34;
    // Broad palette wash behind the crest: this is what gives the ring mass. It
    // stays a wide low bump rather than more stacked hairlines, so it survives
    // the LOD gate instead of turning into fog.
    float auraQ = (dw - r) / max(w0 * 6.0 + 0.030, 1e-4);
    float aura = exp(-auraQ * auraQ) * (0.10 + 0.16 * comet) * (0.55 + glow * 0.25);

    // ── Echo rings: concentric, each running its own way ──────────
    float echoes = 0.0;
    for (int layer = 1; layer < 5; layer++) {
        if (layer >= layerCount) break;
        float lf = float(layer);
        float er = r - lf * (w0 * 1.55 + 0.011);
        float ew = w0 * (1.0 - lf * 0.17);
        float ea = 1.0 - lf / (float(layerCount) + 0.75);
        float dir = mix(1.0, -1.0, mod(lf, 2.0));
        float cells = (styleId == 2 ? 30.0 : 11.0) + lf * 6.0;
        float t = fract(ua * cells + lf * 0.21 + time * rotation * (0.09 + lf * 0.05) * dir);
        float dash = styleId == 0 || styleId == 5
                ? 0.55 + 0.45 * (0.5 + 0.5 * cos(t * TAU))
                : window(t, 0.08, 0.62, au * cells * 0.40);
        echoes += neon(dw - er, ew, aaFloor, haloGain, haloSq) * ea * dash
                * (0.30 + 0.80 * comet);
    }

    // ── Satellites: thin dashed rings outside the crest ───────────
    float sats = 0.0;
    for (int ring = 0; ring < 3; ring++) {
        float rf = float(ring);
        float sr = r + w0 * 1.1 + rf * (w0 * 1.4 + 0.013);
        float cells = 8.0 + rf * 7.0;
        float dir = rf == 1.0 ? -1.0 : 1.0;
        float t = fract(ua * cells + rf * 0.17 + time * rotation * (0.14 + rf * 0.09) * dir);
        float dash = window(t, 0.10, 0.60, au * cells * 0.40);
        sats += neon(dw - sr, w0 * 0.24, aaFloor, haloGain, haloSq) * dash
             * (0.62 - rf * 0.13) * (0.45 + 0.85 * comet);
    }

    // ── Rune band: cells ignite behind whichever head just passed ─
    float runes = 0.0;
    if (hasFeature(2) && runeDetail > 0) {
        float cells = 10.0 + float(runeDetail) * 7.0;
        float idx = floor(ua * cells);
        float t = fract(ua * cells);
        float h = hash11(idx + 13.0);
        float feather = max(au * cells * 0.45, 1e-4);
        float rr = r * 0.815;
        float rh = 0.010 + w0 * 0.55;

        float body = window(t, 0.16, 0.30 + h * 0.32, feather)
                   * band(dw, rr - rh, rr + rh, edge);
        float bar = window(t, 0.44, 0.62, feather)
                  * band(dw, rr - rh * 1.8, rr - rh * 0.7, edge) * step(0.48, h);
        float pip = window(t, 0.66, 0.78, feather)
                  * band(dw, rr + rh * 1.4, rr + rh * 2.1, edge) * step(0.66, h);

        float centre = (idx + 0.5) / cells;
        float ignite = clamp(exp(-fract(headA - centre) * 13.0)
                           + exp(-fract(centre - headB) * 13.0) * 0.6
                           + exp(-fract(headC - centre) * 18.0) * 0.45, 0.0, 1.5);
        runes = (body + bar * 0.85 + pip) * (0.16 + 1.05 * ignite)
              * (accentPass ? 1.15 : 0.85);
    }

    // ── Energy flares: tapered wedges outside the crest ───────────
    float flares = 0.0;
    {
        float cells = flareCount * cellScale;
        float idx = floor(ua * cells);
        float t = fract(ua * cells);
        float h = hash11(idx + floor(time * 1.7) * 0.31);
        float base = r + w0 * 0.45;
        float len = flareLength * (0.45 + h * 0.85) * (0.75 + dynamics * 0.45)
                  * (accentPass ? 0.62 : 1.0);
        float k = clamp((dw - base) / max(len, 1e-4), 0.0, 1.0);
        float hw = 0.30 * (1.0 - k) * (1.0 - k) + au * cells * 0.25;
        flares = window(t, 0.5 - hw, 0.5 + hw, au * cells * 0.5)
               * band(dw, base, base + len, edge)
               * (1.0 - k * 0.55) * (0.30 + 0.85 * h) * (0.40 + 1.05 * comet);
    }

    // ── Pressure rings: emitted at the crest, running outward ────
    float pressure = 0.0;
    if (pulse > 0.001) {
        for (int wave = 0; wave < 3; wave++) {
            float wf = float(wave);
            float ph = fract(time * (0.20 + dynamics * 0.14) + wf * 0.3333);
            float pr = r + ph * (flareLength * 0.9 + 0.16);
            float fade = (1.0 - ph) * (1.0 - ph) * pulse;
            pressure += neon(dw - pr, (0.0030 + 0.0075 * (1.0 - ph)) * lw,
                             aaFloor, haloGain, haloSq) * fade * (0.5 + 0.6 * comet);
        }
    }

    // ── Figurehead: a crown riding the leading head ───────────────
    float crown = 0.0;
    if (hasFeature(1)) {
        float headAng = headA * TAU - PI;
        float reach = flareLength * (0.85 + dynamics * 0.30) + w0 * 2.0;
        for (int prong = 0; prong < 3; prong++) {
            float pf = float(prong) - 1.0;
            float centre = headAng + pf * 0.17;
            float plen = reach * (pf == 0.0 ? 1.0 : 0.58);
            float k = clamp((dw - r) / max(plen, 1e-4), 0.0, 1.0);
            float hw = mix(0.055, 0.004, pow(k, 0.7)) * (pf == 0.0 ? 1.0 : 0.62) * lw;
            crown += (1.0 - smoothstep(hw, hw + edge, angleDelta(ang, centre)))
                   * band(dw, r, r + plen, edge) * (1.0 - k * 0.5);
        }
        vec2 jewel = vec2(cos(headAng), sin(headAng)) * (r + reach * 0.82);
        float js = (0.010 + w0 * 0.42) * lw;
        crown += (1.0 - smoothstep(js, js * 2.2, length(p - jewel))) * 1.4;
        crown *= 0.55 + 0.85 * comet;
    }

    // ── Lattice: the array's rotating skeleton ────────────────────
    // Two counter-running figures, so the middle keeps turning even when the
    // rings around it are nearly still.
    float lattice = 0.0;
    if (runeDetail > 1) {
        float sides = styleId == 2 ? 6.0 : (styleId == 4 ? 5.0 : (styleId == 3 ? 4.0 : 3.0));
        float seg = TAU / sides;
        for (int fig = 0; fig < 2; fig++) {
            float ff = float(fig);
            float la = ang + time * rotation * (0.22 + ff * 0.30) * (ff > 0.5 ? -1.0 : 1.0)
                     + ff * (PI / sides);
            float lr = r * (0.66 - ff * 0.16) + sin(time * 1.7 + ff * 2.0) * pulse * 0.010;
            float poly = cos(seg * 0.5) / cos(fract(la / seg) * seg - seg * 0.5);
            lattice += neon(d - lr * poly, w0 * 0.42, aaFloor, haloGain, haloSq)
                     * (0.50 + 0.90 * comet) * (1.0 - ff * 0.25);
        }
    }

    // ── Core iris: a hot centre throwing rotating rays ────────────
    float coreHeat = 0.55 + 0.45 * sin(time * 2.6);
    float coreR = (0.026 + w0 * 1.10) * (0.75 + coreHeat * 0.6) * lw;
    float core = exp(-d * d / max(coreR * coreR * 2.2, 1e-5)) * (0.7 + 0.9 * comet);
    float rays = 0.0;
    {
        float cells = 18.0;
        float idx = floor(ua * cells);
        float t = fract(ua * cells);
        float h = hash11(idx + 5.0);
        float rayLen = r * (0.30 + h * 0.72) * (0.55 + coreHeat * 0.7);
        rays = window(t, 0.38, 0.62, au * cells * 0.35)
             * (1.0 - smoothstep(rayLen * 0.10, max(rayLen, 1e-4), d))
             * (0.55 + 1.30 * comet) * (0.5 + pulse * 0.5);
    }

    // ── Sparks: orbiting points stretched along their own motion ──
    float sparks = 0.0;
    if (hasFeature(4)) {
        int sparkLimit = min(14, 4 + starDensity * 4);
        float spinDir = rotation >= 0.0 ? 1.0 : -1.0;
        for (int i = 0; i < 14; i++) {
            if (i >= sparkLimit) break;
            float fi = float(i);
            float s1 = hash11(fi * 7.13 + 3.0);
            float s2 = hash11(fi * 19.4 + 2.0);
            float s3 = hash11(fi * 4.71 + 9.0);
            float orbit = (s1 + time * spinDir * (0.10 + s2 * 0.22)
                         * (0.4 + dynamics * 0.9)) * TAU;
            float orad = mix(0.30, 1.05, s2) * r;
            vec2 centre = vec2(cos(orbit), sin(orbit)) * max(orad, 0.02);
            vec2 radial = centre / max(orad, 0.02);
            vec2 q = p - centre;
            vec2 local = vec2(dot(q, vec2(-radial.y, radial.x)) / (1.0 + s3 * 3.5 * dynamics),
                              dot(q, radial));
            float size = mix(0.006, 0.014, s3) * max(lw, 0.6);
            float twinkle = 0.30 + 0.70 * (0.5 + 0.5 * sin(time * 5.5 + fi * 2.39));
            sparks += (1.0 - smoothstep(size, size * 2.8, length(local))) * twinkle;
        }
        sparks = min(sparks, 1.8);
    }

    // ── Starfield: twinkling points spread across the disc ────────
    float stars = 0.0;
    if (hasFeature(8) && starDensity > 0) {
        float gridScale = 9.0 + float(starDensity) * 5.0;
        vec2 grid = p * gridScale;
        vec2 cell = floor(grid);
        vec2 local = fract(grid) - 0.5;
        vec2 jitter = vec2(hash21(cell), hash21(cell + 19.3)) - 0.5;
        float rv = hash21(cell + 7.7);
        float presence = step(0.72 - float(starDensity) * 0.10, rv);
        float region = smoothstep(0.10, 0.42, d) * (1.0 - smoothstep(0.92, 1.05, d));
        float twinkle = 0.30 + 0.70 * (0.5 + 0.5 * sin(time * (2.0 + rv * 4.0) + rv * 20.0));
        float size = mix(0.012, 0.030, rv) * max(lw, 0.7);
        stars = (1.0 - smoothstep(size, size * 2.4, length(local - jitter * 0.55)))
              * presence * region * twinkle;
    }

    // A low inner sheen keeps the array from reading as free-standing wirework,
    // and it rides the sweep heads so the disc has somewhere to look alive.
    float sheen = smoothstep(r, r * 0.22, d) * (0.030 + 0.095 * comet)
                * (0.45 + 0.55 * noiseField);

    // Level of detail: once one screen pixel spans more of the array than a stroke
    // does, the fine figures only stack up into fog, so they yield to the crest.
    float detail = 1.0 - smoothstep(w0 * 0.85, w0 * 2.4, px);

    // Split the stack into a palette-weighted half and an accent-weighted half so
    // the three user colours keep their places instead of averaging into beige.
    float cool = crest * 0.72 + echoes * 0.85 + sats + flares + pressure * 0.55
               + lattice * 0.75 + sheen * 1.2 + aura;
    float hot = crest * 0.28 + runes * 1.25 + crown * 1.6 + core * 2.2 + rays
              + (sparks * 1.15 + stars * 1.1) * detail;

    float energy;
    if (accentPass) {
        // The accent pass carries only its own crest, flares, runes, figurehead
        // and burst. Stacking a second copy of every ring merges the two layers
        // into one pale blur.
        energy = (crest + flares + pressure * 0.6 + runes * detail + crown + rays + core) * 0.55;
        cool *= 0.45;
        hot *= 0.55;
    } else {
        energy = cool + hot;
    }
    energy *= 1.0 - smoothstep(0.92, 1.08, d);

    // Glow feeds the stroke halos above; here it adds a modest master gain that
    // bottoms out well above zero, so turning it down softens the array instead
    // of switching it off.
    float intensity = energy * (0.70 + glow * 0.45);
    if (intensity < 0.004) discard;

    float alpha = clamp(intensity * opacity, 0.0, 1.0);

    vec3 primary;
    vec3 secondary;
    vec3 accent;
    stylePalette(styleId, primary, secondary, accent);
    if (colorMode == 1) {
        primary = HaloPrimary.rgb;
        secondary = HaloSecondary.rgb;
        accent = HaloAccent.rgb;
    } else if (colorMode == 2) {
        float phase = ua + time * HaloMotion.w * 0.11;
        primary = spectralColor(phase);
        secondary = spectralColor(phase + 0.18);
        accent = spectralColor(phase + 0.42);
    }
    // Warm one side of the ring, cool the other, and let the split turn with the
    // array so the palette reads as a lit object rather than a flat tint.
    float ramp = clamp(0.5 + 0.5 * sin(ang - time * rotation * 0.30), 0.0, 1.0);
    ramp = ramp * 0.72 + clamp(d / max(r * 1.8, 1e-3), 0.0, 1.0) * 0.28;
    vec3 base = mix(primary, secondary, ramp);
    float accentMix = clamp(hot / max(hot + cool, 1e-4), 0.0, 1.0);
    base = mix(base, accent, accentMix * 0.80);

    // Hue ramp: the palette, its own brighter tint, then white — but white only
    // where several elements genuinely stack up, so a lone stroke stays coloured.
    float heat = clamp(intensity * 0.30 - 0.18, 0.0, 1.0);
    vec3 col = mix(base, mix(base, vec3(1.0), 0.80), heat * heat);
    col *= 0.72 + intensity * brightness * 0.55;

    // Stacked strokes read pale, so lift saturation before rolling the peak off:
    // the hue then survives at full value.
    float grey = dot(col, vec3(0.299, 0.587, 0.114));
    col = max(vec3(0.0), mix(vec3(grey), col, 1.28));

    // Roll the peak off rather than clipping channels: the ratio between them,
    // which is the hue, survives being driven past white.
    float peak = max(max(col.r, col.g), col.b);
    col *= (1.0 - exp(-peak * 1.35)) / max(peak, 1e-4);

    fragColor = vec4(col, alpha) * ColorModulator;
    if (fragColor.a < 0.001) discard;
}

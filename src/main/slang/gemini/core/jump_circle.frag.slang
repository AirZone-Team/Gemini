// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  Jump / landing impact decal (跳跃光环)
//
//  A shock front that races outward across the ground while the array it drags
//  with it discharges:
//    · a crest line with a hairline inner edge, dashed per style
//    · echo layers trailing behind the crest (Layers)
//    · counter-rotating dashed satellite rings (Ring Count)
//    · a rune band that ignites cell by cell behind the travelling head (Rune Detail)
//    · tapered energy flares outside the crest (Spikes)
//    · shock rings that break away and run ahead of the front (Shockwave)
//    · orbiting sparks stretched into tangential streaks (Particles)
//    · an impact burst: white core plus radial rays, only in the first beat
//
//  Two comets ride the crest in opposite directions and every element brightens
//  as one passes over it, which is what makes the ring look like it is being
//  driven round rather than simply fading.
//
//  Clarity comes from the same two rules throughout:
//    1. every stroke is an analytic line whose width widens to one screen pixel
//       (px, from fwidth), so the foreshortened far side of a tilted decal stops
//       turning into mush;
//    2. colour is tone-mapped on its PEAK, not per channel, so an over-driven
//       stroke rolls off towards white without the whole disc losing its hue —
//       the old per-channel clamp is what washed the palette out.
//
//  vertexColor.rgb = palette colour, vertexColor.a = life progress 0→1
//  styleWords/materialWords = flat 32-bit configuration, see JumpCircle.java
// ═══════════════════════════════════════════════════════════════════

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

in vec4 vertexColor;
in vec2 uvCoord;
flat in ivec2 styleWords;
flat in ivec2 materialWords;
out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

uint unpackWords(ivec2 words) {
    uint lowWord = uint(words.x & 65535);
    uint highWord = uint(words.y & 65535);
    return lowWord | (highWord << 16u);
}

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

float easeMotion(float t, int mode) {
    if (mode == 0) return t;
    if (mode == 2) {
        if (t <= 0.0 || t >= 1.0) return t;
        return clamp(pow(2.0, -10.0 * t) * sin((t * 10.0 - 0.75) * TAU / 3.0) + 1.0, 0.0, 1.0);
    }
    float outCubic = 1.0 - pow(1.0 - t, 3.0);
    if (mode == 3) {
        outCubic += sin(t * PI * 3.0) * (1.0 - t) * 0.08;
    }
    return clamp(outCubic, 0.0, 1.0);
}

vec3 flowColor(vec3 base, int mode, float ang, float distanceValue, float life) {
    if (mode == 1) {
        // A half-steps rotation, not a full one: swapping channels outright
        // pushed a red palette through green.
        vec3 shifted = mix(base, base.brg, 0.45);
        shifted = mix(shifted, vec3(1.0), 0.18);
        return mix(base, shifted, 0.25 + 0.35 * sin(ang * 2.0 + life * 4.0));
    }
    if (mode == 2) {
        float hue = ang / TAU + life * 0.24 + distanceValue * 0.12;
        return 0.58 + 0.42 * cos(TAU * (hue + vec3(0.00, 0.33, 0.67)));
    }
    if (mode == 3) {
        float pulse = 0.5 + 0.5 * sin(life * 19.0 - distanceValue * 14.0);
        return mix(base * 0.80, mix(base, vec3(1.0), 0.45), pulse);
    }
    return base;
}

[shader("fragment")]

void main() {
    uint styleBits = unpackWords(styleWords);
    uint materialBits = unpackWords(materialWords);

    vec2 p = uvCoord * 2.0 - 1.0;
    float d = length(p);
    float ang = atan(p.y, p.x);

    // One screen pixel measured in UV units. Taken before any discard so the
    // derivatives are always evaluated in uniform control flow.
    float px = max(length(vec2(fwidth(p.x), fwidth(p.y))), 1e-6);
    if (d > 1.04) discard;

    int style = int(styleBits & 7u);
    int colorMode = int((styleBits >> 3u) & 3u);
    int layerCount = int((styleBits >> 5u) & 3u) + 1;
    int ringCount = int((styleBits >> 7u) & 3u) + 1;
    int quality = int((styleBits >> 9u) & 3u);
    int runeDetail = int((styleBits >> 11u) & 3u);
    int particleDensity = int((styleBits >> 13u) & 3u);
    bool spikesEnabled = ((styleBits >> 15u) & 1u) != 0u;
    int eventType = int((styleBits >> 16u) & 3u);
    bool accentLayer = ((styleBits >> 18u) & 1u) != 0u;
    bool shockwaveEnabled = ((styleBits >> 19u) & 1u) != 0u;
    int easing = int((styleBits >> 20u) & 3u);
    float seed = float(styleBits >> 22u) * 0.0009765625;

    float thickness = mix(0.15, 2.5, float(materialBits & 15u) / 15.0);
    float glow = mix(0.0, 2.5, float((materialBits >> 4u) & 15u) / 15.0);
    float distortion = mix(0.0, 1.5, float((materialBits >> 8u) & 15u) / 15.0);
    float spin = mix(-3.0, 3.0, float((materialBits >> 12u) & 15u) / 15.0);
    float opacity = mix(0.05, 1.0, float((materialBits >> 16u) & 7u) / 7.0);
    float brightness = mix(0.25, 2.5, float((materialBits >> 19u) & 7u) / 7.0);
    float clarity = mix(0.40, 2.20, float((materialBits >> 22u) & 15u) / 15.0);
    float dynamics = mix(0.0, 2.0, float((materialBits >> 26u) & 15u) / 15.0);

    // Clarity narrows the strokes, tightens the halo and lets a line thin down
    // closer to a pixel before it is widened back out.
    float lw = 1.0 / clarity;
    float haloGain = clamp(0.035 + glow * 0.085, 0.02, 0.30) * (1.45 - clarity * 0.22);
    float haloSq = mix(1.0 / 11.0, 1.0 / 34.0, clamp((clarity - 0.4) / 1.8, 0.0, 1.0));
    float aaFloor = px * mix(1.7, 0.75, clamp((clarity - 0.4) / 1.8, 0.0, 1.0));

    float life = clamp(vertexColor.a, 0.0, 1.0);
    float motion = easeMotion(life, easing);
    float flash = 1.0 - smoothstep(0.0, 0.12, life);
    float envelope = 1.0 - smoothstep(0.52, 1.0, life);
    float power = eventType == 2 ? 1.45 : (eventType == 0 ? 0.85 : 1.0);

    float front = mix(0.14, 0.80, motion) * (accentLayer ? 1.035 : 1.0);
    float w0 = (0.0065 + thickness * 0.0155) * (accentLayer ? 0.55 : 1.0) * lw;

    // Spiral twist: the array shears against its own radius as it discharges.
    float twist = clamp(spin, -1.0, 1.0) * (0.010 + dynamics * 0.028) * (1.0 - d) * sin(life * PI);
    float ua = fract(ang / TAU + 0.5 + twist);
    float au = px / max(d, 0.06) / TAU;

    float noiseField = fbm(p * mix(3.0, 7.0, distortion / 1.5)
                           + vec2(seed * 11.0) + life * spin * 1.4);
    float dw = d + (noiseField - 0.5) * distortion * 0.045 * (0.35 + life);

    // ── Travelling heads ──────────────────────────────────────────
    // A floor on the speed keeps the sweep alive when Spin is parked at zero.
    float sweep = (spin * 0.55 + 0.35) * (0.30 + dynamics * 0.80);
    float headA = fract(seed + life * sweep);
    float headB = fract(seed * 3.7 + 0.5 - life * sweep * 0.63);
    float tailK = mix(12.0, 4.5, clamp(dynamics * 0.5, 0.0, 1.0));
    float comet = max(exp(-fract(headA - ua) * tailK),
                      exp(-fract(ua - headB) * tailK) * 0.78);

    // ── Style gate: how the crest is broken up ────────────────────
    float cellScale = accentLayer ? 1.37 : 1.0;
    float gate = 1.0;
    if (style == 0) {
        float cells = 12.0 * cellScale;
        float t = fract(ua * cells);
        gate = 0.32 + 0.68 * window(t, 0.07, 0.93, au * cells * 0.5);
    } else if (style == 1) {
        float cells = 28.0 * cellScale;
        float idx = floor(ua * cells);
        float t = fract(ua * cells);
        float hi = 0.42 + hash11(idx + seed * 71.0) * 0.45;
        gate = 0.16 + 0.84 * window(t, 0.06, hi, au * cells * 0.5);
    } else if (style == 2) {
        gate = 0.52 + 0.48 * (0.5 + 0.5 * cos(ua * TAU * 5.0 - life * spin * 3.0));
    } else if (style == 3) {
        gate = 0.40 + 0.85 * noiseField;
    } else {
        float cells = 22.0 * cellScale;
        float idx = floor(ua * cells);
        float t = fract(ua * cells);
        float on = step(0.34, hash11(idx + floor(life * 6.0) * 0.17 + seed * 41.0));
        gate = 0.10 + 0.90 * on * window(t, 0.04, 0.96, au * cells * 0.5);
    }

    // ── Crest: main line plus a hairline riding just inside it ────
    float crest = neon(dw - front, w0, aaFloor, haloGain, haloSq) * (0.42 + 1.30 * comet) * gate;
    crest += neon(dw - (front - w0 * 1.6), w0 * 0.20, aaFloor, haloGain, haloSq)
           * (0.28 + 0.72 * comet) * gate;

    // ── Echo layers: a tight motion trail hugging the leading edge ─
    float echoes = 0.0;
    for (int layer = 1; layer < 4; layer++) {
        if (layer >= layerCount) break;
        float lf = float(layer);
        float er = front - lf * (0.020 + 0.006 * lf);
        float ew = w0 * (1.0 - lf * 0.22);
        float ea = 1.0 - lf / (float(layerCount) + 0.55);
        echoes += neon(dw - er, ew, aaFloor, haloGain, haloSq) * ea * (0.30 + 0.80 * comet) * gate;
    }

    // ── Satellite rings: concentric, each running its own way ─────
    float sats = 0.0;
    for (int ring = 1; ring < 4; ring++) {
        if (ring >= ringCount) break;
        float rf = float(ring);
        float sr = front * mix(0.62, 0.26, (rf - 1.0) / 3.0);
        float cells = 8.0 + rf * 5.0;
        float dir = step(0.5, fract(rf * 0.5)) * 2.0 - 1.0;
        float t = fract(ua * cells + rf * 0.17 + life * spin * (0.22 + rf * 0.10) * dir);
        float dash = window(t, 0.10, 0.60, au * cells * 0.40);
        sats += neon(dw - sr, w0 * 0.26, aaFloor, haloGain, haloSq) * dash * (0.55 - rf * 0.09);
    }

    // ── Rune band: cells ignite behind whichever head just passed ─
    float runes = 0.0;
    if (runeDetail > 0) {
        float cells = 10.0 + float(runeDetail) * 6.0;
        float idx = floor(ua * cells);
        float t = fract(ua * cells);
        float h = hash11(idx + seed * 97.0);
        float feather = max(au * cells * 0.45, 1e-4);
        float rr = front * 0.815;
        float rh = 0.012 + w0 * 0.50;
        float edge = max(px * 0.9, aaFloor * 0.35);

        float body = window(t, 0.16, 0.30 + h * 0.32, feather)
                   * band(dw, rr - rh, rr + rh, edge);
        float bar = window(t, 0.44, 0.62, feather)
                  * band(dw, rr - rh * 1.8, rr - rh * 0.7, edge) * step(0.48, h);
        float pip = window(t, 0.66, 0.78, feather)
                  * band(dw, rr + rh * 1.4, rr + rh * 2.1, edge) * step(0.66, h);

        float centre = (idx + 0.5) / cells;
        float ignite = clamp(exp(-fract(headA - centre) * 13.0)
                           + exp(-fract(centre - headB) * 13.0) * 0.6, 0.0, 1.4);
        runes = (body + bar * 0.85 + pip) * (0.18 + 0.95 * ignite) * (accentLayer ? 1.15 : 0.80);
    }

    // ── Energy flares: tapered wedges outside the crest ───────────
    float spikes = 0.0;
    if (spikesEnabled) {
        float cells = (style == 1 ? 24.0 : (style == 3 ? 30.0 : 16.0)) * cellScale;
        float idx = floor(ua * cells);
        float t = fract(ua * cells);
        float h = hash11(idx + seed * 53.0 + floor(life * 11.0) * 0.31);
        float base = front + w0 * 0.35;
        float len = (0.034 + 0.095 * flash) * (0.35 + h * 0.95)
                  * (0.70 + dynamics * 0.55) * (accentLayer ? 0.62 : 1.0);
        float k = clamp((dw - base) / max(len, 1e-4), 0.0, 1.0);
        float hw = 0.34 * (1.0 - k);
        spikes = window(t, 0.5 - hw, 0.5 + hw, au * cells * 0.5)
               * band(dw, base, base + len, max(px * 0.9, aaFloor * 0.35))
               * (1.0 - k * 0.7) * (0.30 + 0.90 * flash + 0.45 * h) * (0.45 + 0.95 * comet);
    }

    // ── Pressure rings riding just ahead of the front ─────────────
    float shock = 0.0;
    if (shockwaveEnabled) {
        for (int wave = 0; wave < 2; wave++) {
            float wf = float(wave);
            float lead = (0.040 + wf * 0.048) * (0.35 + 0.65 * flash + dynamics * 0.22);
            float sr = front + lead;
            float live = (1.0 - smoothstep(0.28, 0.80, life)) * step(0.001, motion);
            shock += neon(dw - sr, (0.0032 + flash * 0.011) * lw, aaFloor, haloGain, haloSq)
                   * live * (1.0 - wf * 0.42);
        }
    }

    // ── Sparks: orbiting points stretched along their own motion ──
    float sparks = 0.0;
    int sparkLimit = min(12, 3 + particleDensity * 3 + quality);
    float spinDir = spin >= 0.0 ? 1.0 : -1.0;
    for (int i = 0; i < 12; i++) {
        if (i >= sparkLimit) break;
        float fi = float(i);
        float s1 = hash11(fi * 7.13 + seed * 131.0);
        float s2 = hash11(fi * 19.4 + 2.0 + seed * 77.0);
        float s3 = hash11(fi * 4.71 + seed * 33.0);
        float orbit = (s1 + life * spinDir * (0.25 + s2 * 0.55) * (0.4 + dynamics * 0.8)) * TAU;
        float orad = mix(0.16, 0.92, s2) * front + life * 0.10;
        vec2 centre = vec2(cos(orbit), sin(orbit)) * max(orad, 0.02);
        vec2 radial = centre / max(orad, 0.02);
        vec2 q = p - centre;
        vec2 local = vec2(dot(q, vec2(-radial.y, radial.x)) / (1.0 + s3 * 3.0 * dynamics),
                          dot(q, radial));
        float size = mix(0.008, 0.020, s3) * (1.15 - life * 0.35) * max(lw, 0.6);
        float twinkle = 0.35 + 0.65 * (0.5 + 0.5 * sin(life * 26.0 + fi * 2.39));
        sparks += (1.0 - smoothstep(size, size * 2.6, length(local))) * twinkle * 0.85;
    }

    // ── Inscribed polygon: the array's rotating skeleton ──────────
    // Two counter-running figures, so the lattice keeps turning even when the
    // rings around it are nearly still.
    float lattice = 0.0;
    if (runeDetail > 1) {
        float sides = style == 1 ? 6.0 : (style == 3 ? 5.0 : (style == 4 ? 4.0 : 3.0));
        float seg = TAU / sides;
        for (int fig = 0; fig < 2; fig++) {
            float ff = float(fig);
            float la = ang + life * spin * (0.55 + ff * 0.85) * (ff > 0.5 ? -1.0 : 1.0)
                     + ff * (PI / sides);
            float lr = front * (0.50 - ff * 0.07);
            float poly = cos(seg * 0.5) / cos(fract(la / seg) * seg - seg * 0.5);
            lattice += neon(d - lr * poly, w0 * 0.36, aaFloor, haloGain, haloSq)
                     * (0.52 + 0.70 * comet) * (1.0 - ff * 0.28);
        }
    }

    // ── Impact burst: the first beat only ─────────────────────────
    float core = exp(-d * d / (0.012 + 0.075 * (1.0 - flash)))
               * flash * power * (eventType == 0 ? 0.45 : 1.0);
    float rays = 0.0;
    if (flash > 0.0) {
        float cells = 18.0;
        float idx = floor(ua * cells);
        float t = fract(ua * cells);
        float h = hash11(idx + seed * 23.0);
        float rayLen = (0.20 + h * 0.62) * front * flash * power;
        rays = window(t, 0.36, 0.64, au * cells * 0.35)
             * (1.0 - smoothstep(rayLen * 0.22, max(rayLen, 1e-4), d))
             * flash * power * 0.85;
    }

    // A low inner sheen keeps the array from reading as free-standing wirework,
    // and it rides the sweep heads so the disc has somewhere to look alive.
    float sheen = smoothstep(front, front * 0.28, d) * (0.040 + 0.085 * comet)
                * (0.45 + 0.55 * noiseField);

    // Level of detail: once one screen pixel spans more of the array than a stroke
    // does, the fine figures only stack up into fog, so they yield to the crest.
    float detail = 1.0 - smoothstep(w0 * 0.85, w0 * 2.4, px);

    float intensity;
    if (accentLayer) {
        // The accent pass carries only its own crest, flares and runes. Stacking
        // a second copy of every ring is what merged the two layers into one
        // pale blur.
        intensity = (crest + runes * detail + spikes + shock + rays + core) * envelope * 0.60;
    } else {
        intensity = (crest + shock + spikes + rays + core
                   + (echoes + sats + lattice + runes + sparks * 0.9 + sheen) * detail) * envelope;
    }

    if (intensity < 0.004) discard;

    float alpha = clamp(intensity * opacity, 0.0, 1.0);

    // Hue ramp: the palette, its own brighter tint, then white — but white only
    // where several elements genuinely stack up, so a lone stroke stays coloured.
    vec3 base = flowColor(vertexColor.rgb, colorMode, ang, d, life);
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

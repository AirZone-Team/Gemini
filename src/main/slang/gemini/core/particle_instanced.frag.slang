// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  Instanced particle sigils (实例粒子)
//
//  Five shapes, each drawn in the jump-circle line language instead of as a flat
//  filled tile:
//    0 RUNE      — diamond frame, hash-picked glyph strokes, tip pips
//    1 HEXAGON   — dashed hexagonal frame, counter-running hairline, corner pips
//    2 TRIANGLE  — shard outline with an inner echo and a hot apex
//    3 FEATHER   — lens-blade outline, rib and swept barbs
//    4 STARLIGHT — superellipse core with four rays that taper to nothing
//
//  Shared ornament: two comet heads ride the frame, echo layers trail it, an
//  orbit ring carries sparks, and the birth beat throws radial rays.
//
//  Clarity comes from the same two rules as jump_circle:
//    1. every stroke is an analytic line widened to one screen pixel (px, from
//       fwidth) — a sigil eight pixels across cannot carry a hairline;
//    2. colour is tone-mapped on its PEAK, not per channel, so an over-driven
//       sigil rolls off towards white while the sea around it keeps its hue.
//
//  vertexColor.rgb = palette, vertexColor.a = life progress 0→1
//  styleWords = per-particle configuration, materialWords = per-frame material.
//  Both ride flat integer varyings: a byte colour channel quantises a shape id to
//  1/255 and a neighbour's interpolation can flip it mid-frame.
// ═══════════════════════════════════════════════════════════════════

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

#define u_time ModelOffset.x

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

// Analytic stroke: a core that never goes sub-pixel plus a gaussian halo. `gain`
// trades brightness for the extra coverage a widened stroke needs, on a square
// root so a distant sigil stays hot enough to read as light.
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

float dot2(vec2 v) { return dot(v, v); }

// Distance to the segment ab, so glyph strokes and barbs can be drawn as lines.
float segDist(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot2(ba), 1e-6), 0.0, 1.0);
    return length(pa - ba * h);
}

// Regular-polygon radius at angle a, normalised so 1.0 is a vertex and
// cos(PI/sides) is an edge midpoint.
float polyRadius(float a, float sides) {
    float seg = TAU / sides;
    return cos(seg * 0.5) / cos(fract(a / seg) * seg - seg * 0.5);
}

vec3 flowColor(vec3 base, int mode, float ang, float distanceValue, float life) {
    if (mode == 1) {
        // A gentle drift towards the rotated channels, not a swap: a sigil is a
        // few pixels wide, so anything stronger reads as the wrong colour.
        vec3 shifted = mix(base, base.brg, 0.45);
        shifted = mix(shifted, vec3(1.0), 0.18);
        return mix(base, shifted, 0.10 + 0.16 * sin(ang * 2.0 + life * 4.0));
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

    vec2 q = uvCoord * 2.0 - 1.0;
    float d = length(q);
    float ang = atan(q.y, q.x);

    // One screen pixel measured in sigil units, taken before any discard so the
    // derivatives are always evaluated in uniform control flow.
    float px = max(length(vec2(fwidth(q.x), fwidth(q.y))), 1e-6);
    if (d > 1.02) discard;

    int shape      = int(styleBits & 7u);
    int colorMode  = int((styleBits >> 3u) & 3u);
    int ornament   = int((styleBits >> 5u) & 7u);
    int runeDetail = int((styleBits >> 8u) & 3u);
    int variant    = int((styleBits >> 10u) & 7u);
    int echoCount  = int((styleBits >> 13u) & 3u) + 1;
    bool burst     = ((styleBits >> 15u) & 1u) != 0u;
    float seed     = float((styleBits >> 16u) & 1023u) * 0.0009765625;

    float thickness  = mix(0.15, 2.5, float(materialBits & 15u) / 15.0);
    float glow       = mix(0.0, 2.5, float((materialBits >> 4u) & 15u) / 15.0);
    float clarity    = mix(0.40, 2.2, float((materialBits >> 8u) & 15u) / 15.0);
    float brightness = mix(0.25, 2.5, float((materialBits >> 12u) & 15u) / 15.0);
    float opacity    = mix(0.05, 1.0, float((materialBits >> 16u) & 7u) / 7.0);
    float dynamics   = mix(0.0, 2.0, float((materialBits >> 19u) & 15u) / 15.0);
    float accentMix  = float((materialBits >> 23u) & 7u) / 7.0;
    int quality      = int((materialBits >> 26u) & 3u);
    bool orbitOn     = ((materialBits >> 28u) & 1u) != 0u;

    // Clarity narrows the strokes, tightens the halo and lets a line thin down
    // closer to a pixel before it is widened back out.
    float lw = 1.0 / clarity;
    float haloGain = clamp(0.035 + glow * 0.085, 0.02, 0.30) * (1.45 - clarity * 0.22);
    float haloSq = mix(1.0 / 11.0, 1.0 / 34.0, clamp((clarity - 0.4) / 1.8, 0.0, 1.0));
    float aaFloor = px * mix(1.7, 0.75, clamp((clarity - 0.4) / 1.8, 0.0, 1.0));

    float life = clamp(vertexColor.a, 0.0, 1.0);
    float birth = smoothstep(0.0, 0.08, life);
    float envelope = birth * (1.0 - smoothstep(0.48, 1.0, life));
    float flash = 1.0 - smoothstep(0.0, 0.18, life);
    float breathe = 0.88 + 0.12 * sin(u_time * 3.4 + seed * TAU + life * 11.0);

    float w0 = (0.014 + thickness * 0.024) * lw;
    float frameR = 0.66;

    // Level of detail: once one screen pixel spans more of the sigil than a
    // stroke does, the fine figures only stack up into fog. This collapses early
    // on purpose — hundreds of these overlap, and ornament that survives at four
    // pixels per sigil turns the whole sea into noise.
    float detailGate = 1.0 - smoothstep(w0 * 0.55, w0 * 1.45, px);
    // The orbit ring is the widest and busiest figure, so it needs the frame to
    // be several times larger than a pixel before it can read at all.
    float ringGate = 1.0 - smoothstep(w0 * 1.6, w0 * 4.5, px);

    // ── Perimeter coordinate and the two comet heads ──────────────
    float ua = fract(ang / TAU + 0.5);
    float au = px / max(d, 0.06) / TAU;
    float dir = fract(seed * 7.31) > 0.5 ? 1.0 : -1.0;
    float sweep = (0.30 + dynamics * 0.85) * dir;
    float headA = fract(seed + life * sweep + u_time * 0.035);
    float headB = fract(seed * 3.7 + 0.5 - life * sweep * 0.63);
    float tailK = mix(13.0, 5.0, clamp(dynamics * 0.5, 0.0, 1.0));
    float comet = max(exp(-fract(headA - ua) * tailK),
                      exp(-fract(ua - headB) * tailK) * 0.78);

    // ── Ornament gate: how the frame is broken up ─────────────────
    float gate = 1.0;
    if (ornament == 1) {
        float cells = 12.0;
        float t = fract(ua * cells);
        gate = 0.52 + 0.48 * window(t, 0.07, 0.93, au * cells * 0.5);
    } else if (ornament == 2) {
        float cells = 24.0;
        float idx = floor(ua * cells);
        float t = fract(ua * cells);
        float hi = 0.42 + hash11(idx + seed * 71.0) * 0.45;
        gate = 0.34 + 0.66 * window(t, 0.06, hi, au * cells * 0.5);
    } else if (ornament == 3) {
        gate = 0.62 + 0.38 * (0.5 + 0.5 * cos(ua * TAU * 5.0 + life * 4.0 * dir));
    } else if (ornament == 4) {
        float cells = 20.0;
        float idx = floor(ua * cells);
        float t = fract(ua * cells);
        float on = step(0.34, hash11(idx + floor(life * 6.0) * 0.17 + seed * 41.0));
        gate = 0.28 + 0.72 * on * window(t, 0.04, 0.96, au * cells * 0.5);
    }
    // A collapsing dash pattern reads as noise, so the frame runs solid when the
    // cells no longer fit.
    gate = mix(1.0, gate, detailGate);

    // ── The five frames ───────────────────────────────────────────
    // frameSdf is zero on the outline; the echo layers reuse the same field so
    // they hug the shape instead of drifting off as a circle.
    float frameSdf;
    float figure = 0.0;
    float pips = 0.0;
    float core = 0.0;
    float spinPhase = life * (0.45 + dynamics * 0.9) * dir + u_time * 0.06 * dir;

    if (shape == 1) {
        // HEXAGON: dashed frame, a hairline running the other way, pips on the
        // six vertices.
        float pr = polyRadius(ang + spinPhase, 6.0);
        float pr2 = polyRadius(ang - spinPhase * 1.7 + PI / 6.0, 6.0);
        frameSdf = d - frameR * pr;
        figure = neon(d - frameR * pr2 * 0.70, w0 * 0.26, aaFloor, haloGain, haloSq)
               * (0.45 + 0.85 * comet);

        float seg = TAU / 6.0;
        for (int i = 0; i < 6; i++) {
            vec2 v = vec2(cos(-spinPhase + float(i) * seg), sin(-spinPhase + float(i) * seg)) * frameR;
            pips += (1.0 - smoothstep(0.022, 0.062, length(q - v))) * (0.45 + 1.05 * comet);
        }
        core = exp(-d * d / 0.060) * 0.22;
    } else if (shape == 2) {
        // TRIANGLE: shard outline, an inner echo running against it, and a hot
        // apex so the shape has a front.
        float pr = polyRadius(ang + spinPhase, 3.0);
        float pr2 = polyRadius(ang - spinPhase * 1.4 + PI / 3.0, 3.0);
        frameSdf = d - frameR * pr;
        figure = neon(d - frameR * pr2 * 0.58, w0 * 0.30, aaFloor, haloGain, haloSq)
               * (0.40 + 0.80 * comet);

        vec2 apex = vec2(cos(-spinPhase), sin(-spinPhase)) * frameR;
        pips += (1.0 - smoothstep(0.03, 0.10, length(q - apex))) * (0.7 + 1.5 * comet);
        core = exp(-d * d / 0.050) * 0.22;
    } else if (shape == 3) {
        // FEATHER: a lens blade. Two discs intersect along the y axis, so the
        // outline is two arcs meeting at points rather than a blurred ellipse.
        float rr = 0.98;
        float off = 0.66;
        frameSdf = max(length(q - vec2(off, 0.0)) - rr, length(q + vec2(off, 0.0)) - rr);

        float along = clamp(abs(q.y) / 0.72, 0.0, 1.0);
        figure = neon(abs(q.x), w0 * 0.24, aaFloor, haloGain, haloSq)
               * (1.0 - along * along) * (0.5 + 0.9 * comet);
        if (detailGate > 0.01) {
            for (int i = 0; i < 3; i++) {
                float t = (float(i) + 0.5) / 3.0;
                float yy = mix(-0.40, 0.46, t);
                // The blade's own half-width at this height, so the barbs end
                // just inside the outline instead of stabbing through it.
                float halfWidth = (sqrt(max(rr * rr - yy * yy, 0.0)) - off) * 0.88;
                for (int s = 0; s < 2; s++) {
                    float side = float(s) * 2.0 - 1.0;
                    figure += neon(segDist(q, vec2(0.0, yy), vec2(side * halfWidth, yy + 0.09)),
                                   w0 * 0.17, aaFloor, haloGain, haloSq) * 0.8 * detailGate;
                }
            }
        }
        core = exp(-dot2(q * vec2(2.6, 1.0)) / 0.5) * 0.22;
    } else if (shape == 4) {
        // STARLIGHT: a superellipse core plus four rays that taper to nothing.
        // The old bars were unbounded, which is what threw arms across the quad.
        // pow is undefined at zero, and the axes are exactly where it is zero.
        vec2 aq = max(abs(q), vec2(1e-4));
        float star = pow(aq.x, 0.62) + pow(aq.y, 0.62);
        frameSdf = star - 0.30;

        float armLen = 0.94;
        float rayH = neon(q.y, max(w0 * 0.42 * (1.0 - aq.x / armLen), px * 0.35),
                          aaFloor, haloGain, haloSq)
                   * (1.0 - smoothstep(0.22, armLen, aq.x));
        float rayV = neon(q.x, max(w0 * 0.42 * (1.0 - aq.y / armLen), px * 0.35),
                          aaFloor, haloGain, haloSq)
                   * (1.0 - smoothstep(0.22, armLen, aq.y));
        figure = (rayH + rayV) * (0.55 + 1.10 * comet);

        float seg = TAU / 4.0;
        for (int i = 0; i < 4; i++) {
            float a2 = -spinPhase + float(i) * seg + seg * 0.5;
            vec2 v = vec2(cos(a2), sin(a2)) * 0.40;
            pips += (1.0 - smoothstep(0.020, 0.052, length(q - v))) * (0.4 + 0.9 * comet);
        }
        core = exp(-d * d / 0.014) * 0.95;
    } else {
        // RUNE: a diamond cell with a glyph inside it. Every stroke is masked to
        // the cell — the previous version let the stem and the bar run to the
        // quad edge, which is what drew the little square grids.
        float dia = abs(q.x) + abs(q.y);
        frameSdf = dia - frameR;
        float inside = 1.0 - smoothstep(0.50, 0.62, dia);

        float h1 = hash11(float(variant) * 7.13 + seed * 131.0);
        float h2 = hash11(float(variant) * 19.4 + 2.0 + seed * 77.0);
        float h3 = hash11(float(variant) * 4.71 + seed * 33.0);

        float glyph = neon(segDist(q, vec2(0.0, -0.36), vec2(0.0, 0.36)),
                           w0 * 0.30, aaFloor, haloGain, haloSq);
        vec2 armA = vec2(mix(-0.30, -0.14, h1), mix(-0.12, 0.04, h2));
        vec2 armB = vec2(mix(0.30, 0.14, h1), mix(0.20, 0.36, h3));
        glyph += neon(segDist(q, armA, armB), w0 * 0.26, aaFloor, haloGain, haloSq)
               * (0.55 + 1.0 * comet);
        glyph += neon(segDist(q, vec2(-armA.x, armA.y * 0.55), vec2(-armB.x, armB.y * 0.45)),
                      w0 * 0.26, aaFloor, haloGain, haloSq) * step(0.35, h2);
        if (runeDetail > 1) {
            glyph += 1.0 - smoothstep(0.028, 0.062, length(q - vec2(0.0, mix(0.16, 0.30, h3))));
        }
        figure = glyph * inside;

        // The diamond's four tips sit on the axes, not on a rotated circle.
        for (int i = 0; i < 4; i++) {
            vec2 v = vec2(cos(float(i) * PI * 0.5), sin(float(i) * PI * 0.5)) * frameR;
            pips += (1.0 - smoothstep(0.022, 0.058, length(q - v))) * (0.45 + 1.0 * comet);
        }
        core = exp(-d * d / 0.055) * 0.22;
    }

    float frame = neon(frameSdf, w0, aaFloor, haloGain, haloSq)
                * gate * (0.42 + 1.30 * comet);

    // ── Echo layers: copies of the frame falling back inside it ────
    float echoes = 0.0;
    for (int layer = 1; layer < 4; layer++) {
        if (layer >= echoCount) break;
        float lf = float(layer);
        echoes += neon(frameSdf + lf * (0.030 + 0.012 * lf),
                       w0 * (1.0 - lf * 0.20), aaFloor, haloGain, haloSq)
                * (1.0 - lf / (float(echoCount) + 0.5)) * (0.22 + 0.55 * comet) * gate;
    }

    // ── Orbit ring and its satellites ────────────────────────────
    float orbit = 0.0;
    float sparks = 0.0;
    if (orbitOn) {
        float orbitR = 0.86;
        float cells = 16.0;
        float t = fract(ua * cells - spinPhase / TAU);
        float dash = window(t, 0.10, 0.58, au * cells * 0.40);
        orbit = neon(d - orbitR, w0 * 0.22, aaFloor, haloGain, haloSq)
              * dash * 0.30 * ringGate;

        int satLimit = min(3, 1 + quality);
        for (int i = 0; i < 3; i++) {
            if (i >= satLimit) break;
            float fi = float(i);
            float s1 = hash11(fi * 13.7 + seed * 211.0);
            float oa = (s1 + life * (0.55 + s1 * 0.7) * dir) * TAU;
            vec2 centre = vec2(cos(oa), sin(oa)) * orbitR;
            vec2 radial = centre / max(orbitR, 1e-4);
            vec2 local = vec2(dot(q - centre, vec2(-radial.y, radial.x)) / (1.0 + dynamics * 1.1),
                              dot(q - centre, radial));
            float size = mix(0.020, 0.042, s1) * (1.15 - life * 0.35) * max(lw, 0.6);
            float twinkle = 0.35 + 0.65 * (0.5 + 0.5 * sin(life * 26.0 + fi * 2.39));
            sparks += (1.0 - smoothstep(size, size * 2.6, length(local))) * twinkle;
        }
        sparks *= 0.6 * mix(1.0, ringGate, 0.7);
    }

    // ── Birth beat: white core plus radial rays ────────────────────
    float rays = 0.0;
    float popCore = 0.0;
    if (burst && flash > 0.0) {
        float cells = 14.0;
        float idx = floor(ua * cells);
        float t = fract(ua * cells);
        float h = hash11(idx + seed * 23.0);
        float rayLen = (0.30 + h * 0.62) * flash;
        rays = window(t, 0.38, 0.62, au * cells * 0.35)
             * (1.0 - smoothstep(0.10, max(rayLen, 1e-4), d)) * flash * 0.9;
        popCore = exp(-d * d / 0.014) * flash * 0.8;
    }

    // A low inner sheen keeps the sigil from reading as free-standing wirework.
    float sheen = (1.0 - smoothstep(0.0, frameR, d)) * (0.045 + 0.085 * comet) * breathe;

    // Once the frame collapses below a pixel, hand its light to a soft mote so a
    // distant sigil still reads as a glowing point instead of a faint grey ring.
    float mote = (1.0 - detailGate) * exp(-d * d / 0.10) * 1.7;

    // Sparks ride in `hot`: they are points of the palette itself, and tinting
    // them with the accent turned them into grey-green leaves around gold sigils.
    float hot  = frame + echoes * 0.8 + rays + popCore + core * 1.35 + sparks + mote;
    float cool = (figure * 0.7 + pips * 0.8 + orbit + sheen * 0.8) * detailGate;
    float intensity = (hot + cool) * breathe;

    if (intensity < 0.004) discard;

    float alpha = clamp(intensity * opacity, 0.0, 1.0) * envelope;

    // Hue ramp: the palette, its own brighter tint, then white — but white only
    // where several elements genuinely stack up, so a lone stroke stays coloured.
    // The accent stays a paler copy of the same hue and only rotates channels when
    // Accent Mix is pushed up; a sea where every sigil drifts to a neighbouring
    // hue reads as mud rather than as light.
    vec3 base = flowColor(vertexColor.rgb, colorMode, ang, d, life);
    vec3 lift = mix(base, vec3(1.0), 0.34);
    vec3 accent = mix(lift, lift.brg, accentMix);
    vec3 col = base * hot + accent * cool;

    float heat = clamp(intensity * 0.26 - 0.26, 0.0, 1.0);
    col = mix(col, mix(col, vec3(1.0), 0.80), heat * heat);
    col *= 0.72 + intensity * brightness * 0.55;

    // Stacked strokes read pale, so lift saturation before rolling the peak off.
    float grey = dot(col, vec3(0.299, 0.587, 0.114));
    col = max(vec3(0.0), mix(vec3(grey), col, 1.28));

    // Roll the peak off rather than clipping channels: the ratio between them,
    // which is the hue, survives being driven past white.
    float peak = max(max(col.r, col.g), col.b);
    col *= (1.0 - exp(-peak * 1.35)) / max(peak, 1e-4);

    fragColor = vec4(col, alpha) * ColorModulator;
    if (fragColor.a < 0.001) discard;
}

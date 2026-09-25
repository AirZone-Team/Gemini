// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  Hypernova summoning array — fragment shader (极超新星 · 黑洞召唤法阵)
//
//  One disc, drawn by two pens that run round it in opposite directions.
//  KillEffectRenderer stacks copies of this disc in world space — ground
//  sigil, echo, tower tiers, armillary cage, ascending shock rings — and hands
//  each plane its own slice of the script, so a plane whose reveal stops at a
//  SCRIPT_* boundary shows only the figures below it. That is what keeps
//  eighteen overlapping copies from turning into a tangle of near-identical
//  circles: the cage is nothing but rim, the tiers are rim plus runes, and the
//  full geometry only the ground sigil and the two lowest tiers ever carry.
//
//  The script, in the order the pens lay it down:
//    1. rim      five concentric boundary rings
//    2. rune     a 64-cell hashed rune band riding over a 72-tick rail
//    3. lattice  hexagram, three rotated squares, three interlocking circles,
//                a guilloché rose ring and 24 radial spokes
//    4. seals    six satellite seals on a fluted orbit ring with arc brackets
//    5. core     twelve-petal rosette, inner rings, white-hot seed
//
//  vertexColor.r = reveal (0→1 script progress)
//  vertexColor.g = animation phase (0→1, wraps seamlessly): line boil, the
//                  travelling radial pulse and the orbit sparks. Rotation is
//                  NOT read here — every plane turns in its own geometry (a
//                  byte channel could only step 1.4° at a time). Figures may
//                  sway against that turn by a bounded amount, which is what
//                  keeps them byte-safe.
//  vertexColor.b = energy / 4 (HDR — this is what the bloom chain latches onto)
//  vertexColor.a = master alpha
//
//  UV is the disc's own plane, which the renderer orients in world space, so
//  perspective foreshortens the design like any other object in the world.
//  Every stroke is an inverse-distance neon line widened to at least one pixel:
//  a tower tier seen near edge-on drives a hairline sub-pixel, and without that
//  floor the whole array would shimmer frame to frame.
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

#define TAU 6.28318530718
#define PI  3.14159265359

// Must match KillEffectInstance.SCRIPT_*.
#define SCRIPT_RIM_END     0.16
#define SCRIPT_RUNE_END    0.34
#define SCRIPT_LATTICE_END 0.60
#define SCRIPT_SEAL_END    0.82

// Where each pen lifts off, and how wide a stroke's drawn edge is softened.
#define HEAD_OUT  1.90
#define HEAD_IN  -0.60
#define PEN_EDGE   0.055

// ── Hash / noise ──────────────────────────────────────────────────

float hash1(float n) {
    return fract(sin(n * 127.1) * 43758.5453123);
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1.0, 0.0)), f.x),
               mix(hash2(i + vec2(0.0, 1.0)), hash2(i + vec2(1.0, 1.0)), f.x), f.y);
}

// ── Neon line: crisp core plus an analytic halo ───────────────────
//
// `px` is the screen diagonal of one pixel in UV units. A stroke narrower than
// it widens to fit, and brightness is traded for the extra coverage on a square
// root — the stroke has to stay hot enough for the bloom chain to find it.

float neon(float dist, float w, float px) {
    float ww = max(w, px);
    float gain = sqrt(w / ww);
    float a = abs(dist);
    float core = smoothstep(ww, 0.0, a) * gain;
    float halo = exp(-(a * a) / (ww * ww * 22.0)) * 0.34 * gain;
    return core + halo;
}

float bandMask(float d, float lo, float hi) {
    return smoothstep(lo - 0.005, lo + 0.005, d)
         * (1.0 - smoothstep(hi - 0.005, hi + 0.005, d));
}

// A stroke running along the radius at one angle, clipped to a radial band.
// `across` is the tangential offset in UV units, so the line stays the same
// width wherever the band is.
float radialLine(float across, float d, float r0, float r1, float w, float px) {
    return neon(across, w, px) * bandMask(d, r0, r1);
}

// ── Regular polygon outline, exact and branch-free ────────────────
//
// Fold the angle into one sector and measure against that sector's edge line
// (apothem = R·cos(π/n)). Near a vertex this reads the two edge lines rather
// than the corner, which is exactly right for a neon stroke: the crossing of
// two lines *is* the pointed corner.

float ngonLine(float d, float ang, float R, float n, float rot0) {
    float an = PI / n;
    float g = mod(ang - rot0 - an, 2.0 * an) - an;
    return d * cos(g) - R * cos(an);
}

// ── Summon script ─────────────────────────────────────────────────

float slice(float reveal, float lo, float hi) {
    return clamp((reveal - lo) / (hi - lo), 0.0, 1.0);
}

float wrapTau(float x) {
    return x - floor(x / TAU) * TAU;
}

// 1 behind the pen head, 0 ahead of it, so a stroke is drawn round rather than
// appearing all at once. Hand it −ang to run the pen the other way.
float pen(float ang, float start, float p) {
    float a = wrapTau(ang - start);
    float head = p * TAU;
    // Once fully drawn the soft edge goes, so no seam is left where the pen
    // lifted off.
    return mix(smoothstep(head + PEN_EDGE, head - PEN_EDGE, a),
               1.0, step(0.999, p)) * step(0.0015, p);
}

// The hot dot riding a pen's tip as it travels round a circle of radius r.
float penTip(float ang, float d, float start, float p, float r) {
    float a = wrapTau(ang - start);
    float live = step(0.002, p) * (1.0 - step(0.998, p));
    float along = (a - p * TAU) * max(r, 0.05) * 14.0;
    float across = (d - r) * 16.0;
    return exp(-along * along - across * across) * live;
}

// ═══════════════════════════════════════════════════════════════════
//  Main
// ═══════════════════════════════════════════════════════════════════

[shader("fragment")]

void main() {
    float reveal = vertexColor.r;
    float phase  = vertexColor.g;          // 0→1, wraps seamlessly
    float energy = vertexColor.b * 4.0;
    float alpha  = vertexColor.a;

    if (alpha < 0.002) discard;   // uniform across the quad, so the derivatives
                                  // below are still taken in uniform control flow

    vec2 uv = (uvCoord - 0.5) * 2.0;
    float d = length(uv);
    float ang = atan(uv.y, uv.x);
    // Taken before the rim discard below, so the gradients are still read in
    // uniform control flow.
    float px = length(vec2(fwidth(uvCoord.x), fwidth(uvCoord.y))) * 2.0;

    if (d > 1.07) discard;        // clipping at the rim keeps a halo from
                                   // spilling past the disc

    // Bounded counter-rotation of the inner figures against the plane's own
    // turn. Only sin/cos of the wrapped phase is safe through a byte.
    float sway = 0.05 * sin(phase * TAU);
    float runeRot = sway;
    float latRot  = -sway * 1.6;
    float sealRot = sway * 0.7;
    float sunRot  = -sway * 0.4;

    // ── Script: which figure is being drawn right now ─────────────
    // The rim is four pens' worth of rings laid down one after another, so the
    // outer band closes before the runes begin.
    float pRim  = slice(reveal, 0.0, SCRIPT_RIM_END);
    float pRimA = clamp(pRim * 4.0, 0.0, 1.0);
    float pRimB = clamp(pRim * 4.0 - 1.0, 0.0, 1.0);
    float pRimC = clamp(pRim * 4.0 - 2.0, 0.0, 1.0);
    float pRimD = clamp(pRim * 4.0 - 3.0, 0.0, 1.0);
    float pRune = slice(reveal, SCRIPT_RIM_END, SCRIPT_RUNE_END);
    float pLat  = slice(reveal, SCRIPT_RUNE_END, SCRIPT_LATTICE_END);
    float pSeal = slice(reveal, SCRIPT_LATTICE_END, SCRIPT_SEAL_END);
    float pCore = slice(reveal, SCRIPT_SEAL_END, 1.0);

    float rimA = pen(ang, HEAD_OUT, pRimA);
    float rimB = pen(ang, HEAD_OUT, pRimB);
    float rimC = pen(ang, HEAD_OUT, pRimC);
    float rimD = pen(ang, HEAD_OUT, pRimD);
    float runeMask = pen(ang, HEAD_OUT, pRune);
    float latMask  = pen(-ang, HEAD_IN, pLat);
    float sealMask = pen(-ang, HEAD_IN, pSeal);
    // The core blooms outward from the centre instead of being swept, so the
    // summon's last beat reads differently from everything before it.
    float coreR = pCore * 0.42;
    float sunMask = (1.0 - smoothstep(coreR - 0.05, coreR + 0.05, d)) * step(0.0015, pCore);

    // ── Boiling: slow-domain turbulence, phase-continuous ─────────
    vec2 boilDrift = vec2(sin(phase * TAU), cos(phase * TAU)) * 0.35;
    float boil = 0.87 + 0.13 * noise(uv * 4.0 + boilDrift);

    // ── 1 · Rim: five concentric boundary rings ───────────────────
    float rings =
          neon(d - 0.996, 0.0032, px) * 1.05 * rimA
        + neon(d - 0.974, 0.0064, px) * 1.40 * rimA
        + neon(d - 0.940, 0.0024, px) * 0.72 * rimB
        + neon(d - 0.906, 0.0048, px) * 1.00 * rimC
        + neon(d - 0.872, 0.0020, px) * 0.58 * rimD;

    // ── 2 · Rune band: 64 hashed cells over a 72-tick rail ────────
    // Cells are addressed by angle, so a glyph's tangential offset is
    // `cellArc * (t - 0.5)` in UV units — the strokes keep their width out to
    // the rim instead of smearing into each other.
    float cells = 64.0;
    float cellArc = TAU * d / cells;
    float cellPos = (ang + runeRot) * cells / TAU;
    float cellIdx = floor(cellPos);
    float t = fract(cellPos);
    float u = (t - 0.5) * cellArc;
    float h1 = hash1(cellIdx);
    float h2 = hash1(cellIdx * 7.13);
    float h3 = hash1(cellIdx * 3.71);
    float h4 = hash1(cellIdx * 11.7);

    float glyphs = 0.0;
    // Full-height bar
    glyphs += step(0.20, h1) * neon(u, 0.0026, px) * bandMask(d, 0.882, 0.932);
    // Twin short bars
    glyphs += step(0.52, h2) * (neon(u + cellArc * 0.16, 0.0022, px)
                               + neon(u - cellArc * 0.16, 0.0022, px))
            * bandMask(d, 0.884, 0.928);
    // Arc segments, high and low in the band
    glyphs += step(0.38, h3) * neon(d - 0.924, 0.0024, px)
            * smoothstep(0.16, 0.26, t) * (1.0 - smoothstep(0.62, 0.72, t));
    glyphs += step(0.62, h3) * neon(d - 0.890, 0.0020, px)
            * smoothstep(0.30, 0.40, t) * (1.0 - smoothstep(0.78, 0.88, t));
    // Ink dot — round in the disc's own coordinates, not in the cell's
    glyphs += step(0.70, h4)
            * exp(-(u * u + (d - 0.908) * (d - 0.908)) / 0.00006);
    // Chevron
    glyphs += step(0.80, h1) * neon(u + (d - 0.896) * 0.9, 0.0022, px) * bandMask(d, 0.884, 0.908)
            + step(0.80, h1) * neon(u - (d - 0.918) * 0.9, 0.0022, px) * bandMask(d, 0.906, 0.930);
    glyphs *= 1.30 * runeMask;

    // Fine tick rail just inside the rune band
    float tickPos = ang * 72.0 / TAU;
    float tickArc = TAU * d / 72.0;
    float ticks = neon((fract(tickPos) - 0.5) * tickArc * 2.0, 0.0016, px)
                * bandMask(d, 0.840, 0.866) * 0.55 * runeMask;

    // ── 3 · Lattice: the great geometry ───────────────────────────
    // Guarded by the script so the twelve upper planes never evaluate it.
    float lattice = 0.0;
    if (pLat > 0.001) {
        // Hexagram: two counter-turned triangles
        lattice += neon(ngonLine(d, ang, 0.812, 3.0, latRot), 0.0038, px) * 1.05;
        lattice += neon(ngonLine(d, ang, 0.812, 3.0, latRot + PI / 3.0), 0.0038, px) * 1.05;
        // Three rotated squares → a twelve-fold lattice
        for (int k = 0; k < 3; k++) {
            float r0 = latRot * 0.6 + float(k) * PI / 12.0;
            lattice += neon(ngonLine(d, ang, 0.742, 4.0, r0), 0.0022, px) * 0.62;
        }
        // Three interlocking circles
        for (int k = 0; k < 3; k++) {
            float a = latRot + float(k) * TAU / 3.0;
            vec2 c = vec2(cos(a), sin(a)) * 0.352;
            lattice += neon(length(uv - c) - 0.462, 0.0030, px) * 0.90;
        }
        // Guilloché rose ring — two modulations, and it reads as engraving
        float rose = 0.628 + 0.0130 * cos(12.0 * (ang - sunRot))
                           + 0.0055 * cos(36.0 * (ang + sunRot * 0.5));
        lattice += neon(d - rose, 0.0022, px) * 0.80;
        // 24 radial spokes out to the rim
        lattice += pow(max(0.0, cos(ang * 24.0 - sunRot * 24.0)), 90.0)
                 * bandMask(d, 0.30, 0.845) * 0.50;
        lattice *= latMask;
    }

    // ── 4 · Seals: six satellites on a fluted orbit ───────────────
    float seals = 0.0;
    if (pSeal > 0.001) {
        seals += neon(d - 0.585, 0.0022, px) * (0.55 + 0.45 * cos(ang * 24.0)) * 0.75;
        for (int k = 0; k < 6; k++) {
            float a = sealRot + float(k) * TAU / 6.0;
            vec2 c = vec2(cos(a), sin(a)) * 0.585;
            vec2 q = uv - c;
            float qr = length(q);
            seals += neon(qr - 0.108, 0.0026, px) * 1.00;
            seals += neon(qr - 0.056, 0.0018, px) * 0.70;
            // Three marks at 120°, no trig beyond the centre itself
            for (int j = 0; j < 3; j++) {
                float b = a + float(j) * TAU / 3.0;
                vec2 m = vec2(cos(b), sin(b)) * 0.082;
                seals += exp(-dot(q - m, q - m) / 0.00035) * 0.85;
            }
            seals += exp(-qr * qr / 0.0009) * 0.9;
        }
        // Twelve arc brackets hugging the rune band's inner rail. The window is
        // already soft in the angular coordinate, so no extra width term is
        // needed — the stroke thins with the band on its own.
        float bPos = (ang - sealRot) * 12.0 / TAU;
        float bt = fract(bPos);
        seals += neon(d - 0.828, 0.0020, px)
               * smoothstep(0.10, 0.22, bt) * (1.0 - smoothstep(0.58, 0.70, bt))
               * 0.9;
        seals *= sealMask;
    }

    // ── 5 · Core: twelve-petal sun and its seed ───────────────────
    float corePat = 0.0;
    if (pCore > 0.001) {
        float petal = 0.262 + 0.076 * cos(12.0 * (ang - sunRot));
        corePat += neon(d - petal, 0.0044, px) * 1.10;
        corePat += neon(d - 0.196, 0.0028, px) * 0.85;
        corePat += neon(d - 0.330, 0.0022, px) * 0.70;
        corePat += pow(max(0.0, cos(ang * 4.0 - sunRot * 4.0)), 60.0)
                 * bandMask(d, 0.05, 0.196) * 0.55;
        corePat *= sunMask;
    }
    // The white-hot seed rides the core's own beat rather than opening the
    // summon: the upper planes stop short of it in the script, so they cannot
    // stack a second and third copy of the same blob on the middle.
    float seed = (exp(-d * d * 300.0) * 1.7 + exp(-d * d * 26.0) * 0.34) * pCore;

    // ── Moving light: radial pulse and orbit sparks ───────────────
    // The wide pulse only runs on planes that have reached the seals — on a
    // rim-only plane it would read as rings floating around nothing. The outer
    // spark train belongs to the rim, the inner one to the seal orbit.
    float pulse = exp(-pow((fract(d * 2.4 - phase * 2.0) - 0.5) * 8.0, 2.0))
                * bandMask(d, 0.16, 0.96) * 0.50 * pSeal;

    float sparks = pow(max(0.0, sin(ang * 12.0 - phase * TAU * 3.0)), 26.0)
                 * exp(-pow((d - 0.906) * 70.0, 2.0)) * 1.7 * rimC
                 + pow(max(0.0, sin(ang * 8.0 + phase * TAU * 2.0)), 34.0)
                 * exp(-pow((d - 0.585) * 90.0, 2.0)) * 1.2 * pSeal;

    // ── Combine ───────────────────────────────────────────────────
    float pattern = (rings + glyphs + ticks) * boil
                  + lattice * boil
                  + seals
                  + corePat
                  + pulse
                  + sparks
                  + seed;

    // The pen tips: what makes a summon look drawn rather than faded in.
    pattern += penTip(ang, d, HEAD_OUT, pRimA, 0.974) * 1.5;
    pattern += penTip(ang, d, HEAD_OUT, pRimC, 0.906) * 1.3;
    pattern += penTip(ang, d, HEAD_OUT, pRune, 0.906) * 1.7;
    pattern += penTip(-ang, d, HEAD_IN, pLat, 0.628) * 1.5;
    pattern += penTip(-ang, d, HEAD_IN, pSeal, 0.585) * 1.4;
    // And a crest riding the core's outward bloom.
    pattern += exp(-pow((d - coreR) * 26.0, 2.0)) * 1.25
             * step(0.002, pCore) * (1.0 - step(0.998, pCore));

    if (pattern < 0.004) discard;

    // ── Palette: ember halo → gold body → white-hot core ──────────
    vec3 ember = vec3(0.78, 0.28, 0.02);
    vec3 gold  = vec3(1.00, 0.70, 0.16);
    vec3 hot   = vec3(1.00, 0.95, 0.74);

    float heat = clamp(pattern * 0.5, 0.0, 1.0);
    vec3 col = mix(ember, gold, heat);
    col = mix(col, hot, smoothstep(0.55, 1.0, heat));
    col *= pattern * energy * 1.30;

    float finalAlpha = clamp(pattern * alpha * 1.05, 0.0, 1.0);

    fragColor = vec4(col, finalAlpha) * ColorModulator;
    if (fragColor.a < 0.0015) discard;
}

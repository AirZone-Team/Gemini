// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  Thaumaturgy Strike sky sigil fragment shader (奇术打击 · 魔法阵)
//
//  A violet thaumaturgic disc drawn as neon light-lines:
//    · twin outer rings carrying a hashed rune band
//    · three great interlocking circles (the triquetra)
//    · a pentagram seal inside each great circle
//    · a twelve-petal sun rosette around a white core
//    · radial spokes out to the rim
//
//  UV here is the disc's own plane, which the renderer orients in world space
//  (ThaumaturgyRenderer#drawSigil) — nothing in this shader is screen-relative,
//  so perspective foreshortens the design like a real object.
//
//  Every element is an inverse-distance "neon" line rather than a hard
//  mask, so the same pass yields both the crisp stroke and its halo —
//  the halo is what the bloom chain picks up.
//
//  Summoning: `reveal` is a drawing script, not a wipe. Two pens run round the
//  disc in opposite directions and each figure claims a slice of it, so the rim
//  rings, the rune band, the triquetra, the seals and the rosette are laid down
//  one after another. A plane whose reveal stops at a SCRIPT_* boundary never
//  reaches the figures above it, which is how the renderer keeps its secondary
//  planes from tangling with the primary disc.
//
//  vertexColor.r = reveal (0→1 summon script progress)
//  vertexColor.g = animation phase (0→1, wraps seamlessly): line boil and the
//                  travelling radial pulse. Rotation is NOT read here — the
//                  disc turns in its own geometry (a byte channel could only
//                  step by 1.4°). Elements below sway against that rotation by
//                  a bounded amount, which is what keeps them byte-safe.
//  vertexColor.b = energy / 4 (HDR)
//  vertexColor.a = master alpha
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

#define TAU 6.28318530718

// Must match ThaumaturgyEffectInstance.SCRIPT_*.
#define SCRIPT_RIM_END  0.22
#define SCRIPT_RUNE_END 0.42
#define SCRIPT_TRI_END  0.68
#define SCRIPT_SEAL_END 0.88

// Where each pen lifts off, and how wide a stroke's drawn edge is softened.
#define HEAD_OUTER 1.90
#define HEAD_INNER -0.60
#define PEN_EDGE   0.05

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
// it widens to fit, which stops the compressed far side of a tilted disc from
// flickering frame to frame. Brightness is traded for the extra coverage, but
// only on a square-root: the stroke has to stay hot enough for the bloom chain
// to find it, and that chain is what makes the sigil glow.

float neon(float dist, float w, float px) {
    float ww = max(w, px);
    float gain = sqrt(w / ww);
    float a = abs(dist);
    float core = smoothstep(ww, 0.0, a) * gain;
    float halo = exp(-(a * a) / (ww * ww * 22.0)) * 0.34 * gain;
    return core + halo;
}

float bandMask(float d, float lo, float hi) {
    return smoothstep(lo - 0.006, lo + 0.006, d)
         * (1.0 - smoothstep(hi - 0.006, hi + 0.006, d));
}

float sdSeg(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-6), 0.0, 1.0);
    return length(pa - ba * h);
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
    // Once it is fully drawn the soft edge goes, so no seam is left behind
    // where the pen lifted off.
    float mask = mix(smoothstep(head + PEN_EDGE, head - PEN_EDGE, a),
                     1.0, step(0.999, p));
    return mask * step(0.0015, p);
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

    if (alpha < 0.002) discard;   // uniform across the quad, so derivatives
                                  // below are still taken in uniform control flow

    vec2 uv = (uvCoord - 0.5) * 2.0;
    float d = length(uv);
    float px = length(vec2(fwidth(uv.x), fwidth(uv.y)));
    // Clipping at the rim keeps a stroke's halo from spilling past the disc. It
    // sits after the derivative so that widening stays well defined.
    if (d > 1.06) discard;

    float ang = atan(uv.y, uv.x);

    // Bounded counter-rotation of the inner figures against the disc's spin.
    // Only sin/cos of the wrapped phase is safe here: a monotonic angle sent
    // through a byte would turn in 1.4° jumps.
    float sway = 0.055 * sin(phase * TAU);
    float runeRot = sway;
    float triRot = -sway * 1.5;
    float sunRot = sway * 0.6;

    // ── Script: which figure is being drawn right now ─────────────
    // The rim is three rings laid down one after another, so the outer band
    // closes before the runes begin.
    float pRim = slice(reveal, 0.0, SCRIPT_RIM_END);
    float pRimA = clamp(pRim * 3.0, 0.0, 1.0);
    float pRimB = clamp(pRim * 3.0 - 1.0, 0.0, 1.0);
    float pRimC = clamp(pRim * 3.0 - 2.0, 0.0, 1.0);
    float pRune = slice(reveal, SCRIPT_RIM_END, SCRIPT_RUNE_END);
    float pTri = slice(reveal, SCRIPT_RUNE_END, SCRIPT_TRI_END);
    float pSeal = slice(reveal, SCRIPT_TRI_END, SCRIPT_SEAL_END);
    float pSun = slice(reveal, SCRIPT_SEAL_END, 1.0);

    float rimA = pen(ang, HEAD_OUTER, pRimA);
    float rimB = pen(ang, HEAD_OUTER, pRimB);
    float rimC = pen(ang, HEAD_OUTER, pRimC);
    float runeMask = pen(ang, HEAD_OUTER, pRune);
    float triMask = pen(-ang, HEAD_INNER, pTri);
    float sealMask = pen(-ang, HEAD_INNER, pSeal);
    // The rosette and its spokes bloom outward from the centre instead of being
    // swept, so the summon's last beat reads differently from the rest.
    float sunR = pSun * 0.50;
    float sunMask = (1.0 - smoothstep(sunR - 0.05, sunR + 0.05, d)) * step(0.0015, pSun);

    // ── Boiling: slow-domain turbulence, phase-continuous ─────────
    // Offsets travel on sin/cos of the wrapped phase, so the noise field
    // loops seamlessly instead of popping when phase wraps 1 → 0.
    vec2 boilDrift = vec2(sin(phase * TAU), cos(phase * TAU)) * 0.35;
    float boil = 0.86 + 0.14 * noise(uv * 5.0 + boilDrift);

    // ── Outer rings + rune band ───────────────────────────────────
    float rings = neon(d - 0.985, 0.0055, px) * 1.15 * rimA
                + neon(d - 0.925, 0.0035, px) * 0.85 * rimB
                + neon(d - 0.875, 0.0022, px) * 0.55 * rimC;

    float cells = 44.0;
    float runeAng = ang + runeRot;
    float cellIdx = floor(runeAng * cells / TAU);
    float t0 = fract(runeAng * cells / TAU);
    float h = hash1(cellIdx);

    float glyphs = 0.0;
    glyphs += step(0.30, h) * (1.0 - smoothstep(0.12, 0.34, t0))
            * bandMask(d, 0.934, 0.950);
    glyphs += step(0.52, hash1(cellIdx * 7.13)) * smoothstep(0.36, 0.56, t0)
            * (1.0 - smoothstep(0.68, 0.88, t0))
            * bandMask(d, 0.955, 0.972);
    glyphs += step(0.42, hash1(cellIdx * 3.71)) * smoothstep(0.58, 0.76, t0)
            * bandMask(d, 0.938, 0.970);
    glyphs *= 1.15 * runeMask;

    // ── Triquetra: three great circles + their pentagram seals ────
    float tri = 0.0;
    float seals = 0.0;
    for (int k = 0; k < 3; k++) {
        float a = triRot + float(k) * TAU / 3.0;
        vec2 c = vec2(cos(a), sin(a)) * 0.44;
        vec2 q = uv - c;
        float qr = length(q);

        tri += neon(qr - 0.50, 0.0048, px);
        seals += neon(qr - 0.158, 0.0028, px);

        // {5/2} star polygon: vertex i joined to vertex i+2
        float star = 0.0;
        float sp = triRot + float(k) * 0.7;
        for (int i = 0; i < 5; i++) {
            float a0 = TAU * float(i) / 5.0 + sp;
            float a1 = TAU * (float(i) + 2.0) / 5.0 + sp;
            vec2 p0 = vec2(cos(a0), sin(a0)) * 0.128;
            vec2 p1 = vec2(cos(a1), sin(a1)) * 0.128;
            star += neon(sdSeg(q, p0, p1), 0.0032, px);
        }
        seals += star * 0.85;

        // Small satellite rings riding the outer band
        float oa = float(k) * TAU / 3.0 + TAU / 6.0;
        vec2 oc = vec2(cos(oa), sin(oa)) * 0.80;
        tri += neon(length(uv - oc) - 0.085, 0.0030, px) * 0.8;
    }
    tri *= triMask;
    seals *= sealMask;

    // ── Central rosette: 12-petal sun ─────────────────────────────
    float petal = 0.295 + 0.082 * cos(12.0 * (ang - sunRot));
    float rosette = neon(d - petal, 0.0050, px)
                  + neon(d - 0.205, 0.0032, px)
                  + neon(d - 0.352, 0.0024, px);
    // The white-hot seed rides the rosette's own beat rather than opening the
    // summon: the secondary planes stop short of it in the script, so they
    // cannot stack a second and third copy of the same blob on the middle.
    float core = (exp(-d * d * 260.0) * 1.6 + exp(-d * d * 26.0) * 0.35) * pSun;

    // ── Spokes from the rosette out to the rim ────────────────────
    float spokes = smoothstep(0.988, 1.0, abs(cos((ang - sunRot) * 12.0)))
                 * bandMask(d, 0.36, 0.925) * 0.55;

    // ── Travelling energy pulse along the radius ──────────────────
    float pulse = exp(-pow((fract(d * 3.0 - phase * 4.0) - 0.5) * 9.0, 2.0))
                * bandMask(d, 0.36, 0.90) * 0.42;

    // ── Combine ───────────────────────────────────────────────────
    float pattern = (rings + glyphs + tri * 1.05 + seals * 0.95
                   + (rosette * 1.1 + spokes) * sunMask + pulse * rimA) * boil
                  + core;

    // The pen tips: what makes a summon look drawn rather than faded in.
    pattern += penTip(ang, d, HEAD_OUTER, pRimA, 0.985) * 1.5;
    pattern += penTip(ang, d, HEAD_OUTER, pRune, 0.952) * 1.9;
    pattern += penTip(-ang, d, HEAD_INNER, pTri, 0.66) * 1.6;
    pattern += penTip(-ang, d, HEAD_INNER, pSeal, 0.30) * 1.4;
    // And a crest riding the rosette's outward bloom.
    pattern += exp(-pow((d - sunR) * 26.0, 2.0)) * 1.3
             * step(0.002, pSun) * (1.0 - step(0.998, pSun));

    if (pattern < 0.004) discard;

    // ── Palette: violet halo → magenta body → white-hot core ──────
    vec3 violet  = vec3(0.52, 0.16, 1.00);
    vec3 magenta = vec3(0.86, 0.38, 1.00);
    vec3 hot     = vec3(1.00, 0.92, 1.00);

    float heat = clamp(pattern * 0.55, 0.0, 1.0);
    vec3 col = mix(violet, magenta, heat);
    col = mix(col, hot, smoothstep(0.55, 1.0, heat));
    col *= pattern * energy * 1.40;

    float finalAlpha = clamp(pattern * alpha * 1.05, 0.0, 1.0);

    fragColor = vec4(col, finalAlpha) * ColorModulator;
    if (fragColor.a < 0.0015) discard;
}

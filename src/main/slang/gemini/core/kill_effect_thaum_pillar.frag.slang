// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  Thaumaturgy Strike light column fragment shader (奇术打击 · 光柱)
//
//  Three crossed vertical quads per column, at fixed world angles (see
//  ThaumaturgyRenderer#emitColumn), so the beam has parallax instead of
//  always turning its face to the camera. The shader splits each quad into
//  a tight white core and a wide violet halo, caps the beam at its
//  descending tip, and runs animated energy streaks down its length.
//
//  uvCoord.x = pillarIndex + across (0 = one edge, 0.5 = axis, 1 = other)
//  uvCoord.y = along (0 = sigil end, 1 = ground end)
//  vertexColor.r = descent (0→1); the beam only exists for along ≤ r
//  vertexColor.g = fract(flow phase)
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

float hash1(float n) {
    return fract(sin(n * 91.7) * 43758.5453123);
}

[shader("fragment")]

void main() {
    float descent = vertexColor.r;
    float phase   = vertexColor.g;
    float energy  = vertexColor.b * 4.0;
    float alpha   = vertexColor.a;

    if (alpha < 0.002 || descent < 0.001) discard;

    float pillarIdx = floor(uvCoord.x);
    float across = abs(fract(uvCoord.x) - 0.5) * 2.0;   // 0 axis → 1 edge
    float along  = uvCoord.y;                            // 0 sigil → 1 ground
    // Screen width of one pixel in `across` units, taken before the tip cut so
    // the derivative stays in uniform control flow.
    float pixel = fwidth(across);

    // ── The column has not reached this far down yet ──────────────
    if (along > descent + 0.008) discard;

    // ── Cross-section: tight core riding in a wide halo ───────────
    // A quad turning edge-on squeezes its 0.1-wide core beneath a pixel, so the
    // profile widens to the pixel and hands back the brightness that extra area
    // would otherwise invent. Three such faces overlap on the axis, and this is
    // what keeps their crossing point from crawling with the camera.
    float coreW = max(0.1, pixel * 0.5);
    float core = exp(-(across * across) / (2.0 * coreW * coreW)) * (0.1 / coreW);
    float halo = exp(-(across * across) / 0.62);
    float body = core + halo * 0.40;

    // ── Longitudinal shaping ──────────────────────────────────────
    // Root: where the beam leaves the sigil
    float root = exp(-along * 16.0) * 0.45;
    // Tip: the leading edge of the fall, brightest while it travels
    float tipDist = max(descent - along, 0.0);
    float tip = exp(-tipDist * 26.0) * (1.0 - smoothstep(0.97, 1.0, descent)) * 0.85;
    // Foot: pinned flare once the column is driven into the ground
    float foot = exp(-pow((along - 1.0) * 9.0, 2.0)) * smoothstep(0.90, 1.0, descent) * 0.50;

    // ── Downward energy streaks ───────────────────────────────────
    // Integer flow rates: when `phase` wraps 1 → 0 the fract() argument
    // shifts by a whole number, so the streaks loop with no visible jump.
    float seed = hash1(pillarIdx * 3.17);
    float lanes = 3.0 + floor(seed * 3.0);
    float rate = 4.0 + floor(seed * 5.0);
    float s = fract(along * lanes - phase * rate);
    float streak = exp(-pow((s - 0.5) * 7.0, 2.0)) * (0.35 + 0.45 * seed);
    // A second, faster, thinner set reads as arcing filaments
    float s2 = fract(along * (lanes * 3.0) - phase * 13.0 + seed);
    streak += exp(-pow((s2 - 0.5) * 16.0, 2.0)) * 0.28;

    // ── Slight vertical breathing so the beam is never static ─────
    float shimmer = 0.90 + 0.10 * sin(along * 34.0 + phase * TAU * 2.0 + seed * 9.0);

    // Root, tip and foot are shaped by a narrow lobe. Given the broad halo (or
    // nothing at all) they paint a full-width band across every quad, which both
    // reads as a hard-edged star where three crossed quads meet and turns the
    // whole column into one white slab instead of a core in a sheath.
    float lobe = core + halo * 0.35;
    float brightness = (body * (1.0 + streak) + (root + tip + foot) * lobe) * shimmer;

    if (brightness < 0.004) discard;

    // ── Palette: violet sheath → magenta body → white core ────────
    vec3 violet  = vec3(0.46, 0.12, 1.00);
    vec3 magenta = vec3(0.80, 0.34, 1.00);
    vec3 hot     = vec3(1.00, 0.95, 1.00);

    float heat = clamp(brightness * 0.7, 0.0, 1.0);
    vec3 col = mix(violet, magenta, heat);
    // Only the axis goes white-hot: weighting the blend by `core` keeps the
    // sheath violet however bright the column gets.
    col = mix(col, hot, smoothstep(0.45, 0.95, heat) * (0.15 + 0.85 * core));
    col *= brightness * energy * 1.5;

    // Taper the silhouette edges so the quad boundary never shows
    float edge = 1.0 - smoothstep(0.80, 1.0, across);
    float finalAlpha = clamp(brightness * alpha * edge, 0.0, 1.0);

    fragColor = vec4(col, finalAlpha) * ColorModulator;
    if (fragColor.a < 0.0015) discard;
}

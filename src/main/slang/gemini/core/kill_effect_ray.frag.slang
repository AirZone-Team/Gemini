// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  Radial Ray fragment shader — pseudo ray-tracing light streaks
//
//  Each ray is rendered as a camera-facing billboard elongated along
//  a radial direction from the effect center.  The billboard quad's
//  UV coordinates encode:
//    u = distance along ray (0=at source → 1=at tip)
//    v = cross-sectional position (0=left edge, 0.5=center, 1=right edge)
//
//  vertexColor.r = normalized distance along ray
//  vertexColor.g = ray angular index (0..N-1 scaled to 0..1)
//  vertexColor.b = intensity
//  vertexColor.a = master alpha
//
//  Depth-tested: rays are correctly occluded by blocks/entities between
//  the viewer and the ray, creating realistic light-shadow interaction.
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

// ── Hash for per-ray variation ────────────────────────────────────

float hash(float n) {
    return fract(sin(n) * 43758.5453123);
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

// ═══════════════════════════════════════════════════════════════════
//  Main
// ═══════════════════════════════════════════════════════════════════

[shader("fragment")]

void main() {
    float distAlong  = vertexColor.r;  // 0 at source, 1 at tip
    float rayIndex   = vertexColor.g;  // ray angular index
    float intensity  = vertexColor.b * 4.0;  // intensity packed normalized (x/4)
    float alpha      = vertexColor.a;

    if (alpha < 0.001 || intensity < 0.001) { discard; return; }

    // UV: u = along ray, v = across ray width
    float u = uvCoord.x;  // 0→1 along ray direction
    float v = uvCoord.y;  // 0→1 across ray

    // ── Cross-sectional gaussian profile ────────────────────────────
    // Narrow at source (tight beam), wider at tip (scatter)
    float widthAtU = 0.03 + u * 0.10;
    float vCentered = abs(v - 0.5) * 2.0;  // 0 at center, 1 at edges
    float crossSection = exp(-vCentered * vCentered / (widthAtU * widthAtU));

    // ── Distance falloff ────────────────────────────────────────────
    // Quadratic falloff: bright near source, dim at tip
    float distFalloff = 1.0 / (1.0 + u * u * 8.0);

    // Sharp initial peak near source
    float sourcePeak = exp(-u * 6.0) * 0.7;

    // ── Ray dithering ───────────────────────────────────────────────
    // Per-ray and per-position noise for organic variation
    float dither = hash2(vec2(u * 20.0 + rayIndex, rayIndex * 7.13)) * 0.3 + 0.7;

    // ── Temporal shimmer ────────────────────────────────────────────
    // Subtle flicker along each ray
    float shimmer = 1.0 + 0.15 * sin(u * 40.0 + rayIndex * 13.0 + distAlong * 30.0);

    // ── Composite brightness ────────────────────────────────────────
    float brightness = (crossSection * distFalloff + sourcePeak * crossSection * 2.0)
                     * dither * shimmer * intensity;

    // ── Color ───────────────────────────────────────────────────────
    // Source: pure blinding white
    // Mid:   blue-white
    // Tip:   deep blue-violet
    vec3 srcColor  = vec3(1.0, 1.0, 1.0);
    vec3 midColor  = vec3(0.55, 0.8, 1.0);
    vec3 tipColor  = vec3(0.2, 0.15, 0.8);

    float colorT = u;
    vec3 col = mix(srcColor, midColor, smoothstep(0.0, 0.4, colorT));
    col = mix(col, tipColor, smoothstep(0.4, 1.0, colorT));

    // Ray index → slight hue variation per ray
    float hueShift = hash(rayIndex * 17.0) * 0.15;
    col = mix(col, col * vec3(1.0 + hueShift, 1.0, 1.0 - hueShift), 0.3);

    col *= brightness * 3.0;  // HDR

    // ── Alpha ───────────────────────────────────────────────────────
    float finalAlpha = brightness * alpha;

    fragColor = vec4(col, finalAlpha) * ColorModulator;

    if (fragColor.a < 0.0005) discard;
}

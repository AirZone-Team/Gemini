// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  Thaumaturgy Strike spark + ground ring fragment shader
//
//  One additive pass covers five shapes, picked by vertexColor.g:
//    MOTE   (0.00)  slow rising violet motes drawn to the sigil
//    IMPACT (0.25)  white-hot sparks blown out radially at a column foot
//    INWARD (0.50)  energy streaming into the centre while the spell charges
//    DEBRIS (0.75)  burning fragments thrown off the nova sphere
//    RING   (1.00)  horizontal shock ring + flare where a column lands
//
//  vertexColor.r = lifeRatio (0→1)
//  vertexColor.g = kind, normalized
//  vertexColor.a = master alpha
//  uvCoord       = [0,1]² across the billboard
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

[shader("fragment")]

void main() {
    float life = vertexColor.r;
    float kind = vertexColor.g;
    float alpha = vertexColor.a;

    if (alpha < 0.003) discard;

    vec2 p = (uvCoord - 0.5) * 2.0;
    float d = length(p);

    vec3 violet  = vec3(0.50, 0.14, 1.00);
    vec3 magenta = vec3(0.84, 0.36, 1.00);
    vec3 hot     = vec3(1.00, 0.94, 1.00);

    float brightness;
    vec3 col;

    if (kind > 0.88) {
        // ── Ground shock ring: annulus over a soft centre flare ────
        float ring = exp(-pow((d - 0.78) * 11.0, 2.0));
        // Four-fold asymmetry keeps the ring from looking like a decal
        ring *= 0.82 + 0.18 * cos(atan(p.y, p.x) * 4.0 + life * 3.0);
        float inner = exp(-d * d * 5.5) * 0.55 * (1.0 - smoothstep(0.25, 0.85, life));
        brightness = ring + inner;
        col = mix(magenta, hot, clamp(brightness * 0.9, 0.0, 1.0));
        col *= 2.1;
    } else if (kind > 0.62) {
        // ── Nova debris: hot streak with a broad burning halo ──────
        float core = exp(-d * d * 11.0);
        float halo = exp(-d * d * 2.2) * 0.55;
        // Elongated tail along the radial direction reads as motion blur
        float tail = exp(-pow(p.x * 3.4, 2.0) - pow(p.y * 13.0, 2.0)) * 0.5;
        brightness = core + halo + tail;
        // White-hot at birth, cooling to violet ember as the arc falls
        col = mix(hot, violet, smoothstep(0.0, 0.85, life)) * 2.9;
    } else if (kind > 0.37) {
        // ── Inward stream: compact, very hot, long halo ────────────
        float core = exp(-d * d * 9.0);
        float halo = exp(-d * d * 1.6) * 0.42;
        brightness = core + halo;
        col = mix(violet, hot, clamp(core * 1.6, 0.0, 1.0)) * 2.6;
    } else if (kind > 0.12) {
        // ── Impact spark: dot plus a four-point flare ──────────────
        float core = exp(-d * d * 14.0);
        float cross = (exp(-abs(p.x) * 11.0) * exp(-abs(p.y) * 2.6)
                     + exp(-abs(p.y) * 11.0) * exp(-abs(p.x) * 2.6)) * 0.42;
        brightness = core + cross;
        col = mix(magenta, hot, clamp(brightness, 0.0, 1.0)) * 2.3;
    } else {
        // ── Rising mote: soft sphere impostor, cool violet ─────────
        float sphere = 1.0 - smoothstep(0.55, 1.0, d);
        float core = exp(-d * d * 5.5);
        brightness = core * 0.85 + sphere * 0.35;
        col = mix(violet, magenta, clamp(core, 0.0, 1.0)) * 1.7;
    }

    if (brightness < 0.004) discard;

    float fadeIn  = smoothstep(0.0, 0.12, life);
    float fadeOut = 1.0 - smoothstep(0.55, 1.0, life);
    float fade = min(fadeIn, fadeOut);

    float finalAlpha = clamp(brightness * alpha * fade, 0.0, 1.0);

    fragColor = vec4(col * brightness, finalAlpha) * ColorModulator;
    if (fragColor.a < 0.0015) discard;
}

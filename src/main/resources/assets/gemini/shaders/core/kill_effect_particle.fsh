#version 330

// ═══════════════════════════════════════════════════════════════════
//  Kill Effect Particle fragment shader
//
//  vertexColor.r = lifeRatio (0→1) — GPU computes color from this
//  vertexColor.g = mode flag: 0 = accretion (warm), 0.5 = burst (HDR debris),
//                  1 = sky (cool)
//  vertexColor.b = unused (reserved)
//  vertexColor.a = master alpha
//
//  Renders a soft sphere-impostor on each camera-facing billboard.
//  Three color modes:
//    Accretion (G=0):   white → gold → orange → red → dark (hot debris)
//    Burst (G=0.5):     HDR white-hot → gold → ember → smoke, cools with life
//    Sky (G=1):         white → light blue → deep blue → dark (celestial)
//  Flicker added to accretion/burst modes for burning debris feel.
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

void main() {
    // Sphere impostor: UV centered, circular mask
    vec2 p = (uvCoord - 0.5) * 2.0;
    float d = length(p);

    // Soft sphere edge
    float sphere = 1.0 - smoothstep(0.7, 1.0, d);

    // Inner brightness peak
    float core = exp(-d * d * 6.0);

    // Combine: bright core with soft falloff
    float brightness = core * 0.8 + sphere * 0.4;

    // ── GPU-side color ramp from lifeRatio ──────────────────────
    float lifeRatio = vertexColor.r;               // 0→1
    float masterAlpha = vertexColor.a;
    bool skyMode   = vertexColor.g > 0.75;
    bool burstMode = vertexColor.g > 0.24 && vertexColor.g <= 0.75;

    vec3 color;
    if (skyMode) {
        // ── Sky particle mode: cool celestial tones ─────────────
        // 0.0 = bright white, 0.4 = light blue, 0.7 = deep blue, 1.0 = dark
        vec3 hotColor   = vec3(1.0, 1.0, 1.0);      // white
        vec3 midColor   = vec3(0.55, 0.75, 1.0);    // light blue
        vec3 coolColor  = vec3(0.15, 0.30, 0.80);   // deep blue
        vec3 deadColor  = vec3(0.02, 0.04, 0.10);   // dark blue

        float t = lifeRatio;
        if (t < 0.4) {
            float p0 = t / 0.4;
            color = mix(hotColor, midColor, p0);
        } else if (t < 0.7) {
            float p1 = (t - 0.4) / 0.3;
            color = mix(midColor, coolColor, p1);
        } else {
            float p2 = (t - 0.7) / 0.3;
            color = mix(coolColor, deadColor, p2);
        }
    } else if (burstMode) {
        // ── Burst mode: explosion debris, HDR hot → cool smoke ──
        // 0.0 = HDR white-hot, 0.3 = gold, 0.65 = ember orange, 1.0 = dark smoke
        vec3 c0 = vec3(1.00, 1.00, 0.95);   // white-hot
        vec3 c1 = vec3(1.00, 0.80, 0.35);   // gold
        vec3 c2 = vec3(1.00, 0.42, 0.08);   // ember orange
        vec3 c3 = vec3(0.30, 0.05, 0.02);   // dark smoke

        float t = lifeRatio;
        color = mix(c0, c1, smoothstep(0.0, 0.30, t));
        color = mix(color, c2, smoothstep(0.30, 0.65, t));
        color = mix(color, c3, smoothstep(0.65, 1.0, t));

        // HDR at birth (feeds bloom), cools quadratically with life
        float heat = 2.6 * (1.0 - t) * (1.0 - t) + 0.7;
        color *= heat;
    } else {
        // ── Accretion mode: hot debris tones ────────────────────
        // 0.0 = white-hot, 0.3 = yellow, 0.6 = orange, 0.8 = red, 1.0 = dark
        vec3 hotColor   = vec3(1.0, 0.95, 0.70);    // white-gold
        vec3 midColor   = vec3(1.0, 0.55, 0.08);    // orange
        vec3 coolColor  = vec3(0.60, 0.08, 0.02);   // deep red
        vec3 deadColor  = vec3(0.15, 0.02, 0.00);   // near-dark

        float t = lifeRatio;
        if (t < 0.4) {
            float p0 = t / 0.4;
            color = mix(hotColor, midColor, p0);
        } else if (t < 0.7) {
            float p1 = (t - 0.4) / 0.3;
            color = mix(midColor, coolColor, p1);
        } else {
            float p2 = (t - 0.7) / 0.3;
            color = mix(coolColor, deadColor, p2);
        }
    }

    // ── Flicker: simulates uneven burning (accretion + burst) ──
    float flicker = 1.0;
    if (!skyMode) {
        flicker = 1.0 + sin(lifeRatio * 30.0 + uvCoord.x * 10.0) * 0.2;
    }
    color *= flicker;

    // ── Fade in/out based on life ─────────────────────────────
    float fadeIn  = smoothstep(0.0, 0.1, lifeRatio);
    float fadeOut = 1.0 - smoothstep(0.7, 1.0, lifeRatio);
    float fade    = min(fadeIn, fadeOut);

    float finalAlpha = brightness * masterAlpha * fade;

    // ── Glow halo (independent of brightness) ────────────────
    vec3 glow = color * exp(-d * d * 1.5) * 0.15;

    fragColor = vec4(color * brightness + glow, finalAlpha) * ColorModulator;

    if (fragColor.a < 0.002) {
        discard;
    }
}

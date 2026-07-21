// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════
//  GPU Material System — Glass Panel Shader
//
//  Produces a modern glass-morphism panel with:
//    • SDF rounded corners with adaptive AA (fwidth)
//    • Vertical luminance gradient
//    • Top-edge specular highlight (glass reflection)
//    • Fresnel edge glow (all edges catch light)
//    • Dynamic noise grain (material texture)
//    • Subtle aurora color sweep (barely perceptible)
//    • Mouse proximity lighting
//    • Enabled state: background lift + accent border + bloom
//
//  Aesthetic target: Linear • Arc • VisionOS • Vercel
// ═══════════════════════════════════════════════════════════

// ── Per-frame uniforms ──────────────────────────────────
layout(std140) uniform FrameData {
    vec2 u_mouse;       // mouse position in GUI pixels
    float u_time;       // elapsed time in seconds
    float u_pad0;
};

// ── Per-element uniforms ────────────────────────────────
layout(std140) uniform ElementData {
    vec2 u_pos;         // element top-left (GUI pixels)
    vec2 u_size;        // element width, height (pixels)
    float u_radius;     // corner radius (pixels)
    float u_enabled;    // 0.0 = off, 1.0 = on
    vec4 u_accent;      // accent color (rgb) + intensity (a)
};

// ── Standard pipeline uniforms ──────────────────────────
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 fragPos;

out vec4 fragColor;

// ── SDF: rounded box distance ───────────────────────────
float roundedBoxSDF(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// ── Hash noise ──────────────────────────────────────────
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float hash2D(vec2 p) {
    float h = dot(p, vec2(12.9898, 78.233));
    return fract(sin(h) * 43758.5453);
}

[shader("fragment")]

void main() {
    // ── Local coordinates ────────────────────────────────
    vec2 halfSize = u_size * 0.5;
    vec2 center   = u_pos + halfSize;
    vec2 local    = fragPos - center;          // centered, pixel units
    vec2 uv       = (fragPos - u_pos) / u_size; // [0,1] within element

    // ── SDF rounded corner mask ──────────────────────────
    float dist = roundedBoxSDF(local, halfSize, u_radius);
    float edgeWidth = fwidth(dist);
    float mask = 1.0 - smoothstep(0.0, edgeWidth, dist);
    if (mask < 0.005) discard;

    // ── ① Vertical luminance gradient ────────────────────
    //    Darker top → subtly brighter bottom
    vec3 topBase    = vec3(0.070, 0.080, 0.100);
    vec3 bottomBase = vec3(0.100, 0.110, 0.135);
    vec3 baseColor = mix(topBase, bottomBase, uv.y);

    // ── ② Top glass highlight ────────────────────────────
    //    Sharp exponential falloff from the very top edge
    float topLight = exp(-uv.y * 18.0) * 0.10;
    baseColor += vec3(topLight);

    // ── ③ Fresnel edge glow ──────────────────────────────
    //    All four edges catch a subtle ambient light
    float edgeDist = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    float fresnel = pow(1.0 - edgeDist * 3.0, 4.0);
    fresnel = clamp(fresnel, 0.0, 1.0);
    baseColor += fresnel * vec3(0.04, 0.045, 0.06);

    // ── ④ Noise grain (material texture) ─────────────────
    //    Breaks up perfect solid-color look — 1% strength
    float noise = hash2D(fragPos + u_time * 4.0);
    baseColor += (noise - 0.5) * 0.012;

    // ── ⑤ Aurora sweep ───────────────────────────────────
    //    Ultra-subtle color drift — barely perceptible (2%)
    vec3 aurora = vec3(
        sin(u_time * 0.2),
        sin(u_time * 0.15 + 2.0),
        sin(u_time * 0.18 + 4.0)
    );
    aurora = 0.5 + aurora * 0.5;
    baseColor += aurora * 0.02;

    // ── ⑥ Mouse proximity glow ───────────────────────────
    //    Region near cursor brightens slightly
    float mouseDist = length(fragPos - u_mouse);
    float mouseGlow = exp(-mouseDist * 0.006) * 0.08;
    baseColor += mouseGlow * vec3(0.55, 0.58, 0.68);

    // ── ⑦ Enabled state effects ──────────────────────────
    vec3 accentRGB = u_accent.rgb;
    if (u_enabled > 0.5) {
        // Background lift
        baseColor += accentRGB * 0.10;

        // Accent border glow (inner edge)
        float borderDist = dist;
        float border = 1.0 - smoothstep(-3.0, -0.5, borderDist);
        baseColor += accentRGB * border * 0.30;

        // Internal bloom near edge
        float bloom = exp(-abs(dist) * 0.12) * 0.06;
        baseColor += accentRGB * bloom;
    }

    // ── ⑧ Glass alpha ────────────────────────────────────
    float alpha = mask * ColorModulator.a * 0.90;

    vec3 finalColor = baseColor * ColorModulator.rgb;

    fragColor = vec4(finalColor, alpha);
}

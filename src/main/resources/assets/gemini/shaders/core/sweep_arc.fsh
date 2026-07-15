#version 330

// ═══════════════════════════════════════════════════════════════════════
//  Sweep Attack Arc + Ring Fragment Shader
//
//  Two modes selected by Color.g:
//    Arc mode (Color.g = 0.0):
//      UV0.x = normalized angle (0→1 maps to arcStart→arcEnd)
//      UV0.y = radial distance (0=inner, 1=outer)
//      Color.r = glow multiplier
//      Color.a = master alpha
//      → Renders triple-layer SDF arc (blue / purple / white)
//
//    Ring mode (Color.g = 1.0):
//      UV0 = billboard UV (0→1)
//      Color.r = intensity
//      Color.a = alpha
//      → Renders expanding photon ring with rainbow color
//
//  Time: ModelOffset.x (set by Java via DynamicTransforms)
// ═══════════════════════════════════════════════════════════════════════

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

#define u_time ModelOffset.x

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

// ── Noise ──────────────────────────────────────────────────────

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i), hash(i + vec2(1,0)), f.x),
        mix(hash(i + vec2(0,1)), hash(i + vec2(1,1)), f.x), f.y);
}

// ── Rainbow ────────────────────────────────────────────────────

vec3 rainbow(float t) {
    return 0.5 + 0.5 * cos(6.28318 * (t + vec3(0.0, 0.33, 0.66)));
}

// ════════════════════════════════════════════════════════════════
//  Arc mode: triple-layer energy arc
// ════════════════════════════════════════════════════════════════

vec4 arcMode() {
    float u = vUv.x;    // normalized angle (0→1)
    float v = vUv.y;    // radial distance (0→1)
    float glowMult = vColor.r;
    float masterAlpha = vColor.a;

    // ── Triple-layer SDF arc ────────────────────────────────────
    // Layer 1: tight blue core
    float d1 = abs(v - 0.75);
    float arc1 = smoothstep(0.03, 0.0, d1);

    // Layer 2: medium purple
    float d2 = abs(v - 0.65);
    float arc2 = smoothstep(0.05, 0.0, d2);

    // Layer 3: wide white aura
    float d3 = abs(v - 0.55);
    float arc3 = smoothstep(0.08, 0.0, d3);

    // ── Edge softening ──────────────────────────────────────────
    float edgeSoft = smoothstep(0.0, 0.05, u) * smoothstep(0.0, 0.05, 1.0 - u);

    // ── Energy flow noise ───────────────────────────────────────
    float flow = vnoise(vec2(u * 12.0 - u_time * 4.0, v * 6.0));
    flow = pow(flow, 2.0) * 1.5 + 0.5;

    // ── Colors ──────────────────────────────────────────────────
    vec3 c1 = vec3(0.2, 0.8, 1.0);   // cyan-blue
    vec3 c2 = vec3(1.0, 0.4, 1.0);   // purple
    vec3 c3 = vec3(1.0, 1.0, 1.0);   // white

    vec3 col = c1 * arc1 * 1.5
             + c2 * arc2 * 0.8
             + c3 * arc3 * 0.4;

    col *= flow * edgeSoft;

    // ── Composite ───────────────────────────────────────────────
    float a = (arc1 + arc2 * 0.6 + arc3 * 0.3) * masterAlpha * edgeSoft;

    // HDR boost — values > 1.0 picked up by bloom
    float hdr = 1.0 + arc1 * 2.5 + glowMult * 1.0;
    col *= hdr;

    return vec4(col, a);
}

// ════════════════════════════════════════════════════════════════
//  Ring mode: expanding photon ring
// ════════════════════════════════════════════════════════════════

vec4 ringMode() {
    // UV0 = billboard UV (0→1), center at (0.5, 0.5)
    vec2 uv = (vUv - 0.5) * 2.0;  // → -1 to 1
    float dist = length(uv);

    // Ring SDF: sharp peak at dist ≈ 0.75
    float ring = exp(-abs(dist - 0.75) * 60.0);

    // Outer glow
    float outerGlow = exp(-abs(dist - 0.75) * 8.0) * 0.3;

    // Rainbow color shift over time
    vec3 col = rainbow(u_time * 0.5 + dist * 2.0);

    // Combine
    float intensity = vColor.r;
    float alpha = vColor.a;

    vec3 finalCol = col * (ring + outerGlow) * intensity * 3.0;
    float finalAlpha = (ring + outerGlow) * alpha;

    // HDR boost
    finalCol *= 1.0 + ring * 2.0;

    return vec4(finalCol, finalAlpha);
}

// ════════════════════════════════════════════════════════════════
//  Main
// ════════════════════════════════════════════════════════════════

void main() {
    vec4 result;

    // Mode selection via Color.g: 0.0 = arc, 1.0 = ring
    if (vColor.g < 0.5) {
        result = arcMode();
    } else {
        result = ringMode();
    }

    fragColor = result * ColorModulator;
    if (fragColor.a < 0.002) discard;
}

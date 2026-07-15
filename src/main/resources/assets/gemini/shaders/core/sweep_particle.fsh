#version 330

// ═══════════════════════════════════════════════════════════════════════
//  Sweep Attack Particle / Speed Line Fragment Shader
//
//  Two modes selected by Color.g:
//    Particle mode (Color.g = 0.0):
//      UV0 = billboard UV (0→1)
//      Color.r = lifeRatio (0→1)
//      Color.a = alpha
//      → Renders glowing energy particle with tail
//
//    Speed line mode (Color.g = 1.0):
//      UV0.x = along line (0→1), UV0.y = cross-section (-1→1)
//      Color.a = alpha
//      → Renders speed line with exp glow
//
//  Time: ModelOffset.x
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

// ── Hash ───────────────────────────────────────────────────────

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

// ════════════════════════════════════════════════════════════════
//  Particle mode: glowing energy particle
// ════════════════════════════════════════════════════════════════

vec4 particleMode() {
    float lifeRatio = vColor.r;
    float masterAlpha = vColor.a;

    // Centered UV
    vec2 p = (vUv - 0.5) * 2.0;
    float d = length(p);

    // Star shape SDF — 4-pointed star
    float angle = atan(p.y, p.x);
    float star = 0.8 + 0.2 * cos(angle * 4.0);
    float starShape = smoothstep(star, star - 0.3, d);

    // Core glow
    float core = exp(-d * d * 8.0);

    // Outer glow
    float glow = exp(-d * 3.0) * 0.4;

    // Tail — directional fade
    float tail = exp(-abs(p.y) * 6.0) * smoothstep(1.0, -0.5, p.x) * 0.5;

    // Combine
    float brightness = (core + glow + tail) * starShape;

    // Color: cyan-white energy
    vec3 col = mix(
        vec3(0.3, 0.8, 1.0),  // cyan
        vec3(1.0, 1.0, 1.0),  // white
        core * 0.7
    );

    // Life fade
    float lifeFade = pow(lifeRatio, 3.0);

    // Flicker
    float flicker = 1.0 + sin(lifeRatio * 25.0 + p.x * 8.0) * 0.15;

    float a = brightness * masterAlpha * lifeFade * flicker;

    // HDR boost
    col *= 1.0 + core * 2.0;

    return vec4(col * brightness * flicker, a);
}

// ════════════════════════════════════════════════════════════════
//  Speed line mode: radiating energy lines
// ════════════════════════════════════════════════════════════════

vec4 speedLineMode() {
    float u = vUv.x;    // along line (0→1)
    float v = vUv.y;    // cross-section (-1→1)
    float masterAlpha = vColor.a;

    // Cross-section glow — exp falloff from center
    float crossGlow = exp(-abs(v) * 40.0);

    // Length fade — bright in middle, faded at ends
    float lengthFade = smoothstep(0.0, 0.2, u) * smoothstep(1.0, 0.6, u);

    // Energy flow
    float flow = sin(u * 20.0 - u_time * 8.0) * 0.3 + 0.7;

    // Color: white-blue
    vec3 col = vec3(0.6, 0.85, 1.0);

    float brightness = crossGlow * lengthFade * flow;
    float a = brightness * masterAlpha;

    // HDR boost
    col *= 1.0 + crossGlow * 1.5;

    return vec4(col * brightness, a);
}

// ════════════════════════════════════════════════════════════════
//  Main
// ════════════════════════════════════════════════════════════════

void main() {
    vec4 result;

    // Mode selection via Color.g: 0.0 = particle, 1.0 = speed line
    if (vColor.g < 0.5) {
        result = particleMode();
    } else {
        result = speedLineMode();
    }

    fragColor = result * ColorModulator;
    if (fragColor.a < 0.002) discard;
}

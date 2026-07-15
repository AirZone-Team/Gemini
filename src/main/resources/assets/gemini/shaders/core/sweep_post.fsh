#version 330

// ═══════════════════════════════════════════════════════════════════════
//  Sweep Attack Post-Processing Fragment Shader
//
//  Compile-time defines select the active pass:
//    SWEEP_DISTORT  — space distortion (heat haze / gravitational lensing)
//    SWEEP_CHROMATIC — RGB channel separation (chromatic aberration)
//
//  Uniforms (SweepPostUniforms, std140, 32 bytes = 2 × vec4):
//    vec4 Params:   fbWidth, fbHeight, time, 0
//    vec4 Strength: distortStr, chromaticStr, 0, 0
// ═══════════════════════════════════════════════════════════════════════

uniform sampler2D SceneSampler;

layout(std140) uniform SweepPostUniforms {
    vec4 Params;     // x=fbW, y=fbH, z=time, w=0
    vec4 Strength;   // x=distortStr, y=chromaticStr, z=0, w=0
};

in vec2 vUv;
out vec4 fragColor;

// ── Noise ──────────────────────────────────────────────────────

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1,0)), f.x),
               mix(hash(i + vec2(0,1)), hash(i + vec2(1,1)), f.x), f.y);
}

// ════════════════════════════════════════════════════════════════
//  SWEEP_DISTORT: Space distortion
// ════════════════════════════════════════════════════════════════

#ifdef SWEEP_DISTORT
void main() {
    float strength = Strength.x * 0.05;
    float time = Params.z;

    vec2 center = vec2(0.5);
    vec2 dir = vUv - center;
    float dist = length(dir);

    // Radial distortion with noise modulation
    float distort = strength / (dist + 0.1);
    float n = noise(vUv * 60.0 + time * 0.8) * 0.5 + 0.5;
    distort *= (0.6 + n * 0.8);
    distort = min(distort, 0.06);

    vec2 offsetUv = vUv + normalize(dir + 0.001) * distort;

    // Edge fade — stronger near center, faded at screen edges
    float edgeFade = exp(-dist * dist * 2.0);

    vec3 distorted = texture(SceneSampler, offsetUv).rgb;
    vec3 original  = texture(SceneSampler, vUv).rgb;

    fragColor = vec4(mix(original, distorted, edgeFade), 1.0);
}
#endif

// ════════════════════════════════════════════════════════════════
//  SWEEP_CHROMATIC: RGB channel separation
// ════════════════════════════════════════════════════════════════

#ifdef SWEEP_CHROMATIC
void main() {
    float strength = Strength.y * 0.006;
    float time = Params.z;

    vec2 center = vec2(0.5);
    vec2 dir = normalize(vUv - center + 0.001);
    float dist = length(vUv - center);

    // Scale offset by distance from center
    float scale = strength * dist;

    // Animate slightly
    scale *= 1.0 + sin(time * 3.0) * 0.2;

    float r = texture(SceneSampler, vUv + dir * scale).r;
    float g = texture(SceneSampler, vUv).g;
    float b = texture(SceneSampler, vUv - dir * scale).b;

    fragColor = vec4(r, g, b, 1.0);
}
#endif

// ── Fallback ──────────────────────────────────────────────────

#if !defined(SWEEP_DISTORT) && !defined(SWEEP_CHROMATIC)
void main() {
    fragColor = texture(SceneSampler, vUv);
}
#endif

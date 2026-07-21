// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════════
//  VFX Post-Processing Fragment Shader — unified light-pollution passes
//
//  Compile-time defines select the active pass:
//    VFX_DISTORT        — screen-space heat haze / lensing distortion
//    VFX_GODRAY         — screen-space radial blur (god rays)
//    VFX_CHROMATIC      — RGB channel separation (chromatic aberration)
//    VFX_BLOOM_COMPOSITE— scene + bloom additive composite
//    VFX_VIGNETTE       — vignette + soft clamp to LDR
//
//  Uniforms (VFXUniforms, std140, 96 bytes = 6 × vec4):
//    vec4 Params:       fbWidth, fbHeight, time, 0
//    vec4 DistortPack:  strength, 0, 0, 0
//    vec4 GodRayPack:   strength, 0, 0, 0
//    vec4 ChromPack:    strength, 0, 0, 0
//    vec4 BloomPack:    threshold, strength, 0, 0
//    vec4 LensPack:     intensity, 0, 0, 0
// ═══════════════════════════════════════════════════════════════════════

uniform sampler2D SceneSampler;
uniform sampler2D BloomSampler;

layout(std140) uniform VFXUniforms {
    vec4 Params;        // x=fbW, y=fbH, z=time, w=unused
    vec4 DistortPack;   // x=distortStrength
    vec4 GodRayPack;    // x=godRayStrength
    vec4 ChromPack;     // x=chromaticStrength
    vec4 BloomPack;     // x=threshold, y=strength
    vec4 LensPack;      // x=intensity
};

in vec2 vUv;
out vec4 fragColor;

// ── Pseudo-random hash ────────────────────────────────────────────

float vfxHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float vfxNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(vfxHash(i), vfxHash(i + vec2(1,0)), f.x),
               mix(vfxHash(i + vec2(0,1)), vfxHash(i + vec2(1,1)), f.x), f.y);
}

// ══════════════════════════════════════════════════════════════════
//  VFX_DISTORT: Screen-space heat haze
// ══════════════════════════════════════════════════════════════════

#ifdef VFX_DISTORT
[shader("fragment")]
void main() {
    float strength = DistortPack.x * 0.04;
    vec2 center = vec2(0.5);

    vec2 dir = vUv - center;
    float dist = length(dir);

    float distort = strength / (dist + 0.05);
    float n = vfxNoise(vUv * 80.0 + Params.z * 0.5) * 0.6;
    distort *= (0.7 + n);
    distort = min(distort, 0.08);

    vec2 offsetUv = vUv + normalize(dir + 0.001) * distort;
    float edgeFade = 1.0 - smoothstep(0.75, 1.0, length(vUv - 0.5) * 2.0);

    vec3 distorted = texture(SceneSampler, offsetUv).rgb;
    vec3 original  = texture(SceneSampler, vUv).rgb;

    fragColor = vec4(mix(original, distorted, edgeFade), 1.0);
}
#endif

// ══════════════════════════════════════════════════════════════════
//  VFX_GODRAY: Screen-space radial blur
// ══════════════════════════════════════════════════════════════════

#ifdef VFX_GODRAY
[shader("fragment")]
void main() {
    float strength = GodRayPack.x;
    vec2 center = vec2(0.5);

    vec2 toCenter = center - vUv;
    float dist = length(toCenter);
    vec2 dir = toCenter / max(dist, 0.001);

    vec3 rays = vec3(0.0);
    float wSum = 0.0;
    int samples = 32;

    for (int i = 0; i < samples; i++) {
        float t = (float(i) + 0.5) / float(samples);
        vec2 suv = vUv + dir * t * dist;
        float w = exp(-t * 3.0);
        rays += texture(SceneSampler, suv).rgb * w;
        wSum += w;
    }
    rays /= max(wSum, 0.001);

    float decay = exp(-dist * 1.5);
    rays *= decay * strength;

    vec3 scene = texture(SceneSampler, vUv).rgb;
    fragColor = vec4(scene + rays, 1.0);
}
#endif

// ══════════════════════════════════════════════════════════════════
//  VFX_CHROMATIC: RGB channel separation
// ══════════════════════════════════════════════════════════════════

#ifdef VFX_CHROMATIC
[shader("fragment")]
void main() {
    float strength = ChromPack.x * 0.008;
    vec2 center = vec2(0.5);
    vec2 dir = normalize(vUv - center + 0.001);
    float dist = length(vUv - center);
    float scale = strength * dist;

    float r = texture(SceneSampler, vUv + dir * scale).r;
    float g = texture(SceneSampler, vUv).g;
    float b = texture(SceneSampler, vUv - dir * scale).b;

    fragColor = vec4(r, g, b, 1.0);
}
#endif

// ══════════════════════════════════════════════════════════════════
//  VFX_BLOOM_COMPOSITE: Scene + bloom
// ══════════════════════════════════════════════════════════════════

#ifdef VFX_BLOOM_COMPOSITE
[shader("fragment")]
void main() {
    vec3 scene = texture(SceneSampler, vUv).rgb;
    vec3 bloom = texture(BloomSampler, vUv).rgb;
    float strength = BloomPack.y;

    fragColor = vec4(scene + bloom * strength, 1.0);
}
#endif

// ══════════════════════════════════════════════════════════════════
//  VFX_VIGNETTE: Vignette + filmic soft clamp
// ══════════════════════════════════════════════════════════════════

#ifdef VFX_VIGNETTE
[shader("fragment")]
void main() {
    vec3 color = texture(SceneSampler, vUv).rgb;

    // ── ACES-like soft clamp for HDR → LDR ──
    vec3 a = color * (color * 2.51 + 0.03);
    vec3 b = color * (color * 2.43 + 0.59) + 0.14;
    vec3 mapped = clamp(a / b, 0.0, 1.0);

    // ── Vignette ──
    vec2 uvC = vUv - 0.5;
    float vignette = 1.0 - dot(uvC, uvC) * 0.4;

    fragColor = vec4(mapped * vignette, 1.0);
}
#endif

// ── Fallback ──────────────────────────────────────────────────────

#if !defined(VFX_DISTORT) && !defined(VFX_GODRAY) \
    && !defined(VFX_CHROMATIC) && !defined(VFX_BLOOM_COMPOSITE) \
    && !defined(VFX_VIGNETTE)
[shader("fragment")]
void main() {
    fragColor = texture(SceneSampler, vUv);
}
#endif

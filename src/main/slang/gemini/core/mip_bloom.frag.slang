// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════════
//  Mip Bloom Fragment Shader — multi-pass bloom processor
//
//  Compile-time defines select the active pass:
//    MIP_BRIGHT      — luminance threshold extraction
//    MIP_BLUR_H      — separable horizontal gaussian blur
//    MIP_BLUR_V      — separable vertical gaussian blur
//    MIP_DOWNSAMPLE  — box-filter 2× downscale
//    MIP_UPSAMPLE    — bilinear upsample + additive blend
//    MIP_COMPOSITE   — scene + weighted bloom
//
//  Uniforms (BloomUniforms, std140, 32 bytes = 2 × vec4):
//    vec4 Params:   fbWidth, fbHeight, threshold, 0
//    vec4 Weights:  w0, w1, w2, w3  (mip level weights)
// ═══════════════════════════════════════════════════════════════════════

uniform sampler2D SceneSampler;
uniform sampler2D BloomSampler;

layout(std140) uniform BloomUniforms {
    vec4 Params;      // x=fbWidth, y=fbHeight, z=threshold, w=unused
    vec4 Weights;     // x=w0, y=w1, z=w2, w=w3  (w4 = 1-sum(w0..w3))
};

in vec2 vUv;
out vec4 fragColor;

// ── Luminance ─────────────────────────────────────────────────────
float lum(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

// ══════════════════════════════════════════════════════════════════
//  MIP_BRIGHT: Luminance threshold extraction with soft knee
// ══════════════════════════════════════════════════════════════════

#ifdef MIP_BRIGHT
[shader("fragment")]
void main() {
    vec3 color = texture(SceneSampler, vUv).rgb;
    float l = lum(color);
    float t = Params.z;
    float knee = 0.08;

    float w = clamp((l - t + knee) / (2.0 * knee), 0.0, 1.0);
    float bright = w * max(l - t, 0.0) / max(l, 0.001);

    fragColor = vec4(color * bright, 1.0);
}
#endif

// ══════════════════════════════════════════════════════════════════
//  MIP_BLUR_H: Separable horizontal gaussian blur (9-tap)
// ══════════════════════════════════════════════════════════════════

#ifdef MIP_BLUR_H
[shader("fragment")]
void main() {
    vec2 ts = vec2(1.0 / Params.x, 0.0);
    float sigma = 2.0;

    vec3 color = vec3(0.0);
    float wSum = 0.0;

    for (int i = -8; i <= 8; i++) {
        float w = exp(-float(i * i) / (2.0 * sigma * sigma));
        color += texture(SceneSampler, vUv + ts * float(i)).rgb * w;
        wSum += w;
    }
    fragColor = vec4(color / wSum, 1.0);
}
#endif

// ══════════════════════════════════════════════════════════════════
//  MIP_BLUR_V: Separable vertical gaussian blur (9-tap)
// ══════════════════════════════════════════════════════════════════

#ifdef MIP_BLUR_V
[shader("fragment")]
void main() {
    vec2 ts = vec2(0.0, 1.0 / Params.y);
    float sigma = 2.0;

    vec3 color = vec3(0.0);
    float wSum = 0.0;

    for (int i = -8; i <= 8; i++) {
        float w = exp(-float(i * i) / (2.0 * sigma * sigma));
        color += texture(SceneSampler, vUv + ts * float(i)).rgb * w;
        wSum += w;
    }
    fragColor = vec4(color / wSum, 1.0);
}
#endif

// ══════════════════════════════════════════════════════════════════
//  MIP_DOWNSAMPLE: Box-filter 2× downscale
//  SceneSampler = source (higher res), output is half res
// ══════════════════════════════════════════════════════════════════

#ifdef MIP_DOWNSAMPLE
[shader("fragment")]
void main() {
    vec2 oneTexel = 1.0 / Params.xy;

    // 4-sample box average at corners of this output pixel's footprint
    vec2 halfStep = oneTexel * 0.5;

    vec3 s0 = texture(SceneSampler, vUv + vec2(-halfStep.x, -halfStep.y)).rgb;
    vec3 s1 = texture(SceneSampler, vUv + vec2( halfStep.x, -halfStep.y)).rgb;
    vec3 s2 = texture(SceneSampler, vUv + vec2(-halfStep.x,  halfStep.y)).rgb;
    vec3 s3 = texture(SceneSampler, vUv + vec2( halfStep.x,  halfStep.y)).rgb;

    fragColor = vec4((s0 + s1 + s2 + s3) * 0.25, 1.0);
}
#endif

// ══════════════════════════════════════════════════════════════════
//  MIP_UPSAMPLE: Bilinear upsample + additive blend
//  SceneSampler = current level (blurred)
//  BloomSampler = next coarser level (to upsample and add)
// ══════════════════════════════════════════════════════════════════

#ifdef MIP_UPSAMPLE
[shader("fragment")]
void main() {
    vec3 current = texture(SceneSampler, vUv).rgb;
    vec3 coarser = texture(BloomSampler, vUv).rgb;

    // Additive blend: finer = current + upsampled coarser
    // BloomSampler is already linearly filtered by the sampler
    fragColor = vec4(current + coarser, 1.0);
}
#endif

// ══════════════════════════════════════════════════════════════════
//  MIP_COMPOSITE: Scene + weighted bloom
//  SceneSampler = original scene
//  BloomSampler = combined bloom (all mip levels merged)
// ══════════════════════════════════════════════════════════════════

#ifdef MIP_COMPOSITE
[shader("fragment")]
void main() {
    vec3 scene = texture(SceneSampler, vUv).rgb;
    vec3 bloom = texture(BloomSampler, vUv).rgb;

    // Bloom strength is embedded in w0 (Weights.x)
    float strength = Weights.x;

    // Soft clamp bloom contribution to prevent over-brightening
    bloom = bloom / (1.0 + bloom);

    fragColor = vec4(scene + bloom * strength, 1.0);
}
#endif

// ── Fallback ──────────────────────────────────────────────────────

#if !defined(MIP_BRIGHT) && !defined(MIP_BLUR_H) && !defined(MIP_BLUR_V) \
    && !defined(MIP_DOWNSAMPLE) && !defined(MIP_UPSAMPLE) \
    && !defined(MIP_COMPOSITE)
[shader("fragment")]
void main() {
    fragColor = texture(SceneSampler, vUv);
}
#endif

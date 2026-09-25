// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  Hell's Hand ember fragment shader
//
//  vertexColor.r = lifeRatio (0→1) — GPU computes the hot→smoke ramp
//  vertexColor.g = unused (reserved)
//  vertexColor.b = unused (reserved)
//  vertexColor.a = master alpha
//
//  Soft round impostor of a burning coal torn off the rift lip:
//    white-hot birth → orange → deep red → dark smoke,
//    flickering with an ember "breath", fed to the bloom chain.
//  Hell's Hand owns this pipeline entirely — it does NOT share the
//  Hypernova PARTICLE_PIPE shader asset.
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

// ── tiny value noise (uv is [0,1] per billboard; lifeRatio breaks ties) ──
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

[shader("fragment")]

void main() {
    // Sphere impostor on the billboard
    vec2 p = (uvCoord - 0.5) * 2.0;
    float d = length(p);

    float sphere = 1.0 - smoothstep(0.55, 1.0, d);
    float core   = exp(-d * d * 7.0);
    float shape  = core * 0.9 + sphere * 0.45;

    float life = clamp(vertexColor.r, 0.0, 1.0);
    float masterAlpha = vertexColor.a;

    // ── Burning-coal ramp: birth white-hot, death dark smoke ──────
    vec3 c0 = vec3(1.00, 0.96, 0.82);   // white-hot
    vec3 c1 = vec3(1.00, 0.62, 0.14);   // molten orange
    vec3 c2 = vec3(0.78, 0.16, 0.03);   // deep ember red
    vec3 c3 = vec3(0.10, 0.02, 0.01);   // cold smoke

    vec3 color = mix(c0, c1, smoothstep(0.0, 0.28, life));
    color = mix(color, c2, smoothstep(0.28, 0.62, life));
    color = mix(color, c3, smoothstep(0.62, 1.0, life));

    // ── Ember flicker: deterministic per-billboard phase + life wobble ──
    float phase = hash12(floor(uvCoord * 3.0) + vec2(life * 41.0, 7.0));
    float flick = 1.0 + sin(life * 26.0 + phase * 6.2831) * 0.22;

    // HDR heat at birth feeds the bloom chain; cools quadratically.
    float heat = 2.4 * (1.0 - life) * (1.0 - life) + 0.55;

    vec3 outc = color * shape * flick * heat;

    // Thin smoke halo as the ember dies (alpha only, no glow)
    float smoke = (1.0 - sphere) * smoothstep(0.55, 1.0, life) * 0.25;
    outc += vec3(0.06, 0.03, 0.02) * smoke;

    float alpha = (shape + smoke) * masterAlpha;

    fragColor = vec4(outc, alpha) * ColorModulator;

    if (fragColor.a < 0.002) {
        discard;
    }
}

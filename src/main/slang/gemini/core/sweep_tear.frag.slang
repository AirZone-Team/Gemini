// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  Sweeping Attack VFX — air tear (撕裂空气)
//
//  A ribbon laid along the path the blade already swept. The shader carves a
//  ragged fissure out of it: a lightless void interior, a white-hot lip and a
//  filament strung between the two edges. The cut pinches shut from the entry
//  end toward the tip while the lips keep boiling.
//
//  vUv.x = position along the rift, vUv.y = across it (0.5 = centre line)
//  vColor.r = per-slice variation, vColor.b = heal progress (0 open → 1 closed)
//  vColor.a = master alpha
//
//  Blend: TRANSLUCENT — the void really does knock the scene back to black,
//  which is what sells the cut as an absence rather than another glow layer.
//  Vertex stage is shared with core/sweep_arc.
// ═══════════════════════════════════════════════════════════════════
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform SweepUniforms {
    vec4 Params;      // time, sweep progress, effect progress, intensity
    vec4 Geometry;    // radius, thickness, vertical lift, glow
    vec4 Style;       // style, color mode, layer count, noise
    vec4 Motion;      // flow speed, echo spacing, ring thickness, ring count
    vec4 Primary;     // primary rgb, opacity
    vec4 Accent;      // accent rgb
    vec4 Core;        // core rgb
    vec4 Misc;        // ring scale, line length, line width, seed
};

in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float weight = 0.5;
    for (int i = 0; i < 3; i++) {
        value += weight * noise2(p);
        p *= 2.07;
        weight *= 0.5;
    }
    return value;
}

[shader("fragment")]

void main() {
    float u = vUv.x;
    float v = vUv.y - 0.5;
    float heal = vColor.b;
    float seed = Misc.w + vColor.r * 7.0;
    float boil = Params.x * (0.6 + abs(Motion.x) * 0.22);

    // ── Ragged half-height of the fissure, in v units ───────────────
    // Turbulence drives barb length; even a calm preset keeps a floor so
    // the rift never degenerates into a smooth band.
    float rough = 0.34 + 0.66 * clamp(Style.w, 0.0, 1.0);
    float body = fbm(vec2(u * 6.5 + seed, boil * 0.55));
    float barbs = pow(hash(vec2(floor(u * 54.0), floor(boil * 1.7) + seed)), 2.4);
    float lip = clamp(0.20 + (body - 0.5) * 0.30 * rough + barbs * 0.15 * rough,
            0.015, 0.47);

    // ── Healing: the pinch front travels entry → tip ────────────────
    float pinch = clamp((heal * 1.45 - u * 0.62) / 0.42, 0.0, 1.0);
    lip *= 1.0 - pinch;
    float sealed = 1.0 - smoothstep(0.88, 1.0, pinch);

    float distance = abs(v) / max(lip, 0.0015);
    float voidMask = 1.0 - smoothstep(0.72, 1.0, distance);
    float edge = exp(-abs(distance - 1.0) * 16.0);
    float filament = exp(-v * v / max(lip * lip * 0.03, 0.00006));
    float bright = clamp(edge * 1.05 + filament, 0.0, 1.0);

    // ── Light leaking through the gap, and the suction swirl ────────
    float swirl = fbm(vec2(u * 15.0 - boil * 1.3, v * 9.0 + seed));
    float leak = step(0.84, hash(vec2(floor(u * 44.0), floor(boil * 5.0) + seed)))
            * voidMask * (1.0 - pinch);
    vec3 voidColor = vec3(0.006, 0.008, 0.014)
            + mix(Accent.rgb, Core.rgb, 0.45) * (swirl * 0.09 + leak * 0.5);
    vec3 edgeColor = mix(Primary.rgb, Core.rgb, 0.62)
            * (1.0 + Geometry.w * (edge + filament) * 1.35);

    float ends = smoothstep(0.0, 0.055, u) * smoothstep(1.0, 0.88, u);
    float energy = max(voidMask * 0.94, bright);
    float alpha = energy * ends * sealed * vColor.a * Primary.a;
    if (alpha < 0.003) discard;

    fragColor = vec4(mix(voidColor, edgeColor, bright), clamp(alpha, 0.0, 1.0))
            * ColorModulator;
    if (fragColor.a < 0.003) discard;
}

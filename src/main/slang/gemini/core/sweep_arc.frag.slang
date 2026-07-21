// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
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

const float TAU = 6.28318530718;

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

vec3 spectrum(float t) {
    return 0.56 + 0.44 * cos(TAU * (t + vec3(0.0, 0.68, 0.35)));
}

vec3 palette(float u, float phase) {
    int mode = int(Style.y + 0.5);
    if (mode == 1) return mix(Primary.rgb, Accent.rgb, smoothstep(0.05, 0.95, u));
    if (mode == 2) return spectrum(u * 0.78 - Params.x * Motion.x * 0.08 + phase);
    if (mode == 3) {
        float pulse = 0.5 + 0.5 * sin(Params.x * Motion.x * 4.0 + u * TAU * 2.0);
        return mix(Primary.rgb, Accent.rgb, pulse);
    }
    return Primary.rgb;
}

float arcCenter(float u, int style) {
    if (style == 1) return 0.58 + 0.13 * sin(u * 3.14159265);
    if (style == 2) return 0.64 + 0.045 * sin(u * TAU * 4.0);
    if (style == 3) {
        float turbulence = noise2(vec2(u * 14.0 - Params.x * Motion.x, Misc.w));
        return 0.61 + (turbulence - 0.5) * 0.18 * Style.w;
    }
    if (style == 4) {
        float shard = hash(vec2(floor(u * 18.0), Misc.w)) - 0.5;
        return 0.64 + shard * 0.28 * max(0.35, Style.w);
    }
    return 0.76 - 0.20 * sin(u * 3.14159265);
}

vec4 arcMode() {
    float u = vUv.x;
    float v = vUv.y;
    int style = int(Style.x + 0.5);
    int layerCount = clamp(int(Style.z + 0.5), 1, 5);
    float center = arcCenter(u, style);
    float thickness = mix(0.018, 0.16, clamp(Geometry.y / 2.5, 0.0, 1.0));
    float energy = 0.0;
    float coreEnergy = 0.0;
    vec3 color = vec3(0.0);

    for (int i = 0; i < 5; i++) {
        if (i >= layerCount) break;
        float fi = float(i);
        float layerCenter = center - fi * thickness * 0.72;
        float layerWidth = thickness * (0.42 + fi * 0.36);
        float band = exp(-pow(abs(v - layerCenter) / max(layerWidth, 0.001), 1.45 + fi * 0.22));
        float layerStrength = 1.0 / (1.0 + fi * 0.62);
        vec3 layerColor = mix(palette(u, fi * 0.08), Accent.rgb, fi / 7.0);
        color += layerColor * band * layerStrength;
        energy += band * layerStrength;
        if (i == 0) coreEnergy = band;
    }

    if (style == 2) {
        float cells = step(0.52, hash(vec2(floor(u * 34.0), floor(v * 15.0) + Misc.w)));
        float runeRail = exp(-abs(v - center + thickness * 1.65) * 80.0);
        energy += runeRail * cells * 0.75;
        color += Accent.rgb * runeRail * cells * 1.4;
    } else if (style == 3) {
        float plasma = noise2(vec2(u * 24.0 - Params.x * Motion.x * 2.2,
                                  v * 11.0 + Misc.w));
        energy *= 0.62 + plasma * 0.9;
        color *= 0.72 + plasma * 1.15;
    } else if (style == 4) {
        float shardMask = smoothstep(0.3, 0.78,
                hash(vec2(floor(u * 23.0), floor(v * 13.0) + Misc.w)));
        energy *= 0.65 + shardMask;
        color += Core.rgb * shardMask * coreEnergy * 0.8;
    }

    float edge = smoothstep(0.0, 0.045, u) * smoothstep(0.0, 0.08, 1.0 - u);
    float traveling = 0.72 + 0.28 * sin(u * 34.0 - Params.x * Motion.x * 7.0);
    float noiseFlow = mix(1.0,
            0.58 + noise2(vec2(u * 19.0 - Params.x * Motion.x * 1.8, v * 8.0 + Misc.w)),
            clamp(Style.w, 0.0, 1.0));
    energy *= edge * traveling * noiseFlow;
    color *= edge * traveling * noiseFlow;
    color += Core.rgb * coreEnergy * coreEnergy * 1.8;
    color *= 1.0 + Geometry.w * (0.72 + coreEnergy * 1.8);

    float alpha = clamp(energy, 0.0, 1.0) * vColor.a * Primary.a;
    return vec4(color * vColor.r, alpha);
}

vec4 ringMode() {
    vec2 p = (vUv - 0.5) * 2.0;
    float distanceToCenter = length(p);
    float thickness = mix(0.018, 0.15, clamp(Motion.z, 0.0, 1.0));
    float ring = exp(-abs(distanceToCenter - 0.735) / max(thickness, 0.004));
    float outerGlow = exp(-abs(distanceToCenter - 0.735) * 7.0) * Geometry.w * 0.22;
    float spokes = pow(max(0.0, sin(atan(p.y, p.x) * 12.0
            - Params.x * Motion.x * 2.0)), 18.0) * exp(-abs(distanceToCenter - 0.58) * 8.0);
    vec3 color = palette(atan(p.y, p.x) / TAU + 0.5, vColor.r);
    float energy = ring + outerGlow + spokes * 0.35;
    color = color * energy + Core.rgb * ring * ring * 1.6;
    color *= 1.0 + Geometry.w * (ring + outerGlow);
    return vec4(color, clamp(energy, 0.0, 1.0) * vColor.a * Primary.a);
}

vec4 burstMode() {
    vec2 p = (vUv - 0.5) * 2.0;
    float distanceToCenter = length(p);
    float angle = atan(p.y, p.x);
    float raysA = pow(abs(cos(angle * 4.0)), 28.0);
    float raysB = pow(abs(cos(angle * 7.0 + 0.55)), 42.0) * 0.65;
    float rays = (raysA + raysB) * exp(-distanceToCenter * 2.6);
    float core = exp(-distanceToCenter * distanceToCenter * 18.0);
    float halo = exp(-abs(distanceToCenter - 0.32) * 13.0) * 0.32;
    float energy = (rays + core + halo) * (1.0 - smoothstep(0.82, 1.0, distanceToCenter));
    vec3 color = mix(Accent.rgb, Core.rgb, clamp(core * 1.6, 0.0, 1.0));
    color *= energy * (1.4 + Geometry.w * 1.8);
    return vec4(color, clamp(energy, 0.0, 1.0) * vColor.a * Primary.a);
}

[shader("fragment")]

void main() {
    vec4 result;
    if (vColor.g < 0.5) result = arcMode();
    else if (vColor.b < 0.5) result = ringMode();
    else result = burstMode();
    fragColor = result * ColorModulator;
    if (fragColor.a < 0.002) discard;
}

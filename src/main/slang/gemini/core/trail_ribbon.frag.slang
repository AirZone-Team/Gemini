// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

#define PI 3.14159265359

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise21(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0)), f.x), f.y);
}

float fbm(vec2 p, int octaves) {
    float result = 0.0;
    float amplitude = 0.52;
    for (int i = 0; i < 5; i++) {
        if (i >= octaves) break;
        result += noise21(p) * amplitude;
        p = mat2(1.6, -1.2, 1.2, 1.6) * p + 0.17;
        amplitude *= 0.48;
    }
    return result;
}

vec3 spectrum(float hue) {
    return 0.52 + 0.48 * cos(6.2831853 * (hue + vec3(0.0, 0.33, 0.67)));
}

float lineMask(float value, float width) {
    return 1.0 - smoothstep(width, width + 0.055, abs(value));
}

[shader("fragment")]

void main() {
    float time = ModelOffset.x;
    int quality = int(ModelOffset.y + 0.5);
    vec3 primary = ColorModulator.rgb;
    vec3 secondary = TextureMat[0].rgb;
    float glowAmount = TextureMat[0].a;
    vec3 accent = TextureMat[1].rgb;
    float coreWidth = TextureMat[1].a;
    int style = int(TextureMat[2].x + 0.5);
    int colorMode = int(TextureMat[2].y + 0.5);
    float flowSpeed = TextureMat[2].z;
    float detailScale = TextureMat[2].w;
    float distortion = TextureMat[3].x;
    float sparkleDensity = TextureMat[3].y;
    float edgeGlowAmount = TextureMat[3].z;
    float pulseStrength = TextureMat[3].w;

    float age = vColor.r;
    float motion = vColor.g;
    float brightness = vColor.b * 3.0;
    float opacity = vColor.a;
    if (opacity < 0.002) discard;

    float u = vUv.x;
    float v = vUv.y;
    float av = abs(v);
    float flowTime = time * flowSpeed;
    int octaves = quality <= 0 ? 2 : (quality == 1 ? 3 : (quality == 2 ? 4 : 5));

    vec2 flowUv = vec2(u * 7.0 * detailScale - flowTime * 1.8,
                       v * 2.8 * detailScale);
    float broadNoise = fbm(flowUv, octaves);
    float warp = (broadNoise - 0.5) * distortion;
    float pattern = 0.0;
    float auraPattern = 0.0;

    if (style == 1) {
        // Arcane: braided energy and drifting rune-like cuts.
        float braidA = sin((u * 17.0 - flowTime * 4.0) + v * 5.5 + warp * 5.0);
        float braidB = sin((u * 13.0 - flowTime * 3.0) - v * 6.5 - warp * 4.0);
        float braid = pow(max(0.0, braidA * braidB), 2.0);
        float runeCell = step(0.72, hash21(vec2(floor(u * 28.0 - flowTime * 2.0), floor(v * 4.0))));
        float runeStroke = lineMask(fract(u * 14.0 - flowTime) - 0.5, 0.08) * runeCell;
        pattern = 0.25 + braid * 0.85 + runeStroke * 1.2;
        auraPattern = broadNoise;
    } else if (style == 2) {
        // Cyber: segmented data blocks and a sharp scanning core.
        vec2 grid = abs(fract(vec2(u * 32.0 - flowTime * 5.0, v * 5.0)) - 0.5);
        float data = step(0.27, hash21(vec2(floor(u * 32.0 - flowTime * 5.0), floor(v * 5.0))));
        float blocks = (1.0 - smoothstep(0.32, 0.48, max(grid.x, grid.y))) * data;
        float scan = pow(0.5 + 0.5 * sin(u * 80.0 - flowTime * 12.0), 10.0);
        pattern = 0.18 + blocks * 1.2 + scan * 0.8;
        auraPattern = blocks;
    } else if (style == 3) {
        // Inferno: turbulent tongues that become finer toward the edges.
        float flame = fbm(vec2(u * 5.5 - flowTime * 3.0, v * 4.0 + broadNoise * 2.0), octaves);
        float tongues = smoothstep(0.26 + av * 0.24, 0.9, flame + (1.0 - av) * 0.34);
        float embers = pow(hash21(floor(vec2(u * 90.0 - flowTime * 13.0, v * 14.0))), 18.0);
        pattern = 0.2 + tongues * 1.45 + embers;
        auraPattern = flame;
    } else if (style == 4) {
        // Void: dark body, luminous event-horizon edges and sparse stars.
        float voidCloud = fbm(flowUv * 0.55 + broadNoise, octaves);
        float stars = pow(hash21(floor(vec2(u * 105.0 - flowTime * 2.0, v * 17.0))), 24.0);
        pattern = 0.08 + voidCloud * 0.28 + stars * 2.2;
        auraPattern = stars + voidCloud * 0.25;
    } else {
        // Aurora: layered silk bands with soft interference.
        float bandA = 0.5 + 0.5 * sin(u * 18.0 - flowTime * 3.0 + v * 5.0 + warp * 8.0);
        float bandB = 0.5 + 0.5 * sin(u * 31.0 - flowTime * 5.0 - v * 3.0);
        pattern = 0.22 + pow(bandA, 2.2) * 0.78 + pow(bandB, 5.0) * 0.45;
        auraPattern = broadNoise;
    }

    float pulse = 0.72 + 0.28 * sin(flowTime * 4.0 - u * 12.0);
    vec3 color;
    if (colorMode == 1) {
        color = spectrum(time * 0.09 * flowSpeed + u * 0.95 + broadNoise * 0.16);
    } else if (colorMode == 2) {
        float colorPulse = 0.5 + 0.5 * sin(flowTime * 4.5 - u * 10.0);
        color = mix(primary, secondary, colorPulse);
    } else if (colorMode == 3) {
        color = primary;
    } else {
        color = mix(primary, secondary, smoothstep(0.05, 0.95, u + warp * 0.18));
    }

    float core = exp(-av / max(0.025, coreWidth)) * pattern;
    float softAura = exp(-av * mix(1.4, 4.5, clamp(glowAmount / 2.5, 0.0, 1.0)));
    float edge = exp(-abs(av - 0.82) * 13.0) * edgeGlowAmount;
    float sparkleGate = pow(hash21(floor(vec2(u * 120.0 - flowTime * 9.0, v * 18.0))), 28.0);
    float sparkle = sparkleGate * sparkleDensity * (0.4 + 0.6 * motion);

    float ageFade = 1.0 - smoothstep(0.38, 1.0, age);
    float tailFade = 1.0 - smoothstep(0.78, 1.0, u);
    float headFade = smoothstep(0.0, 0.025, u + 0.012);
    float fade = ageFade * tailFade * headFade;

    float pulseGain = mix(1.0, max(0.05, pulse), clamp(pulseStrength, 0.0, 1.5));
    vec3 body = color * (core * 1.35 + softAura * (0.12 + auraPattern * 0.18) * glowAmount);
    vec3 ornament = accent * (edge * 0.75 + sparkle * 1.8);
    if (style == 4) body *= 0.42;

    vec3 hdrColor = (body * pulseGain + ornament) * brightness;
    hdrColor *= 1.0 + glowAmount * 0.35 + motion * 0.45;
    hdrColor *= fade;

    float alphaMask = clamp((core + softAura * 0.42 + edge * 0.4 + sparkle) * fade, 0.0, 1.0);
    fragColor = vec4(hdrColor, opacity * alphaMask);
}

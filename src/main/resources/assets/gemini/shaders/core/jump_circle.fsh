#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 uvCoord;
flat in ivec2 styleWords;
flat in ivec2 materialWords;
out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

uint unpackWords(ivec2 words) {
    uint lowWord = uint(words.x & 65535);
    uint highWord = uint(words.y & 65535);
    return lowWord | (highWord << 16u);
}

float hash11(float p) {
    return fract(sin(p * 127.1) * 43758.5453123);
}

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
        mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

float fbm(vec2 p) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += valueNoise(p) * amplitude;
        p = p * 2.07 + 3.17;
        amplitude *= 0.5;
    }
    return sum;
}

float ringMask(float distanceValue, float center, float halfWidth, float softness) {
    return 1.0 - smoothstep(halfWidth, halfWidth + softness, abs(distanceValue - center));
}

float easeMotion(float t, int mode) {
    if (mode == 0) return t;
    if (mode == 2) {
        if (t <= 0.0 || t >= 1.0) return t;
        return clamp(pow(2.0, -10.0 * t) * sin((t * 10.0 - 0.75) * TAU / 3.0) + 1.0, 0.0, 1.0);
    }
    float outCubic = 1.0 - pow(1.0 - t, 3.0);
    if (mode == 3) {
        outCubic += sin(t * PI * 3.0) * (1.0 - t) * 0.08;
    }
    return clamp(outCubic, 0.0, 1.0);
}

vec3 flowColor(vec3 base, int mode, float theta, float distanceValue, float life) {
    if (mode == 1) {
        vec3 shifted = mix(base.brg, vec3(1.0), 0.12);
        return mix(base, shifted, 0.22 + 0.28 * sin(theta * 2.0 + life * 4.0));
    }
    if (mode == 2) {
        float hue = theta / TAU + life * 0.24 + distanceValue * 0.12;
        return 0.58 + 0.42 * cos(TAU * (hue + vec3(0.00, 0.33, 0.67)));
    }
    if (mode == 3) {
        float pulse = 0.5 + 0.5 * sin(life * 19.0 - distanceValue * 14.0);
        return mix(base * 0.78, mix(base, vec3(1.0), 0.52), pulse);
    }
    return base;
}

void main() {
    uint styleBits = unpackWords(styleWords);
    uint materialBits = unpackWords(materialWords);
    vec2 p = uvCoord * 2.0 - 1.0;
    float distanceValue = length(p);
    float theta = atan(p.y, p.x);
    float theta01 = fract(theta / TAU + 1.0);

    int style = int(styleBits & 7u);
    int colorMode = int((styleBits >> 3u) & 3u);
    int layerCount = int((styleBits >> 5u) & 3u) + 1;
    int ringCount = int((styleBits >> 7u) & 3u) + 1;
    int quality = int((styleBits >> 9u) & 3u);
    int runeDetail = int((styleBits >> 11u) & 3u);
    int particleDensity = int((styleBits >> 13u) & 3u);
    bool spikesEnabled = ((styleBits >> 15u) & 1u) != 0u;
    int eventType = int((styleBits >> 16u) & 3u);
    bool accentLayer = ((styleBits >> 18u) & 1u) != 0u;
    bool shockwaveEnabled = ((styleBits >> 19u) & 1u) != 0u;
    int easing = int((styleBits >> 20u) & 3u);

    float thickness = mix(0.15, 2.5, float(materialBits & 15u) / 15.0);
    float glow = mix(0.0, 2.5, float((materialBits >> 4u) & 15u) / 15.0);
    float distortion = mix(0.0, 1.5, float((materialBits >> 8u) & 15u) / 15.0);
    float spin = mix(-3.0, 3.0, float((materialBits >> 12u) & 15u) / 15.0);
    float opacity = mix(0.05, 1.0, float((materialBits >> 16u) & 7u) / 7.0);
    float brightness = mix(0.25, 2.5, float((materialBits >> 19u) & 7u) / 7.0);

    float life = clamp(vertexColor.a, 0.0, 1.0);
    float motion = easeMotion(life, easing);
    float fade = smoothstep(0.0, 0.025, life) * (1.0 - smoothstep(0.58, 1.0, life));
    float impactFlash = 1.0 - smoothstep(0.0, 0.13, life);
    float front = mix(0.12, 0.79, motion);
    float width = (0.018 + thickness * 0.035) * (accentLayer ? 0.48 : 1.0);
    float softness = mix(0.006, 0.018, 1.0 - float(quality) / 3.0);

    float noiseField = fbm(p * mix(3.0, 8.0, distortion / 1.5) + vec2(life * spin * 2.0));
    float warpedDistance = distanceValue + (noiseField - 0.5) * distortion * 0.055 * (0.4 + life);
    float rotation = theta + life * spin * TAU * 0.42;
    float angular = fract(rotation / TAU + 1.0);

    float styleGate = 1.0;
    if (style == 0) {
        float arcCell = fract(angular * 12.0);
        styleGate = 0.55 + 0.45 * smoothstep(0.05, 0.16, arcCell) * (1.0 - smoothstep(0.82, 0.96, arcCell));
    } else if (style == 1) {
        float segment = fract(angular * 24.0 + floor(distanceValue * 24.0) * 0.17);
        styleGate = step(0.18, segment) * (1.0 - step(0.83, segment));
    } else if (style == 2) {
        styleGate = 0.78 + 0.22 * sin(rotation * 5.0 + life * 7.0);
    } else if (style == 3) {
        styleGate = 0.55 + 0.70 * noiseField;
    } else {
        float broken = hash11(floor(angular * 30.0) + floor(life * 9.0) * 0.07);
        styleGate = smoothstep(0.26, 0.62, broken) * (0.72 + noiseField * 0.4);
    }

    float core = 0.0;
    float halo = 0.0;
    for (int layer = 0; layer < 4; layer++) {
        if (layer >= layerCount) break;
        float layerF = float(layer);
        float trailCenter = front - layerF * (0.035 + life * 0.018);
        float layerWidth = width * mix(1.0, 0.42, layerF / 3.0);
        float layerAlpha = 1.0 - layerF / (float(layerCount) + 0.65);
        float layerRing = ringMask(warpedDistance, trailCenter, layerWidth, softness);
        core += layerRing * layerAlpha * styleGate;
        halo += ringMask(warpedDistance, trailCenter, layerWidth * (2.2 + glow), 0.04) * layerAlpha;
    }

    for (int ringIndex = 1; ringIndex < 4; ringIndex++) {
        if (ringIndex >= ringCount) break;
        float indexF = float(ringIndex);
        float satelliteCenter = max(0.08, front * (1.0 - indexF * 0.17));
        float satellite = ringMask(warpedDistance, satelliteCenter, width * 0.48, softness);
        float dash = style == 2 ? 1.0 : step(0.20, fract(angular * (8.0 + indexF * 5.0)));
        core += satellite * dash * (0.52 / indexF);
        halo += satellite * 0.25;
    }

    float ornaments = 0.0;
    if (runeDetail > 0) {
        float runeCount = 12.0 + float(runeDetail) * 8.0;
        float runeCell = fract(angular * runeCount + life * spin * 0.17);
        float runeBand = ringMask(distanceValue, max(0.16, front - 0.105), 0.018 + float(runeDetail) * 0.004, 0.008);
        float glyph = step(0.14, runeCell) * (1.0 - step(0.32 + hash11(floor(angular * runeCount)) * 0.42, runeCell));
        float crossBar = ringMask(distanceValue, max(0.12, front - 0.145), 0.008, 0.006)
                       * step(0.46, runeCell) * (1.0 - step(0.70, runeCell));
        ornaments += (runeBand * glyph + crossBar) * (accentLayer ? 1.35 : 0.72);
    }

    if (spikesEnabled) {
        float spikeCount = style == 1 ? 20.0 : (style == 3 ? 28.0 : 16.0);
        float spikeCell = abs(fract(angular * spikeCount) - 0.5) * 2.0;
        float spikeLength = pow(1.0 - spikeCell, 3.0) * (0.08 + 0.10 * impactFlash);
        float outer = front + spikeLength;
        float spikeBody = smoothstep(front - width, front, warpedDistance)
                        * (1.0 - smoothstep(outer, outer + 0.012, warpedDistance));
        ornaments += spikeBody * (accentLayer ? 1.0 : 0.48);
    }

    float particles = 0.0;
    int particleLimit = min(12, particleDensity * 3 + quality);
    for (int i = 0; i < 12; i++) {
        if (i >= particleLimit) break;
        float fi = float(i);
        float seed = hash11(fi * 7.13 + float(eventType) * 13.7);
        float particleAngle = seed * TAU + life * spin * (0.55 + hash11(fi + 8.0));
        float particleRadius = mix(0.16, 0.92, hash11(fi * 19.4 + 2.0)) * front + life * 0.09;
        vec2 particlePos = vec2(cos(particleAngle), sin(particleAngle)) * particleRadius;
        float size = mix(0.009, 0.026, hash11(fi * 4.71)) * (1.15 - life * 0.35);
        float star = 1.0 - smoothstep(size, size * 2.7, length(p - particlePos));
        particles += star * (0.55 + 0.45 * sin(life * 24.0 + fi * 2.1));
    }

    float shock = 0.0;
    if (shockwaveEnabled) {
        shock = ringMask(distanceValue, min(0.98, front * 1.18 + 0.04), 0.010 + impactFlash * 0.014, 0.022);
        shock *= (0.32 + impactFlash * 0.78) * (accentLayer ? 1.2 : 0.7);
    }

    float impactDisk = (1.0 - smoothstep(0.0, 0.34 + life * 0.16, distanceValue))
                     * impactFlash * (eventType == 2 ? 0.60 : 0.34);
    float intensity = core + ornaments + particles + shock + impactDisk;
    float glowField = halo * glow * 0.24 + particles * glow * 0.32;
    float alpha = clamp((intensity + glowField) * fade * opacity, 0.0, 1.0);

    vec3 color = flowColor(vertexColor.rgb, colorMode, theta, distanceValue, life);
    float whiteFlash = impactFlash * (eventType == 2 ? 0.72 : 0.45);
    color = mix(color, vec3(1.0), whiteFlash);
    vec3 rgb = color * (intensity * brightness + glowField * (0.75 + brightness * 0.35));

    if (style == 4 && !accentLayer) {
        rgb *= 0.66;
        rgb += color * glowField * 0.35;
    }

    fragColor = vec4(rgb, alpha) * ColorModulator;
    if (fragColor.a < 0.001) discard;
}

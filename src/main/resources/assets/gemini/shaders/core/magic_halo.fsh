#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform HaloUniforms {
    vec4 HaloMeta;       // time, style, color mode, feature flags
    vec4 HaloPrimary;    // custom primary rgb, opacity
    vec4 HaloSecondary;  // custom secondary rgb, brightness
    vec4 HaloAccent;     // custom accent rgb, glow
    vec4 HaloGeometry;   // radius, thickness, spike length, spike count
    vec4 HaloDetail;     // layers, rune detail, star density, sharpness
    vec4 HaloMotion;     // rotation, pulse, distortion, rainbow speed
    vec4 HaloReserved;
};

in vec2 uvCoord;
out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

float hash11(float value) {
    return fract(sin(value * 127.17) * 43758.5453);
}

float hash21(vec2 value) {
    return fract(sin(dot(value, vec2(127.1, 311.7))) * 43758.5453);
}

float valueNoise(vec2 value) {
    vec2 cell = floor(value);
    vec2 local = fract(value);
    local = local * local * (3.0 - 2.0 * local);
    return mix(
        mix(hash21(cell), hash21(cell + vec2(1.0, 0.0)), local.x),
        mix(hash21(cell + vec2(0.0, 1.0)), hash21(cell + vec2(1.0, 1.0)), local.x),
        local.y
    );
}

float fbm(vec2 value) {
    float result = 0.0;
    float weight = 0.5;
    for (int i = 0; i < 4; i++) {
        result += valueNoise(value) * weight;
        value = value * 2.03 + 7.13;
        weight *= 0.5;
    }
    return result;
}

vec2 rotate2d(vec2 value, float angle) {
    float sineValue = sin(angle);
    float cosineValue = cos(angle);
    return mat2(cosineValue, -sineValue, sineValue, cosineValue) * value;
}

float band(float distanceValue, float radius, float halfWidth, float softness) {
    return 1.0 - smoothstep(halfWidth, halfWidth + softness, abs(distanceValue - radius));
}

float disc(vec2 point, float radius, float softness) {
    return 1.0 - smoothstep(radius, radius + softness, length(point));
}

float angularCell(float angle, float count) {
    float width = TAU / max(count, 1.0);
    return mod(angle + width * 0.5, width) - width * 0.5;
}

float dashedArc(float angle, float count, float fill, float phase, float softness) {
    float wave = cos(angle * count + phase);
    return smoothstep(cos(PI * fill) - softness, cos(PI * fill) + softness, wave);
}

bool hasFeature(int featureBit) {
    int flags = int(HaloMeta.w + 0.5);
    return (flags & featureBit) != 0;
}

void stylePalette(int styleId, out vec3 primary, out vec3 secondary, out vec3 accent) {
    if (styleId == 1) {
        primary = vec3(0.56, 0.27, 1.00);
        secondary = vec3(0.12, 0.88, 1.00);
        accent = vec3(1.00, 0.80, 0.34);
    } else if (styleId == 2) {
        primary = vec3(0.08, 0.92, 1.00);
        secondary = vec3(1.00, 0.12, 0.64);
        accent = vec3(0.88, 1.00, 1.00);
    } else if (styleId == 3) {
        primary = vec3(0.31, 0.08, 0.76);
        secondary = vec3(0.94, 0.04, 0.30);
        accent = vec3(0.82, 0.68, 1.00);
    } else if (styleId == 4) {
        primary = vec3(1.00, 0.28, 0.015);
        secondary = vec3(1.00, 0.72, 0.06);
        accent = vec3(1.00, 0.96, 0.62);
    } else if (styleId == 5) {
        primary = vec3(0.22, 0.72, 1.00);
        secondary = vec3(0.56, 0.96, 1.00);
        accent = vec3(0.96, 1.00, 1.00);
    } else if (styleId == 6) {
        primary = vec3(1.00, 0.22, 0.68);
        secondary = vec3(0.18, 0.82, 1.00);
        accent = vec3(1.00, 0.88, 0.30);
    } else {
        primary = vec3(1.00, 0.66, 0.18);
        secondary = vec3(1.00, 0.28, 0.70);
        accent = vec3(1.00, 0.98, 0.82);
    }
}

vec3 spectralColor(float phase) {
    return 0.58 + 0.42 * cos(TAU * (phase + vec3(0.00, 0.33, 0.67)));
}

void resolvePalette(int styleId, int colorMode, float angle,
                    out vec3 primary, out vec3 secondary, out vec3 accent) {
    stylePalette(styleId, primary, secondary, accent);
    if (colorMode == 1) {
        primary = HaloPrimary.rgb;
        secondary = HaloSecondary.rgb;
        accent = HaloAccent.rgb;
    } else if (colorMode == 2) {
        float phase = angle / TAU + HaloMeta.x * HaloMotion.w * 0.16;
        primary = spectralColor(phase);
        secondary = spectralColor(phase + 0.18);
        accent = spectralColor(phase + 0.42);
    }
}

float radialSpike(float distanceValue, float angle, float baseRadius,
                  float lengthValue, float count, float widthScale,
                  float variation, float softness) {
    float cellWidth = TAU / max(count, 1.0);
    float localAngle = angularCell(angle, count);
    float cellIndex = floor((angle + PI) / cellWidth);
    float alternating = mix(1.0, 0.58, mod(cellIndex, 2.0));
    float randomScale = mix(1.0, 0.62 + 0.52 * hash11(cellIndex + 11.0), variation);
    float tipRadius = baseRadius + lengthValue * alternating * randomScale;
    float progress = saturate((distanceValue - baseRadius) / max(tipRadius - baseRadius, 0.001));
    float halfWidth = cellWidth * widthScale * pow(1.0 - progress, 1.25) + 0.002;
    float angularMask = 1.0 - smoothstep(halfWidth, halfWidth + softness, abs(localAngle));
    float radialMask = smoothstep(baseRadius - softness, baseRadius + softness, distanceValue)
                     * (1.0 - smoothstep(tipRadius - softness * 2.0, tipRadius + softness, distanceValue));
    return angularMask * radialMask;
}

float directionalSpike(vec2 point, float centerAngle, float baseRadius,
                       float tipRadius, float width, float softness) {
    float distanceValue = length(point);
    float angle = atan(point.y, point.x);
    float delta = abs(mod(angle - centerAngle + PI, TAU) - PI);
    float progress = saturate((distanceValue - baseRadius) / max(tipRadius - baseRadius, 0.001));
    float taperedWidth = mix(width, 0.003, pow(progress, 0.72));
    float angularMask = 1.0 - smoothstep(taperedWidth, taperedWidth + softness, delta);
    float radialMask = smoothstep(baseRadius - softness, baseRadius + softness, distanceValue)
                     * (1.0 - smoothstep(tipRadius - softness, tipRadius + softness, distanceValue));
    return angularMask * radialMask;
}

float layeredRings(float distanceValue, float angle, float radius,
                   float thickness, int layers, int styleId, float softness, float time) {
    float result = band(distanceValue, radius, thickness, softness);
    for (int i = 1; i < 5; i++) {
        if (i >= layers) break;
        float layer = float(i);
        float layerRadius = radius - thickness * (1.9 + layer * 1.48);
        float ring = band(distanceValue, layerRadius, thickness * (0.42 + 0.07 * layer), softness);
        float segmentation = 1.0;
        if (styleId == 1) {
            segmentation = dashedArc(angle, 8.0 + layer * 5.0, 0.69, time * (0.7 + layer * 0.15), 0.06);
        } else if (styleId == 2) {
            segmentation = dashedArc(angle, 16.0 + layer * 8.0, 0.38, -time * 1.3, 0.03);
        } else if (styleId == 3) {
            segmentation = 0.45 + 0.55 * smoothstep(0.12, 0.85,
                hash11(floor((angle + PI) * (5.0 + layer))));
        } else if (styleId == 4) {
            segmentation = 0.68 + 0.32 * sin(angle * (5.0 + layer * 2.0) - time * 2.0);
        } else if (styleId == 5) {
            segmentation = dashedArc(angle, 6.0 + layer * 6.0, 0.72, layer * PI * 0.3, 0.06);
        } else if (styleId == 6) {
            segmentation = 0.55 + 0.45 * sin(angle * (3.0 + layer * 2.0) + time);
        }
        result = max(result, ring * saturate(segmentation));
    }
    return result;
}

float styleOrnament(vec2 point, float distanceValue, float angle, int styleId,
                    float radius, float thickness, float spikeLength,
                    float spikeCount, float softness, float time) {
    float ornament = 0.0;

    if (styleId == 0) {
        float rays = radialSpike(distanceValue, angle, radius + thickness * 0.4,
            spikeLength, spikeCount, 0.25, 0.18, softness);
        float fineRays = radialSpike(distanceValue, angle + PI / spikeCount,
            radius, spikeLength * 0.58, spikeCount, 0.12, 0.0, softness);
        ornament = max(rays, fineRays * 0.78);
    } else if (styleId == 1) {
        float arcaneRays = radialSpike(distanceValue, angle + time * 0.07,
            radius, spikeLength * 0.72, spikeCount, 0.18, 0.35, softness);
        float sigil = band(distanceValue, radius + spikeLength * 0.44,
            thickness * 0.12, softness) * dashedArc(angle, spikeCount * 0.5, 0.24, -time, 0.04);
        ornament = max(arcaneRays, sigil);
    } else if (styleId == 2) {
        float railRadius = radius + spikeLength * 0.32;
        float rails = band(distanceValue, railRadius, thickness * 0.26, softness)
                    * dashedArc(angle, spikeCount, 0.33, time * 1.8, 0.025);
        float ticks = radialSpike(distanceValue, angle, radius,
            spikeLength * 0.46, spikeCount, 0.32, 0.0, softness);
        ornament = max(rails, ticks);
    } else if (styleId == 3) {
        float jagged = radialSpike(distanceValue, angle + sin(time * 0.3) * 0.15,
            radius - thickness * 0.2, spikeLength * 1.08, spikeCount, 0.20, 1.0, softness);
        float fractures = band(distanceValue, radius + spikeLength * 0.48,
            thickness * 0.11, softness) * dashedArc(angle, spikeCount * 0.72, 0.20, time * 2.3, 0.02);
        ornament = max(jagged, fractures);
    } else if (styleId == 4) {
        float flameWave = sin(angle * spikeCount - time * 3.0) * 0.5 + 0.5;
        float flameTip = radius + spikeLength * (0.38 + 0.62 * pow(flameWave, 2.0));
        float flameBody = smoothstep(radius - softness, radius + softness, distanceValue)
                        * (1.0 - smoothstep(flameTip - thickness, flameTip + softness, distanceValue));
        float flameCuts = dashedArc(angle, spikeCount, 0.43, -time * 1.6, 0.04);
        ornament = flameBody * flameCuts;
    } else if (styleId == 5) {
        float crystal = radialSpike(distanceValue, angle, radius,
            spikeLength, max(6.0, floor(spikeCount / 6.0) * 6.0), 0.19, 0.0, softness);
        float branches = radialSpike(distanceValue, angle + PI / 6.0,
            radius + spikeLength * 0.26, spikeLength * 0.43,
            max(12.0, floor(spikeCount / 3.0) * 3.0), 0.11, 0.0, softness);
        ornament = max(crystal, branches * 0.82);
    } else {
        float petals = abs(sin(angle * max(3.0, spikeCount * 0.25) + time * 0.35));
        float petalRadius = radius + spikeLength * (0.28 + 0.72 * pow(petals, 1.8));
        float petalBand = band(distanceValue, petalRadius, thickness * 0.30, softness);
        float prismTicks = radialSpike(distanceValue, angle - time * 0.12,
            radius, spikeLength * 0.55, spikeCount, 0.14, 0.15, softness);
        ornament = max(petalBand, prismTicks * 0.72);
    }
    return ornament;
}

float runeField(float distanceValue, float angle, float radius, float thickness,
                int runeDetail, int styleId, float softness, float time) {
    if (!hasFeature(2) || runeDetail <= 0) return 0.0;
    float runeRadius = radius - thickness * (3.4 + float(runeDetail) * 0.35);
    float count = 10.0 + float(runeDetail) * 8.0;
    float direction = styleId == 3 ? -1.0 : 1.0;
    float track = band(distanceValue, runeRadius, thickness * 0.15, softness);
    float glyphs = dashedArc(angle, count, 0.30, time * direction * 1.7, 0.04);
    float punctuation = band(distanceValue, runeRadius - thickness * 0.85,
        thickness * 0.09, softness) * dashedArc(angle, count * 0.5, 0.12, -time * 1.1, 0.03);
    return max(track * glyphs, punctuation);
}

float crownField(vec2 point, float radius, float thickness,
                 float spikeLength, int styleId, float softness, float time) {
    if (!hasFeature(1)) return 0.0;
    float crownLength = spikeLength * (styleId == 0 ? 1.30 : 1.05);
    float base = radius - thickness * 0.25;
    float center = directionalSpike(point, PI * 0.5, base,
        radius + crownLength, 0.105, softness);
    float left = directionalSpike(point, PI * 0.5 + 0.31, base,
        radius + crownLength * 0.74, 0.076, softness);
    float right = directionalSpike(point, PI * 0.5 - 0.31, base,
        radius + crownLength * 0.74, 0.076, softness);
    float jewelPosition = radius + crownLength * (0.42 + sin(time * 2.0) * 0.015);
    vec2 jewelCenter = vec2(0.0, jewelPosition);
    float jewel = disc(point - jewelCenter, thickness * 0.48, softness);
    return max(max(center, max(left, right)), jewel);
}

float orbitalField(vec2 point, float radius, float spikeLength,
                   int starDensity, float softness, float time) {
    if (!hasFeature(4)) return 0.0;
    int satelliteCount = 2 + starDensity * 2;
    float result = 0.0;
    for (int i = 0; i < 8; i++) {
        if (i >= satelliteCount) break;
        float seed = float(i);
        float orbitAngle = time * (0.58 + seed * 0.075) + seed * TAU / float(satelliteCount);
        float orbitRadius = radius + spikeLength * (0.34 + 0.09 * mod(seed, 3.0));
        vec2 center = vec2(cos(orbitAngle), sin(orbitAngle)) * orbitRadius;
        float size = 0.010 + 0.008 * hash11(seed + 3.0);
        float core = disc(point - center, size, softness);
        vec2 trailCenter = vec2(cos(orbitAngle - 0.05), sin(orbitAngle - 0.05)) * orbitRadius;
        float trail = disc(point - trailCenter, size * 1.7, softness * 2.0) * 0.28;
        result += core + trail;
    }
    return saturate(result);
}

float starField(vec2 point, float distanceValue, float radius,
                int density, float softness, float time) {
    if (!hasFeature(8) || density <= 0) return 0.0;
    float gridScale = 11.0 + float(density) * 5.0;
    vec2 grid = point * gridScale;
    vec2 cell = floor(grid);
    vec2 local = fract(grid) - 0.5;
    vec2 randomOffset = vec2(hash21(cell), hash21(cell + 19.3)) - 0.5;
    float randomValue = hash21(cell + 7.7);
    float presence = step(0.78 - float(density) * 0.09, randomValue);
    float region = smoothstep(radius * 0.55, radius * 0.95, distanceValue)
                 * (1.0 - smoothstep(radius + 0.45, radius + 0.62, distanceValue));
    float flicker = 0.45 + 0.55 * sin(time * (3.0 + randomValue * 5.0) + randomValue * 20.0);
    float star = 1.0 - smoothstep(0.035, 0.115 + softness, length(local - randomOffset * 0.55));
    return star * presence * region * flicker;
}

void main() {
    float time = HaloMeta.x;
    int styleId = clamp(int(HaloMeta.y + 0.5), 0, 6);
    int colorMode = clamp(int(HaloMeta.z + 0.5), 0, 2);
    float opacity = HaloPrimary.a;
    float brightness = HaloSecondary.a;
    float glowStrength = HaloAccent.a;

    if (opacity < 0.001 || brightness < 0.001) {
        discard;
        return;
    }

    float radius = HaloGeometry.x;
    float thickness = HaloGeometry.y;
    float spikeLength = HaloGeometry.z;
    float spikeCount = max(4.0, floor(HaloGeometry.w + 0.5));
    int layers = clamp(int(HaloDetail.x + 0.5), 1, 5);
    int runeDetail = clamp(int(HaloDetail.y + 0.5), 0, 3);
    int starDensity = clamp(int(HaloDetail.z + 0.5), 0, 3);
    float sharpness = saturate(HaloDetail.w);
    float pulseAmount = HaloMotion.y;
    float distortion = HaloMotion.z;

    vec2 point = (uvCoord - 0.5) * 2.0;
    point = rotate2d(point, time * HaloMotion.x * 0.22);

    float rawDistance = length(point);
    float rawAngle = atan(point.y, point.x);
    float warp = (fbm(vec2(rawAngle * 2.8, rawDistance * 6.0 - time * 0.28)) - 0.5)
               * distortion * 0.065;
    float styleWarp = styleId == 3 ? sin(rawAngle * 7.0 - time * 1.4) * distortion * 0.035 : 0.0;
    float distanceValue = rawDistance + warp + styleWarp;
    float angle = rawAngle + sin(rawDistance * 11.0 - time) * distortion * 0.016;

    float pulseWave = sin(time * 3.0) * 0.5 + 0.5;
    float pulsedRadius = radius * (1.0 + (pulseWave - 0.5) * pulseAmount * 0.055);
    float softness = mix(0.018, 0.0025, sharpness);

    float rings = layeredRings(distanceValue, angle, pulsedRadius,
        thickness, layers, styleId, softness, time);
    float ornaments = styleOrnament(point, distanceValue, angle, styleId,
        pulsedRadius, thickness, spikeLength, spikeCount, softness, time);
    float runes = runeField(distanceValue, angle, pulsedRadius,
        thickness, runeDetail, styleId, softness, time);
    float crown = crownField(point, pulsedRadius, thickness,
        spikeLength, styleId, softness, time);
    float orbitals = orbitalField(point, pulsedRadius, spikeLength,
        starDensity, softness, time);
    float stars = starField(point, rawDistance, pulsedRadius,
        starDensity, softness, time);

    float structure = max(max(rings, ornaments), max(runes, crown));
    float details = max(orbitals, stars * 0.82);

    float ringDistance = abs(distanceValue - pulsedRadius);
    float tightGlow = exp(-ringDistance * ringDistance / max(thickness * thickness * 5.0, 0.0002));
    float innerAura = exp(-abs(distanceValue - pulsedRadius) * (7.0 - glowStrength * 0.6));
    float outerAura = exp(-max(distanceValue - pulsedRadius, 0.0) * (3.6 - glowStrength * 0.45))
                    * smoothstep(pulsedRadius * 0.45, pulsedRadius, distanceValue);
    float rayAura = ornaments * 0.34 + exp(-abs(distanceValue - (pulsedRadius + spikeLength * 0.32)) * 9.0)
                  * dashedArc(angle, spikeCount, 0.34, time, 0.12) * 0.16;
    float glow = (tightGlow * 0.34 + innerAura * 0.16 + outerAura * 0.055 + rayAura)
               * glowStrength;

    float shimmer = 0.78 + 0.22 * sin(angle * (6.0 + float(styleId)) - time * 2.0)
                  + (fbm(point * 8.0 + time * 0.12) - 0.5) * distortion * 0.25;
    float energy = saturate(structure * shimmer);
    float alpha = saturate(energy * 0.92 + details + glow * 0.36) * opacity;

    vec3 primary;
    vec3 secondary;
    vec3 accent;
    resolvePalette(styleId, colorMode, angle, primary, secondary, accent);

    float gradient = sin(angle * (styleId == 6 ? 3.0 : 1.5) - time * 0.55) * 0.5 + 0.5;
    gradient = saturate(gradient * 0.72 + distanceValue * 0.42);
    vec3 baseColor = mix(primary, secondary, gradient);
    vec3 coreColor = mix(baseColor, accent, saturate(tightGlow * 0.72 + details));
    vec3 color = baseColor * (energy + glow * 0.48)
               + coreColor * (tightGlow * 0.52 + details * 1.45)
               + accent * (crown * 0.32 + orbitals * 1.7 + stars * 0.7);

    float whiteCore = pow(saturate(structure), mix(2.8, 0.82, sharpness));
    color += accent * whiteCore * (0.22 + glowStrength * 0.10);
    color *= brightness * (0.92 + pulseWave * pulseAmount * 0.16);

    // A restrained HDR lift keeps bloom-compatible highlights without
    // flattening the user-selected palette.
    color *= 1.0 + tightGlow * glowStrength * 0.24 + orbitals * 0.5;

    fragColor = vec4(color, alpha) * ColorModulator;
    if (fragColor.a < 0.001) discard;
}

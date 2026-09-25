// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
//
// Sorcery Array — the KillAura target indicator. Two procedural material
// pages, no textures: page 0 is the world-aligned ground sigil (counter-
// rotating rune band, style-sided polygon gate, spoke wheel, attack shock
// ring), page 1 is the camera-facing aura ring (per-style ornaments and a
// view-fixed health arc with a bright tip flare). Palettes, dash dialects
// and FBM distortion mirror MagicHalo so the halo overhead and the sigil
// underfoot read as one visual language.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

layout(std140) uniform TargetUniforms {
    vec4 TargetMeta;      // time, styleId, colorMode, attackPulse01
    vec4 TargetPrimary;   // custom primary rgb, opacity
    vec4 TargetSecondary; // custom secondary rgb, rainbow speed
    vec4 TargetAccent;    // custom accent rgb, glow
    vec4 TargetMotion;    // rotation speed, pulse, distortion, sharpness
};

in vec4 vertexColor;
in vec2 uvCoord;
flat in ivec2 targetWords; // x: health(8) | acquire(8)   y: seed(8) | material(4)

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

float lineMask(float value, float width) {
    float aa = max(fwidth(value), 0.006);
    return 1.0 - smoothstep(width, width + aa, abs(value));
}

float dashedArc(float angle, float count, float fill, float phase, float softness) {
    float wave = cos(angle * count + phase);
    return smoothstep(cos(PI * fill) - softness, cos(PI * fill) + softness, wave);
}

float polygonRadius(vec2 point, float sides) {
    float angle = atan(point.y, point.x);
    float sector = TAU / sides;
    return cos(floor(0.5 + angle / sector) * sector - angle) * length(point);
}

float radialSpike(float distanceValue, float angle, float baseRadius,
                  float lengthValue, float count, float widthScale,
                  float variation, float softness) {
    float cellWidth = TAU / max(count, 1.0);
    float localAngle = mod(angle + cellWidth * 0.5, cellWidth) - cellWidth * 0.5;
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

// Same table as MagicHalo's stylePalette — the halo overhead and this
// indicator must share hues verbatim.
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

vec3 healthColor(float health) {
    vec3 low = vec3(1.00, 0.27, 0.23);
    vec3 mid = vec3(1.00, 0.84, 0.04);
    vec3 high = vec3(0.20, 0.89, 0.31);
    return health >= 0.5 ? mix(mid, high, (health - 0.5) * 2.0)
                         : mix(low, mid, health * 2.0);
}

void resolvePalette(int styleId, int colorMode, float angle, float health,
                    out vec3 primary, out vec3 secondary, out vec3 accent) {
    stylePalette(styleId, primary, secondary, accent);
    if (colorMode == 1) {
        primary = TargetPrimary.rgb;
        secondary = TargetSecondary.rgb;
        accent = TargetAccent.rgb;
    } else if (colorMode == 2) {
        float phase = angle / TAU + TargetMeta.x * TargetSecondary.a * 0.16;
        primary = spectralColor(phase);
        secondary = spectralColor(phase + 0.18);
        accent = spectralColor(phase + 0.42);
    } else if (colorMode == 3) {
        primary = healthColor(health);
        secondary = mix(healthColor(health), vec3(1.0), 0.35);
        accent = mix(healthColor(health), vec3(1.0), 0.60);
    }
}

float gateSides(int styleId) {
    if (styleId == 2) return 4.0;
    if (styleId == 4) return 5.0;
    if (styleId == 6) return 3.0;
    if (styleId == 1 || styleId == 3 || styleId == 5) return 6.0;
    return 8.0;
}

[shader("fragment")]

void main() {
    float time = TargetMeta.x;
    int styleId = clamp(int(TargetMeta.y + 0.5), 0, 6);
    int colorMode = clamp(int(TargetMeta.z + 0.5), 0, 3);
    float attackPulse = saturate(TargetMeta.w);
    float opacity = TargetPrimary.a;
    float glowStrength = TargetAccent.a;
    float rotationSpeed = TargetMotion.x;
    float pulseAmount = TargetMotion.y;
    float distortion = TargetMotion.z;
    float sharpness = saturate(TargetMotion.w);
    float softness = mix(0.018, 0.0025, sharpness);

    int packedA = targetWords.x & 0xFFFF;
    float health = float(packedA & 0xFF) / 255.0;
    float acquire = saturate(float((packedA >> 8) & 0xFF) / 255.0);
    int packedB = targetWords.y & 0xFFFF;
    float seed = float(packedB & 0xFF);
    int material = (packedB >> 8) & 0xF;

    vec2 point = (uvCoord - 0.5) * 2.0;
    float rawDistance = length(point);
    if (rawDistance > 1.24) discard;

    float pulseWave = sin(time * 3.0) * 0.5 + 0.5;
    float charge = 1.0 + acquire * 0.85 + attackPulse * attackPulse * 0.45;

    float structure = 0.0;
    float glowAcc = 0.0;
    float whiteHot = 0.0;
    float healthArc = 0.0;
    float paletteAngle = 0.0;

    if (material == 0) {
        // ---- Ground sigil ----
        float rotation = time * rotationSpeed * 1.35 + seed * 2.399;
        float burst = 1.0 + acquire * 0.10;
        float dist = rawDistance
                   + (fbm(vec2(point.x * 3.1 + seed * 0.7, point.y * 3.1 - time * 0.28)) - 0.5)
                   * distortion * 0.055;
        float angle = atan(point.y, point.x);
        paletteAngle = angle;

        // Outer rim: fine dashed ring, counter-rotating, breathing with the pulse.
        float rimRadius = 0.94 * burst * (1.0 + (pulseWave - 0.5) * pulseAmount * 0.06);
        vec2 rimSpace = rotate2d(point, -rotation * 0.6);
        float rim = band(dist, rimRadius, 0.009, softness)
                  * dashedArc(atan(rimSpace.y, rimSpace.x), 36.0, 0.74, time * 0.35, 0.05);

        // Rune band: glyph track with a punctuation dot ring, co-rotating.
        float runeRadius = 0.80 * burst;
        vec2 runeSpace = rotate2d(point, rotation * 0.9);
        float runeAngle = atan(runeSpace.y, runeSpace.x);
        float track = band(dist, runeRadius, 0.022, softness)
                    * dashedArc(runeAngle, 14.0, 0.30, -time * 0.8, 0.05);
        float punctuation = band(dist, runeRadius - 0.058, 0.011, softness)
                          * dashedArc(runeAngle, 7.0, 0.12, time * 0.5, 0.04);
        float runeBand = max(track, punctuation * 0.85);

        // Style-sided polygon gate.
        vec2 gateSpace = rotate2d(point, rotation * 0.45 + time * 0.05);
        float gateDistance = polygonRadius(gateSpace, gateSides(styleId));
        float gate = max(lineMask(gateDistance - 0.58 * burst, 0.016),
                         lineMask(gateDistance - 0.49 * burst, 0.008) * 0.55);

        // Spoke wheel and gem heart.
        vec2 coreSpace = rotate2d(point, -rotation * 1.5);
        float coreAngle = atan(coreSpace.y, coreSpace.x);
        float spokes = pow(abs(cos(coreAngle * 6.0)), 42.0)
                     * smoothstep(0.14, 0.24, dist)
                     * (1.0 - smoothstep(0.42 * burst, 0.52 * burst, dist));
        float innerRing = band(dist, 0.33 * burst, 0.013, softness)
                        * dashedArc(coreAngle, 6.0, 0.5, time * 0.9, 0.05);
        vec2 gemSpace = rotate2d(point, rotation * 2.2);
        float gem = lineMask(polygonRadius(gemSpace, 4.0) - 0.115, 0.016)
                  + disc(point, 0.045, softness) * 1.4;

        structure = max(max(rim, runeBand), max(gate, max(spokes * 0.85, max(innerRing, gem))));

        glowAcc = exp(-abs(dist - rimRadius) * 15.0) * 0.16
                + exp(-abs(dist - runeRadius) * 11.0) * 0.14
                + exp(-abs(dist - 0.58 * burst) * 10.0) * 0.10
                + exp(-dist * 4.2) * 0.10;

        whiteHot = disc(point, 0.045, softness) * 0.9 + gem * 0.18;

        // Attack pulse: bright shock ring sweeping outward + center flash.
        float pulseLife = 1.0 - attackPulse;
        float shockRadius = mix(0.10, 1.00, pow(pulseLife, 0.85)) * burst;
        float shock = band(dist, shockRadius, 0.016 + pulseLife * 0.055, softness)
                    * attackPulse * attackPulse;
        float flash = disc(point, 0.36 * attackPulse, softness) * attackPulse * attackPulse;
        structure = max(structure, shock);
        whiteHot += flash * 1.2;
    } else {
        // ---- Aura ring ----
        float radius = (0.72 + (pulseWave - 0.5) * pulseAmount * 0.05) * (1.0 + acquire * 0.08);
        vec2 spinSpace = rotate2d(point, time * 0.45 + seed * 1.7);
        float spinAngle = atan(spinSpace.y, spinSpace.x);
        float angle = atan(point.y, point.x);
        paletteAngle = angle;
        float dist = rawDistance
                   + (fbm(point * 5.0 + time * 0.2) - 0.5) * distortion * 0.04;

        // Segmented main ring — MagicHalo dash dialects per style.
        float ring = band(dist, radius, 0.017, softness);
        float segmentation = 1.0;
        if (styleId == 0) {
            segmentation = dashedArc(spinAngle, 8.0, 0.69, time * 0.7, 0.06);
        } else if (styleId == 1) {
            segmentation = dashedArc(spinAngle, 16.0, 0.38, -time * 1.3, 0.04);
        } else if (styleId == 2) {
            segmentation = dashedArc(spinAngle, 24.0, 0.33, time * 1.8, 0.03);
        } else if (styleId == 3) {
            segmentation = 0.45 + 0.55 * smoothstep(0.12, 0.85,
                hash11(floor((spinAngle + PI) * 5.0)));
        } else if (styleId == 4) {
            segmentation = 0.68 + 0.32 * sin(spinAngle * 5.0 - time * 2.0);
        } else if (styleId == 5) {
            segmentation = dashedArc(spinAngle, 12.0, 0.72, time * 0.4, 0.06);
        } else {
            segmentation = 0.55 + 0.45 * sin(spinAngle * 3.0 + time);
        }
        float mainRing = ring * saturate(0.32 + 0.68 * segmentation);

        // Style ornaments hugging the ring.
        float ornament = 0.0;
        if (styleId == 0) {
            ornament = radialSpike(dist, spinAngle, radius + 0.02, 0.15, 18.0, 0.25, 0.18, softness)
                     + radialSpike(dist, spinAngle + 0.17, radius, 0.09, 18.0, 0.12, 0.0, softness) * 0.7;
        } else if (styleId == 1) {
            ornament = radialSpike(dist, spinAngle - time * 0.07, radius, 0.11, 12.0, 0.18, 0.35, softness)
                     + band(dist, radius + 0.075, 0.007, softness)
                     * dashedArc(spinAngle, 6.0, 0.24, -time, 0.04) * 0.8;
        } else if (styleId == 2) {
            ornament = band(dist, radius + 0.055, 0.012, softness)
                     * dashedArc(spinAngle, 20.0, 0.33, time * 1.8, 0.03)
                     + radialSpike(dist, spinAngle, radius, 0.07, 20.0, 0.30, 0.0, softness);
        } else if (styleId == 3) {
            ornament = radialSpike(dist, spinAngle + sin(time * 0.3) * 0.15,
                radius - 0.01, 0.18, 14.0, 0.20, 1.0, softness);
        } else if (styleId == 4) {
            float flameWave = sin(spinAngle * 16.0 - time * 3.0) * 0.5 + 0.5;
            float flameTip = radius + 0.06 + 0.10 * pow(flameWave, 2.0);
            ornament = smoothstep(radius - softness, radius + softness, dist)
                     * (1.0 - smoothstep(flameTip - 0.035, flameTip + softness, dist))
                     * dashedArc(spinAngle, 16.0, 0.43, -time * 1.6, 0.04);
        } else if (styleId == 5) {
            ornament = radialSpike(dist, spinAngle, radius, 0.15, 12.0, 0.19, 0.0, softness)
                     + radialSpike(dist, spinAngle + 0.26, radius + 0.05, 0.06, 24.0, 0.11, 0.0, softness) * 0.8;
        } else {
            float petals = abs(sin(spinAngle * 5.0 + time * 0.35));
            float petalRadius = radius + 0.045 + 0.10 * pow(petals, 1.8);
            ornament = band(dist, petalRadius, 0.015, softness)
                     + radialSpike(dist, spinAngle - time * 0.12, radius, 0.08, 16.0, 0.14, 0.15, softness) * 0.7;
        }

        // Inner echo ring, counter-spinning.
        float echo = band(dist, radius - 0.10, 0.006, softness)
                   * dashedArc(-spinAngle, 20.0, 0.40, -time * 1.2, 0.04) * 0.6;

        structure = max(max(mainRing, ornament), echo);

        float ringGlow = exp(-abs(rawDistance - radius) * (11.0 - glowStrength * 0.6));
        glowAcc = ringGlow * 0.22
                + exp(-abs(rawDistance - (radius - 0.10)) * 13.0) * 0.10;

        // Health arc: view-fixed sweep starting at the bottom, flare at the tip.
        float sweep = clamp(health, 0.02, 1.0) * TAU;
        float delta = mod(angle + PI * 0.5, TAU);
        float arcMask = (1.0 - smoothstep(sweep - 0.015, sweep + 0.015, delta))
                      * band(rawDistance, radius, 0.030, softness);
        float tip = exp(-abs(delta - sweep) * 13.0)
                  * band(rawDistance, radius, 0.055, softness)
                  * step(0.03, health) * step(health, 0.999);
        healthArc = arcMask + tip * 1.3;

        // Attack crest: flare at the arc tip + an expanding echo ring.
        float pulseLife = 1.0 - attackPulse;
        float crest = exp(-abs(delta - sweep) * 9.0)
                    * band(rawDistance, radius, 0.075, softness)
                    * attackPulse * attackPulse;
        float echoRing = band(rawDistance, radius + pulseLife * 0.30, 0.012 + pulseLife * 0.05, softness)
                       * attackPulse * attackPulse;
        whiteHot = crest + echoRing;
    }

    // Energy composition — the MagicHalo recipe: shimmering structure,
    // themed gradient, accent-white hot cores.
    float shimmer = 0.80 + 0.20 * sin(paletteAngle * 5.0 - time * 2.2)
                  + (fbm(point * 8.0 + time * 0.12) - 0.5) * distortion * 0.22;
    float energy = saturate(structure * shimmer);
    float alpha = saturate(energy + healthArc * 0.85 + glowAcc * glowStrength * 0.40)
                * opacity * vertexColor.a * (1.0 + acquire * 0.35);
    if (alpha < 0.003) discard;

    vec3 primary;
    vec3 secondary;
    vec3 accent;
    resolvePalette(styleId, colorMode, paletteAngle, health, primary, secondary, accent);

    float gradient = saturate(0.5 + 0.5 * sin(paletteAngle * 1.5 - time * 0.55));
    vec3 baseColor = mix(primary, secondary, gradient);
    vec3 arcColor = colorMode == 3 ? healthColor(health) : accent;

    vec3 color = baseColor * (energy * 1.08 + glowAcc * glowStrength * 0.42)
               + accent * whiteHot * 1.35
               + arcColor * healthArc * 1.55;
    color += vec3(1.0) * whiteHot * 0.22;
    color *= charge;

    fragColor = vec4(color, alpha) * ColorModulator;
    if (fragColor.a < 0.001) discard;
}

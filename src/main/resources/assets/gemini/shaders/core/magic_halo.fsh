#version 330

// ═══════════════════════════════════════════════════════════════════════
//  Magic Halo (魔法光环) fragment shader — 湮灭日食 (Annihilation Eclipse)
//
//  A high-contrast dark sci-fi variant of the sacred halo, replacing
//  warm celestial light with a撕裂 void energy field and razor-sharp
//  geometric crystal shards.
//
//  Visual design:
//    • Central circular ring — structural backbone (warped)
//    • Inner dashed rune ring — reverse-rotating astrolabe
//    • Floating shards — irregular, fractured crystal spikes with
//      variable lengths driven by noise, replacing uniform triangles
//    • Forked crown spikes — compound geometric tines at the top
//    • Void palette — deep purple ↔ crimson ↔ aurora silver
//      (replaces gold/white/cyan for an annihilating contrast)
//    • Accretion-disk core glow — dense, throbbing singularity pulse
//    • Orbiting sparkles — dynamic circling star-points with trails
//    • Harsh broken-crystal edges (step) mixed with radiant glow
//
//  Vertex color encoding:
//    .r = 1.0 (unused, reserved)
//    .g = spike count normalised (spikeCount / 16.0)
//    .b = intensity multiplier
//    .a = master alpha
//
//  Time flow: encoded in DynamicTransforms.ModelOffset.x on the Java side.
//  The vertex shader normally uses ModelOffset for entity translation, but
//  since our halo is rendered via a manually placed billboard, ModelOffset
//  is unused for geometry and repurposed to carry animation time.
//
//  UV maps to the horizontal billboard quad:
//    (0.5, 0.5) = effect center (player head position)
//    (0,0) → (-1,-1) in effect-local space
// ═══════════════════════════════════════════════════════════════════════

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

// Animation time (seconds) — set from Java via DynamicTransforms.ModelOffset.x
#define u_time ModelOffset.x

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

// ═══════════════════════════════════════════════════════════════════════
//  Constants
// ═══════════════════════════════════════════════════════════════════════

#define PI      3.14159265359
#define TWO_PI  6.28318530718

// ── Ring geometry ────────────────────────────────────────────────────
const float RING_INNER    = 0.26;
const float RING_OUTER    = 0.38;
const float RING_CENTER   = 0.32;
const float RING_SOFTNESS = 0.004;   // much sharper edge (was 0.015)

// ── Spike geometry ───────────────────────────────────────────────────
const float SPIKE_BASE_R  = 0.36;
const float SPIKE_TIP_R   = 0.68;
const float SPIKE_WIDTH   = 0.20;    // slightly narrower (was 0.22)
const float SPIKE_TIP_W   = 0.02;    // sharper tip (was 0.03)

// ── Forked/crown spikes (top of halo) ───────────────────────────────
const float FORK_BASE_R   = 0.37;
const float FORK_TIP_R    = 0.82;
const float FORK_SPLIT    = 0.25;
const float FORK_INNER_R  = 0.50;

// ═══════════════════════════════════════════════════════════════════════
//  Hash & noise
// ═══════════════════════════════════════════════════════════════════════

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
        mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    float freq = 1.0;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p * freq);
        freq *= 2.2;
        a *= 0.4;
    }
    return v;
}

// ═══════════════════════════════════════════════════════════════════════
//  Celestial color palette — sacred gold / pure white / psychic cyan
// ═══════════════════════════════════════════════════════════════════════

vec3 celestialColor(float angle) {
    // Unstable colour flow — conveys "annihilation" energy
    float t = sin(angle * 2.0 + u_time * -1.5) * 0.5 + 0.5;
    float pulse = fbm(vec2(angle * 3.0, u_time)) * 0.5 + 0.5;

    vec3 voidPurple = vec3(0.10, 0.0,  0.50);
    vec3 crimson    = vec3(1.00, 0.05, 0.15);
    vec3 silver     = vec3(0.90, 0.95, 1.00);

    vec3 color = mix(voidPurple, crimson, t * pulse);
    // Sharp silver flare at peak highlights
    color = mix(color, silver, smoothstep(0.8, 1.0, t));

    return color;
}

// ═══════════════════════════════════════════════════════════════════════
//  Ring shape — sharp circular band with hard inner/outer edges
// ═══════════════════════════════════════════════════════════════════════

float ringShape(vec2 uv) {
    float d = length(uv);

    // Use stepped smoothstep for much sharper edges
    float inner = 1.0 - smoothstep(RING_INNER - RING_SOFTNESS,
                                    RING_INNER + RING_SOFTNESS, d);
    float outer = 1.0 - smoothstep(RING_OUTER - RING_SOFTNESS,
                                    RING_OUTER + RING_SOFTNESS, d);

    return outer - inner;
}

// ═══════════════════════════════════════════════════════════════════════
//  Regular spike pattern — sharper triangular rays
// ═══════════════════════════════════════════════════════════════════════

float regularSpikes(vec2 uv, float angle, float dist, int count) {
    float spikeAngle = TWO_PI / float(count);
    // Time-varying rotational offset — shards drift independently
    float offsetAngle = angle + sin(u_time * 0.5) * 0.2;
    float wrapped = mod(offsetAngle + spikeAngle * 0.5, spikeAngle) - spikeAngle * 0.5;

    // High-frequency noise yields varied shard lengths
    float noiseRadius = SPIKE_TIP_R + fbm(vec2(angle * 10.0, u_time)) * 0.15;
    float t = clamp((dist - SPIKE_BASE_R) / (noiseRadius - SPIKE_BASE_R), 0.0, 1.0);

    // Crystal tip: near-zero width for a piercing look
    float halfWidth = mix(SPIKE_WIDTH * 0.8, 0.002, t);

    // Hard threshold (step) instead of smoothstep — broken-crystal edges
    float angularMask = 1.0 - step(halfWidth, abs(wrapped));

    // Radial shatter — each shard is fractured into floating segments
    float shatter = step(0.15, fract(dist * 12.0 - u_time * 2.0));

    float radialMask = smoothstep(SPIKE_BASE_R - 0.01, SPIKE_BASE_R, dist)
                     * (1.0 - smoothstep(noiseRadius - 0.02, noiseRadius, dist));

    return angularMask * radialMask * shatter;
}

// ═══════════════════════════════════════════════════════════════════════
//  Forked crown spike — unchanged structure, sharper edges
// ═══════════════════════════════════════════════════════════════════════

float forkedCrownSpike(vec2 uv, float centerAngle) {
    float d = length(uv);
    // Safety distance check: discard UV-mapping artifacts far from the spike area
    if (d > FORK_TIP_R + 0.05) return 0.0;

    float a = uv.y >= 0.0 ? atan(uv.y, uv.x) : atan(uv.y, uv.x) + TWO_PI;
    if (a < 0.0) a += TWO_PI;

    float da = a - centerAngle;
    da = mod(da + PI, TWO_PI) - PI;

    float stemT = clamp((d - FORK_BASE_R) / (FORK_INNER_R - FORK_BASE_R), 0.0, 1.0);
    float stemHalfW = mix(0.10, 0.06, stemT);
    float stem = (1.0 - smoothstep(0.0, stemHalfW, abs(da)))
               * smoothstep(FORK_BASE_R - 0.003, FORK_BASE_R + 0.003, d)
               * (1.0 - smoothstep(FORK_INNER_R - 0.005, FORK_INNER_R + 0.005, d));

    float leftAngle = centerAngle + FORK_SPLIT * (0.3 + 0.7 * clamp((d - FORK_INNER_R) / (FORK_TIP_R - FORK_INNER_R), 0.0, 1.0));
    float daL = a - leftAngle;
    daL = mod(daL + PI, TWO_PI) - PI;
    float tineT = clamp((d - FORK_INNER_R) / (FORK_TIP_R - FORK_INNER_R), 0.0, 1.0);
    float tineHalfW = mix(0.04, 0.015, tineT);
    float leftTine = (1.0 - smoothstep(0.0, tineHalfW, abs(daL)))
                   * smoothstep(FORK_INNER_R - 0.005, FORK_INNER_R + 0.005, d)
                   * (1.0 - smoothstep(FORK_TIP_R - 0.005, FORK_TIP_R + 0.005, d));

    float rightAngle = centerAngle - FORK_SPLIT * (0.3 + 0.7 * clamp((d - FORK_INNER_R) / (FORK_TIP_R - FORK_INNER_R), 0.0, 1.0));
    float daR = a - rightAngle;
    daR = mod(daR + PI, TWO_PI) - PI;
    float rightTine = (1.0 - smoothstep(0.0, tineHalfW, abs(daR)))
                    * smoothstep(FORK_INNER_R - 0.005, FORK_INNER_R + 0.005, d)
                    * (1.0 - smoothstep(FORK_TIP_R - 0.005, FORK_TIP_R + 0.005, d));

    float jewelR = FORK_INNER_R + (FORK_TIP_R - FORK_INNER_R) * 0.55;
    float jewelDist = abs(d - jewelR);
    float jewelHalfW = 0.025;
    float jewel = (1.0 - smoothstep(0.0, jewelHalfW, abs(da)))
                * (1.0 - smoothstep(0.0, 0.04, jewelDist))
                * 0.6;

    return max(max(stem, max(leftTine, rightTine)), jewel);
}

// ═══════════════════════════════════════════════════════════════════════
//  Radiant glow — multi-layer bright aura with sharp core
//
//  Three distinct layers create a "radiant" rather than "blurry" look:
//    1. Tight core glow — hugs the ring tightly, very bright
//    2. Medium radial burst — spiky star-burst extending outward
//    3. Wide soft halo — the ethereal ambient aura
// ═══════════════════════════════════════════════════════════════════════

/**
 * Tight core glow — a bright, narrow band around the ring.
 * This is what makes the halo "glow" rather than just look blurry.
 * The brightness peaks exactly on the ring and falls off quickly.
 */
float coreGlow(float dist) {
    float d = abs(dist - RING_CENTER);
    // Dense accretion-disk core: extreme tightness with throbbing pulse
    float core = exp(-d * d * 300.0) * 0.8
               + exp(-d * d * 80.0)  * 0.5;
    // Energetic pulsation — unstable singularity
    float throb = 0.8 + 0.2 * sin(u_time * 8.0);
    return core * throb;
}

/**
 * Radial light burst — shafts of light extending outward from the ring.
 * Gives a "radiant holy light" feel with visible directional structure.
 */
float radialBurst(vec2 uv, float dist, float angle) {
    if (dist < RING_INNER) return 0.0;

    // Distance-based intensity: brightest near ring, fading outward
    float distFactor = exp(-(dist - RING_CENTER) * 5.0);

    // Subtle radial rays (16 soft rays for a sunburst texture)
    float rays = sin(angle * 16.0 + u_time * 0.5) * 0.5 + 0.5;
    rays = pow(rays, 4.0) * 0.3 + 0.7;  // narrow the bright rays

    // FBM noise for organic light texture
    float organic = fbm(vec2(dist * 8.0, angle * 4.0) + u_time * 0.15) * 0.3 + 0.7;

    return distFactor * rays * organic * 0.35;
}

/**
 * Soft ambient halo — wide ethereal aura for the outermost layer.
 */
float softGlow(float dist) {
    float aura = exp(-dist * dist * 1.8) * 0.25;
    float broad = exp(-dist * 0.7) * 0.15;
    float ringGlow = exp(-abs(dist - RING_CENTER) * 6.0) * 0.3;
    return aura + broad + ringGlow;
}

// ═══════════════════════════════════════════════════════════════════════
//  Glass edge highlight — bright rim on the ring (sharper than before)
// ═══════════════════════════════════════════════════════════════════════

float glassHighlight(vec2 uv, float dist, float angle) {
    // Sharper, narrower specular rim
    float outerEdge = (1.0 - smoothstep(RING_OUTER - 0.015, RING_OUTER + 0.005, dist))
                    * smoothstep(RING_OUTER - 0.03, RING_OUTER - 0.015, dist);

    float innerEdge = smoothstep(RING_INNER - 0.005, RING_INNER + 0.015, dist)
                    * (1.0 - smoothstep(RING_INNER + 0.015, RING_INNER + 0.04, dist));

    float specAngle = angle + u_time * 0.3;
    float specular = (sin(specAngle * 3.0) * 0.5 + 0.5) * 0.4
                   + (cos(specAngle * 5.0 + 1.2) * 0.5 + 0.5) * 0.2;

    float highlight = (outerEdge * 0.8 + innerEdge * 0.4) * (0.5 + specular * 0.5);

    // Boost highlight brightness
    return highlight * 1.3;
}

// ═══════════════════════════════════════════════════════════════════════
//  Orbiting sparkles
// ═══════════════════════════════════════════════════════════════════════

float orbitingSparkles(vec2 uv, float angle, float dist) {
    float d = abs(dist - RING_CENTER);
    if (d > 0.08) return 0.0;

    float sparkle = 0.0;
    for (int i = 0; i < 5; i++) {
        float offset = float(i) * TWO_PI / 5.0;
        float orbitAngle = offset + u_time * (1.5 + float(i) * 0.2);

        float sda = mod(angle - orbitAngle + PI, TWO_PI) - PI;

        float brightness = exp(-sda * sda * 300.0) * exp(-d * d * 1500.0);

        float trail = exp(-abs(sda) * 15.0) * exp(-d * d * 600.0)
                    * smoothstep(0.0, -sda, 0.0);

        float flicker = 0.6 + 0.4 * sin(u_time * 10.0 + float(i));
        sparkle += (brightness + trail * 0.4) * flicker;
    }
    return sparkle;
}

// ═══════════════════════════════════════════════════════════════════════
//  Main
// ═══════════════════════════════════════════════════════════════════════

void main() {
    float spikeNorm   = vertexColor.g;
    float intensity   = vertexColor.b;
    float masterAlpha = vertexColor.a;

    if (masterAlpha < 0.001 || intensity < 0.001) { discard; return; }

    vec2 uv = (uvCoord - 0.5) * 2.0;
    float dist = length(uv);
    float angle = atan(uv.y, uv.x);

    int numSpikes = max(4, int(spikeNorm * 16.0 + 0.5));

    // ══════════════════════════════════════════════════════════════
    //  Shape composition
    // ══════════════════════════════════════════════════════════════

    float ring = ringShape(uv);

    // Inner dashed rune ring
    float innerRadius = RING_INNER - 0.05;
    float innerRingShape = smoothstep(innerRadius - 0.01, innerRadius, dist)
                         * (1.0 - smoothstep(innerRadius, innerRadius + 0.01, dist));
    float dashCount = 24.0;
    float dash = sin(angle * dashCount - u_time * 3.0);
    float innerDashes = innerRingShape * smoothstep(0.0, 0.2, dash);

    float spikes = regularSpikes(uv, angle, dist, numSpikes);

    float crownAngle1 = PI * 0.5;
    float crownAngle2 = PI * 0.5 + 0.45;
    float crownAngle3 = PI * 0.5 - 0.45;
    float crown1 = forkedCrownSpike(uv, crownAngle1);
    float crown2 = forkedCrownSpike(uv, crownAngle2);
    float crown3 = forkedCrownSpike(uv, crownAngle3);
    float crownSpikes = max(max(crown1, crown2), crown3);

    // ── Crown mask: remove regular spikes near crown positions ────
    float da1 = abs(mod(angle - crownAngle1 + PI, TWO_PI) - PI);
    float da2 = abs(mod(angle - crownAngle2 + PI, TWO_PI) - PI);
    float da3 = abs(mod(angle - crownAngle3 + PI, TWO_PI) - PI);
    float nearCrown = min(min(da1, da2), da3);
    float crownMask = smoothstep(0.25, 0.40, nearCrown);

    // ── Combined structure (sharp, opaque core) ───────────────────
    float structure = max(ring, max(spikes * crownMask, crownSpikes));
    structure = max(structure, innerDashes * 0.8);

    // ══════════════════════════════════════════════════════════════
    //  Glow layers (bright, layered radiance)
    // ══════════════════════════════════════════════════════════════

    // Layer 1: Tight core glow on the ring — creates "radiant edge"
    float core = coreGlow(dist);

    // Layer 2: Radial light burst outward from ring — sunburst feel
    float burst = radialBurst(uv, dist, angle);

    // Layer 3: Wide ambient aura — soft outer halo
    float soft = softGlow(dist);

    // Glass specular highlight (sharper, on the structure edges)
    float glass = glassHighlight(uv, dist, angle);

    // Orbiting sparkles
    float sparkle = orbitingSparkles(uv, angle, dist);

    // ── Organic shimmer (reduced influence on structure opacity) ──
    float shimmer = fbm(uv * 12.0 + u_time * 0.4) * 0.2
                  + fbm(uv * 5.0 - u_time * 0.25) * 0.1
                  + 0.7;

    // ══════════════════════════════════════════════════════════════
    //  Color
    // ══════════════════════════════════════════════════════════════

    vec3 baseColor = celestialColor(angle);

    // Structure colour: mostly pure colour, slight shimmer lift
    vec3 structColor = mix(baseColor, vec3(1.0), 0.15 * shimmer);

    // Glow colours
    vec3 coreColor   = mix(baseColor, vec3(1.0), 0.5);  // whitened, bright
    vec3 burstColor  = baseColor;                        // keep hue
    vec3 softColor   = baseColor * 0.8;                  // dimmer, atmospheric

    // ══════════════════════════════════════════════════════════════
    //  Alpha composition — sharp structure + bright glow
    // ══════════════════════════════════════════════════════════════

    // Structure: much more opaque (was 0.78)
    float structAlpha = structure * shimmer * 0.95;
    structAlpha = max(structAlpha, ring * 0.95 * shimmer);

    // Core glow: bright tight band on the ring
    float coreAlpha = core * 0.55;

    // Radial burst: directional shafts of light
    float burstAlpha = burst * 0.30;

    // Soft aura
    float softAlpha = soft * 0.18;

    // Glass highlight
    float glassAlpha = glass * 0.65;

    // Sparkles
    float sparkleAlpha = sparkle * 0.9;

    // ── Combine ─────────────────────────────────────────────────
    float totalAlpha = structAlpha + coreAlpha + burstAlpha + softAlpha + glassAlpha + sparkleAlpha;
    totalAlpha = clamp(totalAlpha, 0.0, 1.0);

    // ══════════════════════════════════════════════════════════════
    //  Final colour blend — additive HDR layering
    // ══════════════════════════════════════════════════════════════

    vec3 color = vec3(0.0);

    // Sharp structure (fully coloured)
    color += structColor * structAlpha;

    // Core glow (bright, whitened — the "radiant edge")
    color += coreColor * coreAlpha * 1.5;

    // Radial burst (directional shafts)
    color += burstColor * burstAlpha;

    // Soft outer aura
    color += softColor * softAlpha;

    // Pure white specular highlight (glass reflection)
    color += vec3(1.0) * glassAlpha;

    // Sparkles with flash boost
    color += mix(baseColor, vec3(1.0), 0.8) * sparkleAlpha * 2.5;

    // Normalise
    if (totalAlpha > 0.001) {
        color /= totalAlpha;
    }

    // Apply intensity
    color *= intensity;

    // Apply master alpha
    float finalAlpha = totalAlpha * masterAlpha;

    // HDR boost for bloom compatibility
    float hdrBoost = 1.0 + core * 0.6 + sparkle * 0.5;
    color *= hdrBoost;

    fragColor = vec4(color, finalAlpha) * ColorModulator;

    if (fragColor.a < 0.0005) discard;
}

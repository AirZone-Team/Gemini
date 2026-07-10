#version 330

// ═══════════════════════════════════════════════════════════════════
//  Volumetric Glow Ball fragment shader
//
//  Renders a multi-layer self-illuminated glow sphere with pseudo
//  ray-tracing effects. Designed for depth-tested additive blending
//  so that block/entity geometry correctly occludes the glow.
//
//  vertexColor.r = time progress (0→1)
//  vertexColor.g = layer selector:
//    0.0 = dense core     — ultra-bright inner sphere, tight gaussian
//    0.5 = mid glow       — intense halo with fresnel ring + ray streaks
//    1.0 = outer glow     — soft volumetric aura, wide falloff
//    1.5 = ambient sphere — far-reaching subtle ambient light
//  vertexColor.b = intensity
//  vertexColor.a = master alpha
//
//  UV maps to the camera-facing billboard quad; (0.5,0.5) is the
//  effect center.  Distance from center drives all radial effects.
//
//  Pseudo ray-tracing: angular noise sampling creates directional
//  light streaks that simulate volumetric rays emanating from the
//  core.  Combined with depth-tested geometry, rays naturally
//  terminate at occluding surfaces.
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

// ═══════════════════════════════════════════════════════════════════
//  Hash & noise functions
// ═══════════════════════════════════════════════════════════════════

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float hash3(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453123);
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

// 5-octave FBM — higher quality for glow effects
float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    float freq = 1.0;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p * freq);
        freq *= 2.3;
        a *= 0.45;
    }
    return v;
}

// Simplex-like 3D noise for temporal variation
float noise3D(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash3(i), hash3(i + vec3(1,0,0)), f.x),
            mix(hash3(i + vec3(0,1,0)), hash3(i + vec3(1,1,0)), f.x), f.y),
        mix(mix(hash3(i + vec3(0,0,1)), hash3(i + vec3(1,0,1)), f.x),
            mix(hash3(i + vec3(0,1,1)), hash3(i + vec3(1,1,1)), f.x), f.y),
        f.z
    );
}

// ═══════════════════════════════════════════════════════════════════
//  Voronoi for cellular glow texture
// ═══════════════════════════════════════════════════════════════════

float voronoi(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float minDist = 1.0;
    float secondDist = 1.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 neighbor = vec2(float(x), float(y));
            vec2 point = vec2(hash(i + neighbor), hash(i + neighbor + vec2(31.0)));
            vec2 diff = neighbor + point - f;
            float d = dot(diff, diff);
            if (d < minDist) {
                secondDist = minDist;
                minDist = d;
            } else if (d < secondDist) {
                secondDist = d;
            }
        }
    }
    return sqrt(secondDist) - sqrt(minDist);
}

// ═══════════════════════════════════════════════════════════════════
//  Pseudo ray-tracing: angular light streaks
//
//  Samples noise along radial lines emanating from center.
//  Produces directional bright streaks that simulate volumetric
//  light rays.  Rays are strongest at specific angles and fade
//  with distance.
// ═══════════════════════════════════════════════════════════════════

float pseudoRays(vec2 uv, float time, float numRays) {
    float angle = atan(uv.y, uv.x);
    float dist = length(uv);

    // Quantize angle to create discrete ray directions
    float rayAngle = floor(angle * numRays / 6.283185307) / numRays * 6.283185307;

    // Angular proximity to nearest ray
    float angDist = abs(angle - rayAngle);
    angDist = min(angDist, 6.283185307 - angDist);

    // Ray angular width — wider near center, narrower at edges
    float rayWidth = 0.08 + dist * 0.04;

    // Gaussian falloff from ray center line
    float rayProfile = exp(-angDist * angDist / (rayWidth * rayWidth));

    // Ray length: rays are strongest at medium distances
    float distProfile = exp(-dist * 1.8) * (1.0 - exp(-dist * 6.0));

    // Temporal flicker per ray
    float flicker = 1.0 + 0.4 * sin(time * 12.0 + rayAngle * 3.0 + hash(vec2(floor(rayAngle * numRays / 6.283185307), 0.5)) * 6.28);

    // Noise along each ray for organic variation
    float rayNoise = fbm(vec2(dist * 5.0, rayAngle * 3.0 + time * 0.3)) * 0.5 + 0.5;

    return rayProfile * distProfile * flicker * rayNoise;
}

// ═══════════════════════════════════════════════════════════════════
//  Glow layer functions
// ═══════════════════════════════════════════════════════════════════

// Dense core — ultra-bright, tight gaussian, pure white-hot
vec4 renderCore(vec2 uv, float d, float t, float intensity) {
    // Central ultra-bright spot (self-illumination)
    float core = exp(-d * d * 80.0);

    // Tight inner glow
    float inner = exp(-d * d * 18.0);

    // Micro-flare: very small intense spot
    float flare = exp(-d * d * 400.0) * 0.7;

    // Temporal pulse
    float pulse = 1.0 + 0.08 * sin(t * 25.0) + 0.04 * sin(t * 37.0 + 1.5);
    pulse *= 1.0 + 0.03 * noise3D(vec3(uv * 30.0, t * 5.0));

    // Combine
    float brightness = core * 0.9 + inner * 0.35 + flare * 1.2;
    brightness *= pulse * intensity;

    // Color: pure white core → slight blue-white at edges
    vec3 col = mix(vec3(1.0, 1.0, 1.0), vec3(0.85, 0.9, 1.0), smoothstep(0.0, 0.12, d));
    col = mix(col, vec3(0.6, 0.75, 1.0), smoothstep(0.12, 0.25, d));

    // HDR — values >> 1 for bloom extraction
    col *= brightness * 3.0;

    float alpha = brightness * 0.3 + core * 1.5 + flare * 2.0;
    return vec4(col, alpha);
}

// Mid glow — intense halo with Fresnel ring + ray streaks
vec4 renderMidGlow(vec2 uv, float d, float angle, float t, float intensity) {
    // Main radial gaussian
    float body = exp(-d * d * 3.5);

    // Fresnel rim: brighter at specific radius (simulates sphere surface)
    float rimRadius = 0.55;
    float rim = exp(-abs(d - rimRadius) * 12.0);
    float rimInner = exp(-abs(d - rimRadius * 0.55) * 10.0) * 0.4;

    // Outer Fresnel ring
    float outerRim = exp(-abs(d - 0.72) * 8.0) * 0.35;

    // Volumetric scatter
    float scatter = exp(-d * 1.5) * 0.5;

    // Pseudo ray-tracing streaks
    float rays = pseudoRays(uv, t, 12.0);

    // FBM noise for organic texture
    float organic = fbm(uv * 8.0 + t * 0.5) * 0.4 + 0.6;
    organic += fbm(uv * 3.5 - t * 0.3) * 0.25;

    // Temporal pulse
    float pulse = 1.0 + 0.06 * sin(t * 18.0 + d * 3.0) + 0.03 * sin(t * 31.0);

    float brightness = (body * 0.7 + rim * 0.85 + rimInner * 0.5 + outerRim * 0.4 + scatter * 0.4 + rays * 0.55) * organic * pulse * intensity;

    // Color: warm white core → electric blue halo → purple rim
    vec3 col = vec3(1.0, 0.95, 0.85);
    col = mix(col, vec3(0.65, 0.85, 1.0), smoothstep(0.0, 0.35, d));
    col = mix(col, vec3(0.35, 0.55, 1.0), smoothstep(0.35, 0.6, d));
    col = mix(col, vec3(0.3, 0.15, 0.8), smoothstep(0.6, 0.85, d));

    // Ray streaks are whiter
    vec3 rayCol = vec3(1.0, 0.95, 0.9);
    col = mix(col, rayCol, rays * 0.6);

    col *= brightness * 2.5;

    float alpha = brightness * 0.4 + rim * 1.0 + rays * 0.6;
    return vec4(col, alpha);
}

// Outer glow — soft volumetric aura, wide falloff
vec4 renderOuterGlow(vec2 uv, float d, float angle, float t, float intensity) {
    // Wide soft gaussian
    float body = exp(-d * d * 0.9);

    // Very soft exponential falloff
    float expo = exp(-d * 0.8);

    // Subtle directional rays (fewer, wider)
    float rays = pseudoRays(uv, t, 8.0) * 0.5;

    // Voronoi cellular pattern for ethereal texture
    float cell = voronoi(uv * 6.0 + t * 0.15);
    float cellGlow = (1.0 - abs(cell)) * 0.3;

    // FBM for organic variation
    float organic = fbm(uv * 4.0 + t * 0.2) * 0.5 + 0.5;

    // Slow deep pulse
    float pulse = 1.0 + 0.1 * sin(t * 7.0 + d * 1.5) + 0.05 * sin(t * 13.0);

    float brightness = (body * 0.5 + expo * 0.35 + rays * 0.25 + cellGlow * 0.2) * organic * pulse * intensity;

    // Color: blue-white → deep blue-purple
    vec3 col = mix(vec3(0.7, 0.85, 1.0), vec3(0.25, 0.4, 0.9), smoothstep(0.0, 0.5, d));
    col = mix(col, vec3(0.12, 0.1, 0.5), smoothstep(0.5, 0.9, d));

    col *= brightness * 1.2;

    float alpha = brightness * 0.15;
    return vec4(col, alpha);
}

// Ambient sphere — far-reaching subtle glow
vec4 renderAmbient(vec2 uv, float d, float t, float intensity) {
    // Very wide, subtle exponential
    float body = exp(-d * d * 0.15);

    // Ultra-wide ambient scatter
    float ambient = exp(-d * 0.3) * 0.6;

    // Very faint rays
    float rays = pseudoRays(uv, t, 5.0) * 0.2;

    // Slow breath-like pulse
    float breath = 1.0 + 0.08 * sin(t * 4.0) + 0.04 * sin(t * 6.5 + 2.0);

    float brightness = (body * 0.3 + ambient * 0.25 + rays * 0.1) * breath * intensity;

    // Color: very faint blue-purple
    vec3 col = mix(vec3(0.2, 0.3, 0.6), vec3(0.06, 0.04, 0.3), d * 0.5);

    col *= brightness * 0.6;

    float alpha = brightness * 0.05;
    return vec4(col, alpha);
}

// ═══════════════════════════════════════════════════════════════════
//  Main
// ═══════════════════════════════════════════════════════════════════

void main() {
    float time      = vertexColor.r;
    float layer     = vertexColor.g;   // 0=core, 0.5=mid, 1.0=outer, 1.5=ambient
    float intensity = vertexColor.b;
    float alpha     = vertexColor.a;

    if (alpha < 0.001 || intensity < 0.001) { discard; return; }

    vec2 uv = (uvCoord - 0.5) * 2.0;
    float d = length(uv);
    float angle = atan(uv.y, uv.x);

    vec4 result;

    if (layer < 0.25) {
        // Dense core
        result = renderCore(uv, d, time, intensity);
    } else if (layer < 0.75) {
        // Mid glow
        result = renderMidGlow(uv, d, angle, time, intensity);
    } else if (layer < 1.25) {
        // Outer glow
        result = renderOuterGlow(uv, d, angle, time, intensity);
    } else {
        // Ambient sphere
        result = renderAmbient(uv, d, time, intensity);
    }

    // Apply master alpha
    result.a *= alpha;

    // HDR: keep values >> 1 so bloom captures them
    fragColor = result * ColorModulator;

    if (fragColor.a < 0.0005) discard;
}

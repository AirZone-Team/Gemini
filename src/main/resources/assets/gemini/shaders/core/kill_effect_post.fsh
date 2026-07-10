#version 330

// ═══════════════════════════════════════════════════════════════════
//  KillEffect Post-Processing — multi-pass fragment shader
//
//  Compile-time defines select the active pass:
//    BRIGHT_PASS   — luminance threshold → bloom source
//    BLUR_H        — separable horizontal gaussian blur
//    BLUR_V        — separable vertical gaussian blur
//    DISTORTION    — screen-space heat distortion
//    GODRAY        — screen-space radial blur from effect centers
//    CHROMATIC     — RGB channel separation (chromatic aberration)
//    BLACK_HOLE    — Screen-space black hole center: event horizon, photon
//                    ring (HDR), accretion disk (ray-marched), gravitational
//                    lensing, photon sphere glow, alpha=1 override
//    GLOW_FLASH    — Supernova pre-flash: bright pulsing central spot with
//                    expanding halo, flicker envelope, blue/purple color ramp
//    SHOCKWAVE     — Expanding concentric shock rings: UV distortion + blue
//                    shift at wave crests, bipolar stretch along Y axis
//    ACES          — ACES filmic tone mapping (Narkowicz 2015 fit)
//
//  Uniforms (PostUniforms, std140):
//    vec4 Params:       x=fbWidth, y=fbHeight, z=bloomStrength, w=threshold
//    vec4 TimePack:     x=time(sec), yzw=unused
//    vec4 Center1:      xy=effect center NDC (-1..1), zw=unused
//    vec4 Center2:      xy=secondary center NDC, zw=unused
//    vec4 PassParams:   x=distortionStrength, y=godRayStrength,
//                       z=chromaticStrength, w=bloomRadius
// ═══════════════════════════════════════════════════════════════════

uniform sampler2D SceneSampler;
uniform sampler2D BloomSampler;

layout(std140) uniform PostUniforms {
    vec4 Params;       // xy=fbRes, z=bloomStrength, w=threshold
    vec4 TimePack;     // x=time (sec)
    vec4 Center1;      // xy=effectCenter1 NDC
    vec4 Center2;      // xy=effectCenter2 NDC
    vec4 PassParams;   // x=distort, y=godRay, z=chromatic, w=bloomRadius
    vec4 BHParams;     // x=bhRadius(UV), y=stage, z=progress, w=intensity
};

in vec2 vUv;
out vec4 fragColor;

// ── Luminance ─────────────────────────────────────────────────────

float luminance(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

// ── ACES (Narkowicz 2015) ────────────────────────────────────────

vec3 aces(vec3 x) {
    float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

// ══════════════════════════════════════════════════════════════════
//  Pass 1: Bright Extract (luminance threshold with soft knee)
//  In:  SceneSampler (scene)
//  Out: fragColor (bright pixels only)
// ══════════════════════════════════════════════════════════════════

#ifdef BRIGHT_PASS

void main() {
    vec3 color = texture(SceneSampler, vUv).rgb;
    float lum = luminance(color);
    float t = Params.w;            // threshold
    float knee = 0.08;

    // Soft knee around threshold
    float w = clamp((lum - t + knee) / (2.0 * knee), 0.0, 1.0);
    float bright = w * max(lum - t, 0.0) / max(lum, 0.001);

    fragColor = vec4(color * bright, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 2a: Horizontal Gaussian Blur (separable, linear-sampled)
//  In:  SceneSampler (previous pass output)
//  Out: fragColor (horizontally blurred)
// ══════════════════════════════════════════════════════════════════

#ifdef BLUR_H

void main() {
    vec2 texelSize = vec2(1.0 / Params.x, 0.0);   // horizontal only
    float radius = PassParams.w * 0.5;              // bloom radius in pixels
    float sigma = max(radius, 0.5);

    vec3 color = vec3(0.0);
    float weightSum = 0.0;

    // 9-tap gaussian (radius=4px → sigma≈1.7): [-4σ, +4σ]
    int taps = int(ceil(sigma * 3.0));
    taps = clamp(taps, 3, 8);

    for (int i = -8; i <= 8; i++) {
        if (abs(i) > taps) continue;
        float w = exp(-float(i * i) / (2.0 * sigma * sigma));
        color += texture(SceneSampler, vUv + texelSize * float(i)).rgb * w;
        weightSum += w;
    }
    fragColor = vec4(color / weightSum, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 2b: Vertical Gaussian Blur (separable, linear-sampled)
//  In:  SceneSampler (horizontally blurred output)
//  Out: fragColor (fully blurred bloom buffer)
// ══════════════════════════════════════════════════════════════════

#ifdef BLUR_V

void main() {
    vec2 texelSize = vec2(0.0, 1.0 / Params.y);   // vertical only
    float radius = PassParams.w * 0.5;
    float sigma = max(radius, 0.5);

    vec3 color = vec3(0.0);
    float weightSum = 0.0;

    int taps = int(ceil(sigma * 3.0));
    taps = clamp(taps, 3, 8);

    for (int i = -8; i <= 8; i++) {
        if (abs(i) > taps) continue;
        float w = exp(-float(i * i) / (2.0 * sigma * sigma));
        color += texture(SceneSampler, vUv + texelSize * float(i)).rgb * w;
        weightSum += w;
    }
    fragColor = vec4(color / weightSum, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 3: Screen-space Heat Distortion
//  In:  SceneSampler (scene), BloomSampler (depth/silhouette ref)
//  Out: fragColor (distorted scene)
//
//  Offsets UVs radially away from the effect center, creating a
//  heat-haze / gravitational lensing look in screen space.
// ══════════════════════════════════════════════════════════════════

#ifdef DISTORTION

float hashDistort(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noiseDistort(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hashDistort(i), hashDistort(i + vec2(1,0)), f.x),
               mix(hashDistort(i + vec2(0,1)), hashDistort(i + vec2(1,1)), f.x), f.y);
}

void main() {
    float strength = PassParams.x * 0.04;     // max ~4% screen offset
    vec2 center = Center1.xy * 0.5 + 0.5;     // NDC [-1,1] → UV [0,1]

    // Direction from effect center
    vec2 dir = vUv - center;
    float dist = length(dir);

    // Distortion falls off with 1/distance
    float distort = strength / (dist + 0.05);

    // Noise-based wobble (prevents sterile look)
    float n = noiseDistort(vUv * 80.0 + TimePack.x * 0.5) * 0.6;
    distort *= (0.7 + n);

    // Clamp maximum distortion
    distort = min(distort, 0.08);

    vec2 offsetUv = vUv + normalize(dir + 0.001) * distort;

    // Fade distortion near screen edges
    float edgeFade = 1.0 - smoothstep(0.75, 1.0, length(vUv - 0.5) * 2.0);

    vec3 distorted = texture(SceneSampler, offsetUv).rgb;
    vec3 original  = texture(SceneSampler, vUv).rgb;

    fragColor = vec4(mix(original, distorted, edgeFade), 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 4: God Rays (screen-space radial blur from effect centers)
//  In:  SceneSampler (HDR scene or bloom buffer)
//  Out: fragColor (scene + god rays)
//
//  Radially samples toward effect centers, accumulating bright
//  areas into volumetric light beams.
// ══════════════════════════════════════════════════════════════════

#ifdef GODRAY

void main() {
    float strength = PassParams.y;
    vec2 center1 = Center1.xy * 0.5 + 0.5;    // NDC → UV
    vec2 center2 = Center2.xy * 0.5 + 0.5;

    vec3 rays = vec3(0.0);
    float weightSum = 0.0;
    int samples = 32;

    // God rays from center1 (primary: black hole / hypernova)
    vec2 toCenter1 = center1 - vUv;
    float dist1 = length(toCenter1);
    vec2 dir1 = toCenter1 / max(dist1, 0.001);
    float decay1 = exp(-dist1 * 1.5);

    for (int i = 0; i < samples; i++) {
        float t = (float(i) + 0.5) / float(samples);
        vec2 suv = vUv + dir1 * t * dist1;
        float w = exp(-t * 3.0);              // attenuation along ray
        rays += texture(SceneSampler, suv).rgb * w;
        weightSum += w;
    }
    rays /= max(weightSum, 0.001);
    rays *= decay1 * strength;

    // God rays from center2 (secondary effect center)
    vec2 toCenter2 = center2 - vUv;
    float dist2 = length(toCenter2);
    if (dist2 < 2.0 && strength > 0.01) {
        vec2 dir2 = toCenter2 / max(dist2, 0.001);
        float decay2 = exp(-dist2 * 2.0);
        vec3 rays2 = vec3(0.0);
        float ws2 = 0.0;
        for (int i = 0; i < 16; i++) {
            float t = (float(i) + 0.5) / 16.0;
            vec2 suv = vUv + dir2 * t * dist2;
            float w = exp(-t * 3.0);
            rays2 += texture(SceneSampler, suv).rgb * w;
            ws2 += w;
        }
        rays2 /= max(ws2, 0.001);
        rays += rays2 * decay2 * strength * 0.6;
    }

    vec3 scene = texture(SceneSampler, vUv).rgb;
    fragColor = vec4(scene + rays, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 5: Chromatic Aberration
//  In:  SceneSampler
//  Out: fragColor (RGB-separated)
//
//  Offsets R and B channels radially from screen center.
// ══════════════════════════════════════════════════════════════════

#ifdef CHROMATIC

void main() {
    float strength = PassParams.z * 0.008;     // max ~0.8% offset
    vec2 center = vec2(0.5);                    // screen center
    vec2 dir = normalize(vUv - center + 0.001);
    float dist = length(vUv - center);
    float scale = strength * dist;

    float r = texture(SceneSampler, vUv + dir * scale).r;
    float g = texture(SceneSampler, vUv).g;
    float b = texture(SceneSampler, vUv - dir * scale).b;

    fragColor = vec4(r, g, b, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 6: Black Hole Center (screen-space, HDR)
//  In:  SceneSampler (composited HDR scene with 3D BH, bloom, etc.)
//  Out: fragColor (scene + screen-space BH center, alpha=1)
//
//  Renders the black hole center in screen space:
//    1. Event horizon    — pure black disc
//    2. Photon ring      — extreme HDR ring (vec3(20,15,8))
//    3. Accretion disk   — ray-marched procedural disk
//    + Screen-space gravitational lensing on background
//    + Photon sphere volumetric glow
//    + Alpha = 1  (center override, blocks background)
//
//  Runs BEFORE ACES so HDR photon ring survives bloom extraction
//  and tone mapping.
// ══════════════════════════════════════════════════════════════════

#ifdef BLACK_HOLE

// ── Pseudo-random hash (for accretion disk turbulence) ──────
float bhHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

// ══════════════════════════════════════════════════════════════
//  Screen-space gravitational lensing
//
//  Warps UV coordinates toward the black hole center, with
//  deflection strongest near the photon sphere and falling
//  off as 1/r².  Produces the characteristic "einstein ring"
//  distortion of the background scene.
// ══════════════════════════════════════════════════════════════

vec2 bhGravLens(vec2 uv, vec2 center, float rs, vec2 aspect) {
    vec2 delta = uv - center;
    vec2 ad = delta * aspect;          // aspect-corrected
    float d = length(ad);
    if (d < 0.001) return uv;

    float b = d;                       // impact parameter
    // Weak-field deflection: α ∝ Rs / b² (softened at center)
    float alpha = rs * 0.15 / (b * b + rs * rs * 0.08);

    // Critical boost at photon sphere (r ≈ 1.5 Rs in UV space)
    // Dramatically enhanced for Interstellar-like background warping
    float rPhoton = rs * 1.5;
    float distToPhoton = abs(b - rPhoton);
    alpha *= 1.0 + exp(-distToPhoton * 25.0 / max(rs, 0.01)) * 6.0;

    // Clamp to avoid tearing at extreme angles (raised for stronger effect)
    alpha = min(alpha, 0.55);

    vec2 dir = delta / max(d, 0.001);
    return uv + dir * alpha / aspect;
}

// ══════════════════════════════════════════════════════════════
//  Ray-marched accretion disk
//
//  Simulates a geometrically thin, optically thick accretion
//  disk in the equatorial plane.  Uses "fake 3D" ray marching
//  to produce the characteristic double-peaked emission line
//  profile and relativistic Doppler beaming.
// ══════════════════════════════════════════════════════════════

vec3 bhAccretionDisk(vec2 delta, vec2 aspect, float time, float rs) {
    vec2 ad = delta * aspect;
    float d = length(ad);

    // Disk lies in horizontal plane through BH center
    float diskY = delta.y;
    float diskX = delta.x;
    float dist = length(delta);

    // Innermost stable circular orbit (ISCO) ≈ 3 Rs
    // Disk extends from ISCO to outer radius
    float isco = rs * 2.8;
    float outer = rs * 9.0;

    if (dist < isco * 0.4 || dist > outer * 1.2) return vec3(0.0);

    // ── Profile: hot inner peak + extended cool tail ──
    float profile = 0.0;

    // Main peak near ISCO
    profile += exp(-abs(dist - isco) * 1.8 / max(rs, 0.01));

    // Secondary ring (density wave)
    profile += exp(-abs(dist - isco * 1.8) * 0.8 / max(rs, 0.01)) * 0.45;

    // Outer disk glow
    profile += exp(-abs(dist - isco * 3.5) * 0.4 / max(rs, 0.01)) * 0.2;

    profile = clamp(profile, 0.0, 1.0);

    // ── Spiral waves + turbulence ──
    float angle = atan(diskX, diskY);
    float spiral = sin(angle * 3.0 + dist * 6.0 - time * 3.5) * 0.5 + 0.5;
    float turb = bhHash(vec2(angle * 4.0, dist * 5.0) + time * 0.2) * 0.35;

    float diskBright = profile * (spiral * 0.5 + turb * 0.4 + 0.1);

    // ── Temperature: hot inner → cool outer ──
    float temp = clamp((dist - isco) / (outer - isco + 0.001), 0.0, 1.0);

    vec3 colInner = vec3(0.50, 0.65, 1.00);  // blue-white
    vec3 colMid   = vec3(1.00, 0.55, 0.08);  // orange
    vec3 colOuter = vec3(0.65, 0.12, 0.02);  // deep red

    vec3 diskCol = mix(colInner, colMid, smoothstep(0.0, 0.45, temp));
    diskCol = mix(diskCol, colOuter, smoothstep(0.45, 1.0, temp));

    // ── Doppler beaming ──
    // Left side of the BH (negative x) material moves toward us, brighter/bluer
    // Right side moves away, dimmer/redder
    float doppler = delta.x * 0.5 + 0.5;          // 0=left, 1=right
    float beaming = 1.0 + (1.0 - doppler) * 2.5;  // approaching 3.5× brighter
    diskCol *= (0.25 + beaming * 0.75);

    return diskCol * diskBright * 1.8;
}

// ══════════════════════════════════════════════════════════════
//  Polar jet / corona (vertical emission near the poles)
// ══════════════════════════════════════════════════════════════

float bhCorona(vec2 delta, float rs) {
    // Vertical jet-like emission along the y-axis
    float jet = exp(-abs(delta.x) * 8.0 / max(rs, 0.01))
              * exp(-abs(delta.y - rs * 0.3) * 4.0 / max(rs, 0.01));
    jet += exp(-abs(delta.x) * 6.0 / max(rs, 0.01))
          * exp(-abs(delta.y + rs * 0.3) * 4.0 / max(rs, 0.01)) * 0.5;
    return jet * 0.15;
}

// ══════════════════════════════════════════════════════════════
//  Main
// ══════════════════════════════════════════════════════════════

void main() {
    vec2 centerUV = Center1.xy * 0.5 + 0.5;     // NDC → UV
    vec2 aspect = vec2(1.0, Params.y / Params.x);   // height → width correction

    vec2 delta = (vUv - centerUV) * aspect;
    float r = length(delta);                      // aspect-corrected distance

    // ── BH parameters ───────────────────────────────────────────
    float bhRadius   = BHParams.x;                 // event horizon in UV space
    float stage      = BHParams.y;
    float progress   = BHParams.z;
    float intensity  = clamp(BHParams.w, 0.0, 5.0);
    float time       = TimePack.x;

    // Scale radius by stage (forming → grow, collapse → shrink)
    float finalRs = bhRadius;

    // Stage 3 (BLACK_HOLE forming): exponential growth from small to full
    if (stage > 2.5 && stage < 3.5) {
        float growT = 1.0 - exp(-progress * 4.0);
        finalRs *= growT;

    // Stage 5 (COLLAPSE): quadratic shrink
    } else if (stage > 4.5) {
        float shrinkT = 1.0 - progress * progress * 0.85;
        finalRs *= max(shrinkT, 0.01);
    }

    // Early-out if BH is negligible
    if (finalRs < 0.001) {
        fragColor = texture(SceneSampler, vUv);
        return;
    }

    // ════════════════════════════════════════════════════════════
    //  1. Screen-space gravitational lensing
    //  Warp the scene behind/across the black hole
    // ════════════════════════════════════════════════════════════

    vec2 lensedUv = bhGravLens(vUv, centerUV, finalRs, aspect);
    vec3 sceneLensed = texture(SceneSampler, lensedUv).rgb;
    vec3 sceneOrig   = texture(SceneSampler, vUv).rgb;

    // ════════════════════════════════════════════════════════════
    //  2. Three-layer black hole center
    // ════════════════════════════════════════════════════════════

    float normR = r / max(finalRs, 0.001);

    // ── Layer 1: Event horizon (pure black) ───────────────────
    //  r < 0.7 in normalized BH radius units
    float ehMask = 1.0 - smoothstep(0.65, 0.72, normR);

    // ── Layer 2: Photon ring (HDR, extreme brightness) ────────
    //  Sharp bright ring at r ≈ 0.8
    float ringR   = 0.80;
    float ringR2  = 0.77;   // secondary (double-orbit) ring
    float pr      = exp(-abs(normR - ringR)  * 50.0);
    float pr2     = exp(-abs(normR - ringR2) * 35.0) * 0.25;

    // ── Layer 3: Accretion disk (ray-marched) ─────────────────
    vec3 diskCol = bhAccretionDisk(delta, aspect, time, finalRs);

    // ── Photon sphere glow (volumetric, around photon ring) ────
    // Sharp inner sphere (Interstellar look): tight gaussian at photon orbit
    float sphereGlow        = exp(-abs(normR - ringR) * 60.0) * 1.2;
    // Wider outer glow for volumetric halo
    float sphereGlowWide    = exp(-abs(normR - ringR) * 22.0) * 0.55;
    // Ultra-wide ambient spill (subtle depth cue)
    float sphereGlowAmbient = exp(-abs(normR - ringR) * 8.0) * 0.18;

    // ── Corona (polar emission) ──────────────────────────────
    float corona = bhCorona(delta, finalRs);

    // ════════════════════════════════════════════════════════════
    //  4. Color assembly
    // ════════════════════════════════════════════════════════════

    // Start with gravitationally lensed scene
    vec3 color = sceneLensed;

    // Event horizon: pure black (overwrites scene)
    color = mix(color, vec3(0.0), ehMask);

    // Accretion disk (render on top, additive)
    color += diskCol * intensity;

    // Photon ring: extreme HDR → picked up by bloom + ACES
    vec3 ringColor = vec3(20.0, 15.0, 8.0) * intensity;
    color += ringColor * pr;
    color += ringColor * pr2 * 0.3;

    // Photon sphere: warm volumetric glow (3-layer composite)
    color += vec3(3.0, 2.0, 0.6) * sphereGlow * intensity;
    color += vec3(2.5, 1.7, 0.45) * sphereGlowWide * intensity;
    color += vec3(1.2, 0.8, 0.25) * sphereGlowAmbient * intensity;

    // Corona: faint purple glow at poles
    color += vec3(0.6, 0.3, 0.9) * corona * intensity;

    // ════════════════════════════════════════════════════════════
    //  5. Influence zone: smooth blend outside BH region
    // ════════════════════════════════════════════════════════════

    float influence = 1.0 - smoothstep(finalRs * 1.3, finalRs * 5.0, r);
    vec3 finalColor = mix(sceneOrig, color, influence);

    // ════════════════════════════════════════════════════════════
    //  6. Alpha = 1 — force opaque in center, override background
    // ════════════════════════════════════════════════════════════

    // Inside the influence zone: fully opaque
    float bhAlpha = max(ehMask, influence);
    bhAlpha = clamp(bhAlpha, 0.0, 1.0);

    // But outside the BH zone: preserve original alpha (≈1)
    float finalAlpha = mix(1.0, bhAlpha, influence);
    // Always opaque in the BH region to block background bleed

    fragColor = vec4(finalColor, finalAlpha);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 7: Glow Flash (预爆闪烁)
//  In:  SceneSampler (composited HDR scene)
//  Out: fragColor (scene + flashing glow center)
//
//  Bright pulsing glow at the black hole center before supernova:
//    - Central bright spot with flickering (sin × random envelope)
//    - Expanding halo rings (multiple Gaussian falloffs)
//    - Color: white core → blue/purple halo as radius grows
//    - HDR output so bloom picks up the flash
//
//  BHParams:  y=stage(6), z=preExpansionRadius, w=flashIntensity
// ══════════════════════════════════════════════════════════════════

#ifdef GLOW_FLASH

// ── Pseudo-random for flicker envelope ──
float gfHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    vec2 centerUV = Center1.xy * 0.5 + 0.5;
    vec2 delta = vUv - centerUV;
    float dist = length(delta);

    float flashIntensity  = BHParams.w;           // bell pulse 0→1→0
    float preExpansionR   = max(BHParams.z, 0.01); // progress 0→1

    if (flashIntensity < 0.01) {
        fragColor = texture(SceneSampler, vUv);
        return;
    }

    float time = TimePack.x;

    // ── Flicker: fast sine envelope ──
    float flicker = sin(time * 70.0 + gfHash(vec2(floor(time * 4.0), 0.0)) * 6.28) * 0.5 + 0.5;
    flicker = mix(flicker, 1.0, 0.3);
    float flashMod = flashIntensity * flicker;

    // Radius grows from 0 to ~1.2 screen units over the flash duration
    float ringRadius = preExpansionR * 1.2;

    // ── Core: extremely bright central spot (HDR) ──
    float core = exp(-dist * 80.0) * flashMod * 200.0;

    // ── Inner glow: softer, wider ──
    float innerGlow = exp(-dist * 15.0) * flashMod * 30.0;

    // ── Main ring: expands from center ──
    float ring = exp(-abs(dist - ringRadius) * 35.0) * flashMod * 15.0;
    // Ring echo (inner)
    ring += exp(-abs(dist - ringRadius * 0.5) * 30.0) * flashMod * 6.0;
    // Ring precursor (outer, fainter)
    ring += exp(-abs(dist - ringRadius * 1.6) * 18.0) * flashMod * 3.0;

    // ── Wide halo ──
    float wideHalo = exp(-dist / (ringRadius * 1.5 + 0.1)) * flashMod * 8.0;

    // ── Color progression ──
    // Core: pure white
    vec3 coreColor = vec3(1.0, 1.0, 1.0);
    // Ring: white → electric blue → purple
    float colorT = preExpansionR;
    vec3 ringColor = mix(vec3(1.0, 1.0, 1.0), vec3(0.4, 0.65, 1.0), smoothstep(0.0, 0.5, colorT));
    ringColor = mix(ringColor, vec3(0.6, 0.2, 1.0), smoothstep(0.5, 1.0, colorT));
    // Halo: warm purple → deep violet
    vec3 haloColor = mix(vec3(0.7, 0.35, 1.0), vec3(0.3, 0.1, 0.7), colorT);

    // ── Composite ──
    vec3 scene = texture(SceneSampler, vUv).rgb;
    vec3 glowColor = coreColor * core
                   + ringColor * (innerGlow + ring)
                   + haloColor * wideHalo;

    // Global screen brightening (very subtle)
    glowColor += vec3(0.4, 0.3, 1.0) * flashMod * 0.8;

    fragColor = vec4(scene + glowColor, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 8: Shockwave (超新星冲击波)
//  In:  SceneSampler (composited HDR scene)
//  Out: fragColor (scene with shockwave distortion + glow)
//
//  Expanding concentric shock rings in screen space:
//    - UV distortion: sin wave × radial distance
//    - Multiple wave frequencies for layered rings
//    - Brightness/blue shift at wave crests
//    - Bipolar stretch along jet axis (Y direction)
//
//  BHParams:  y=stage(7), z=shockStrength, w=shockSpeed
// ══════════════════════════════════════════════════════════════════

#ifdef SHOCKWAVE

void main() {
    vec2 centerUV = Center1.xy * 0.5 + 0.5;
    vec2 delta = vUv - centerUV;
    float dist = length(delta);
    vec2 dir = normalize(delta + 0.001);

    float strength = BHParams.z;        // shockwave amplitude
    float speed    = BHParams.w;        // expansion speed
    float time     = TimePack.x;

    // ── Primary shock ring ──
    float wave1 = sin(dist * 100.0 - time * speed * 20.0);
    float wave2 = sin(dist * 160.0 - time * speed * 35.0) * 0.4;
    float wave3 = sin(dist * 60.0  - time * speed * 10.0) * 0.6;

    // Envelope: ring expands outward over time
    float ringCenter = time * speed * 0.3;
    float envelope = exp(-abs(dist - ringCenter) * 8.0);
    envelope += exp(-abs(dist - ringCenter * 0.6) * 6.0) * 0.5;

    float wave = (wave1 + wave2 + wave3) * envelope * strength;

    // ── Bipolar stretch: stronger distortion along Y axis ──
    float bipolar = 1.0 + abs(delta.y) * 0.5;
    wave *= bipolar;

    // Clamp to prevent tearing
    wave = clamp(wave, -0.05, 0.05);

    // ── Warp UVs ──
    vec2 offsetUv = vUv + dir * wave;

    // ── Scene with shockwave ──
    vec3 scene = texture(SceneSampler, offsetUv).rgb;
    vec3 original = texture(SceneSampler, vUv).rgb;

    // ── Brightness boost + blue shift at wave crests ──
    float waveCrest = abs(wave1) * envelope * strength * 5.0;
    vec3 boost = vec3(0.6, 0.8, 1.5) * waveCrest;

    vec3 finalColor = scene + boost;

    // Fade influence near edges
    float edgeFade = 1.0 - smoothstep(0.7, 1.0, length(vUv - 0.5) * 2.0);
    finalColor = mix(original, finalColor, edgeFade);

    fragColor = vec4(finalColor, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 8b: Flash Screen (全屏闪光)
//  In:  SceneSampler (composited HDR scene)
//  Out: fragColor (scene + global white flash)
//
//  Global white flash during hypernova initiation:
//    - Quick bell-curve: 0→1→0 over ~0.3s
//    - Core: full white overlay
//    - Edge: subtle vignette preserved
//    - HDR output so bloom picks up the flash after ACES
//
//  BHParams:  z=flashIntensity(0→1), w=flashDuration
// ══════════════════════════════════════════════════════════════════

#ifdef FLASH_SCREEN

void main() {
    vec3 scene = texture(SceneSampler, vUv).rgb;

    // ── Auto-compute flash intensity from BHParams ─────────────
    float flashIntensity;
    if (abs(BHParams.y - 7.0) < 0.5) {
        // Stage 7 (FLASH): intensity directly in w (bell-curve pulse)
        flashIntensity = BHParams.w;
    } else if (abs(BHParams.y - 8.0) < 0.5) {
        // Stage 8 (HYPERNOVA): compute from shockSpeed in w
        // shockSpeed starts at 1.0 and increases → flash decays fast
        flashIntensity = exp(-(BHParams.w - 1.0) * 5.0) * 0.85;
    } else {
        flashIntensity = BHParams.z;
    }
    if (flashIntensity < 0.001) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    // ── Global white flash ──────────────────────────────────────
    // Pure white overlay, intensity driven by flash bell-curve
    float flash = flashIntensity;

    // Slight color: warm white → cool white based on intensity
    vec3 flashColor = mix(vec3(1.0, 0.95, 0.85), vec3(1.0, 1.0, 1.0), flashIntensity);

    // Preserve vignette at screen edges (flash fades at corners)
    float vignette = 1.0 - length(vUv - 0.5) * 0.5;

    // Composite: scene → partially flash → near-white at peak
    vec3 result = mix(scene, flashColor * 1.5, flash * vignette);

    // At peak flash (>0.8), push toward pure white
    result = mix(result, vec3(1.0), smoothstep(0.8, 1.0, flash) * 0.9);

    fragColor = vec4(result, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 8c: Temporal Afterimage (残影 / 拖尾)
//  In:  SceneSampler (current composited frame)
//       BloomSampler (previous frame history)
//  Out: fragColor (current + trail blend)
//
//  Blends current frame with previous frames for motion trails:
//    current + prev*0.5 + prev2*0.25
//  Creates cinematic drag / ghosting effect during hypernova.
//
//  BHParams:  z=afterimageStrength (0=none, 1=full)
// ══════════════════════════════════════════════════════════════════

#ifdef AFTERIMAGE

void main() {
    vec3 current = texture(SceneSampler, vUv).rgb;
    vec3 prev    = texture(BloomSampler, vUv).rgb;

    // Afterimage strength: strongest at hypernova onset, decays with shockSpeed
    // BHParams.w (shockSpeed) starts at 1.0 → strength = 1.0, decays to ~0.3
    float strength = 1.0 / max(BHParams.w, 0.3);
    if (strength < 0.01) {
        fragColor = vec4(current, 1.0);
        return;
    }

    // Blend: current dominates, previous frame ghosts behind
    vec3 blend = current + prev * 0.5 * strength;

    // Slight desaturation on the trail (makes it look like an afterimage)
    float lum = dot(prev, vec3(0.299, 0.587, 0.114));
    blend += lum * 0.15 * strength;

    fragColor = vec4(blend, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 9: ACES Tone Mapping (HDR → LDR)
//  In:  SceneSampler (composited HDR scene)
//  Out: fragColor (LDR 0..1)
//
//  Must run LAST, after all HDR effects are composited.
// ══════════════════════════════════════════════════════════════════

#ifdef ACES

void main() {
    vec3 hdr = texture(SceneSampler, vUv).rgb;

    // Apply ACES tone mapping (handles HDR → LDR)
    vec3 mapped = aces(hdr);

    // Subtle vignette
    vec2 uvC = vUv - 0.5;
    float vignette = 1.0 - dot(uvC, uvC) * 0.35;

    fragColor = vec4(mapped * vignette, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass: Composite (scene + bloom, pre-ACES)
//  Used internally between BLUR_V and subsequent passes.
//  Not a standalone pass — composed inline by the processor.
// ══════════════════════════════════════════════════════════════════

#ifdef COMPOSITE

void main() {
    vec3 scene = texture(SceneSampler, vUv).rgb;
    vec3 bloom = texture(BloomSampler, vUv).rgb;
    float strength = Params.z;
    fragColor = vec4(scene + bloom * strength, 1.0);
}

#endif

// ── Fallback ──────────────────────────────────────────────────────

#if !defined(BRIGHT_PASS) && !defined(BLUR_H) && !defined(BLUR_V) \
    && !defined(DISTORTION) && !defined(GODRAY) && !defined(CHROMATIC) \
    && !defined(BLACK_HOLE) && !defined(GLOW_FLASH) && !defined(SHOCKWAVE) \
    && !defined(FLASH_SCREEN) && !defined(AFTERIMAGE) \
    && !defined(ACES) && !defined(COMPOSITE)
void main() {
    fragColor = texture(SceneSampler, vUv);
}
#endif

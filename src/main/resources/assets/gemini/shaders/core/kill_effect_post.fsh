#version 330

// ═══════════════════════════════════════════════════════════════════
//  KillEffect Post-Processing — multi-pass fragment shader
//
//  Compile-time defines select the active pass:
//    BRIGHT_PASS          — luminance threshold → bloom source
//    BRIGHT_PASS_EDGE     — enhanced BRIGHT_PASS with depth-edge detection
//    BLUR_H               — separable horizontal gaussian blur
//    BLUR_V               — separable vertical gaussian blur
//    DISTORTION           — screen-space heat distortion
//    GODRAY               — screen-space radial blur from effect centers
//    VOLUMETRIC_GODRAY    — ray-marched volumetric god rays (depth-aware)
//    CHROMATIC            — RGB channel separation (chromatic aberration)
//    SCREEN_LIGHTING      — pseudo ray-traced point light: N·L + specular
//                           with depth-buffer shadow rays (blocks/entities occlude)
//    SSRT                 — screen-space ray-traced reflections
//    BLACK_HOLE           — Screen-space black hole center: event horizon, photon
//                           ring (HDR), accretion disk (ray-marched), gravitational
//                           lensing, photon sphere glow, alpha=1 override
//    GLOW_FLASH           — Supernova pre-flash: bright pulsing central spot
//    SHOCKWAVE            — Expanding concentric shock rings
//    FLASH_SCREEN         — Full-screen white flash overlay
//    AFTERIMAGE           — Temporal motion trail blending
//    ACES                 — ACES filmic tone mapping (Narkowicz 2015 fit)
//
//  Uniforms (PostUniforms, std140, 160 bytes = 10 × vec4):
//    vec4 Params:       x=fbWidth, y=fbHeight, z=bloomStrength, w=threshold
//    vec4 TimePack:     x=time(sec), y=frameIndex, zw=unused
//    vec4 Center1:      xy=effect center NDC (-1..1), z=worldDist, w=unused
//    vec4 Center2:      xy=secondary center NDC, zw=unused
//    vec4 PassParams:   x=distortionStrength, y=godRayStrength,
//                       z=chromaticStrength, w=bloomRadius
//    vec4 BHParams:     x=bhRadiusUV, y=stage, z=progress, w=intensity
//    vec4 CameraParams: x=FOV(rad), y=aspect, z=near, w=far
//    vec4 LightViewPos: xyz=light pos (view space), w=radius
//    vec4 LightColor:   rgb=light color, a=intensity
//    vec4 MiscParams:   x=ssrIntensity, y=volumetricSteps, z=chainFade, w=unused
// ═══════════════════════════════════════════════════════════════════

uniform sampler2D SceneSampler;
uniform sampler2D BloomSampler;

layout(std140) uniform PostUniforms {
    vec4 Params;        // xy=fbRes, z=bloomStrength, w=threshold    [0..15]
    vec4 TimePack;      // x=time(sec), y=frameIndex                  [16..31]
    vec4 Center1;       // xy=effectCenter1 NDC                       [32..47]
    vec4 Center2;       // xy=effectCenter2 NDC                       [48..63]
    vec4 PassParams;    // x=distort, y=godRay, z=chromatic, w=bloomRad [64..79]
    vec4 BHParams;      // x=bhRadiusUV, y=stage, z=progress, w=intensity [80..95]
    vec4 CameraParams;  // x=FOV(rad), y=aspect, z=near, w=far        [96..111]
    vec4 LightViewPos;  // xyz=light pos (view space), w=radius       [112..127]
    vec4 LightColor;    // rgb=color, w=intensity                     [128..143]
    vec4 MiscParams;    // x=ssrIntensity, y=volumetricSteps, z=chainFade [144..159]
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

// ── Shared hash ──────────────────────────────────────────────────

float postHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
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
//  Pass 1b: Bright Extract (depth-edge enhanced)
//  In:  SceneSampler (scene), DepthSampler (depth)
//  Out: fragColor (bright pixels + edge-enhanced regions)
//
//  Extends BRIGHT_PASS with depth discontinuity detection.
//  Objects near the light source get edge glow even if their
//  raw luminance is below threshold, simulating rim lighting
//  from the glow sphere.
// ══════════════════════════════════════════════════════════════════

#ifdef BRIGHT_PASS_EDGE

uniform sampler2D DepthSampler;

void main() {
    vec3 color = texture(SceneSampler, vUv).rgb;
    float depth = texture(DepthSampler, vUv).r;

    float lum = luminance(color);
    float t = Params.w;
    float knee = 0.08;

    // Standard soft-knee threshold
    float w = clamp((lum - t + knee) / (2.0 * knee), 0.0, 1.0);
    float bright = w * max(lum - t, 0.0) / max(lum, 0.001);

    // ── Depth edge detection (Sobel 3×3) ────────────────────────
    vec2 ts = vec2(1.0 / Params.x, 1.0 / Params.y);

    float dTL = texture(DepthSampler, vUv + vec2(-ts.x,  ts.y)).r;
    float dT  = texture(DepthSampler, vUv + vec2( 0.0,   ts.y)).r;
    float dTR = texture(DepthSampler, vUv + vec2( ts.x,  ts.y)).r;
    float dL  = texture(DepthSampler, vUv + vec2(-ts.x,   0.0)).r;
    float dR  = texture(DepthSampler, vUv + vec2( ts.x,   0.0)).r;
    float dBL = texture(DepthSampler, vUv + vec2(-ts.x,  -ts.y)).r;
    float dB  = texture(DepthSampler, vUv + vec2( 0.0,  -ts.y)).r;
    float dBR = texture(DepthSampler, vUv + vec2( ts.x,  -ts.y)).r;

    float gx = -dTL - 2.0*dL - dBL + dTR + 2.0*dR + dBR;
    float gy = -dTL - 2.0*dT - dTR + dBL + 2.0*dB + dBR;
    float edge = sqrt(gx*gx + gy*gy);

    // ── Proximity to light source in screen space ────────────────
    vec2 lightNDC = Center1.xy;  // already NDC [-1,1]
    vec2 ndc = vUv * 2.0 - 1.0;
    float distToLight = length(ndc - lightNDC);
    float proximity = exp(-distToLight * 2.8);

    // ── Edge boost ──────────────────────────────────────────────
    float edgeBoost = edge * 3.5 * proximity;
    float enhancedBright = bright + edgeBoost * 0.25;

    // Fade edge boost by depth (distant edges produce less glow)
    float depthFade = 1.0 - smoothstep(0.85, 1.0, depth);
    enhancedBright += edge * 1.5 * proximity * depthFade * 0.1;

    enhancedBright = clamp(enhancedBright, 0.0, 3.0);

    // Subtle blue shift at edges (Fresnel-like)
    vec3 edgeColor = vec3(0.4, 0.6, 1.0);
    color = mix(color, color + edgeColor * 0.08, edge * proximity * 0.4 * depthFade);

    fragColor = vec4(color * enhancedBright, 1.0);
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
//  Pass 4b: Volumetric God Rays (ray-marched, depth-aware)
//  In:  SceneSampler (composited HDR scene), DepthSampler (depth)
//  Out: fragColor (scene + volumetric light shafts)
//
//  Replaces screen-space radial blur with true ray-marching
//  along the view direction toward the 3D light position.
//  Density accumulates via Beer's law through a spherical
//  volume centered on the light source.  Depth buffer provides
//  correct integration bounds — rays terminate at surfaces.
//
//  Algorithm:
//    1. Reconstruct view-space position from depth + UV
//    2. Step along the ray from camera toward the light source
//    3. At each step: compute density (Gaussian around light sphere)
//    4. Accumulate in-scattered light × phase function × transmittance
//    5. Composite result over the scene
//
//  Performance: 16 steps (configurable via MiscParams.y)
// ══════════════════════════════════════════════════════════════════

#ifdef VOLUMETRIC_GODRAY

uniform sampler2D DepthSampler;

// ── Reconstruct view-space position from depth + UV ──────────────
// Returns position in camera-basis view space:
//   X = camera right, Y = camera up, Z = camera forward (positive)
vec3 viewPosFromDepth(vec2 uv, float depth) {
    float fov   = CameraParams.x;
    float aspect = CameraParams.y;
    float near  = CameraParams.z;
    float far   = CameraParams.w;

    // NDC [-1,1]
    float ndcX = uv.x * 2.0 - 1.0;
    float ndcY = uv.y * 2.0 - 1.0;
    float ndcZ = depth * 2.0 - 1.0;

    // Reconstruct linear view-space Z (camera-forward distance)
    float viewZ = 2.0 * far * near / ((far + near) - ndcZ * (far - near));

    float halfFovTan = tan(fov * 0.5);
    float viewX = ndcX * viewZ * aspect * halfFovTan;
    float viewY = ndcY * viewZ * halfFovTan;

    return vec3(viewX, viewY, viewZ);
}

// Henyey-Greenstein phase function (forward-scattering bias)
float phaseHG(float cosTheta, float g) {
    float g2 = g * g;
    float denom = 1.0 + g2 - 2.0 * g * cosTheta;
    return (1.0 - g2) / (4.0 * 3.14159265 * denom * sqrt(denom));
}

void main() {
    float strength = PassParams.y;
    vec3 scene = texture(SceneSampler, vUv).rgb;
    float depth = texture(DepthSampler, vUv).r;

    if (strength < 0.01) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    // ── Reconstruct view-space position ──────────────────────────
    vec3 viewPos = viewPosFromDepth(vUv, depth);

    // ── Light position (view space) ─────────────────────────────
    vec3 lightPos = LightViewPos.xyz;
    float lightRadius = LightViewPos.w;

    vec3 toLight = lightPos - viewPos;
    float distToLight = length(toLight);
    vec3 lightDir = toLight / max(distToLight, 0.001);

    if (distToLight > lightRadius * 4.0) {
        // Too far from light source — skip for performance
        fragColor = vec4(scene, 1.0);
        return;
    }

    // ── Ray march ───────────────────────────────────────────────
    int steps = int(MiscParams.y);
    steps = clamp(steps, 8, 32);
    float stepSize = distToLight / float(steps);

    // Jitter start position to reduce banding
    float jitter = postHash(vUv + fract(TimePack.x)) * stepSize;
    float t = jitter;

    vec3 accumulated = vec3(0.0);
    float transmittance = 1.0;

    for (int i = 0; i < 32; i++) {
        if (i >= steps) break;

        vec3 samplePos = viewPos + lightDir * t;
        float d = length(lightPos - samplePos);

        // Density: Gaussian peak at light center, zero outside radius
        float density = exp(-d * d / (lightRadius * lightRadius * 0.25));

        if (density > 0.002) {
            // Beer's law absorption + out-scattering
            float absorption = density * stepSize * 0.12;
            transmittance *= exp(-absorption);

            // Phase function (forward-scattering g=0.65)
            float cosTheta = dot(normalize(-viewPos), lightDir);
            float phase = phaseHG(cosTheta, 0.65);

            // In-scattered radiance from light source
            vec3 lightContrib = LightColor.rgb * LightColor.w
                / (1.0 + d * d * 0.0008);

            float scatterCoeff = density * stepSize * phase * transmittance * 0.025;
            accumulated += lightContrib * scatterCoeff;
        }

        t += stepSize;
        if (t > distToLight) break;
    }

    // ── Composite ───────────────────────────────────────────────
    vec3 godRays = accumulated * strength;

    // Warmer color near the light source
    float glowProximity = exp(-distToLight / (lightRadius * 1.5));
    godRays = mix(godRays, godRays * LightColor.rgb, glowProximity * 0.4);

    // Screen-blend with scene (1 - (1-scene)(1-godRays) ≈ scene + godRays)
    fragColor = vec4(scene + godRays, 1.0);
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
//  Pass 5b: Screen-Space Dynamic Lighting (pseudo ray-traced)
//  In:  SceneSampler (composited HDR scene), DepthSampler (depth)
//  Out: fragColor (scene with dynamic light contribution)
//
//  The explosion's residual light source illuminates nearby blocks and
//  entities WITHOUT touching Minecraft's lightmap or world state —
//  this is a pure post-processing overlay.
//
//  Per pixel:
//    1. Reconstruct view-space position + screen-space normal from depth
//    2. N·L wrap diffuse + Blinn-Phong specular, inverse-square falloff
//    3. SHADOW RAY MARCH (pseudo ray tracing): step from the surface
//       point toward the light, projecting each step back to screen
//       space and comparing against the depth buffer.  Any blocker —
//       blocks OR entities (both write depth) — shadows the point.
//    4. Small unshadowed ambient term so fully shadowed areas still
//      receive a faint bounce.
//
//  Budget: 1 depth + 4 normal taps + 10 shadow taps ≈ 15 samples/px.
// ══════════════════════════════════════════════════════════════════

#ifdef SCREEN_LIGHTING

uniform sampler2D DepthSampler;

// Reconstruct view-space position (shared with VOLUMETRIC_GODRAY)
vec3 slViewPosFromDepth(vec2 uv, float depth) {
    float fov    = CameraParams.x;
    float aspect = CameraParams.y;
    float near   = CameraParams.z;
    float far    = CameraParams.w;
    float ndcX = uv.x * 2.0 - 1.0;
    float ndcY = uv.y * 2.0 - 1.0;
    float ndcZ = depth * 2.0 - 1.0;
    float viewZ = 2.0 * far * near / ((far + near) - ndcZ * (far - near));
    float halfFovTan = tan(fov * 0.5);
    float viewX = ndcX * viewZ * aspect * halfFovTan;
    float viewY = ndcY * viewZ * halfFovTan;
    return vec3(viewX, viewY, viewZ);
}

// Reconstruct screen-space normal from depth gradients
vec3 screenSpaceNormal(vec2 uv, float depth, vec3 viewPos) {
    vec2 ts = vec2(1.0 / Params.x, 1.0 / Params.y);

    // Reconstruct neighbors' view-space positions
    vec3 posR = slViewPosFromDepth(uv + vec2(ts.x, 0.0),
                    texture(DepthSampler, uv + vec2(ts.x, 0.0)).r);
    vec3 posL = slViewPosFromDepth(uv - vec2(ts.x, 0.0),
                    texture(DepthSampler, uv - vec2(ts.x, 0.0)).r);
    vec3 posU = slViewPosFromDepth(uv + vec2(0.0, ts.y),
                    texture(DepthSampler, uv + vec2(0.0, ts.y)).r);
    vec3 posD = slViewPosFromDepth(uv - vec2(0.0, ts.y),
                    texture(DepthSampler, uv - vec2(0.0, ts.y)).r);

    // Check for depth discontinuities (object boundaries)
    float maxDz = max(
        max(abs(posR.z - viewPos.z), abs(posL.z - viewPos.z)),
        max(abs(posU.z - viewPos.z), abs(posD.z - viewPos.z))
    );

    // At large depth jumps, fall back to camera-facing normal
    if (maxDz > 8.0) {
        return vec3(0.0, 0.0, -1.0);
    }

    vec3 dx = normalize(posR - posL);
    vec3 dy = normalize(posU - posD);
    vec3 n = normalize(cross(dx, dy));

    // Ensure normal faces the camera
    if (dot(n, vec3(0.0, 0.0, -1.0)) < 0.0) n = -n;

    return n;
}

// Project view-space position → screen UV + expected depth buffer value
bool slProjectToScreen(vec3 pos, out vec2 screenUV, out float expectedDepth) {
    float fov    = CameraParams.x;
    float aspect = CameraParams.y;
    float near   = CameraParams.z;
    float far    = CameraParams.w;

    if (pos.z < near) return false;

    float halfFovTan = tan(fov * 0.5);
    float ndcX = pos.x / (pos.z * aspect * halfFovTan);
    float ndcY = pos.y / (pos.z * halfFovTan);

    screenUV = vec2(ndcX * 0.5 + 0.5, ndcY * 0.5 + 0.5);
    if (screenUV.x < 0.0 || screenUV.x > 1.0 || screenUV.y < 0.0 || screenUV.y > 1.0) {
        return false;
    }

    float ndcZ = (far + near - 2.0 * far * near / pos.z) / (far - near);
    expectedDepth = ndcZ * 0.5 + 0.5;
    return true;
}

// ── Pseudo ray-traced shadow ─────────────────────────────────────
// March from the surface point toward the light.  At each step the
// sample is projected to screen space; if the depth buffer there is
// closer than the sample, a block/entity occludes the light.
// Returns 1.0 = fully lit, ~0.12 = shadowed (keeps a faint bounce).
float slShadowVisibility(vec3 viewPos, vec3 N, vec3 lightPos) {
    vec3 toLight = lightPos - viewPos;
    float dist = length(toLight);
    vec3 L = toLight / max(dist, 0.001);

    // Lift the origin off the surface (acne avoidance)
    vec3 origin = viewPos + N * 0.06 + L * 0.05;
    float rayLen = dist - 0.15;
    if (rayLen <= 0.0) return 1.0;

    const int STEPS = 10;
    float stepSize = rayLen / float(STEPS);

    // Jitter the start to hide banding between steps
    float jitter = postHash(vUv * 173.0 + fract(TimePack.x) * 7.0);
    float t = stepSize * (0.5 + jitter * 0.5);

    for (int i = 0; i < STEPS; i++) {
        vec3 sp = origin + L * t;
        vec2 suv;
        float expected;
        if (slProjectToScreen(sp, suv, expected)) {
            float sceneDepth = texture(DepthSampler, suv).r;
            // Bias grows along the ray (discretization tolerance)
            float bias = 0.0012 + 0.004 * (t / rayLen);
            if (sceneDepth < expected - bias) {
                return 0.12; // occluded by a block or entity
            }
        }
        t += stepSize;
    }
    return 1.0;
}

void main() {
    vec3 lightPos = LightViewPos.xyz;
    float lightRadius = LightViewPos.w;
    vec3 lightCol = LightColor.rgb;
    float intensity = LightColor.w;

    vec3 scene = texture(SceneSampler, vUv).rgb;
    float depth = texture(DepthSampler, vUv).r;

    // ── Sky / far geometry: only subtle ambient ──────────────────
    if (depth >= 0.999) {
        float ambientDist = length(vUv - Center1.xy * 0.5 - 0.5);
        float ambient = exp(-ambientDist * 2.5) * intensity * 0.04;
        fragColor = vec4(scene + lightCol * ambient, 1.0);
        return;
    }

    // ── Reconstruct view-space position ──────────────────────────
    vec3 viewPos = slViewPosFromDepth(vUv, depth);

    // ── Distance to light ────────────────────────────────────────
    vec3 toLight = lightPos - viewPos;
    float dist = length(toLight);
    vec3 L = toLight / max(dist, 0.001);

    // ── Distance attenuation ─────────────────────────────────────
    // Inverse-square with linear term for stability
    float atten = 1.0 / (1.0 + dist * 0.09 + dist * dist * 0.032);
    float cutoff = 1.0 - smoothstep(lightRadius * 0.6, lightRadius, dist);
    atten *= cutoff;

    if (atten < 0.002) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    // ── Screen-space normal ──────────────────────────────────────
    vec3 N = screenSpaceNormal(vUv, depth, viewPos);

    // ── Pseudo ray-traced shadow (blocks + entities occlude) ─────
    float shadow = slShadowVisibility(viewPos, N, lightPos);

    // ── Diffuse (N·L, half-Lambert) ──────────────────────────────
    float NdotL = max(dot(N, L), 0.0);
    float halfLambert = NdotL * 0.5 + 0.5;
    halfLambert = halfLambert * halfLambert;

    // ── Specular (Blinn-Phong) ───────────────────────────────────
    vec3 V = normalize(vec3(0.0, 0.0, 1.0)); // view dir in camera-basis
    vec3 H = normalize(L + V);
    float specular = pow(max(dot(N, H), 0.0), 32.0);

    // ── Light contribution ───────────────────────────────────────
    vec3 lightContrib = lightCol * intensity * atten * halfLambert * shadow;
    lightContrib += lightCol * specular * atten * intensity * 0.35 * shadow;

    // Ambient bounce (simulate 1-bounce indirect, unshadowed)
    lightContrib += lightCol * intensity * atten * 0.06;

    // ── Composite: additive blend ────────────────────────────────
    // Soft clamp to prevent extreme HDR blowout in bloom
    vec3 lit = scene + lightContrib;
    float sceneLum = luminance(scene);
    float litLum = luminance(lit);
    if (litLum > 8.0 && litLum > sceneLum * 3.0) {
        lit = mix(lit, scene, smoothstep(8.0, 15.0, litLum));
    }

    fragColor = vec4(lit, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 5c: Screen-Space Ray-Traced Reflections (SSRT)
//  In:  SceneSampler (composited HDR scene), DepthSampler (depth)
//  Out: fragColor (scene + indirect illumination via reflections)
//
//  Traces reflected view rays through screen space to simulate
//  indirect lighting.  For each pixel:
//    1. Reconstruct view-space position and normal
//    2. Reflect view direction about the normal
//    3. March along the reflected ray in view space
//    4. Project each step to screen space, check depth collision
//    5. On hit: sample scene color, apply Fresnel + distance falloff
//
//  Active only near the light source (hypernova stages 7-8)
//  where indirect illumination is visually significant.
//
//  Performance: 32 steps with adaptive step size.
// ══════════════════════════════════════════════════════════════════

#ifdef SSRT

uniform sampler2D DepthSampler;

// Reconstruct view-space position (shared)
vec3 ssrtViewPosFromDepth(vec2 uv, float depth) {
    float fov    = CameraParams.x;
    float aspect = CameraParams.y;
    float near   = CameraParams.z;
    float far    = CameraParams.w;
    float ndcX = uv.x * 2.0 - 1.0;
    float ndcY = uv.y * 2.0 - 1.0;
    float ndcZ = depth * 2.0 - 1.0;
    float viewZ = 2.0 * far * near / ((far + near) - ndcZ * (far - near));
    float halfFovTan = tan(fov * 0.5);
    float viewX = ndcX * viewZ * aspect * halfFovTan;
    float viewY = ndcY * viewZ * halfFovTan;
    return vec3(viewX, viewY, viewZ);
}

// Project view-space position → screen-space UV + expected depth
// Returns false if the point is behind the camera or off-screen
bool projectToScreen(vec3 pos, out vec2 screenUV, out float expectedDepth) {
    float fov    = CameraParams.x;
    float aspect = CameraParams.y;
    float near   = CameraParams.z;
    float far    = CameraParams.w;

    float viewZ = pos.z;
    if (viewZ < near) return false;

    float halfFovTan = tan(fov * 0.5);
    float ndcX = pos.x / (viewZ * aspect * halfFovTan);
    float ndcY = pos.y / (viewZ * halfFovTan);

    screenUV = vec2(ndcX * 0.5 + 0.5, ndcY * 0.5 + 0.5);
    if (screenUV.x < 0.0 || screenUV.x > 1.0 || screenUV.y < 0.0 || screenUV.y > 1.0) {
        return false;
    }

    // Expected NDC depth at this position
    float ndcZ = (far + near - 2.0 * far * near / viewZ) / (far - near);
    expectedDepth = ndcZ * 0.5 + 0.5;
    return true;
}

void main() {
    float ssrIntensity = MiscParams.x;
    vec3 scene = texture(SceneSampler, vUv).rgb;
    float depth = texture(DepthSampler, vUv).r;

    // Skip sky (infinite depth) and when intensity is off
    if (depth >= 0.999 || ssrIntensity < 0.02) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    // ── Reconstruct ──────────────────────────────────────────────
    vec3 viewPos = ssrtViewPosFromDepth(vUv, depth);

    // Only trace near the light source
    vec3 toLight = LightViewPos.xyz - viewPos;
    float distToLight = length(toLight);
    if (distToLight > LightViewPos.w * 2.5) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    // ── Screen-space normal ──────────────────────────────────────
    vec2 ts = vec2(1.0 / Params.x, 1.0 / Params.y);
    vec3 posR = ssrtViewPosFromDepth(vUv + vec2(ts.x, 0.0),
                   texture(DepthSampler, vUv + vec2(ts.x, 0.0)).r);
    vec3 posL = ssrtViewPosFromDepth(vUv - vec2(ts.x, 0.0),
                   texture(DepthSampler, vUv - vec2(ts.x, 0.0)).r);
    vec3 posU = ssrtViewPosFromDepth(vUv + vec2(0.0, ts.y),
                   texture(DepthSampler, vUv + vec2(0.0, ts.y)).r);
    vec3 posD = ssrtViewPosFromDepth(vUv - vec2(0.0, ts.y),
                   texture(DepthSampler, vUv - vec2(0.0, ts.y)).r);

    float maxDz = max(max(abs(posR.z-viewPos.z), abs(posL.z-viewPos.z)),
                      max(abs(posU.z-viewPos.z), abs(posD.z-viewPos.z)));
    vec3 N;
    if (maxDz > 8.0) {
        N = vec3(0.0, 0.0, -1.0); // camera-facing
    } else {
        vec3 dx = normalize(posR - posL);
        vec3 dy = normalize(posU - posD);
        N = normalize(cross(dx, dy));
        if (dot(N, vec3(0.0, 0.0, -1.0)) < 0.0) N = -N;
    }

    // ── View direction ───────────────────────────────────────────
    vec3 V = normalize(vec3(0.0, 0.0, 1.0)); // toward camera (+Z in camera-basis)

    // Fresnel at this pixel
    float fresnel = pow(1.0 - abs(dot(N, V)), 5.0);
    fresnel = mix(0.04, 1.0, fresnel);

    // Only trace surfaces that can reflect (skip glancing angles for sky)
    if (fresnel < 0.05) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    // ── Reflect view direction ───────────────────────────────────
    vec3 R = reflect(-V, N);

    // ── Ray march ────────────────────────────────────────────────
    float rayDist = 0.0;
    float maxDist = distToLight * 1.3;
    int maxSteps = 32;
    float stepSize = maxDist / float(maxSteps);

    // Jitter
    rayDist += postHash(vUv + fract(TimePack.x * 100.0)) * stepSize * 0.5;

    vec3 hitColor = vec3(0.0);
    float hitWeight = 0.0;

    for (int i = 0; i < 32; i++) {
        if (i >= maxSteps || rayDist > maxDist) break;

        vec3 rayPos = viewPos + R * rayDist;
        vec2 screenUV;
        float expectedDepth;

        if (!projectToScreen(rayPos, screenUV, expectedDepth)) {
            rayDist += stepSize;
            continue;
        }

        float actualDepth = texture(DepthSampler, screenUV).r;
        float depthDiff = actualDepth - expectedDepth;

        // Collision: surface is in front of the ray point
        if (depthDiff > -stepSize * 0.8 && depthDiff < stepSize * 3.0) {
            vec3 reflectedColor = texture(SceneSampler, screenUV).rgb;

            // Distance falloff along the ray
            float distAtten = 1.0 / (1.0 + rayDist * rayDist * 0.005);

            // Proximity to light source (closer = brighter reflection)
            vec3 hitViewPos = ssrtViewPosFromDepth(screenUV, actualDepth);
            float hitDistToLight = length(LightViewPos.xyz - hitViewPos);
            float lightProximity = exp(-hitDistToLight / LightViewPos.w);

            hitColor = reflectedColor * fresnel * distAtten * lightProximity;
            hitWeight = 1.0;
            break;
        }

        // Adaptive step (larger steps further from camera)
        float adaptiveStep = stepSize * (0.5 + abs(rayPos.z) * 0.015);
        rayDist += max(adaptiveStep, stepSize * 0.25);
    }

    // ── Composite ────────────────────────────────────────────────
    float blend = ssrIntensity * 0.55;

    // Edge fade (reflections tend to disappear at screen edges)
    float edgeFade = 1.0 - smoothstep(0.7, 1.0, length(vUv - 0.5) * 2.0);
    blend *= edgeFade;

    // Stronger blend for rough surfaces (low fresnel = rough)
    blend *= mix(0.4, 1.0, fresnel);

    vec3 result = scene + hitColor * blend;

    fragColor = vec4(result, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 6: Black Hole Center (screen-space, HDR)
//  In:  SceneSampler (composited HDR scene with 3D BH, bloom, etc.)
//  Out: fragColor (scene + screen-space BH center)
//
//  Renders the black hole in screen space (n = radius / horizon radius):
//    1. Event horizon    — pure black disk, crisp edge (n < 1)
//    2. Horizon outline  — thin bright rim at the capture radius (n ≈ 1)
//    3. Photon ring      — extreme HDR ring at the photon sphere (n ≈ 1.3)
//    4. Accretion disk   — Keplerian differential rotation (ω ∝ n^-1.5),
//                          spiral density waves + fbm turbulence, relativistic
//                          doppler beaming, blackbody temperature ramp
//    + Screen-space gravitational lensing on the real scene background
//    + Photon sphere volumetric glow + polar corona
//
//  Runs BEFORE ACES so HDR photon ring survives bloom extraction
//  and tone mapping.
// ══════════════════════════════════════════════════════════════════

#ifdef BLACK_HOLE

// ── Pseudo-random hash (for accretion disk turbulence) ──────
float bhHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float bhNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(bhHash(i), bhHash(i + vec2(1.0, 0.0)), f.x),
               mix(bhHash(i + vec2(0.0, 1.0)), bhHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float bhFbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * bhNoise(p);
        p *= 2.1;
        a *= 0.5;
    }
    return v;
}

// ══════════════════════════════════════════════════════════════
//  Screen-space gravitational lensing
//
//  Warps UV coordinates toward the black hole center, with
//  deflection strongest near the photon sphere and falling
//  off as 1/b².  Produces the characteristic "einstein ring"
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

    // Critical boost at photon sphere (r ≈ 1.3 Rs in UV space)
    // Dramatically enhanced for Interstellar-like background warping
    float rPhoton = rs * 1.3;
    float distToPhoton = abs(b - rPhoton);
    alpha *= 1.0 + exp(-distToPhoton * 25.0 / max(rs, 0.01)) * 6.0;

    // Clamp to avoid tearing at extreme angles
    alpha = min(alpha, 0.55);

    vec2 dir = delta / max(d, 0.001);
    return uv + dir * alpha / aspect;
}

// ══════════════════════════════════════════════════════════════
//  Accretion disk — Keplerian rotation + doppler beaming
//
//  Geometrically thin, optically thick disk in normalized radius
//  units (n = r / Rs;  event horizon at n = 1):
//    - Emissivity peaks just outside the ISCO (n ≈ 1.6)
//    - Differential rotation: ω ∝ n^-1.5 (inner disk orbits faster)
//    - Relativistic doppler beaming: approaching side brighter/bluer
//    - Blackbody temperature ramp: blue-white inner → red outer
// ══════════════════════════════════════════════════════════════

vec3 bhAccretionDisk(vec2 delta, float time, float rs, float rotSpeed) {
    float safeRs = max(rs, 0.001);

    // A steeply inclined equatorial plane reads as a disk instead of a halo.
    vec2 diskP = vec2(delta.x, (delta.y + safeRs * 0.035) / 0.235);
    float diskR = length(diskP);
    float n = diskR / safeRs;
    float screenN = length(delta) / safeRs;
    if (n > 8.4 && screenN > 3.8) return vec3(0.0);

    float ang = atan(diskP.y, diskP.x);
    float omega = 5.4 * pow(max(n, 1.0), -1.5) * rotSpeed;
    float rotAng = ang - time * omega;

    float diskMask = smoothstep(1.48, 1.72, n)
                   * (1.0 - smoothstep(7.2, 8.35, n));

    float profile = exp(-max(n - 1.7, 0.0) * 0.70);
    profile += 0.52 * exp(-abs(n - 2.65) * 1.9);
    profile += 0.28 * exp(-abs(n - 4.15) * 1.4);
    profile += 0.13 * exp(-abs(n - 6.35) * 1.1);

    float spiralA = 0.5 + 0.5 * sin(rotAng * 2.0 + log(max(n, 1.01)) * 10.5);
    float spiralB = 0.5 + 0.5 * sin(rotAng * 5.0 - log(max(n, 1.01)) * 6.5 + 1.4);
    float turb = bhFbm(vec2(rotAng * 2.2, n * 3.3) + vec2(time * 0.10, 0.0));
    float filaments = smoothstep(0.34, 0.78,
            spiralA * 0.48 + spiralB * 0.20 + turb * 0.56);
    float darkLane = smoothstep(0.28, 0.66,
            bhFbm(vec2(rotAng * 4.1 + 8.0, n * 6.8 - time * 0.16)));
    float emission = diskMask * profile
                   * (0.16 + filaments * 1.20 + turb * 0.34)
                   * mix(0.52, 1.0, darkLane);

    // Relativistic Doppler beaming and temperature shift.
    float approaching = clamp(-diskP.x / max(diskR, 0.0001), -1.0, 1.0);
    float beam = pow(max(0.38, 1.0 + approaching * 0.72), 2.35);

    float temp = clamp((n - 1.55) / 6.3, 0.0, 1.0);
    vec3 col = mix(vec3(0.78, 0.90, 1.35), vec3(1.35, 0.58, 0.08),
                   smoothstep(0.02, 0.43, temp));
    col = mix(col, vec3(0.48, 0.035, 0.008), smoothstep(0.43, 1.0, temp));
    col *= mix(vec3(1.32, 0.77, 0.52), vec3(0.68, 0.86, 1.38),
               approaching * 0.5 + 0.5);

    // Far-side matter is bent over the shadow into a thin Einstein arc.
    float farArc = exp(-abs(screenN - 1.58) * 18.0);
    farArc *= smoothstep(-0.18 * safeRs, 0.72 * safeRs, delta.y);
    farArc *= 0.32 + 0.68 * smoothstep(0.0, 2.6 * safeRs, abs(delta.x));
    float arcNoise = 0.62 + 0.38 * bhNoise(vec2(rotAng * 5.0, time * 0.35));
    vec3 arcCol = mix(vec3(1.45, 0.45, 0.06), vec3(1.0, 0.92, 0.72),
                      clamp(approaching * 0.5 + 0.5, 0.0, 1.0));

    return col * emission * beam * 2.75 + arcCol * farArc * arcNoise * 1.8;
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

    // Early-out: negligible, or fully outside the influence zone
    float n = r / max(finalRs, 0.001);
    if (finalRs < 0.001 || n > 9.0) {
        fragColor = texture(SceneSampler, vUv);
        return;
    }

    // Stage-entry smooth ramp (forming only — collapse shrinks instead)
    float stageRamp = 1.0;
    if (stage > 2.5 && stage < 3.5) {
        stageRamp = smoothstep(0.0, 0.10, progress);
    } else if (stage > 4.5) {
        // Collapse: emissions (disk, photon ring, rim, glow, corona) die
        // out before the VOID stage — the pass deactivates at the boundary,
        // so anything still emitting would pop off.
        stageRamp = 1.0 - smoothstep(0.60, 0.92, progress);
    }

    // ════════════════════════════════════════════════════════════
    //  1. Screen-space gravitational lensing
    //  Warp the scene behind/across the black hole
    // ════════════════════════════════════════════════════════════

    vec2 lensedUv = bhGravLens(vUv, centerUV, finalRs, aspect);
    // Blend the lensing by stageRamp too — it grows during forming and
    // relaxes during collapse, so the warped region never pops.
    vec3 sceneLensed = texture(SceneSampler, mix(vUv, lensedUv, stageRamp)).rgb;
    vec3 sceneOrig   = texture(SceneSampler, vUv).rgb;

    // ════════════════════════════════════════════════════════════
    //  2. Black hole components (n = radius in event-horizon units)
    // ════════════════════════════════════════════════════════════

    // ── Event horizon: pure black disk with a crisp edge ─────
    float eh = 1.0 - smoothstep(0.97, 1.01, n);

    // ── Event horizon outline: thin bright rim right at the edge ──
    // (light trapped at the capture radius outlines the shadow)
    float rim = exp(-abs(n - 1.02) * 55.0);

    // ── Photon ring: ultra-thin HDR ring at the photon sphere ──
    float pr  = exp(-abs(n - 1.32) * 42.0);
    float pr2 = exp(-abs(n - 1.22) * 28.0) * 0.30;

    // ── Accretion disk (Keplerian rotation, doppler beamed) ──
    float rotSpeed = (stage > 4.5) ? 2.2 : 1.0;  // spin-up during collapse
    vec3 diskCol = bhAccretionDisk(delta, time, finalRs, rotSpeed);

    // ── Photon-sphere volumetric glow ──
    float sphereGlow     = exp(-abs(n - 1.3) * 7.0) * 0.50;
    float sphereGlowWide = exp(-abs(n - 1.6) * 3.0) * 0.20;

    // ── Corona (polar emission) ──
    float corona = bhCorona(delta, finalRs);

    // ════════════════════════════════════════════════════════════
    //  3. Color assembly
    // ════════════════════════════════════════════════════════════

    // Start with the gravitationally lensed scene
    vec3 color = sceneLensed;

    // Event horizon: pure black (overwrites scene)
    color = mix(color, vec3(0.0), eh);

    // Accretion disk (additive over the lensed background)
    color += diskCol * intensity * stageRamp;

    // Photon ring: extreme HDR → picked up by bloom + ACES
    color += vec3(18.0, 13.0, 7.0) * pr * intensity * stageRamp;
    color += vec3(18.0, 13.0, 7.0) * pr2 * 0.4 * intensity * stageRamp;

    // Event horizon outline rim
    color += vec3(1.6, 1.2, 0.45) * rim * intensity * stageRamp;

    // Photon-sphere volumetric glow
    color += vec3(2.2, 1.5, 0.50) * sphereGlow * intensity * stageRamp;
    color += vec3(1.2, 0.8, 0.25) * sphereGlowWide * intensity * stageRamp;

    // Corona: faint purple glow at poles
    color += vec3(0.6, 0.3, 0.9) * corona * intensity * stageRamp;

    // ════════════════════════════════════════════════════════════
    //  4. Influence zone: smooth blend outside the BH region
    // ════════════════════════════════════════════════════════════

    float influence = 1.0 - smoothstep(finalRs * 1.4, finalRs * 8.7, r);
    influence = max(influence, eh); // horizon is always fully opaque black
    vec3 finalColor = mix(sceneOrig, color, influence);

    fragColor = vec4(finalColor, 1.0);
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
    // Die out fully before the hypernova stage — this pass deactivates
    // at the boundary, and any residual core brightness would pop off.
    flashMod *= 1.0 - smoothstep(0.85, 1.0, preExpansionR);

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
//  Enhanced expanding blast:
//    - Three concentric shells (primary front + two echoes), each with
//      easeOutCubic expansion driven by hypernova progress
//    - Refraction warp: UVs bent along the radial direction at crests
//    - Chromatic split: R/B channels separate at the wave front
//    - HDR crest brightness (feeds bloom), white → electric blue ramp
//    - Radial glow: hot expanding core fading behind the front
//    - Angular turbulence on the front (debris look)
//
//  BHParams:  y=stage(8), z=hypernova progress (0→1), w=intensity (merge)
// ══════════════════════════════════════════════════════════════════

#ifdef SHOCKWAVE

void main() {
    vec2 centerUV = Center1.xy * 0.5 + 0.5;
    vec2 aspect = vec2(1.0, Params.y / Params.x);
    vec2 delta = (vUv - centerUV) * aspect;
    float dist = length(delta);
    vec2 dir = delta / max(dist, 0.0001);

    float p = clamp(BHParams.z, 0.0, 1.0);   // hypernova progress
    float intensity = BHParams.w;            // merge multiplier

    // easeOutCubic expansion: fast detonation, slow settle
    float e = 1.0 - pow(1.0 - p, 3.0);

    // ── Three concentric shells ─────────────────────────────────
    float R1 = e * 1.35;
    float R2 = e * 0.95 + 0.015;
    float R3 = e * 0.60 + 0.008;

    // Angular turbulence: the front is not a perfect circle
    float ang = atan(delta.y, delta.x);
    float turb = 0.80 + 0.20 * sin(ang * 14.0 + dist * 30.0 + p * 18.0)
                      * sin(ang * 5.0 - p * 9.0);

    float w1 = exp(-abs(dist - R1 * turb) * 26.0);
    float w2 = exp(-abs(dist - R2 * turb) * 22.0) * 0.55;
    float w3 = exp(-abs(dist - R3 * turb) * 18.0) * 0.30;

    // Delayed shells turn the longer hypernova into a sequence of detonations.
    float p4 = clamp((p - 0.22) / 0.78, 0.0, 1.0);
    float e4 = 1.0 - pow(1.0 - p4, 3.0);
    float w4 = exp(-abs(dist - e4 * 1.22 * turb) * 28.0)
             * smoothstep(0.0, 0.08, p4)
             * (1.0 - smoothstep(0.88, 1.0, p4)) * 0.78;

    float p5 = clamp((p - 0.50) / 0.50, 0.0, 1.0);
    float e5 = 1.0 - pow(1.0 - p5, 2.6);
    float w5 = exp(-abs(dist - e5 * 1.02 * turb) * 24.0)
             * smoothstep(0.0, 0.10, p5)
             * (1.0 - smoothstep(0.84, 1.0, p5)) * 0.58;

    // ── Refraction warp: bend UVs radially at the crests ────────
    float warp = (w1 * 0.040 + w2 * 0.024 + w3 * 0.014
                + w4 * 0.029 + w5 * 0.022) * intensity;
    warp *= smoothstep(0.0, 0.04, p);       // ease in, no pop
    warp *= 1.0 - p * 0.5;                  // calm down as it expands
    vec2 warpedUv = vUv - dir * warp / aspect;

    // ── Chromatic split at the wave front ───────────────────────
    float chroma = (w1 * 0.008 + w2 * 0.004 + w4 * 0.005 + w5 * 0.004)
                 * intensity * (1.0 - p * 0.45);
    vec2 cOff = dir * chroma / aspect;
    vec3 scene;
    scene.r = texture(SceneSampler, warpedUv + cOff).r;
    scene.g = texture(SceneSampler, warpedUv).g;
    scene.b = texture(SceneSampler, warpedUv - cOff).b;

    // ── HDR crest brightness: white-hot → electric blue ─────────
    float crest = (w1 * 3.4 + w2 * 1.8 + w3 * 1.0 + w4 * 2.35 + w5 * 1.75)
                * (1.0 - p * 0.42) * intensity;
    vec3 crestCol = mix(vec3(1.0, 0.98, 0.92), vec3(0.55, 0.72, 1.0),
                        clamp(p * 1.4, 0.0, 1.0));
    scene += crestCol * crest;

    // ── Radial glow: hot core fading behind the expanding front ──
    // The final smoothstep guarantees zero at the stage boundary —
    // this pass deactivates when afterglow begins.
    float glowR = max(R1, 0.03);
    float reigniteA = (p - 0.38) / 0.11;
    float reigniteB = (p - 0.64) / 0.09;
    float coreReignite = exp(-reigniteA * reigniteA) * 0.85
                       + exp(-reigniteB * reigniteB) * 0.55;
    float radialGlow = exp(-dist * 3.5 / glowR)
                     * (exp(-p * 2.2) * 2.15 + coreReignite) * intensity;
    radialGlow *= 1.0 - smoothstep(0.85, 1.0, p);
    scene += vec3(1.0, 0.75, 0.45) * radialGlow;

    // Fade influence near screen edges
    float edgeFade = 1.0 - smoothstep(0.75, 1.0, length(vUv - 0.5) * 2.0);
    vec3 original = texture(SceneSampler, vUv).rgb;
    fragColor = vec4(mix(original, scene, edgeFade), 1.0);
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
        // Stage 8 (HYPERNOVA): z=progress, w=intensity multiplier.
        // Fast exponential decay after detonation; smoothstep ease-in over
        // the first ~5% so the white-out ramps up instead of popping on.
        float p = BHParams.z;
        float primary = exp(-p * 6.0) * 1.0;
        float reignitePhase = (p - 0.34) / 0.055;
        float reignite = exp(-reignitePhase * reignitePhase) * 0.42;
        flashIntensity = (primary + reignite) * BHParams.w;
        flashIntensity *= smoothstep(0.0, 0.05, p);
    } else {
        flashIntensity = BHParams.z;
    }
    if (flashIntensity < 0.001) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    // ── Global white flash ──────────────────────────────────────
    // Re-shape through smoothstep: zero-slope onset, no harsh snap.
    float flash = clamp(flashIntensity, 0.0, 1.0);
    flash = flash * flash * (3.0 - 2.0 * flash);

    // Slight color: warm white → cool white based on intensity
    vec3 flashColor = mix(vec3(1.0, 0.95, 0.85), vec3(1.0, 1.0, 1.0), flash);

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

    // Afterimage strength from hypernova progress (BHParams.z):
    // strongest at detonation, quadratic decay as the explosion settles.
    float strength = 1.0 - BHParams.z;
    strength *= strength;
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
//
//  chainFade (MiscParams.z) crossfades between the raw (clamped) scene
//  and the tone-mapped + vignetted output.  Without this, the moment
//  the post chain activates the ENTIRE screen would suddenly shift
//  brightness/contrast — the "fullscreen flash" bug.  With the fade
//  the filmic look eases in over ~0.9s (smoothstep, driven from Java).
// ══════════════════════════════════════════════════════════════════

#ifdef ACES

void main() {
    vec3 hdr = texture(SceneSampler, vUv).rgb;
    float chainFade = clamp(MiscParams.z, 0.0, 1.0);

    // Apply ACES tone mapping (handles HDR → LDR)
    vec3 mapped = aces(hdr);

    // Subtle vignette (scaled in with the chain)
    vec2 uvC = vUv - 0.5;
    float vignette = 1.0 - dot(uvC, uvC) * 0.35;

    vec3 graded = mapped * vignette;
    vec3 raw    = clamp(hdr, 0.0, 1.0);

    fragColor = vec4(mix(raw, graded, chainFade), 1.0);
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

#if !defined(BRIGHT_PASS) && !defined(BRIGHT_PASS_EDGE) \
    && !defined(BLUR_H) && !defined(BLUR_V) \
    && !defined(DISTORTION) && !defined(GODRAY) && !defined(VOLUMETRIC_GODRAY) \
    && !defined(CHROMATIC) && !defined(SCREEN_LIGHTING) && !defined(SSRT) \
    && !defined(BLACK_HOLE) && !defined(GLOW_FLASH) && !defined(SHOCKWAVE) \
    && !defined(FLASH_SCREEN) && !defined(AFTERIMAGE) \
    && !defined(ACES) && !defined(COMPOSITE)
void main() {
    fragColor = texture(SceneSampler, vUv);
}
#endif

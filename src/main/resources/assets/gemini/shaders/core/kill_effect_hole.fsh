#version 330

// ═══════════════════════════════════════════════════════════════════
//  Black Hole — relativistic gravitational lensing shader
//
//  vertexColor.r = time progress (0→1) within current stage
//  vertexColor.g = stage ID (3=forming, 4=accretion, 5=collapse)
//  vertexColor.b = brightness multiplier (from Java, includes mergeCount)
//  vertexColor.a = master alpha
//
//  Physical model:
//    - Schwarzschild metric with photon sphere at r = 1.5 Rs
//    - Shadow radius = √27 Rs ≈ 2.6 Rs (due to light capture)
//    - Accretion disk in equatorial plane, lensed by gravity
//    - Relativistic Doppler beaming on approaching/receding sides
//    - Background starlight stretched into Einstein arcs
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

// ══════════════════════════════════════════════════════════════════
//  Noise functions (for disk turbulence)
// ══════════════════════════════════════════════════════════════════

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i+vec2(1,0)), f.x),
               mix(hash(i+vec2(0,1)), hash(i+vec2(1,1)), f.x), f.y);
}

float fbm(vec2 p) {
    float v=0.0, a=0.5;
    for(int i=0; i<4; i++){ v+=a*noise(p); p*=2.0; a*=0.5; }
    return v;
}

// ══════════════════════════════════════════════════════════════════
//  Easing functions (for transition animations)
// ══════════════════════════════════════════════════════════════════

float easeOutExpo(float t)  { return t >= 1.0 ? 1.0 : 1.0 - pow(2.0, -10.0 * t); }
float easeInExpo(float t)   { return t <= 0.0 ? 0.0 : pow(2.0, 10.0 * (t - 1.0)); }
float easeInOutCubic(float t){ return t < 0.5 ? 4.0*t*t*t : 1.0-pow(-2.0*t+2.0,3.0)/2.0; }

// ══════════════════════════════════════════════════════════════════
//  Gravitational lensing: ray deflection
//
//  In the weak-field limit, deflection angle α ∝ 1 / impact_parameter.
//  Near the photon sphere, deflection diverges (photons orbit).
//  We model this with a softened 1/b deflection + critical-impact boost.
// ══════════════════════════════════════════════════════════════════

vec2 applyLensing(vec2 uv, float rs, float time, float stage) {
    float d = length(uv);
    if (d < 0.001) return uv;

    float b = d; // impact parameter in UV space
    vec2 dir = uv / b;

    // Base deflection: α ∝ Rs / b (softened near the singularity)
    float alpha = rs * 0.18 / (b * b + rs * rs * 0.04);

    // Critical boost near the photon sphere (r ~ 1.5 Rs)
    float rPhoton   = rs * 1.5;
    float distToPhoton = abs(b - rPhoton);
    float criticalBoost = 1.0 + exp(-distToPhoton * 30.0 / rs) * 2.5;

    // Stronger lensing during accretion/collapse stages
    float stageBoost = 1.0;
    if (stage > 3.5 && stage < 4.5) stageBoost = 1.4;  // accretion
    if (stage > 4.5)                 stageBoost = 1.8;  // collapse

    alpha *= criticalBoost * stageBoost;

    // Deflect rays radially inward
    return uv + dir * alpha;
}

// ══════════════════════════════════════════════════════════════════
//  Accretion disk — equatorial plane, gravitationally lensed
//
//  The disk lies in the equatorial plane (horizontal in world space).
//  On our camera-facing billboard:
//    - The "front" of the disk maps to the bottom half
//    - The "back" is lensed and appears as arcs above the shadow
//  Returns: (disk brightness, disk temperature 0=cool 1=hot)
// ══════════════════════════════════════════════════════════════════

vec2 accretionDisk(vec2 uvLensed, float rs, float angle, float time, float stage) {
    float safeRs = max(rs, 0.012);
    float shadowRadius = safeRs * 2.6;
    float innerRadius = safeRs * 2.75;
    float outerRadius = safeRs * 4.35;

    // A strongly inclined ellipse reads as a disk instead of another halo.
    vec2 diskUv = vec2(uvLensed.x, (uvLensed.y + safeRs * 0.04) / 0.24);
    float diskR = length(diskUv);
    float n = diskR / safeRs;
    float diskAngle = atan(diskUv.y, diskUv.x);

    float diskMask = smoothstep(innerRadius * 0.88, innerRadius, diskR)
                   * (1.0 - smoothstep(outerRadius * 0.88, outerRadius, diskR));

    // Preserve phase across stage boundaries while accelerating toward collapse.
    float phase = time;
    if (stage > 3.5 && stage < 4.5) phase = 1.0 + time * 1.65;
    if (stage > 4.5) phase = 2.65 + time * 2.25;
    float omega = 6.2 * pow(max(n, 1.0), -1.5);
    float rotatingAngle = diskAngle - phase * omega;

    // Layered gas lanes: two main spiral arms, fine filaments, and dark gaps.
    float armA = 0.5 + 0.5 * sin(rotatingAngle * 2.0 + log(max(n, 1.01)) * 11.0);
    float armB = 0.5 + 0.5 * sin(rotatingAngle * 5.0 - log(max(n, 1.01)) * 7.0 + 1.7);
    float turbulence = fbm(vec2(rotatingAngle * 2.3, n * 3.8) + vec2(phase * 0.18, 0.0));
    float filaments = smoothstep(0.42, 0.78, armA * 0.55 + armB * 0.20 + turbulence * 0.55);
    float dustLanes = smoothstep(0.30, 0.62,
            fbm(vec2(rotatingAngle * 4.0 + 7.0, n * 7.0 - phase * 0.25)));

    float emissivity = exp(-max(n - 2.8, 0.0) * 0.72);
    emissivity += exp(-abs(n - 3.35) * 2.8) * 0.50;
    emissivity += exp(-abs(n - 4.15) * 3.5) * 0.24;
    float diskFront = diskMask * emissivity
                    * (0.20 + filaments * 1.10 + turbulence * 0.38)
                    * mix(0.62, 1.0, dustLanes);

    // The far side is bent above the horizon into a thin Einstein arc.
    vec2 arcUv = vec2(uvLensed.x, uvLensed.y * 0.78);
    float arcR = length(arcUv);
    float farArc = exp(-abs(arcR - shadowRadius * 1.34) * 34.0 / safeRs);
    farArc *= smoothstep(-safeRs * 0.25, safeRs * 0.75, uvLensed.y);
    farArc *= 0.28 + 0.72 * smoothstep(0.0, shadowRadius * 1.5, abs(uvLensed.x));
    farArc *= 0.48 + turbulence * 0.40;

    float stageGain = 1.0;
    if (stage > 2.5 && stage < 3.5) stageGain = smoothstep(0.10, 0.78, time);
    if (stage > 3.5 && stage < 4.5) stageGain = 1.38;
    if (stage > 4.5) stageGain = (1.38 + time * 1.6) * (1.0 - smoothstep(0.78, 1.0, time));

    float diskTemp = clamp((diskR - innerRadius) / max(outerRadius - innerRadius, 0.001), 0.0, 1.0);
    return vec2((diskFront + farArc) * stageGain, diskTemp);
}

// ══════════════════════════════════════════════════════════════════
//  Main
// ══════════════════════════════════════════════════════════════════

void main() {
    float time      = vertexColor.r;
    float stage     = vertexColor.g * 8.0;    // stage packed normalized (stage/8)
    float brightness = vertexColor.b * 4.0;   // intensity packed normalized (x/4)
    float alpha     = vertexColor.a;

    // ════════════════════════════════════════════════════════════
    //  Stage-dependent parameters
    // ════════════════════════════════════════════════════════════

    float rs = 0.22;  // Schwarzschild radius in UV space

    // Event horizon and shadow evolve with stage
    float shadowRadius = rs * 2.6; // apparent black hole shadow

    if (stage > 2.5 && stage < 3.5) {
        // Stage 3 (forming): grow from nothing via smoothstep
        float formT = easeOutExpo(time);
        rs     *= formT;
        shadowRadius = rs * 2.6;
    } else if (stage > 4.5) {
        // Stage 5 (collapse): shrink via ease-in-expo (accelerating collapse)
        float collapseT = easeInExpo(time);
        rs     *= (1.0 - collapseT * 0.85);
        shadowRadius = rs * 2.6;
    }

    // ════════════════════════════════════════════════════════════
    //  Coordinate space
    // ════════════════════════════════════════════════════════════

    vec2 uv = (uvCoord - 0.5) * 2.0;  // [-1, 1] centered
    float d = length(uv);
    float angle = atan(uv.y, uv.x);

    // ════════════════════════════════════════════════════════════
    //  Gravitational lensing
    // ════════════════════════════════════════════════════════════

    vec2 uvLensed = applyLensing(uv, rs, time, stage);
    float dLensed = length(uvLensed);
    float angleLensed = atan(uvLensed.y, uvLensed.x);

    // ════════════════════════════════════════════════════════════
    //  Event horizon shadow
    // ════════════════════════════════════════════════════════════

    // The "shadow" is larger than the event horizon due to light capture.
    // Any ray with impact parameter < √27 Rs ≈ 2.6 Rs falls into the hole.
    float insideShadow = 1.0 - smoothstep(shadowRadius - 0.01, shadowRadius + 0.005, dLensed);

    // Soft penumbra just outside the shadow (photons barely escape, appear dim)
    float penumbra = 1.0 - exp(-(dLensed - shadowRadius) * 60.0 / rs);
    penumbra = clamp(penumbra * insideShadow, 0.0, 1.0);

    // ════════════════════════════════════════════════════════════
    //  Accretion disk (gravitationally lensed)
    // ════════════════════════════════════════════════════════════

    vec2 diskResult = accretionDisk(uvLensed, rs, angleLensed, time, stage);
    float diskBrightness = diskResult.x;
    float diskTemp       = diskResult.y;

    // ════════════════════════════════════════════════════════════
    //  Doppler beaming
    //
    //  The accretion disk rotates at relativistic speeds.
    //  Material on the left side (in our view) moves toward us
    //  and appears brighter/bluer. Right side recedes, dimmer/redder.
    // ════════════════════════════════════════════════════════════

    // Doppler boost: depends on the x-position (left/right on screen)
    float doppler = uvLensed.x * 0.5 + 0.5;  // 0=left(approaching), 1=right(receding)
    float beaming = 1.0 + (1.0 - doppler) * 2.5;  // approaching side 3.5× brighter

    // Apply beaming to disk brightness
    diskBrightness *= (0.3 + beaming * 0.7);

    // ════════════════════════════════════════════════════════════
    //  Photon ring (ultra-thin, extreme brightness)
    //
    //  Photons that orbit the black hole at r = 1.5 Rs create an
    //  infinitely thin, bright ring. Due to lensing, this appears
    //  at the shadow boundary (~2.6 Rs) as seen by distant observers.
    // ════════════════════════════════════════════════════════════

    float photonRingRadius = shadowRadius * 1.02;

    // Primary photon ring: razor-thin, extreme HDR
    float photonRing = exp(-abs(dLensed - photonRingRadius) * 250.0);

    // Secondary ring (photons that orbited twice, fainter)
    float photonRing2 = exp(-abs(dLensed - photonRingRadius * 0.97) * 180.0) * 0.3;

    // ════════════════════════════════════════════════════════════
    //  Background star distortion
    //
    //  Distant stars are lensed into thin arcs near the shadow.
    //  We generate a star field and distort it using the lens map.
    // ════════════════════════════════════════════════════════════

    float stars = 0.0;
    vec3 starColor = vec3(0.0);

    // Generate pseudo-random stars and check if their lensed position
    // lands near this pixel
    for (int i = 0; i < 8; i++) {
        float fi = float(i);
        // Original star position (in UV space, far from the black hole)
        vec2 starOrig = vec2(
            hash(vec2(fi, 0.3)) * 4.0 - 2.0,
            hash(vec2(fi, 0.7)) * 4.0 - 2.0
        );
        float starOrigDist = length(starOrig);

        // Only stars that pass near the black hole are visibly lensed
        if (starOrigDist < 2.5 && starOrigDist > shadowRadius * 0.5) {
            // Lens the star's position
            vec2 starLensed = applyLensing(starOrig, rs, time, stage);
            float starDist = length(uv - starLensed);

            // Star appears as a small bright dot, stretched into an arc
            float starBright = hash(vec2(fi, 0.9)) * 0.7 + 0.3;
            float starSize = 0.008 + starBright * 0.015;

            // Stretch factor: stars near the shadow are stretched tangentially
            float stretchFactor = 1.0 / max(dLensed - shadowRadius, 0.02);
            float tangentialDist = abs(atan(uv.y, uv.x) - atan(starLensed.y, starLensed.x));
            tangentialDist /= (3.14159 * 2.0);
            float radialDist = abs(d - length(starLensed));

            // Arc contribution: stretched tangentially, thin radially
            float arc = exp(-radialDist * radialDist / (starSize * starSize * 0.3))
                      * exp(-tangentialDist * tangentialDist / (stretchFactor * 0.04));
            arc *= clamp(stretchFactor * 0.03, 0.1, 1.0);

            stars += arc * starBright;

            // Star color varies
            float starTemp = hash(vec2(fi, 1.1));
            starColor += mix(
                mix(vec3(0.5, 0.7, 1.0), vec3(1.0, 1.0, 1.0), starTemp),
                mix(vec3(1.0, 0.9, 0.5), vec3(1.0, 0.6, 0.3), starTemp * 0.5),
                step(0.5, starTemp)
            ) * arc * starBright;
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Color assembly
    // ════════════════════════════════════════════════════════════

    // Disk thermal gradient: hot inner (blue-white) → cool outer (red-orange)
    vec3 diskInner  = vec3(0.7, 0.85, 1.0);   // blue-white hot (inner disk)
    vec3 diskMid    = vec3(1.0, 0.65, 0.1);    // warm orange
    vec3 diskOuter  = vec3(0.8, 0.2, 0.03);    // deep red-orange (outer disk)

    float t = clamp(diskTemp, 0.0, 1.0);
    vec3 diskColor = mix(diskInner, diskMid, smoothstep(0.0, 0.4, t));
    diskColor = mix(diskColor, diskOuter, smoothstep(0.4, 1.0, t));

    // Doppler color shift: approaching = blue, receding = red
    vec3 diskBlue = diskColor * vec3(0.7, 0.8, 1.3);
    vec3 diskRed  = diskColor * vec3(1.3, 0.8, 0.6);
    diskColor = mix(diskBlue, diskRed, doppler);

    // Photon ring color: extreme HDR blue-white
    vec3 photonRingColor = vec3(14.0, 9.0, 5.0);

    // ════════════════════════════════════════════════════════════
    //  Assemble
    // ════════════════════════════════════════════════════════════

    vec3 rgb = vec3(0.0);

    // Accretion disk (with Doppler beaming)
    rgb += diskColor * diskBrightness * 1.1;

    // Photon ring (ultra-bright, thin, sits at shadow boundary)
    rgb += photonRingColor * photonRing * 1.5;
    rgb += photonRingColor * photonRing2 * 0.5;

    // Background star arcs
    rgb += starColor * stars * 0.6;

    // Subtle photon sphere glow (volumetric scatter around the shadow)
    float photonGlow = exp(-abs(dLensed - shadowRadius) * 5.0 / rs) * 0.12;
    rgb += vec3(2.0, 3.0, 5.0) * photonGlow;

    // Corona: faint vertical emission from the poles
    float coronaV = exp(-abs(uv.y) * 1.0) * exp(-dLensed * 0.8) * 0.08;
    rgb += vec3(0.3, 0.15, 0.7) * coronaV;

    // Brightness boost during collapse
    if (stage > 4.5) {
        float collapseBoost = 1.0 + time * 4.0;
        rgb *= collapseBoost;
        // Photon ring gets brighter as hole shrinks (energy concentration)
        photonRingColor *= collapseBoost;
    }

    // ════════════════════════════════════════════════════════════
    //  Transition animations
    // ════════════════════════════════════════════════════════════

    float transitionAlpha = alpha;

    if (stage > 2.5 && stage < 3.5) {
        // Stage 3 (forming): fade in from nothing
        // Use easeOutExpo for a dramatic "snap into existence" feel
        float formAlpha = easeOutExpo(time * 1.2); // slightly faster than linear
        transitionAlpha *= formAlpha;

        // Brief intense flash at formation
        float birthFlash = exp(-time * 4.0) * 2.5;
        rgb += vec3(1.0, 0.9, 0.6) * birthFlash * 0.5;
    } else if (stage > 4.5) {
        // Stage 5 (collapse): the hole visually shrinks.
        // Alpha stays at 1.0 throughout — the hole disappears
        // because it shrinks to zero radius, not because it fades.
        // But we add a slight final fade in the last 15% for smoothness.
        if (time > 0.85) {
            float fadeOut = 1.0 - (time - 0.85) / 0.15;
            transitionAlpha *= fadeOut;
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Final alpha + output
    // ════════════════════════════════════════════════════════════

    // Alpha is driven by the visible components
    float effectAlpha = photonRing * 0.95
                      + photonRing2 * 0.4
                      + diskBrightness * 0.8
                      + stars * 0.5
                      + photonGlow * 0.3;

    // Inside the shadow: pure black, no emission
    // The world-space pipeline is additive, so only emissive structures
    // contribute here. The screen-space pass supplies the opaque black shadow.

    // Ensure photon ring is always visible if present
    effectAlpha = max(effectAlpha, photonRing * 0.9);

    float finalAlpha = effectAlpha * transitionAlpha;

    fragColor = vec4(rgb * brightness, finalAlpha) * ColorModulator;

    if (fragColor.a < 0.0005) {
        discard;
    }
}

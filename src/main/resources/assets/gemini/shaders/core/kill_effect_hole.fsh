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
    if (stage > 2.5 && stage < 4.5) stageBoost = 1.4;  // accretion
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
    float dLens = length(uvLensed);
    float shadowRadius = rs * 2.6;  // apparent shadow (light capture radius)

    // Disk exists outside the ISCO (innermost stable circular orbit ≈ 3Rs)
    float iscoRadius = rs * 3.0;
    // Disk extends outward
    float diskOuter = rs * 8.0;

    // ── Front of disk (directly visible, below the shadow) ────────
    // The disk plane maps to a horizontal line at y ≈ -shadowRadius * 0.3
    // (We view it slightly above the equatorial plane)
    float diskPlaneY = -shadowRadius * 0.25;
    float diskFront = 0.0;
    float diskTemp = 0.0;

    // Sample the lensed UV at the disk plane
    float diskY = uvLensed.y;
    float diskX = uvLensed.x;
    float diskR = length(vec2(diskX, diskY - diskPlaneY));

    // Front disk: visible directly below the shadow
    if (diskY < diskPlaneY && diskR > iscoRadius * 0.5 && diskR < diskOuter) {
        // Disk density: peaks at ISCO, falls off outward
        float profile = exp(-abs(diskR - iscoRadius) * 0.8 / rs);
        profile += exp(-abs(diskR - iscoRadius * 1.8) * 0.5 / rs) * 0.5;
        profile += exp(-abs(diskR - iscoRadius * 3.0) * 0.3 / rs) * 0.25;
        profile = clamp(profile, 0.0, 1.0);

        // Spiral turbulence
        float spiralAngle = atan(diskX, diskY - diskPlaneY);
        float spiral = sin(spiralAngle * 3.0 + log(diskR + 0.1) * 10.0 - time * 4.0) * 0.5 + 0.5;
        float turb = fbm(vec2(spiralAngle * 3.0, diskR * 5.0) + time * 0.3) * 0.4;

        diskFront = profile * (spiral * 0.6 + turb * 0.4 + 0.3);

        // Temperature: hotter near ISCO (inner), cooler outward
        diskTemp = clamp((diskR - iscoRadius) / (diskOuter - iscoRadius), 0.0, 1.0);
    }

    // ── Back of disk (gravitationally lensed over the top) ─────────
    // Light from the back of the disk is bent around the black hole
    // and appears as arcs above the shadow
    float diskBack = 0.0;
    float diskBackTemp = 0.0;

    // The back of the disk appears at y > 0 (lensed over the top)
    if (diskY > -shadowRadius * 0.5 && dLens > shadowRadius * 0.7) {
        // Lensed image of the back disk
        float backY = diskY;
        float backX = diskX;
        float backR = length(vec2(backX, backY));

        // The back disk image is stretched and appears at various radii
        float backProfile = exp(-abs(backR - shadowRadius * 1.5) * 2.0 / rs);
        backProfile += exp(-abs(backR - shadowRadius * 2.0) * 1.5 / rs) * 0.6;

        // Fade at extreme angles
        float angleFade = 1.0 - abs(uvLensed.y) / (shadowRadius * 3.0 + 0.01);
        angleFade = clamp(angleFade, 0.0, 1.0);

        // Only visible when the lensed ray traces back to the disk plane
        float backVisibility = smoothstep(shadowRadius * 0.8, shadowRadius * 1.2, backR);
        backVisibility *= smoothstep(diskOuter, iscoRadius, backR);

        diskBack = backProfile * angleFade * backVisibility * 0.7;
        diskBackTemp = clamp((backR - shadowRadius) / (shadowRadius * 3.0), 0.0, 1.0);
    }

    float totalDisk = diskFront + diskBack;
    // Blend temperatures: front provides primary temp, back is cooler (redshifted)
    float blendedTemp = diskFront > 0.01 ? diskTemp : diskBackTemp;
    if (diskFront > 0.01 && diskBack > 0.01) {
        blendedTemp = mix(diskTemp, diskBackTemp, diskBack / (diskFront + diskBack));
    }

    return vec2(totalDisk, blendedTemp);
}

// ══════════════════════════════════════════════════════════════════
//  Main
// ══════════════════════════════════════════════════════════════════

void main() {
    float time      = vertexColor.r;
    float stage     = vertexColor.g;
    float brightness = vertexColor.b;
    float alpha     = vertexColor.a;

    // ════════════════════════════════════════════════════════════
    //  Stage-dependent parameters
    // ════════════════════════════════════════════════════════════

    float rs = 0.22;  // Schwarzschild radius in UV space

    // Event horizon and shadow evolve with stage
    float shadowRadius = rs * 2.6; // apparent black hole shadow

    if (stage < 2.5) {
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

    if (stage < 2.5) {
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
    effectAlpha *= insideShadow;

    // Penumbra darkening
    effectAlpha *= penumbra;

    // Ensure photon ring is always visible if present
    effectAlpha = max(effectAlpha, photonRing * 0.9);

    float finalAlpha = effectAlpha * transitionAlpha;

    fragColor = vec4(rgb * brightness, finalAlpha) * ColorModulator;

    if (fragColor.a < 0.0005) {
        discard;
    }
}

// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  BlackHolePet — accretion plasma (disk mode + lensed arc mode)
//
//  Thin-disk emission with the physics that makes a black hole read as one:
//    - multi-colour disk profile  F ∝ n^-3 (1 − sqrt(nIn/n)), n in Rs
//    - Keplerian shear            ω ∝ n^-3/2, so inner filaments overtake
//    - relativistic Doppler beaming from the orbital velocity
//      v = sqrt(M/r) → v/c = 1/sqrt(2n), boosted by g^3, approaching limb
//      brighter and bluer
//    - gravitational redshift     sqrt(1 − 1/n) dims and reddens the inner rim
//  Radii arriving through BHUniforms are in blocks; n = vRadius is in Rs.
//
//  Mode 1 reuses the same emission for the lensed band, masked so it only
//  survives where it wraps over and under the shadow.
// ═══════════════════════════════════════════════════════════════════

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform BHUniforms {
    vec4 Time;      // x: seconds, y: mode, z: spin speed, w: accretion flare
    vec4 Center;    // xyz: pet centre, camera-relative; w: Schwarzschild radius
    vec4 AxisX;     // xyz: disk basis e1; w: inner radius (blocks)
    vec4 AxisY;     // xyz: disk basis e2; w: outer radius (blocks)
    vec4 AxisZ;     // xyz: disk normal; w: band thickness (blocks)
    vec4 Motion;    // x: noise scale, y: turbulence, z: jet length (blocks), w: redshift
    vec4 Primary;   // rgb: inner/hot colour; w: opacity
    vec4 Secondary; // rgb: outer/cool colour; w: brightness
    vec4 Accent;    // rgb: jet/ring tint; w: doppler amount
    vec4 Misc;      // x: palette, y: rainbow speed, z: arc radius (blocks), w: arc span
};

in vec2 vUv;
in vec3 vLocal;
in vec3 vVel;
in float vRadius;

out vec4 fragColor;

const float TAU = 6.28318530718;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * noise2(p);
        p = p * 2.13 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

// Blackbody-flavoured ramp over the disk's temperature, in HDR units.
vec3 temperature(float t, int palette) {
    if (palette == 1) {  // Plasma: violet corona into a white-hot inner rim
        vec3 c = mix(vec3(2.6, 2.9, 3.6), vec3(1.1, 0.55, 2.6), smoothstep(0.0, 0.55, t));
        return mix(c, vec3(0.28, 0.06, 0.55), smoothstep(0.55, 1.0, t));
    }
    if (palette == 2) {  // Ice: pale cyan to deep blue
        vec3 c = mix(vec3(2.4, 3.2, 3.9), vec3(0.45, 1.25, 2.4), smoothstep(0.0, 0.5, t));
        return mix(c, vec3(0.08, 0.2, 0.7), smoothstep(0.5, 1.0, t));
    }
    if (palette == 3) {  // Void: magenta inner rim smothered into crimson
        vec3 c = mix(vec3(3.4, 1.1, 2.9), vec3(1.5, 0.2, 0.75), smoothstep(0.0, 0.5, t));
        return mix(c, vec3(0.32, 0.02, 0.12), smoothstep(0.5, 1.0, t));
    }
    if (palette == 4) {  // Spectrum: hue swept along radius and time
        return 0.55 + 0.45 * cos(TAU * (vec3(t * 0.9 - Time.x * Misc.y,
                                            t * 0.9 + 0.34, t * 0.9 + 0.67)
                                        + vec3(0.0, 0.12, 0.24)));
    }
    if (palette == 5) {  // Custom: the two picked colours, hot core outward
        return mix(Primary.rgb, Secondary.rgb, clamp(t, 0.0, 1.0)) * 2.1;
    }
    // 0: Gargantua — pale gold inner rim cooling to ember at the edge
    vec3 c = mix(vec3(3.1, 3.0, 2.75), vec3(2.3, 1.15, 0.32), smoothstep(0.0, 0.42, t));
    c = mix(c, vec3(0.75, 0.16, 0.03), smoothstep(0.42, 0.82, t));
    return mix(c, vec3(0.16, 0.02, 0.005), smoothstep(0.82, 1.0, t));
}

[shader("fragment")]

void main() {
    int mode = int(Time.y + 0.5);
    int palette = int(Misc.x + 0.5);
    float rs = max(Center.w, 0.0001);
    float n = max(vRadius, 0.05);

    // ── Radial structure ──
    float nIn = max(AxisX.w / rs, 1.02);
    float nOut = max(AxisY.w / rs, nIn + 0.4);
    float tNorm = clamp((n - nIn) / max(nOut - nIn, 0.001), 0.0, 1.0);

    // Standard thin-disk shape, F ∝ n^-p (1 − sqrt(nIn/n)), scaled so the peak
    // sits near 0.6 of a fully lit fragment. Un-normalised the curve tops out at
    // 0.107, which at pet scale put the whole disk a decade below the lensed arc
    // and the jets — it rendered, it just read as nothing.
    float x = n / nIn;
    float profile = pow(x, -1.45) * (1.0 - sqrt(1.0 / max(x, 1.0001))) * 6.0;
    profile = clamp(profile, 0.0, 1.0);

    // ── Filaments, sampled in the frame that co-rotates with the flow ──
    // Differential rotation has to be applied to a 2D field, never to a 1D angle.
    // ω ∝ n^-3/2 sweeps tens of radians of sheared angle between the inner rim
    // and the outer edge within seconds, so every sin(phase) or fbm(phase, …)
    // term resolves as a stack of concentric fringes — the "target" the first
    // build drew. In co-rotating plane coordinates the same shear just winds the
    // turbulence into spiral arms, which is what it physically is anyway.
    float omega = 1.0 / pow(max(n, 1.05), 1.5);
    float angle = vUv.y * TAU - Time.x * omega * Time.z * 3.2;
    vec2 flow = vec2(cos(angle), sin(angle)) * n;

    // Wound finer than a pixel only aliases, so the high frequency octave relaxes
    // as the disk ages while the arms keep shearing.
    float wound = 1.0 / (1.0 + Time.x * Time.z * 0.05);
    float arms = fbm(flow * Motion.x * 1.15 + vec2(Time.x * 0.02, -Time.x * 0.017));
    float eddies = fbm(flow * Motion.x * (1.5 + 2.4 * wound) + vec2(5.3, 2.1));
    float filaments = smoothstep(0.30, 0.84,
            arms * 0.62 + eddies * 0.5 * Motion.y + 0.06);
    float lanes = smoothstep(0.26, 0.72,
            fbm(flow * Motion.x * 2.3 + vec2(4.3, 7.1) - Time.x * 0.03));

    // ── Relativistic beaming ──
    vec3 world = Center.xyz + vLocal * rs;
    vec3 toCamera = normalize(-world);
    float beta = clamp(1.0 / sqrt(2.0 * max(n, 0.6)), 0.0, 0.74);
    float gamma = 1.0 / sqrt(max(1.0 - beta * beta, 0.05));
    float mu = dot(normalize(vVel), toCamera);
    float g = 1.0 / max(gamma * (1.0 - beta * mu), 0.12);
    float beam = pow(g, 3.0);
    float doppler = mix(1.0, beam, clamp(Accent.w, 0.0, 1.0));
    vec3 shift = mix(vec3(1.28, 0.72, 0.46), vec3(0.66, 0.85, 1.42), mu * 0.5 + 0.5);

    // ── Gravitational redshift: the inner rim loses energy on the way out ──
    float redshift = sqrt(max(1.0 - 1.0 / max(n, 1.0001), 0.06));
    float gravityDim = mix(1.0, redshift, clamp(Motion.w, 0.0, 1.0));

    // ── A single hot spot rides the inner disk, so the pet feels alive ──
    float spotAngle = Time.x * Time.z * 0.55;
    float spotDelta = abs(fract((vUv.y - spotAngle / TAU) * 0.5 + 0.5) - 0.5) * TAU;
    float spot = exp(-spotDelta * spotDelta * 26.0)
               * (1.0 - smoothstep(nIn, nIn + 1.1, n)) * 1.35;

    float emission = profile * (0.16 + filaments * 1.1 + arms * 0.3)
                   * mix(0.45, 1.0, lanes) * doppler * gravityDim
                   * (1.0 + spot) * (1.0 + Time.w * 0.85);

    vec3 hot = temperature(tNorm, palette);

    // ── Edge shaping ──
    float innerFade = smoothstep(nIn - 0.05, nIn + 0.30, n);
    float outerFade = 1.0 - smoothstep(nOut - 0.9, nOut, n);
    float mask = innerFade * outerFade;

    if (mode == 1) {
        // The band only reads where it clears the shadow: keep the halves that
        // wrap above and below, and let it dissolve into the real disk plane.
        float along = abs(vUv.y * 2.0 - 1.0);
        float wrapMask = smoothstep(0.18, 0.92, along);
        float across = abs(vUv.x * 2.0 - 1.0);
        float bandMask = 1.0 - smoothstep(0.35, 1.0, across);
        float flare = exp(-abs(n - Misc.z / rs) * 7.0);
        // Modulate along the band only. The disk's co-rotating field is not
        // usable here: v runs over the wrap angle while n still spans the band's
        // width, so reusing it painted the arc as more concentric rings.
        float band = 0.55 + 0.45 * noise2(vec2(vUv.y * 6.0, Time.x * 0.11));
        emission = (0.35 + flare * 1.6) * (0.55 + band * 0.9)
                 * wrapMask * bandMask * doppler * gravityDim * (1.0 + Time.w) * 0.25;
        // The second image is the inner disk seen from the far side, so it runs
        // hotter than the radius it happens to land on.
        hot = mix(temperature(0.12, palette), hot, 0.35);
        mask = 1.0;
    }

    vec3 col = hot * shift;

    // Additive pipeline: the magnitude lives entirely in rgb, and alpha stays
    // zero so the pass never disturbs the destination alpha channel.
    vec3 emissive = col * max(emission, 0.0) * mask * Primary.w * Secondary.w
                  * ColorModulator.rgb;
    fragColor = vec4(emissive, 0.0);

    if (max(emissive.r, max(emissive.g, emissive.b)) < 0.0025) discard;
}

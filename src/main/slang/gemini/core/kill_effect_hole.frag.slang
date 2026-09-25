// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  Black Hole — the depth-tested half of the hole
//
//  vertexColor.r = time progress (0→1) within current stage
//  vertexColor.g = stage ID (3=forming, 4=accretion, 5=collapse)
//  vertexColor.b = brightness multiplier (from Java, includes mergeCount)
//  vertexColor.a = master alpha
//
//  The pipeline draws the hole twice, on purpose, because neither half can do
//  the other's job:
//
//    * this pass is a world-space, depth-tested quad, so blocks and entities in
//      front of the hole correctly hide it;
//    * the BLACK_HOLE post pass owns the opaque shadow and the lensing of the
//      real scene, which an additive world pass cannot express.
//
//  So the split is: everything emissive and radially symmetric lives here (the
//  photon ring, the photon-sphere glow, the lensed starfield, the polar haze);
//  the accretion disk lives in the post pass, where DiskPlane can tilt it to
//  match the camera. It used to be drawn in both, at radii that disagreed by
//  5×, which is what made the outer disk read as two stacked fans.
//
//  Physical model:
//    - Schwarzschild metric with photon sphere at r = 1.5 Rs
//    - Shadow radius = √27 Rs ≈ 2.6 Rs (due to light capture)
//    - Background starlight stretched into Einstein arcs
//
//  The hole's size is animated in Java (KillEffectInstance.blackHoleSizeWorld),
//  which also feeds the post pass its shadow radius. Do not rescale rs here —
//  a second curve on this side put the ring inside the screen-space shadow and
//  erased it.
// ═══════════════════════════════════════════════════════════════════

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

// ══════════════════════════════════════════════════════════════════
//  Constants shared with KillEffectInstance
// ══════════════════════════════════════════════════════════════════

// Schwarzschild radius in billboard UV, where the quad spans [-1, 1].
const float RS_UV = 0.22;
// Light capture widens the apparent shadow to 2.6 Rs. KillEffectInstance
// .HOLE_SHADOW_UV must stay equal to this.
const float SHADOW_UV = RS_UV * 2.6;

// ══════════════════════════════════════════════════════════════════
//  Noise (for the lensed starfield)
// ══════════════════════════════════════════════════════════════════

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

// ══════════════════════════════════════════════════════════════════
//  Easing functions (for transition animations)
// ══════════════════════════════════════════════════════════════════

float easeOutExpo(float t)  { return t >= 1.0 ? 1.0 : 1.0 - pow(2.0, -10.0 * t); }

// ══════════════════════════════════════════════════════════════════
//  Gravitational lensing: ray deflection
//
//  In the weak-field limit, deflection angle α ∝ 1 / impact_parameter.
//  Near the photon sphere, deflection diverges (photons orbit).
//  We model this with a softened 1/b deflection + critical-impact boost.
// ══════════════════════════════════════════════════════════════════

vec2 applyLensing(vec2 uv, float rs, float stage) {
    float d = length(uv);
    if (d < 0.001) return uv;

    vec2 dir = uv / d;

    // Base deflection: α ∝ Rs / b (softened near the singularity)
    float alpha = rs * 0.18 / (d * d + rs * rs * 0.04);

    // Critical boost near the photon sphere (r ~ 1.5 Rs)
    float distToPhoton = abs(d - rs * 1.5);
    alpha *= 1.0 + exp(-distToPhoton * 30.0 / rs) * 2.5;

    // Stronger lensing during accretion/collapse stages
    float stageBoost = 1.0;
    if (stage > 3.5 && stage < 4.5) stageBoost = 1.4;  // accretion
    if (stage > 4.5)                 stageBoost = 1.8;  // collapse
    alpha *= stageBoost;

    // Sample further out than we are: the scene reads as pulled toward the hole
    return uv + dir * alpha;
}

// ══════════════════════════════════════════════════════════════════
//  Main
// ══════════════════════════════════════════════════════════════════

[shader("fragment")]

void main() {
    float time      = vertexColor.r;
    float stage     = vertexColor.g * 8.0;    // stage packed normalized (stage/8)
    float brightness = vertexColor.b * 4.0;   // intensity packed normalized (x/4)
    float alpha     = vertexColor.a;

    float rs = RS_UV;
    float shadowRadius = SHADOW_UV;

    // ════════════════════════════════════════════════════════════
    //  Coordinate space
    // ════════════════════════════════════════════════════════════

    vec2 uv = (uvCoord - 0.5) * 2.0;  // [-1, 1] centered
    float d = length(uv);

    // ════════════════════════════════════════════════════════════
    //  Gravitational lensing
    // ════════════════════════════════════════════════════════════

    vec2 uvLensed = applyLensing(uv, rs, stage);
    float dLensed = length(uvLensed);

    // ════════════════════════════════════════════════════════════
    //  Photon ring (ultra-thin, extreme brightness)
    //
    //  Photons that orbit at r = 1.5 Rs are imaged at the shadow boundary
    //  (~2.6 Rs) for a distant observer — so the ring sits on the shadow edge,
    //  at the same radius the post pass puts its own rim.
    // ════════════════════════════════════════════════════════════

    float photonRingRadius = shadowRadius * 1.02;
    float photonRing = exp(-abs(dLensed - photonRingRadius) * 250.0);

    // Secondary ring: photons that orbited twice, fainter and just outside
    float photonRing2 = exp(-abs(dLensed - photonRingRadius * 1.05) * 160.0) * 0.25;

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
            vec2 starLensed = applyLensing(starOrig, rs, stage);

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

    // Photon ring color: extreme HDR blue-white
    vec3 photonRingColor = vec3(14.0, 9.0, 5.0);

    // Collapse concentrates the same energy into a shrinking ring: the whole
    // hole brightens, and the ring more than the rest of it.
    float collapseBoost = (stage > 4.5) ? 1.0 + time * 4.0 : 1.0;
    photonRingColor *= collapseBoost;

    vec3 rgb = vec3(0.0);

    // Photon ring (ultra-bright, thin, sits at shadow boundary)
    rgb += photonRingColor * photonRing * 1.5;
    rgb += photonRingColor * photonRing2 * 0.5;

    // Background star arcs
    rgb += starColor * stars * 0.6;

    // Subtle photon sphere glow (volumetric scatter around the shadow)
    float photonGlow = exp(-abs(dLensed - shadowRadius) * 5.0 / rs) * 0.12;
    rgb += vec3(2.0, 3.0, 5.0) * photonGlow;

    // Corona: faint haze along the disk plane, where the post pass's disk
    // meets the shadow
    float coronaV = exp(-abs(uv.y) * 1.0) * exp(-dLensed * 0.8) * 0.08;
    rgb += vec3(0.3, 0.15, 0.7) * coronaV;

    rgb *= collapseBoost;

    // ════════════════════════════════════════════════════════════
    //  Transition animations
    // ════════════════════════════════════════════════════════════

    float transitionAlpha = alpha;

    if (stage > 2.5 && stage < 3.5) {
        // Stage 3 (forming): fade in from nothing, with a brief birth flash
        transitionAlpha *= easeOutExpo(time * 1.2);
        float birthFlash = exp(-time * 4.0) * 2.5;
        rgb += vec3(1.0, 0.9, 0.6) * birthFlash * 0.5;
    } else if (stage > 4.5) {
        // Stage 5 (collapse): the hole visually shrinks — Java drives that by
        // shrinking the billboard, so alpha stays at 1.0 and only the last 15%
        // fades so the quad's edge never pops.
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
                      + stars * 0.5
                      + photonGlow * 0.3;

    // Ensure photon ring is always visible if present
    effectAlpha = max(effectAlpha, photonRing * 0.9);

    float finalAlpha = effectAlpha * transitionAlpha;

    fragColor = vec4(rgb * brightness, finalAlpha) * ColorModulator;

    if (fragColor.a < 0.0005) {
        discard;
    }
}

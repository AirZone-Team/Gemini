// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  BlackHolePet — gravitational lensing of the frame around the hole
//
//  This pass owns the one thing world geometry cannot express: the scene
//  behind the pet being bent on its way past the hole. Everything it reads is
//  already rendered — including the 3D accretion disk — so bending the frame
//  here is what folds the far rim of that disk around the shadow, the way a
//  real image plane would.
//
//  Model:
//    - deflection  α ∝ Rs²/b, softened just outside the capture radius, where
//      the bending angle diverges and the photon ring is imaged
//    - frame dragging swirls the sample direction around the hole (Kerr-like),
//      which is what makes the sky look dragged rather than simply magnified
//    - surface brightness is conserved under lensing, so the squeezed annulus
//      near the ring gains a faint magnification sheen instead of a bloom blob
//    - inside the shadow the frame is captured outright
//
//  Distances are measured in the width-normalised UV metric the rest of the
//  repo's post passes use: delta = (uv - centre) * (1, height/width), so a
//  radius R there spans R * width pixels.
// ═══════════════════════════════════════════════════════════════════

uniform sampler2D SceneSampler;

layout(std140) uniform BHPostUniforms {
    vec4 Params;  // x: framebuffer width, y: height, z: seconds, w: unused
    vec4 Lens;    // xy: pet centre in UV, z: shadow radius (width UV), w: strength
    vec4 Look;    // x: photon ring, y: swirl, z: capture, w: chromatic
    vec4 Tint;    // rgb: ring colour, w: unused
};

in vec2 vUv;
out vec4 fragColor;

[shader("fragment")]

void main() {
    vec2 aspect = vec2(1.0, Params.y / max(Params.x, 1.0));
    vec2 delta = (vUv - Lens.xy) * aspect;
    float d = length(delta);

    float rs = max(Lens.z, 0.0006);
    float strength = Lens.w;
    float reach = rs * 9.0;

    vec3 scene = texture(SceneSampler, vUv).rgb;
    if (strength <= 0.001 || d > reach) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    vec2 dir = delta / max(d, 1e-5);

    // Outside the capture radius the bending angle runs away; inside, the ray
    // never arrives, so clamp the sampling radius to the photon ring.
    float b = max(d, rs * 1.04);
    float bend = rs * rs * 1.28 / (b * b + rs * rs * 0.10);
    bend = min(bend, rs * 3.2) * strength;

    // Frame dragging: the closer the ray passes, the further its image is
    // twisted about the hole.
    float swirl = Look.y * rs * rs / (b * b + rs * rs * 0.28) * 1.6;
    float cs = cos(swirl), sn = sin(swirl);
    vec2 twisted = vec2(dir.x * cs - dir.y * sn, dir.x * sn + dir.y * cs);

    // The displaced sample is measured from the lens, not from this pixel:
    // adding the offset to vUv would drag the whole frame sideways instead of
    // pulling the outer sky inwards around the shadow.
    vec2 sampleUv = clamp(Lens.xy + twisted * (b + bend) / aspect,
            vec2(0.0015), vec2(0.9985));

    // A real lens does split the image by wavelength, but at this radius the
    // full-strength split painted the whole rim as a rainbow blob.
    float chroma = Look.w * rs * rs / (b * b + rs * rs * 0.4) * 0.012;
    vec3 warped;
    warped.r = texture(SceneSampler, clamp(sampleUv + dir * chroma / aspect,
            vec2(0.0015), vec2(0.9985))).r;
    warped.g = texture(SceneSampler, sampleUv).g;
    warped.b = texture(SceneSampler, clamp(sampleUv - dir * chroma / aspect,
            vec2(0.0015), vec2(0.9985))).b;

    // Magnification sheen: the annulus squeezed against the ring carries more
    // sky per pixel than it does area.
    float magnify = 1.0 + 0.55 * pow(rs / b, 2.2);
    warped *= magnify;

    // Light the pass does not deliver is simply gone.
    float capture = 1.0 - smoothstep(rs * 1.02, rs * 4.2, d);
    warped *= 1.0 - Look.z * capture * 0.75;

    // Fade the warp out over the reach so the edge of the lens leaves no seam.
    float influence = 1.0 - smoothstep(rs * 1.02, reach, d);
    scene = mix(scene, warped, influence);

    // The shadow itself, kept crisp against the softened geometry edge.
    scene = mix(vec3(0.0), scene, smoothstep(rs * 0.99, rs * 1.05, d));

    // Photon ring: orbiting light imaged at the shadow's edge, plus the much
    // fainter second image of the same ring one orbit later.
    float ring = exp(-abs(d - rs * 1.045) / (rs * 0.052));
    float ring2 = exp(-abs(d - rs * 1.135) / (rs * 0.085)) * 0.22;
    float shimmer = 0.9 + 0.1 * sin(atan(delta.y, delta.x) * 24.0 + Params.z * 5.0);
    scene += Tint.rgb * (ring + ring2) * Look.x * shimmer;

    fragColor = vec4(scene, 1.0);
}

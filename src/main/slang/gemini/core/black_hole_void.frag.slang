// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  BlackHolePet — event horizon shading
//
//  Drawn with a normal (over) blend and depth writes, so the horizon carves a
//  genuinely lightless hole in whatever was already rendered and occludes the
//  far half of the disk. The silhouette softens towards the rim, where photons
//  skimming the sphere drag a faint tint of the disk palette across it.
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
in vec3 vNormal;
in vec3 vWorld;

out vec4 fragColor;

const float TAU = 6.28318530718;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

[shader("fragment")]

void main() {
    vec3 toCamera = normalize(-vWorld);
    float facing = max(dot(normalize(vNormal), toCamera), 0.0);

    // Fresnel: only the very edge of the sphere is visible at all, which is
    // what keeps a small dark marble from reading as a flat disc.
    float rim = pow(1.0 - facing, 2.6);

    // Quantum shimmer on the rim so the hole never looks like a painted dot.
    float ripple = 0.5 + 0.5 * sin(vUv.x * TAU * 6.0 + Time.x * (1.4 + Time.z)
                                   + vUv.y * 9.0);
    rim *= 0.82 + 0.18 * ripple;

    int palette = int(Misc.x + 0.5);
    vec3 tint = palette == 5 ? Secondary.rgb : Accent.rgb;
    vec3 rimColor = mix(vec3(0.05, 0.06, 0.12), tint * 0.45, 0.7);

    vec3 core = vec3(0.002, 0.0015, 0.004);
    fragColor = vec4(mix(core, rimColor, rim), Primary.w * (1.0 - rim * 0.35))
              * ColorModulator;
}

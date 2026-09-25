// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  BlackHolePet — jet shading
//
//  Emission is a soft column with internal working (shock) fronts marching
//  outward, and it is beamed the way a real blazar jet is: the pole turning
//  towards the camera burns blue-white, the one turning away sinks to a dim
//  ember. That asymmetry is view-dependent physics, not a facing trick — the
//  tube itself keeps its world orientation.
//
//  vUv.x = distance along the axis 0→1, vUv.y = around the tube.
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
in vec3 vWorld;
in float vSide;

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

[shader("fragment")]

void main() {
    float along = vUv.x;
    vec3 toCamera = normalize(-vWorld);
    vec3 axis = normalize(AxisZ.xyz) * vSide;

    // Relativistic beaming of the two poles.
    float towards = dot(axis, toCamera);
    float beam = mix(1.0, pow(max(1.0 + towards * 0.9, 0.08), 2.6),
                     clamp(Accent.w, 0.0, 1.0));

    // Footpoint is the hottest thing on screen; the shaft dies off as it
    // entrains air, and it never pops at the tip.
    float core = exp(-along * 2.85);
    float tip = 1.0 - smoothstep(0.55, 1.0, along);

    float shocks = 0.55 + 0.45 * sin(along * 22.0 - Time.x * (5.0 + Time.z * 4.0));
    float grain = noise2(vec2(along * 16.0, vUv.y * 7.0 - Time.x * 1.8));
    float texture_ = 0.62 + 0.38 * mix(shocks, grain, 0.45) + Motion.y * 0.25 * grain;

    float emission = core * tip * texture_ * beam * (1.0 + Time.w * 0.6);

    vec3 hot = mix(vec3(0.72, 0.86, 1.45), Accent.rgb * 1.6 + vec3(0.35), 0.55);
    vec3 cool = mix(vec3(0.16, 0.22, 0.62), Accent.rgb * 0.55, 0.5);
    vec3 col = mix(hot, cool, smoothstep(0.0, 0.72, along));
    // The de-boosted pole reddens as well as dimming.
    col = mix(col, vec3(0.55, 0.18, 0.10), clamp(-towards, 0.0, 1.0) * 0.55);

    // Additive pipeline: magnitude in rgb only, destination alpha untouched.
    vec3 emissive = col * max(emission, 0.0) * Primary.w * Secondary.w * 0.55
                  * ColorModulator.rgb;
    fragColor = vec4(emissive, 0.0);

    if (max(emissive.r, max(emissive.g, emissive.b)) < 0.0025) discard;
}

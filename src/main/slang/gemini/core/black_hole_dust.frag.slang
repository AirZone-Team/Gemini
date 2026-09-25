// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  BlackHolePet — infalling matter shading
//
//  A mote heats up as its orbit decays, so the head of the streak glows and
//  the tail is the cool trail it just left behind. vUv.x runs tail→head.
// ═══════════════════════════════════════════════════════════════════

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
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
in vec4 vColor;
in vec3 vWorld;

out vec4 fragColor;

vec3 ember(float temp, int palette) {
    vec3 cold = palette == 1 ? vec3(0.35, 0.18, 0.9)
            : palette == 2 ? vec3(0.12, 0.45, 0.9)
            : palette == 3 ? vec3(0.75, 0.08, 0.4)
            : vec3(0.85, 0.28, 0.05);
    vec3 hot = palette == 4 ? vec3(0.9, 0.4, 1.6) : vec3(2.6, 2.5, 2.4);
    return mix(cold, hot, clamp(temp, 0.0, 1.0));
}

[shader("fragment")]

void main() {
    int palette = int(Misc.x + 0.5);
    float across = abs(vUv.y * 2.0 - 1.0);
    float body = pow(max(1.0 - across, 0.0), 1.6);
    float head = pow(vUv.x, 1.35);
    float temp = clamp(vColor.r + head * 0.45, 0.0, 1.0);

    // Last flash before the mote disappears behind the horizon.
    float flash = smoothstep(0.82, 1.0, vColor.r) * head * 2.2;

    float emission = body * (head * (0.35 + temp * 1.3) + flash) * vColor.a;
    vec3 col = ember(temp, palette) * (1.0 + flash);

    // Additive pipeline: magnitude in rgb only, destination alpha untouched.
    vec3 emissive = col * max(emission, 0.0) * Primary.w * Secondary.w * ColorModulator.rgb;
    fragColor = vec4(emissive, 0.0);

    if (max(emissive.r, max(emissive.g, emissive.b)) < 0.003) discard;
}

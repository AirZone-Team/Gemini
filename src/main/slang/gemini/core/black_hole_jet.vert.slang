// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  BlackHolePet — polar relativistic jets
//
//  Two cones threaded on the disk normal, built here from the live basis so
//  they tilt with the hole instead of facing the camera. UV0.x runs from -1
//  (negative pole) through the core to +1, UV0.y wraps around the axis.
// ═══════════════════════════════════════════════════════════════════

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
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

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 vUv;
out vec3 vWorld;
out float vSide;  // +1 / -1 for which pole this vertex belongs to

const float TAU = 6.28318530718;

[shader("vertex")]

void main() {
    float s = UV0.x * 2.0 - 1.0;
    float along = abs(s);
    vSide = s < 0.0 ? -1.0 : 1.0;

    vec3 axis = AxisZ.xyz;
    vec3 e1 = AxisX.xyz;
    vec3 e2 = AxisY.xyz;

    // Collimated at the footpoint, then flaring; the helix twist winds the
    // tube around the axis so the shaft reads as a rotating volume.
    float lengthRs = max(Motion.z, 0.1);
    float twist = along * 4.2 - Time.x * Time.z * 1.4;
    float angle = UV0.y * TAU + twist;
    float neck = AxisZ.w * 0.42;
    float radius = (neck + along * along * lengthRs * 0.16)
                 * (1.0 + 0.12 * sin(angle * 3.0 + Time.x * 2.3));

    vec3 local = axis * (s * lengthRs)
               + (e1 * cos(angle) + e2 * sin(angle)) * radius;

    vUv = vec2(along, UV0.y);
    vWorld = Center.xyz + local;
    gl_Position = ProjMat * ModelViewMat * vec4(vWorld, 1.0);
}

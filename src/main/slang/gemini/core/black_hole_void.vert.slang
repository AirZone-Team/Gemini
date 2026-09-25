// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  BlackHolePet — event horizon (UV sphere built from the disk basis)
//
//  The mesh is a (u,v) grid; the sphere is assembled here so its silhouette
//  stays round from any angle and its depth is real — the horizon writes into
//  the depth buffer, which is what hides the back half of the accretion disk
//  and lets the polar jets emerge from behind it.
//
//  UV0.x = longitude / TAU, UV0.y = latitude mapped to 0→1.
//  The block layout matches black_hole_swirl so one uniform buffer serves both.
// ═══════════════════════════════════════════════════════════════════

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
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
out vec3 vNormal;  // disk-basis normal, unit length
out vec3 vWorld;   // camera-relative position of this point

const float TAU = 6.28318530718;

[shader("vertex")]

void main() {
    float lon = UV0.x * TAU;
    float lat = (UV0.y - 0.5) * 3.14159265;
    vec3 e1 = AxisX.xyz;
    vec3 e2 = AxisY.xyz;
    vec3 axis = AxisZ.xyz;

    // Spin the sphere with the disk so the rim shading has somewhere to travel.
    float spun = lon + Time.x * Time.z * 0.35;
    vec3 dir = (e1 * cos(spun) + e2 * sin(spun)) * cos(lat) + axis * sin(lat);

    vNormal = dir;
    vWorld = Center.xyz + dir * Center.w;
    vUv = UV0;

    gl_Position = ProjMat * ModelViewMat * vec4(vWorld, 1.0);
}

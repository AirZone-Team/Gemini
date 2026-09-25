// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  BlackHolePet — emissive swirl geometry (accretion disk + lensed arc)
//
//  The mesh is a plain (u,v) grid. Every world position is built here from
//  the pet's live basis vectors, so the disk is a real plane in the world:
//  it foreshortens, it occludes against the horizon sphere, and it keeps its
//  own momentum when the player turns. Nothing in this pass faces the camera.
//
//  Mode 0 (disk):  u = normalised radius, v = azimuth 0→1 around the axis.
//  Mode 1 (arc):   u = across the band, v = wrap angle, which starts on the
//                  far side of the disk plane and travels over the shadow and
//                  back out beneath it. This band is the second image of the
//                  far half of the disk, which light bending folds around the
//                  shadow; its plane is spanned by the disk normal and the
//                  away-from-camera direction, both derived here rather than
//                  baked on the CPU.
//
//  UV0.xy = (u, v). Color is unused; all physics comes from BHUniforms.
//  vLocal/vRadius are in Rs units, and the world point is Center.xyz + vLocal*Rs.
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
    vec4 AxisX;     // xyz: disk basis e1; w: inner radius in Rs
    vec4 AxisY;     // xyz: disk basis e2; w: outer radius in Rs
    vec4 AxisZ;     // xyz: disk normal; w: band thickness in Rs
    vec4 Motion;    // x: noise scale, y: turbulence, z: jet length in Rs, w: redshift
    vec4 Primary;   // rgb: inner/hot colour; w: opacity
    vec4 Secondary; // rgb: outer/cool colour; w: brightness
    vec4 Accent;    // rgb: jet/ring tint; w: doppler amount
    vec4 Misc;      // x: palette, y: rainbow speed, z: arc radius in Rs, w: arc span
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 vUv;
out vec3 vLocal;   // position relative to the pet centre, in Rs units
out vec3 vVel;     // orbital velocity direction of the emitting matter
out float vRadius; // radius in Rs units

const float TAU = 6.28318530718;

[shader("vertex")]

void main() {
    float rs = max(Center.w, 0.0001);
    vec3 e1 = AxisX.xyz;
    vec3 e2 = AxisY.xyz;
    vec3 axis = AxisZ.xyz;

    vec3 local;
    vec3 vel;

    if (int(Time.y + 0.5) == 1) {
        float wrap = (UV0.y * 2.0 - 1.0) * max(Misc.w, 0.01);
        vec3 away = Center.xyz - axis * dot(Center.xyz, axis);
        float awayLen = length(away);
        vec3 awayDir = awayLen > 0.0001 ? away / awayLen : e1;
        vec3 bandDir = normalize(awayDir * cos(wrap) + axis * sin(wrap));
        float radius = Misc.z + (UV0.x - 0.5) * max(AxisZ.w, 0.01);
        local = bandDir * radius;
        vel = cross(axis, awayDir);
    } else {
        float angle = UV0.y * TAU;
        float radius = mix(AxisX.w, AxisY.w, UV0.x);
        vec3 dir = e1 * cos(angle) + e2 * sin(angle);
        local = dir * radius;
        vel = e2 * cos(angle) - e1 * sin(angle);
    }

    vUv = UV0;
    vLocal = local / rs;
    vRadius = length(local) / rs;
    vVel = vel;

    gl_Position = ProjMat * ModelViewMat * vec4(Center.xyz + local, 1.0);
}

// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  BlackHolePet — infalling matter
//
//  Debris spiralling in from the shoulder, stretched along its own orbital
//  tangent. The CPU bakes the quad in world space (head at UV0.x = 1) so each
//  streak lies in the disk plane and shears with perspective — they are motes
//  of plasma riding an orbit, not sprites pinned to the screen.
//
//  Color.r = temperature 0→1 (packed as a bounded value, not an angle)
//  Color.a = intensity
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
out vec4 vColor;
out vec3 vWorld;

[shader("vertex")]

void main() {
    vUv = UV0;
    vColor = Color;
    vWorld = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}

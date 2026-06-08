#version 330

// ── KillAura foot indicator — pure-colour 3D sphere dots ──
//
// vertexColor.r  = HP percentage (0=dead, 1=full)
// vertexColor.g  = dot index / DOT_COUNT (0→1)
// vertexColor.a  = master alpha
//
// Dots drain sequentially clockwise from north: green → yellow → red.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

const int DOT_COUNT = 16;

void main() {
    // ── Sphere mask ──────────────────────────────────────────
    vec2 p    = (uvCoord - 0.5) * 2.0;
    float d   = length(p);
    if (d > 1.0) discard;

    float hp     = clamp(vertexColor.r, 0.0, 1.0);
    float alpha  = vertexColor.a;
    float dotIdx = vertexColor.g * float(DOT_COUNT);

    // ── Sequential drain ─────────────────────────────────────
    float redThreshold = (1.0 - hp) * float(DOT_COUNT);
    float dotHealth    = smoothstep(redThreshold - 0.7, redThreshold + 0.7, dotIdx);

    vec3 healthy  = vec3(0.15, 0.95, 0.20);
    vec3 mid      = vec3(1.0,  0.88, 0.08);
    vec3 critical = vec3(1.0,  0.12, 0.08);

    vec3 color;
    if (dotHealth > 0.5) {
        color = mix(mid, healthy, (dotHealth - 0.5) * 2.0);
    } else {
        color = mix(critical, mid, dotHealth * 2.0);
    }

    // ── Smooth edge ──────────────────────────────────────────
    float edge = 1.0 - smoothstep(0.78, 1.0, d);
    float finalAlpha = edge * alpha;

    fragColor = vec4(color, finalAlpha) * ColorModulator;
}

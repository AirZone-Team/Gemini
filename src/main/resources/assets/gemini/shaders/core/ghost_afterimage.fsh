#version 330

// ═══════════════════════════════════════════════════════════════════════
//  Ghost AfterImage Fragment Shader
//
//  Color: R=fade, G=tintR, B=tintG, A=tintB
//  Renders an SDF humanoid silhouette with glow and HDR output.
// ═══════════════════════════════════════════════════════════════════════

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

// ── SDF primitives ──────────────────────────────────────────────

float smin(float a, float b, float k) {
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}

float roundedRect(vec2 p, vec2 halfSize, float r) {
    vec2 d = abs(p) - halfSize + r;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}

float circle(vec2 p, float r) {
    return length(p) - r;
}

void main() {
    float fade     = vColor.r;
    vec3  ghostCol = vColor.gba;   // tint in GBA
    float alpha    = fade * 0.55;

    if (alpha < 0.002) discard;

    vec2 p = (vUv - 0.5) * 2.0;

    // ── Body SDF ──────────────────────────────────────────────
    float head = circle(p - vec2(0.0, 0.58), 0.22);
    float neck = roundedRect(p - vec2(0.0, 0.38), vec2(0.08, 0.05), 0.03);
    float torso = roundedRect(p - vec2(0.0, 0.02), vec2(0.20, 0.30), 0.04);

    float laCos = cos(-0.15), laSin = sin(-0.15);
    vec2 laP = p - vec2(-0.24, 0.12);
    vec2 laRot = vec2(laP.x * laCos - laP.y * laSin, laP.x * laSin + laP.y * laCos);
    float leftArm = roundedRect(laRot, vec2(0.065, 0.25), 0.04);

    float raCos = cos(0.15), raSin = sin(0.15);
    vec2 raP = p - vec2(0.24, 0.12);
    vec2 raRot = vec2(raP.x * raCos - raP.y * raSin, raP.x * raSin + raP.y * raCos);
    float rightArm = roundedRect(raRot, vec2(0.065, 0.25), 0.04);

    float leftLeg  = roundedRect(p - vec2(-0.09, -0.38), vec2(0.08, 0.24), 0.04);
    float rightLeg = roundedRect(p - vec2( 0.09, -0.38), vec2(0.08, 0.24), 0.04);

    float body = head;
    body = smin(body, neck, 0.02);
    body = smin(body, torso, 0.05);
    body = smin(body, leftArm, 0.05);
    body = smin(body, rightArm, 0.05);
    body = smin(body, leftLeg, 0.05);
    body = smin(body, rightLeg, 0.05);

    float silhouette = 1.0 - smoothstep(-0.02, 0.03, body);

    // ── Glow layers ───────────────────────────────────────────
    float innerGlow = 1.0 - smoothstep(-0.08, 0.06, body);
    float edgeGlow  = exp(-abs(body + 0.02) * 18.0) * 0.8;
    float outerGlow = exp(-abs(body) * 5.0) * 0.35;

    float shapeMask = silhouette + innerGlow * 0.4 + edgeGlow * 0.5 + outerGlow * 0.3;
    shapeMask = clamp(shapeMask, 0.0, 1.0);

    vec3 col = ghostCol * shapeMask * fade;

    // HDR boost
    float hdr = 1.0 + innerGlow * 2.5 + edgeGlow * 1.0;
    col *= hdr;

    fragColor = vec4(col, shapeMask * alpha);
}

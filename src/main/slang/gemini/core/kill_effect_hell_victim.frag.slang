// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  Hell's Hand — victim silhouette being dragged to hell (被拖入地狱的猎物)
//
//  A camera-facing billboard at the grab point. The fragment shader
//  draws a struggling shadow humanoid (head / torso / flailing arms /
//  kicking legs) with SDF capsules, red hell-light rimming its lower
//  half, and a boiling "swallow line" that rises as the victim sinks
//  into the rift.
//
//  vertexColor.r = sink     (0 above the mouth → 1 fully swallowed)
//  vertexColor.g = struggle (0 limp → 1 violent flailing)
//  vertexColor.b = boil     (rolling time fraction)
//  vertexColor.a = master alpha
//
//  Blend: TRANSLUCENT.
// ═══════════════════════════════════════════════════════════════════

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i+vec2(1,0)), f.x),
               mix(hash(i+vec2(0,1)), hash(i+vec2(1,1)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 3; i++) { v += a * noise(p); p *= 2.03; a *= 0.5; }
    return v;
}

// Capsule SDF in "body meters" space
float sdSeg(vec2 p, vec2 a, vec2 b, float ra, float rb) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-6), 0.0, 1.0);
    float rad = mix(ra, rb, h);
    return length(pa - ba * h) - rad;
}

[shader("fragment")]

void main() {
    float sink     = vertexColor.r;
    float struggle = vertexColor.g;
    float boil     = vertexColor.b;
    float alpha    = vertexColor.a;

    // Body space: x ∈ [-0.75, 0.75] across, y ∈ [-1.10, 1.10] up
    vec2 p = vec2((uvCoord.x - 0.5) * 1.5, uvCoord.y * 2.2 - 1.1);
    float tb = boil * 20.0;

    // Whole-body convulsion while being dragged
    vec2 sway = vec2(sin(tb * 5.7) * 0.045, sin(tb * 4.3) * 0.03) * struggle;
    float lean = sin(tb * 2.9) * 0.12 * struggle;
    // Shear x by lean so the body tilts at the hips
    p.x += (max(p.y, 0.0)) * lean;
    p += sway;

    // ── Limb angles (flailing) ────────────────────────────────────
    float aL = mix(1.9, 2.2 + sin(tb * 6.1) * 0.85, struggle);   // left arm up-out
    float aR = mix(1.2, 1.1 + sin(tb * 5.2 + 2.0) * 0.85, struggle);
    float eL = -0.7 + sin(tb * 6.1 + 1.0) * 0.7 * struggle;      // elbow bend
    float eR = 0.7 + sin(tb * 5.2 + 3.0) * 0.7 * struggle;
    float lL = mix(-1.35, -1.15 + sin(tb * 6.6) * 0.5, struggle); // legs kicking
    float lR = mix(-1.8, -1.95 + sin(tb * 5.9 + 1.5) * 0.5, struggle);

    // ── Torso + head ──────────────────────────────────────────────
    float d = 1e5;
    d = min(d, sdSeg(p, vec2(0.0,  0.55), vec2(0.0, -0.02), 0.17, 0.20)); // torso
    d = min(d, sdSeg(p, vec2(0.0,  0.56), vec2(0.0,  0.64), 0.18, 0.10)); // neck
    d = min(d, length(p - vec2(sin(tb * 4.8) * 0.02, 0.80)) - 0.155);     // head

    // ── Arms: shoulder → elbow → hand ─────────────────────────────
    vec2 shL = vec2(-0.19, 0.48), shR = vec2(0.19, 0.48);
    vec2 elL = shL + vec2(cos(aL), sin(aL)) * 0.30;
    vec2 elR = shR + vec2(cos(aR), sin(aR)) * 0.30;
    vec2 haL = elL + vec2(cos(aL + eL), sin(aL + eL)) * 0.26;
    vec2 haR = elR + vec2(cos(aR + eR), sin(aR + eR)) * 0.26;
    d = min(d, sdSeg(p, shL, elL, 0.075, 0.06));
    d = min(d, sdSeg(p, shR, elR, 0.075, 0.06));
    d = min(d, sdSeg(p, elL, haL, 0.06, 0.045));
    d = min(d, sdSeg(p, elR, haR, 0.06, 0.045));

    // ── Legs: hip → knee → foot ───────────────────────────────────
    vec2 hpL = vec2(-0.10, -0.02), hpR = vec2(0.10, -0.02);
    vec2 knL = hpL + vec2(cos(lL), sin(lL)) * 0.40;
    vec2 knR = hpR + vec2(cos(lR), sin(lR)) * 0.40;
    vec2 ftL = knL + vec2(cos(lL - 0.6), sin(lL - 0.6)) * 0.36;
    vec2 ftR = knR + vec2(cos(lR + 0.6), sin(lR + 0.6)) * 0.36;
    d = min(d, sdSeg(p, hpL, knL, 0.10, 0.075));
    d = min(d, sdSeg(p, hpR, knR, 0.10, 0.075));
    d = min(d, sdSeg(p, knL, ftL, 0.07, 0.05));
    d = min(d, sdSeg(p, knR, ftR, 0.07, 0.05));

    // ── Body mask + hell rim light ────────────────────────────────
    float aa = 0.022;
    float body = 1.0 - smoothstep(0.0, aa, d);
    float rimBand = exp(-abs(d) / 0.035);
    // Red under-light from the rift mouth (stronger low on the body)
    float under = (0.45 + 0.55 * clamp((0.6 - p.y) / 1.6, 0.0, 1.0));
    vec3 col = vec3(0.012, 0.008, 0.014)
             + vec3(0.95, 0.28, 0.06) * rimBand * under * 0.55
             + vec3(0.55, 0.08, 0.02) * (1.0 - smoothstep(-0.55, 0.85, p.y)) * body * 0.10;

    // ── Swallow line: rises from the feet as the victim sinks ─────
    // Boiling boundary — the shadow "melts" into the hell mouth.
    float lineY = mix(-1.35, 1.25, clamp(sink, 0.0, 1.0));
    lineY += (fbm(vec2(p.x * 9.0, tb * 2.2)) - 0.5) * 0.12 * clamp(sink * 2.0, 0.0, 1.0);
    float swallowed = smoothstep(lineY - 0.05, lineY + 0.05, p.y);
    body *= swallowed;

    // Ember glow right at the melt boundary
    col += vec3(1.0, 0.42, 0.08) * exp(-abs(p.y - lineY) / 0.05) * 0.8 * sink;

    float a = body * alpha;
    fragColor = vec4(col, a) * ColorModulator;

    if (fragColor.a < 0.004) {
        discard;
    }
}

// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  Thaumaturgy Strike nova sphere fragment shader (奇术打击 · 光球)
//
//  The ball of light that swells at the foot of the great column.
//  Same analytic ray–sphere intersection and front-to-back emission
//  integration as the Hypernova orb, but re-tuned for thaumaturgy:
//    · a white-hot plasma core over a violet mantle
//    · a turbulent, sharply-defined surface instead of a soft limb,
//      so the ball reads as contained energy rather than a fireball
//    · an ignition shell that races outward at birth and a second one
//      that races inward as the sphere collapses
//
//  vertexColor.r = progress through the sphere's life (0→1)
//  vertexColor.g = heat (1 = white-hot, 0 = cooling violet)
//  vertexColor.b = intensity / 4
//  vertexColor.a = master alpha
//  uvCoord.x     = sphere radius in blocks
// ═══════════════════════════════════════════════════════════════════

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

in vec4 vertexColor;
in vec2 uvCoord;
in vec3 vViewPos;

out vec4 fragColor;

float hash3(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453123);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash3(i);
    float b = hash3(i + vec3(1.0, 0.0, 0.0));
    float c = hash3(i + vec3(0.0, 1.0, 0.0));
    float d = hash3(i + vec3(1.0, 1.0, 0.0));
    float e = hash3(i + vec3(0.0, 0.0, 1.0));
    float g = hash3(i + vec3(1.0, 0.0, 1.0));
    float h = hash3(i + vec3(0.0, 1.0, 1.0));
    float k = hash3(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(a, b, f.x), mix(c, d, f.x), f.y),
               mix(mix(e, g, f.x), mix(h, k, f.x), f.y), f.z);
}

[shader("fragment")]

void main() {
    float progress  = vertexColor.r;
    float heat      = vertexColor.g;
    float intensity = vertexColor.b * 4.0;
    float alpha     = vertexColor.a;
    float radius    = max(uvCoord.x, 0.001);

    if (alpha < 0.002 || intensity < 0.002) { discard; return; }

    vec3 C = (ModelViewMat * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    vec3 D = normalize(vViewPos);

    // ── Analytic ray–sphere intersection ──────────────────────────
    vec3 oc = -C;
    float b = dot(oc, D);
    float c = dot(oc, oc) - radius * radius;
    float h = b * b - c;
    if (h < 0.0) { discard; return; }
    h = sqrt(h);
    float t0 = max(-b - h, 0.0);
    float t1 = -b + h;
    if (t1 <= t0) { discard; return; }

    // ── Palette ───────────────────────────────────────────────────
    vec3 coreCol = mix(vec3(0.72, 0.42, 1.00), vec3(1.00, 0.97, 1.00), heat);
    vec3 rimCol  = mix(vec3(0.20, 0.03, 0.42), vec3(0.58, 0.16, 1.00), heat);

    const int STEPS = 12;
    float dt = (t1 - t0) / float(STEPS);
    vec3 acc = vec3(0.0);
    float trans = 1.0;

    // Surface turbulence in world-ish space so it slides across the ball
    float churn = progress * 9.0;

    for (int i = 0; i < STEPS; i++) {
        float t = t0 + (float(i) + 0.5) * dt;
        vec3 p = D * t;
        float d = length(p - C) / radius;      // 0 centre → 1 surface

        // Dense emissive heart, thinner glowing envelope
        float density = exp(-d * d * 22.0) * 3.1
                      + exp(-d * d * 4.5)  * 1.15;

        // Turbulent shell: 3-octave value noise warped into the radius
        float n = noise3(p * (2.6 + d * 3.0) + vec3(churn, -churn * 0.7, churn * 0.4));
        float surface = 0.86 + 0.30 * (n - 0.5) * 2.0;
        float shell = exp(-pow(d - surface, 2.0) * 46.0) * 1.35;

        // Ignition front racing outward at birth…
        float outShell = exp(-pow(d - (0.18 + progress * 0.95), 2.0) * 130.0)
                       * (1.0 - smoothstep(0.10, 0.40, progress)) * 1.6;
        // …then the collapse front racing back inward.
        float inShell = exp(-pow(d - max(1.05 - (progress - 0.55) * 2.4, 0.02), 2.0) * 150.0)
                      * smoothstep(0.55, 0.75, progress) * 1.9;

        density = density * surface + shell + outShell + inShell;

        vec3 sampleCol = mix(coreCol, rimCol, smoothstep(0.0, 0.92, d));
        acc += sampleCol * density * dt * trans;
        trans *= exp(-density * dt * 0.26);
    }
    acc *= 0.5;   // both shell hemispheres share the integral

    vec3 rgb = acc * intensity;
    float lum = dot(rgb, vec3(0.299, 0.587, 0.114));
    float aOut = clamp(lum * 0.5, 0.0, 1.0) * alpha;

    fragColor = vec4(rgb, aOut) * ColorModulator;
    if (fragColor.a < 0.0005) discard;
}

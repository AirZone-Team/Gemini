#version 330

// ═══════════════════════════════════════════════════════════════════
//  Volumetric Orb fragment shader — true 3D glow ball
//
//  Unlike the old billboard glow (4 camera-facing quads), this renders
//  a real sphere mesh in world space and ray-marches the view ray
//  through its volume per pixel:
//    1. Analytic ray–sphere intersection gives the [t0, t1] segment
//       of the view ray inside the ball (t0 = 0 when camera is inside).
//    2. 12-step front-to-back integration of an emissive density field:
//       dense white-hot core + warm mantle + wide halo, with
//       Beer-law self-absorption (the core silhouettes its own halo).
//    3. Temperature ramps from core to limb; `heat` shifts the whole
//       palette from white-hot (detonation) to ember (fade-out).
//
//  Depth testing against the scene keeps the ball correctly occluded
//  by blocks and entities; both shell hemispheres are drawn (no cull)
//  so it also works when the camera is inside the ball, and the
//  integral is halved to compensate.
//
//  vertexColor.r = time progress within stage (0→1)
//  vertexColor.g = heat (1 = white-hot, 0 = cool ember)
//  vertexColor.b = intensity / 4 (HDR, rescaled here)
//  vertexColor.a = master alpha
//  uvCoord.x     = sphere radius in blocks
// ═══════════════════════════════════════════════════════════════════

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 uvCoord;
in vec3 vViewPos;

out vec4 fragColor;

void main() {
    float progress  = vertexColor.r;
    float heat      = vertexColor.g;          // 1 = white-hot, 0 = cool ember
    float intensity = vertexColor.b * 4.0;    // HDR rescale
    float alpha     = vertexColor.a;
    float radius    = max(uvCoord.x, 0.001);

    if (alpha < 0.002 || intensity < 0.002) { discard; return; }

    // Sphere center in view space = model-space origin transformed
    vec3 C = (ModelViewMat * vec4(0.0, 0.0, 0.0, 1.0)).xyz;

    // View ray: camera is the origin in this space
    vec3 D = normalize(vViewPos);

    // ── Analytic ray–sphere intersection ─────────────────────────
    // |t·D − C|² = R²  →  t = −b ± √(b² − c), b = −C·D, c = |C|² − R²
    vec3 oc = -C;
    float b = dot(oc, D);
    float c = dot(oc, oc) - radius * radius;
    float h = b * b - c;
    if (h < 0.0) { discard; return; }        // ray misses the ball
    h = sqrt(h);
    float t0 = max(-b - h, 0.0);             // entry (0 when camera inside)
    float t1 = -b + h;                       // exit
    if (t1 <= t0) { discard; return; }

    // ── Temperature palette (heat-shifted) ───────────────────────
    vec3 coreCol = mix(vec3(1.00, 0.55, 0.15), vec3(1.00, 0.98, 0.92), heat);
    vec3 rimCol  = mix(vec3(0.45, 0.06, 0.02), vec3(1.00, 0.45, 0.10), heat);

    // ── Front-to-back volumetric integration ─────────────────────
    const int STEPS = 12;
    float dt = (t1 - t0) / float(STEPS);
    vec3 acc = vec3(0.0);
    float trans = 1.0;

    for (int i = 0; i < STEPS; i++) {
        float t = t0 + (float(i) + 0.5) * dt;
        vec3 p = D * t;
        float d = length(p - C) / radius;    // 0 = center, 1 = surface
        float dd = d * d;

        // Emissive density: dense core + warm mantle + wide halo
        float density = exp(-dd * 16.0) * 2.6
                      + exp(-dd * 5.0)  * 1.0
                      + exp(-dd * 1.5)  * 0.28;

        // Temperature falls toward the limb
        vec3 sampleCol = mix(coreCol, rimCol, smoothstep(0.0, 0.85, d));

        acc += sampleCol * density * dt * trans;
        trans *= exp(-density * dt * 0.30);  // Beer-law self-absorption
    }
    acc *= 0.5; // both shell hemispheres share the same integral

    // Subtle organic boiling
    float flicker = 1.0 + 0.06 * sin(progress * 40.0 + vViewPos.x * 3.0);

    vec3 rgb = acc * intensity * flicker;
    float lum = dot(rgb, vec3(0.299, 0.587, 0.114));
    float aOut = clamp(lum * 0.5, 0.0, 1.0) * alpha;

    fragColor = vec4(rgb, aOut) * ColorModulator;
    if (fragColor.a < 0.0005) discard;
}

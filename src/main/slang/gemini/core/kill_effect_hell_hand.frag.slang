// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  Hell's Hand — charred limb segment (焦黑的手臂/手掌/手指)
//
//  The hand is an articulated chain of tapered quads built on the CPU:
//  two for the forearm, two for the palm, three phalanges per finger and
//  two for the thumb. This shader paints a single shared look: a dead
//  charcoal body crossed by molten lava cracks that glow brighter
//  toward the rift side (the base of the limb), with a crease at every
//  joint so the chain reads as knuckles.
//
//  UV: u = across the limb (0..1), v = along it (0 = base → 1 = tip)
//
//  vertexColor.r = curl   (0 open → 1 clenched; tightens the knuckle glow)
//  vertexColor.g = seed   (per-part random phase for crack variation)
//  vertexColor.b = boil   (rolling time fraction, drives crack flicker)
//  vertexColor.a = master alpha
//
//  Blend: TRANSLUCENT (the hand must occlude as a solid dark shape).
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
    for (int i = 0; i < 3; i++) { v += a * noise(p); p *= 2.07; a *= 0.5; }
    return v;
}

[shader("fragment")]

void main() {
    float curl  = vertexColor.r;
    float seed  = vertexColor.g;
    float boil  = vertexColor.b;
    float alpha = vertexColor.a;

    float u = uvCoord.x;
    float v = uvCoord.y;
    float tb = boil * 20.0;
    float vBand = sin(v * 3.14159);      // mid-limb band, 0 at both segment ends

    // ── Silhouette mask ───────────────────────────────────────────
    // Tapering is baked into the geometry; the shader only softens the
    // edges so overlapping segments blend into one organic mass.
    float across = min(u, 1.0 - u) * 2.0;            // 0 at edges → 1 center
    float edgeMask = smoothstep(0.0, 0.62, across);
    // Rounded caps at both ends of the segment. Fingers are chains of three
    // overlapping segments, so the caps stay generous — too much falloff and
    // every joint reads as a hole in the limb.
    float vCap = smoothstep(0.0, 0.14, v) * (1.0 - smoothstep(0.86, 1.0, v));
    float mask = edgeMask * (0.45 + 0.55 * vCap);

    // ── Charcoal body ─────────────────────────────────────────────
    float grain = fbm(vec2(u * 5.0 + seed * 37.0, v * 6.0 + seed * 11.0));
    vec3 body = mix(vec3(0.022, 0.016, 0.018),
                    vec3(0.052, 0.036, 0.034), grain * 0.7 + v * 0.25);

    // ── Knuckle joints ────────────────────────────────────────────
    // Each segment ends at a joint: press a crease into the far end and let
    // the bone ridge underneath glow while the hand is clenched, so the
    // phalanx chain reads as articulated rather than butted quads.
    float joint = smoothstep(0.78, 0.97, v) * edgeMask;
    body *= 1.0 - 0.42 * joint;
    vec3 ridge = mix(vec3(0.13, 0.05, 0.04), vec3(1.0, 0.44, 0.10), curl * 0.8)
               * joint * (0.22 + 0.55 * curl + 0.23 * vBand);

    // ── Molten cracks ─────────────────────────────────────────────
    // Thin ridge lines: |fbm - 0.5| narrow bands, drifting with time.
    float cf = fbm(vec2(u * 7.0 + seed * 53.0, v * 5.5 + tb * 0.55));
    float crack = 1.0 - smoothstep(0.035, 0.11, abs(cf - 0.5));
    // Cracks concentrate at mid-limb, glow through clenched knuckles
    crack *= vBand * (0.7 + 0.3 * edgeMask);
    float flick = 0.78 + 0.22 * sin(tb * 7.3 + seed * 30.0 + v * 9.0);
    vec3 hot = mix(vec3(1.0, 0.30, 0.03), vec3(1.0, 0.72, 0.20),
                   clamp(cf * 2.2 - 0.55, 0.0, 1.0));

    // Heat: strongest at the limb base (still inside the rift), fades up
    // toward the fingers — but clenching squeezes the glow back on.
    float heat = mix(1.0, 0.30, v) + curl * 0.5 * vBand;

    // ── Rift under-light on the edge silhouette ───────────────────
    float rim = (1.0 - edgeMask) * exp(-v * 3.0) * 0.5;

    vec3 col = body + hot * crack * heat * 2.1 * flick
             + ridge * flick
             + vec3(0.95, 0.32, 0.06) * rim * flick;

    fragColor = vec4(col, mask * alpha) * ColorModulator;

    if (fragColor.a < 0.004) {
        discard;
    }
}

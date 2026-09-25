// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  Thaumaturgy Strike lightning ribbon fragment shader (奇术打击 · 闪电)
//
//  A discharge arc drawn as a hot core in a thin sheath. The path itself is
//  generated in Java (ThaumaturgyLightning) and arrives as a chain of spans, so
//  this stage only has to make each span look like lightning: a couple of pixels
//  wide, brighter where the charge is passing, and never evenly lit end to end.
//
//  uvCoord.x = across (0 edge → 0.5 arc → 1 edge)
//  uvCoord.y = along the whole bolt, 0 at the sigil end
//  vertexColor.r = per-bolt seed
//  vertexColor.g = flow phase (0→1, wraps)
//  vertexColor.b = energy / 4 (HDR)
//  vertexColor.a = master alpha
// ═══════════════════════════════════════════════════════════════════

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

#define TAU 6.28318530718

[shader("fragment")]

void main() {
    float seed   = vertexColor.r;
    float phase  = vertexColor.g;
    float energy = vertexColor.b * 4.0;
    float alpha  = vertexColor.a;

    if (alpha < 0.002) discard;

    float across = abs(fract(uvCoord.x) - 0.5) * 2.0;
    float along  = uvCoord.y;

    // ── Cross-section ─────────────────────────────────────────────
    // An arc is only a couple of pixels wide, so the profile is antialiased
    // against the pixel rather than against its own width; without this the
    // bolts crawl and strobe as the camera moves.
    float pixel = fwidth(across);
    float w = max(0.16, pixel * 0.5);
    float core = exp(-(across * across) / (2.0 * w * w)) * (0.16 / w);
    float sheath = exp(-(across * across) / 0.55);
    float body = core + sheath * 0.35;

    // ── Charge travelling down the arc ────────────────────────────
    // Integer rates keep the loop seamless when `phase` wraps 1 → 0.
    float rate = 2.0 + floor(seed * 3.0);
    float node = fract(along * 2.0 - phase * rate);
    float pulse = exp(-pow((node - 0.5) * 8.0, 2.0)) * 0.9;
    // A coarser ripple along the length so no two spans of one bolt read alike.
    float ripple = 0.62 + 0.38 * sin(along * 26.0 + seed * 41.0 + phase * TAU);

    float brightness = body * (0.55 + pulse) * ripple;
    if (brightness < 0.004) discard;

    // ── Palette: violet sheath into a pale, almost blue core ──────
    vec3 violet = vec3(0.55, 0.28, 1.00);
    vec3 white  = vec3(0.93, 0.90, 1.00);
    vec3 col = mix(violet, white, clamp(core * 1.3, 0.0, 1.0));
    col *= brightness * energy * 2.2;

    float edge = 1.0 - smoothstep(0.75, 1.0, across);
    fragColor = vec4(col, clamp(brightness * alpha * edge, 0.0, 1.0)) * ColorModulator;
    if (fragColor.a < 0.0015) discard;
}

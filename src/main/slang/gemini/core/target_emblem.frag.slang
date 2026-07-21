// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ── TargetDisplay "Ring" emblem ──
//
// Circular player avatar surrounded by a health progress ring,
// rendered fully in one quad via SDF math.
//
// Track: a thin light-lavender full circle. Progress: a thicker
// deep-purple arc CENTRED AT THE BOTTOM (6 o'clock) that grows
// symmetrically towards both sides as health rises, with round caps.
//
// Sampler0: player skin texture (face region 8/64..16/64 is sampled
//           for the avatar disc).
//
// Vertex colour encoding (decoded below):
//   vertexColor.r → emblem size     (0–512 px, mapped 0–1)
//   vertexColor.g → arc thickness   (0–64  px, mapped 0–1)
//   vertexColor.b → health progress (0–1)
//   vertexColor.a → master alpha    (0–1)

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

const float PI     = 3.14159265358979323846;
const float TWO_PI = 6.28318530717958647692;

// ── Ring shape ──
const float TRACK_SCALE = 0.5;  // track thickness as a fraction of the arc thickness
const float AVATAR_GAP  = 1.0;  // px gap between avatar disc and track ring

// ── Decode uniforms ──
float emblemSize()    { return vertexColor.r * 512.0; }
float arcThickness()  { return vertexColor.g * 64.0; }
float progress()      { return vertexColor.b; }
float masterAlpha()   { return vertexColor.a; }

[shader("fragment")]

void main() {
    float size = emblemSize();
    float progTh  = min(arcThickness(), size * 0.5);
    float trackTh = progTh * TRACK_SCALE;
    float prog = clamp(progress(), 0.0, 1.0);
    float alpha = masterAlpha();

    // ── Ring geometry ──
    float progHalf   = progTh * 0.5;
    float trackHalf  = trackTh * 0.5;
    float outerLimit = size * 0.5;
    // Mean radius pulled inwards so the thick arc stays inside the quad.
    float midR    = outerLimit - progHalf;
    float avatarR = midR - trackHalf - AVATAR_GAP;

    // UV → pixel position relative to emblem centre (y-down screen space)
    vec2 p = (uvCoord - 0.5) * size;
    float d = length(p);

    // Angle: 0 at top, clockwise positive (y-down screen space)
    float ang = atan(p.y, p.x) + PI * 0.5;
    if (ang < 0.0) ang += TWO_PI;

    // ── Avatar disc ──
    vec2 faceUv = clamp(p / avatarR * 0.5 + 0.5, 0.0, 1.0);
    vec2 skinUv = mix(vec2(8.0 / 64.0), vec2(16.0 / 64.0), faceUv);
    vec4 skin = texture(Sampler0, skinUv);

    vec3 backdrop = vec3(0.925, 0.906, 0.976);            // #ECE7F9
    float innerMask = 1.0 - smoothstep(avatarR - 1.2, avatarR, d);
    vec3 innerColor = mix(backdrop, skin.rgb, skin.a);

    vec4 color = vec4(innerColor, innerMask);

    // ── Track ring: thin light-lavender full circle ──
    float track = 1.0 - smoothstep(trackHalf - 0.7, trackHalf + 0.7, abs(d - midR));

    vec3 trackColor = vec3(0.863, 0.831, 0.949);          // #DCD4F2
    color = mix(color, vec4(trackColor, 1.0), track);

    // ── Progress arc: thick, centred at the bottom (angle = PI) ──
    float halfArc = prog * PI;                            // half the sweep, radians
    // Wrapped angular distance from the bottom, in [0, PI]
    float dAng = abs(ang - PI);
    if (dAng > PI) dAng = TWO_PI - dAng;
    float aaAng = 1.5 / max(midR, 1.0);                   // ~1.5 px angular AA
    float cover = (1.0 - smoothstep(halfArc - aaAng, halfArc, dAng)) * step(0.002, prog);

    // Round caps on both arc ends
    vec2 capA = vec2(sin(PI - halfArc), -cos(PI - halfArc)) * midR;
    vec2 capB = vec2(sin(PI + halfArc), -cos(PI + halfArc)) * midR;
    float caps = (1.0 - smoothstep(progHalf - 1.0, progHalf,
                       min(length(p - capA), length(p - capB))))
               * step(0.002, prog);

    float progBand = 1.0 - smoothstep(progHalf - 0.9, progHalf + 0.9, abs(d - midR));
    float progMask = progBand * max(cover, caps);
    vec3 progColor = vec3(0.412, 0.278, 0.792);           // #6947CA
    color = mix(color, vec4(progColor, 1.0), progMask);

    if (color.a < 0.004) discard;
    fragColor = vec4(color.rgb, color.a * alpha) * ColorModulator;
}

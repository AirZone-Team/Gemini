#version 330

// MTSDF (Multi-channel + True Signed Distance Field) font fragment shader.
// Every per-fragment step lives here: atlas sampling, signed-distance
// reconstruction, derivative-based anti-aliasing, and per-vertex color
// modulation.
//
// Atlas layout (written by MsdfGenerator, Java):
//   R, G, B — three signed distance fields, each tracking a differently
//             colored subset of the glyph outline edges. The true edge
//             distance is reconstructed per fragment as the MEDIAN of the
//             three channels, which preserves sharp corners that
//             single-channel SDF rounds off.
//   A       — the true signed distance over ALL edges. The median field
//             saturates beyond RANGE/2 px from the contour (all channels
//             clamp to 0 or 1); the alpha channel keeps a smooth gradient
//             there for effects that require a true Euclidean distance and
//             stabilizes coverage under extreme minification.
//
//   0.0   = RANGE/2 px (or more) outside the glyph
//   0.5   = exactly on the glyph edge
//   1.0   = RANGE/2 px (or more) inside the glyph
//
// Anti-aliasing follows the reference msdfgen shader: screenPxRange()
// converts the distance-field range into output screen pixels via texture
// derivatives, yielding a constant ~1 px wide AA band at any zoom level —
// glyphs stay crisp from 8 px to 512 px+.
//
// NO mirrored constants: the atlas is self-describing. The field range is
// read from metadata texel (0,0) — written by MsdfGenerator (Java), whose
// RANGE is the single source of truth. Atlas size comes from textureSize()
// and on-screen scale from texture-coordinate derivatives. (MC's GuiRenderer
// only binds the default
// UBOs, so a custom uniform block could never be populated for GUI elements;
// a metadata texel is the robust channel for this value.)

uniform sampler2D Sampler0;

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

// Distance range in atlas pixels, decoded from metadata texel (0,0).
// RANGE*256 is stored as unsigned 16-bit fixed point: R is the low byte and
// G the high byte. texelFetch ignores filtering.
float pxRange() {
    vec2 bytes = round(texelFetch(Sampler0, ivec2(0, 0), 0).rg * 255.0);
    return (bytes.x + 256.0 * bytes.y) / 256.0;
}

float screenPxRange() {
    // textureSize = full atlas size, so unitRange is the field range in
    // atlas UV units and texCoord derivatives need no per-glyph scaling.
    vec2 unitRange = vec2(pxRange()) / vec2(textureSize(Sampler0, 0));
    // Exact derivative length remains correct under non-uniform scaling and
    // rotation; 1/fwidth(texCoord) is only an approximation.
    vec2 dx = dFdx(texCoord);
    vec2 dy = dFdy(texCoord);
    vec2 screenTexSize = inversesqrt(dx * dx + dy * dy);
    return max(0.5 * dot(unitRange, screenTexSize), 1.0);
}

void main() {
    vec4 msd = texture(Sampler0, texCoord);
    float msdfDistance = median(msd.r, msd.g, msd.b);
    float screenRange = screenPxRange();

    // Below two output pixels of distance range, MSDF channel interpolation
    // can no longer reliably retain corner detail. At that scale the detail
    // is subpixel anyway, so transition to MTSDF's true-distance alpha for a
    // stable silhouette. Full-size and enlarged text remains pure MSDF.
    float msdfWeight = smoothstep(1.0, 2.0, screenRange);
    float distance = mix(msd.a, msdfDistance, msdfWeight);

    float screenPxDistance = screenRange * (distance - 0.5);
    float alpha = clamp(screenPxDistance + 0.5, 0.0, 1.0);

    // Apply vertex color (includes per-character tint/gradient)
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * alpha) * ColorModulator;

    // Discard fully transparent pixels for early-Z optimization
    if (color.a < 0.004) {
        discard;
    }

    fragColor = color;
}

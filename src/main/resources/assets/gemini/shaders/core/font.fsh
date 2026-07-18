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
//             there, which matters under minification and for soft effects.
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
// and on-screen scale from fwidth(). (MC's GuiRenderer only binds the default
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

// Distance range in atlas pixels, decoded from metadata texel (0,0):
// R stores round(RANGE * 16), i.e. 1/16 px resolution. texelFetch ignores
// filtering, so the LINEAR sampler does not disturb the value.
float pxRange() {
    return texelFetch(Sampler0, ivec2(0, 0), 0).r * (255.0 / 16.0);
}

float screenPxRange() {
    // textureSize = full atlas size, so unitRange is the field range in
    // atlas UV units and texCoord derivatives need no per-glyph scaling.
    vec2 unitRange = vec2(pxRange()) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / fwidth(texCoord);
    return max(0.5 * dot(unitRange, screenTexSize), 1.0);
}

void main() {
    vec4 msd = texture(Sampler0, texCoord);
    float sd = median(msd.r, msd.g, msd.b);

    // Where the multi-channel field saturates (deep inside/outside), it
    // carries no gradient — swap in the true SDF from the alpha channel.
    // Both encode distance with the same range, so the swap is continuous
    // at the saturation boundary and invisible at normal scale; under
    // minification (screenPxRange -> 1) it keeps silhouette coverage smooth.
    if (sd <= 0.0 || sd >= 1.0) {
        sd = msd.a;
    }

    float screenPxDistance = screenPxRange() * (sd - 0.5);
    float alpha = clamp(screenPxDistance + 0.5, 0.0, 1.0);

    // Apply vertex color (includes per-character tint/gradient)
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * alpha) * ColorModulator;

    // Discard fully transparent pixels for early-Z optimization
    if (color.a < 0.004) {
        discard;
    }

    fragColor = color;
}

#version 330

// SDF (Signed Distance Field) font fragment shader.
//
// The atlas stores distance values in the R channel:
//   0.0   = far outside the glyph
//   0.5   = exactly on the glyph edge
//   1.0   = at the glyph center
//
// smoothstep + fwidth() provides resolution-independent anti-aliasing:
// glyphs stay crisp at any zoom level — from 8px to 512px+.

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

void main() {
    float dist = texture(Sampler0, texCoord).r;

    float smoothing = fwidth(dist);

    float alpha =
        smoothstep(
            0.45 - smoothing,
            0.45 + smoothing,
            dist
        );

    // Apply vertex color (includes per-character tint/gradient)
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * alpha) * ColorModulator;

    // Discard fully transparent pixels for early-Z optimization
    if (color.a < 0.004) {
        discard;
    }

    fragColor = color;
}

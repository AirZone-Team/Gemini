// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Copies only the scissored source region into the blur snapshot. texelFetch
// matches a nearest-filtered framebuffer copy without normalized-coordinate
// rounding or reads from neighboring pixels.

uniform sampler2D InputSampler;

out vec4 fragColor;

[shader("fragment")]

void main() {
    fragColor = texelFetch(InputSampler, ivec2(gl_FragCoord.xy), 0);
}

#version 330

// Copies only the scissored source region into the blur snapshot. texelFetch
// matches a nearest-filtered framebuffer copy without normalized-coordinate
// rounding or reads from neighboring pixels.

uniform sampler2D InputSampler;

out vec4 fragColor;

void main() {
    fragColor = texelFetch(InputSampler, ivec2(gl_FragCoord.xy), 0);
}

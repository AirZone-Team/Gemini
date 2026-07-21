// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Box-filter downsample: averages a factor×factor block of source pixels.
// Uses linear sampling to reduce texture reads.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

[shader("fragment")]

void main() {
    vec2 oneTexel = 1.0 / InSize;
    // The downsample factor is implicit: InSize / OutSize
    vec2 factor = InSize / OutSize;
    float halfFactorX = factor.x * 0.5;
    float halfFactorY = factor.y * 0.5;

    // Sample the source at the center of each output pixel's footprint
    vec2 centerCoord = texCoord;

    // Box average via 4 samples at the corners of the footprint
    // (sufficient for factor <= 4; for larger factors, increase kernel)
    vec2 stepX = vec2(oneTexel.x * halfFactorX, 0.0);
    vec2 stepY = vec2(0.0, oneTexel.y * halfFactorY);

    vec4 s0 = texture(InSampler, centerCoord - stepX - stepY);
    vec4 s1 = texture(InSampler, centerCoord + stepX - stepY);
    vec4 s2 = texture(InSampler, centerCoord - stepX + stepY);
    vec4 s3 = texture(InSampler, centerCoord + stepX + stepY);

    fragColor = (s0 + s1 + s2 + s3) * 0.25;
}

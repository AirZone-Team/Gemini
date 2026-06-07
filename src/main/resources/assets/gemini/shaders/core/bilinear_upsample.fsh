#version 330

// Bilinear upsample: interpolates source pixels to target resolution.
// Uses hardware linear filtering on the sampler for free interpolation.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    // GL_LINEAR sampling on the sampler does the bilinear interpolation
    // automatically. We just need to map the output texcoord to the
    // correct input position.
    // texCoord ranges 0-1 over the output. We sample the input at
    // the corresponding position.

    // Offset by half a texel to sample at pixel centers
    vec2 inCoord = texCoord;

    fragColor = texture(InSampler, inCoord);
}

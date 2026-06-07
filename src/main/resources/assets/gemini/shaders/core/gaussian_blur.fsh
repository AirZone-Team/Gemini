#version 330

// Separable Gaussian blur for post-processing.
// Uses linear-sampling optimization: samples between pixels
// with step=2, reducing sample count by half.
// BlurDir and Radius come from the engine's BlurConfig UBO.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurConfig {
    vec2 BlurDir;
    float Radius;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 oneTexel = 1.0 / InSize;
    vec2 sampleStep = oneTexel * BlurDir;

    vec4 blurred = vec4(0.0);
    float actualRadius = max(1.0, round(Radius));

    // Linear-sampling trick: sample between texels at step=2,
    // halving the number of texture reads.
    for (float a = -actualRadius + 0.5; a <= actualRadius; a += 2.0) {
        blurred += texture(InSampler, texCoord + sampleStep * a);
    }
    // Last sample at half weight (accounts for the odd sample count)
    blurred += texture(InSampler, texCoord + sampleStep * actualRadius) / 2.0;

    fragColor = blurred / (actualRadius + 0.5);
}

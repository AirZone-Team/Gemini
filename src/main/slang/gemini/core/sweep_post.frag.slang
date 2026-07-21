// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
uniform sampler2D SceneSampler;

layout(std140) uniform SweepPostUniforms {
    vec4 Params;    // framebuffer width, height, time, unused
    vec4 Strength;  // distortion, chromatic, flash, vignette
    vec4 Tint;      // effect tint
};

in vec2 vUv;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0)), f.x), f.y);
}

[shader("fragment")]

void main() {
    vec2 centerVector = vUv - 0.5;
    float distanceToCenter = length(centerVector);
    vec2 direction = centerVector / max(distanceToCenter, 0.0001);
    float lens = exp(-distanceToCenter * distanceToCenter * 5.0);
    float wave = sin(distanceToCenter * 52.0 - Params.z * 13.0);
    float grain = noise2(vUv * vec2(70.0, 42.0) + Params.z * 0.7) - 0.5;
    float distortion = Strength.x * 0.018 * lens * (wave * 0.62 + grain * 0.38);
    vec2 warpedUv = clamp(vUv + direction * distortion, vec2(0.001), vec2(0.999));

    float chromatic = Strength.y * 0.0065 * (0.3 + distanceToCenter) * lens;
    vec3 scene;
    scene.r = texture(SceneSampler, clamp(warpedUv + direction * chromatic,
            vec2(0.001), vec2(0.999))).r;
    scene.g = texture(SceneSampler, warpedUv).g;
    scene.b = texture(SceneSampler, clamp(warpedUv - direction * chromatic,
            vec2(0.001), vec2(0.999))).b;

    float flashMask = exp(-distanceToCenter * distanceToCenter * 9.0)
            * (0.78 + 0.22 * sin(Params.z * 17.0));
    scene += Tint.rgb * Strength.z * flashMask * 0.34;

    float vignetteMask = smoothstep(0.18, 0.78, distanceToCenter);
    scene *= 1.0 - Strength.w * vignetteMask * 0.42;
    scene += Tint.rgb * Strength.w * lens * 0.035;
    fragColor = vec4(scene, 1.0);
}

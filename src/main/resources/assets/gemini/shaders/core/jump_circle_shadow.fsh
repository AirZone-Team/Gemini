#version 330

// Ground-projected shadow decal — animates in sync with the main ring.
// vertexColor.a carries the normalised lifetime progress (0→1).
// vertexColor.rgb carries the shadow tint (typically near-black).

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
        mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * noise(p);
        p *= 2.1;
        a *= 0.5;
    }
    return v;
}

// Radius animation — matches the main ring's animRadius
float animRadius(float t) {
    if (t < 0.05) return mix(1.0, 1.2, t / 0.05);
    if (t < 0.12) return mix(1.2, 1.0, (t - 0.05) / 0.07);
    return mix(1.0, 1.5, (t - 0.12) / 0.88);
}

// Master alpha — matches the main ring's animAlpha
float animAlpha(float t) {
    if (t < 0.03) return t / 0.03;
    if (t < 0.45) return 1.0;
    return 1.0 - smoothstep(0.45, 1.0, t);
}

void main() {
    vec2 center = uvCoord - 0.5;
    float dist = length(center) / 0.5;  // normalised 0→~1.4

    float lf = clamp(vertexColor.a, 0.0, 1.0);

    float rScale = animRadius(lf);
    float masterAlpha = animAlpha(lf);

    // Effective radius for the shadow — inner and outer fade
    float innerR = 0.18 * rScale;
    float outerR = 0.85 * rScale;

    float mask = smoothstep(innerR - 0.08, innerR, dist)
               * (1.0 - smoothstep(outerR, outerR + 0.25, dist));

    // Edge noise grows with lifetime (shadow breaks up as ring dissolves)
    float edgeNoise = fbm(center * 8.0 + lf * 2.5) * 0.3;
    float noisyMask = mask * (1.0 + edgeNoise - 0.15);

    // Darker centre (penumbra simulation)
    float centreBoost = smoothstep(0.0, 0.20, dist) * 0.55 + 0.45;
    noisyMask *= centreBoost;

    // Apply shadow opacity (from config, encoded in vertexColor.g) and anim alpha
    float shadowOpacity = vertexColor.g;
    float alpha = noisyMask * masterAlpha * shadowOpacity;

    fragColor = vec4(0.0, 0.0, 0.0, 0.0) * ColorModulator;

    if (fragColor.a < 0.0005) {
        discard;
    }
}

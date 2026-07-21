// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform SweepUniforms {
    vec4 Params;
    vec4 Geometry;
    vec4 Style;
    vec4 Motion;
    vec4 Primary;
    vec4 Accent;
    vec4 Core;
    vec4 Misc;
};

in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;

const float TAU = 6.28318530718;

vec3 spectrum(float t) {
    return 0.56 + 0.44 * cos(TAU * (t + vec3(0.0, 0.68, 0.35)));
}

vec3 palette(float t) {
    int mode = int(Style.y + 0.5);
    if (mode == 1) return mix(Primary.rgb, Accent.rgb, t);
    if (mode == 2) return spectrum(t - Params.x * Motion.x * 0.08);
    if (mode == 3) {
        float pulse = 0.5 + 0.5 * sin(Params.x * Motion.x * 4.0 + t * TAU);
        return mix(Primary.rgb, Accent.rgb, pulse);
    }
    return Primary.rgb;
}

vec4 particleMode() {
    vec2 p = (vUv - 0.5) * 2.0;
    float distanceToCenter = length(p);
    float angle = atan(p.y, p.x);
    float star = 0.72 + 0.28 * cos(angle * (4.0 + mod(Style.x, 3.0) * 2.0));
    float shape = smoothstep(star, star - 0.28, distanceToCenter);
    float core = exp(-distanceToCenter * distanceToCenter * 11.0);
    float rays = pow(abs(cos(angle * 2.0)), 22.0) * exp(-distanceToCenter * 2.5);
    float energy = (core + rays * 0.5 + exp(-distanceToCenter * 4.0) * 0.28) * shape;
    float life = vColor.r;
    float flicker = 0.86 + 0.14 * sin(Params.x * 28.0 + vColor.b * 31.0);
    vec3 color = mix(palette(vColor.b), Core.rgb, core);
    color *= energy * flicker * (1.0 + Geometry.w * core * 1.6);
    return vec4(color, energy * life * vColor.a * Primary.a);
}

vec4 speedLineMode() {
    float crossGlow = exp(-abs(vUv.y) * 4.8);
    float lengthFade = smoothstep(0.0, 0.13, vUv.x)
            * smoothstep(1.0, 0.48, vUv.x);
    float head = exp(-abs(vUv.x - 0.72) * 7.0);
    float flow = 0.68 + 0.32 * sin(vUv.x * 23.0 - Params.x * Motion.x * 9.0);
    float energy = crossGlow * lengthFade * (flow + head * 0.8);
    vec3 color = mix(palette(vColor.r), Core.rgb, head * 0.65);
    color *= energy * (1.0 + Geometry.w * crossGlow);
    return vec4(color, energy * vColor.a * Primary.a);
}

vec4 lightningMode() {
    float coreLine = exp(-abs(vUv.y) * 5.8);
    float aura = exp(-abs(vUv.y) * 1.65) * 0.34;
    float endFade = smoothstep(0.0, 0.08, vUv.x)
            * smoothstep(1.0, 0.9, vUv.x);
    float flicker = 0.72 + 0.28 * sin(Params.x * 51.0 + vColor.r * 27.0);
    float energy = (coreLine + aura) * endFade * flicker;
    vec3 color = mix(Accent.rgb, Core.rgb, coreLine);
    color *= energy * (1.3 + Geometry.w * coreLine * 2.2);
    return vec4(color, clamp(energy, 0.0, 1.0) * vColor.a * Primary.a);
}

[shader("fragment")]

void main() {
    vec4 result;
    if (vColor.g < 0.5) result = particleMode();
    else if (vColor.b < 0.5) result = speedLineMode();
    else result = lightningMode();
    fragColor = result * ColorModulator;
    if (fragColor.a < 0.002) discard;
}

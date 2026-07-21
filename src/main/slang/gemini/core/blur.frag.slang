// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Single-pass radial blur with SDF rounded-rectangle masking.
//
// Uniforms (BlurUniforms, std140):
//   Params1.xy  = inputResolution (framebuffer width, height)
//   Params1.z   = Quality (blur strength in pixels)
//   Params2.xy  = uSize (area width, height in pixels)
//   Params2.zw  = uLocation (area x, y in pixels)
//   Color1      = tint color (RGBA)
//   Params3     = corner radii (rTL, rTR, rBR, rBL in pixels)

uniform sampler2D InputSampler;

layout(std140) uniform BlurUniforms {
    vec3 Params1;
    vec4 Params2;
    vec4 Color1;
    vec4 Params3;
};

out vec4 fragColor;

float roundRectDistance(vec2 position, vec4 innerRect, vec4 radius) {
    vec2 halfSize = (innerRect.zw - innerRect.xy) * 0.5;
    vec2 center = (innerRect.xy + innerRect.zw) * 0.5;
    vec2 p = position - center;

    vec2 s = step(0.0, p);
    float rCurrent = mix(
        mix(radius.x, radius.w, s.y),
        mix(radius.y, radius.z, s.y),
        s.x
    );

    vec2 q = abs(p) - halfSize + rCurrent;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rCurrent;
}

vec4 blur() {
    vec2 inputResolution = Params1.xy;
    vec2 uv = gl_FragCoord.xy / inputResolution.xy;
    vec4 color = texture(InputSampler, uv);

    // A zero-radius blur is exactly the center sample. Avoid issuing the other
    // 80 identical texture reads while preserving the result.
    if (Params1.z <= 0.0) {
        return color + Color1;
    }

    vec2 radius = Params1.zz / inputResolution;

    // Eight axes sampled in both directions are the same 16 radial directions
    // as the previous sin/cos loop. Constants remove per-fragment trigonometry;
    // the sample count, positions, and weights remain unchanged.
    const vec2 directions[8] = vec2[](
        vec2(1.0, 0.0),
        vec2(0.923879533, 0.382683432),
        vec2(0.707106781, 0.707106781),
        vec2(0.382683432, 0.923879533),
        vec2(0.0, 1.0),
        vec2(-0.382683432, 0.923879533),
        vec2(-0.707106781, 0.707106781),
        vec2(-0.923879533, 0.382683432)
    );

    for (int d = 0; d < 8; ++d) {
        vec2 axis = directions[d] * radius;
        for (int i = 1; i <= 5; ++i) {
            vec2 offset = axis * (float(i) * 0.2);
            color += texture(InputSampler, uv + offset);
            color += texture(InputSampler, uv - offset);
        }
    }

    return color / 81.0 + Color1;
}

[shader("fragment")]

void main() {
    vec2 uSize = Params2.xy;
    vec2 uLocation = Params2.zw;
    vec4 radii = Params3;
    vec4 bounds = vec4(uLocation, uLocation + uSize);

    // 将 gl_FragCoord.y 翻转为 Top-Left 坐标系
    // Params1.y 是屏幕高度 (fbHeight)
    vec2 realFragCoord = vec2(gl_FragCoord.x, Params1.y - gl_FragCoord.y);

    // 使用翻转后的坐标计算距离
    float dist = roundRectDistance(realFragCoord, bounds, radii);
    float delta = fwidth(dist);
    float alpha = 1.0 - smoothstep(-delta, delta, dist);

    if (alpha < 0.001) discard;
    fragColor = vec4(blur().rgb, alpha);
}

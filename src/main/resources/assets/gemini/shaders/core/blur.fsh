#version 330

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
    #define TAU 6.28318530718

    vec2 inputResolution = Params1.xy;
    float Quality = Params1.z;
    vec2 Radius = Quality / inputResolution.xy;

    vec2 uv = gl_FragCoord.xy / inputResolution.xy;
    vec4 Color = texture(InputSampler, uv);

    float step = TAU / 16.0;

    for (float d = 0.0; d < TAU; d += step) {
        for (float i = 0.2; i <= 1.0; i += 0.2) {
            Color += texture(InputSampler, uv + vec2(cos(d), sin(d)) * Radius * i);
        }
    }

    Color /= 81.0;
    return (Color + Color1);
}

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

    fragColor = vec4(blur().rgb, alpha);
    if (alpha < 0.001) discard;
}

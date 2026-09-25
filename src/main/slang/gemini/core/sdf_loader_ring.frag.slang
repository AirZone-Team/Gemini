// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ============================================================================
//  启动加载画面四角星外圈环形进度 —— 轨道 + 渐变进度弧 + 笔尖辉光片元着色器
//
//  与 core/sdf_loader_star 配套使用（星形居中、环包围星形外圈），二者共同构成
//  "环 + 星"双层进度语言。仅几何绘制，无任何文字，满足重载期管线约束。
//
//  ── 顶点通道语义（复用 core/sdf_rounded 顶点着色器）────────────────────
//    elemSize    = (midR, thickness)   环中线半径与带厚（GUI px，int）
//    shapeParams = (ringP·10000, alpha·10000)
//                  ringP = 进度揭示 0..1；alpha = 元素整体透明度 0..1
//    pixelPos    = 元素局部像素坐标（quad 已外扩 AA_MARGIN）
//    元素包围盒半径 bound = midR + thickness/2，frag 内以 (bound, bound) 为圆心。
//
//  ── 路径参数 s（揭示顺序，与星形完全同式）──────────────────────────────
//    0 = 9 点钟方向（星形左顶点方位），屏幕 y 向下顺时针：
//    s = fract((atan(p.y, p.x) + PI) / TAU)。进度弧自 s=0 起顺时针扫至 ringP，
//    揭示测试 clamp((ringP − s)·REVEAL_SOFTNESS)；ringP ≥ 0.999 时整圈置满，
//    避免软边残留卡在起笔接缝。
//
//  ── 三层绘制 ──────────────────────────────────────────────────────────
//    轨道：整圈淡近白（TRACK_ALPHA≈0.13），进度为 0 时即可见，承载进度条语义；
//    进度弧：按角度取四锚色渐变（9 点橙 / 12 点红 / 3 点蓝 / 6 点绿），与星形
//      边界同角度同色，整体读作品牌渐变。锚色常量须与 sdf_loader_star 及
//      Java 端 GeminiLoadingOverlay 一致；
//    笔尖辉光：弧头后侧按角距指数衰减的提亮带，向热白混色并抬高不透明度，
//      满进度后熄灭。
//    叠合顺序：进度弧 over 轨道（over 合成），最终乘全局 alpha 与 ColorModulator。
//
//  ── 反走样 ────────────────────────────────────────────────────────────
//    沿用其余 SDF 管线的 fwidth 屏幕空间导数方案：带符号距离
//    d = |length(p) − midR| − thickness/2，edge = max(fwidth(d), 1e-4)。
// ============================================================================

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec4 vertexColor;   // 未使用（管线复用 SDF 顶点格式，颜色在着色器内常量化）
in vec2 pixelPos;      // 元素局部像素坐标
in vec2 elemSize;      // (midR, thickness)
in vec2 shapeParams;   // (ringP·10000, alpha·10000)

out vec4 fragColor;

const float PI  = 3.141592653589793;
const float TAU = 6.283185307179586;

const float REVEAL_SOFTNESS = 20.0;   // 揭示软边宽度（占整圈比例的倒数）
const float TRACK_ALPHA     = 0.13;   // 轨道不透明度
const float GLOW_TAIL       = 25.0;   // 笔尖辉光角距衰减（×圈数）
const float GLOW_GAIN       = 0.75;   // 笔尖辉光最大抬亮

// 左(9点)橙 / 上(12点)红 / 右(3点)蓝 / 下(6点)绿 —— 须与 sdf_loader_star 一致
const vec3 ANCHOR_L = vec3(0.949, 0.663, 0.231);
const vec3 ANCHOR_T = vec3(0.910, 0.263, 0.290);
const vec3 ANCHOR_R = vec3(0.180, 0.486, 0.902);
const vec3 ANCHOR_B = vec3(0.184, 0.639, 0.337);
const vec3 TRACK    = vec3(0.910, 0.925, 0.945);  // 0xFFE8ECF1，与星形描边同色
const vec3 HEAD     = vec3(0.980, 0.985, 1.000);  // 笔尖热白

[shader("fragment")]

void main() {
    float midR      = elemSize.x;
    float thickness = elemSize.y;
    float ringP     = clamp(shapeParams.x / 10000.0, 0.0, 1.0);
    float gAlpha    = clamp(shapeParams.y / 10000.0, 0.0, 1.0);
    if (gAlpha <= 0.004) {
        discard;
    }

    float bound = midR + thickness * 0.5;
    vec2  p     = pixelPos - vec2(bound);

    // 环带掩膜：到中线距离减半带宽，fwidth 抗锯齿
    float d     = abs(length(p) - midR) - thickness * 0.5;
    float edge  = max(fwidth(d), 1e-4);
    float band  = 1.0 - smoothstep(0.0, edge, d);

    // 路径参数：0=9 点钟，顺时针（与 sdf_loader_star 的 s 定义严格一致）
    float s = fract((atan(p.y, p.x) + PI) / TAU);

    // ── 轨道 ──
    float trkA = band * TRACK_ALPHA;

    // ── 进度弧：四锚色角渐变 + 顺时针揭示 ──
    float arcA = 0.0;
    vec3  arcC = vec3(0.0);
    if (ringP > 0.0) {
        float reveal = ringP >= 0.999 ? 1.0 : clamp((ringP - s) * REVEAL_SOFTNESS, 0.0, 1.0);
        if (reveal > 0.0) {
            float q  = s * 4.0;
            int   qi = int(floor(q)) % 4;
            float qf = fract(q);
            vec3 a0 = qi == 0 ? ANCHOR_L : qi == 1 ? ANCHOR_T : qi == 2 ? ANCHOR_R : ANCHOR_B;
            vec3 a1 = qi == 0 ? ANCHOR_T : qi == 1 ? ANCHOR_R : qi == 2 ? ANCHOR_B : ANCHOR_L;
            arcC = mix(a0, a1, qf);
            arcA = band * reveal;

            // 笔尖辉光：弧头后侧角距指数衰减；fract 环绕处理 0/1 接缝
            if (ringP < 0.999) {
                float dd   = fract(ringP - s);
                float glow = exp(-dd * GLOW_TAIL) * GLOW_GAIN;
                arcC  = mix(arcC, HEAD, glow);
                arcA += band * (1.0 - reveal) * glow * 0.6;
                arcA  = clamp(arcA + band * reveal * glow * 0.5, 0.0, 1.0);
            }
        }
    }

    // 进度弧 over 轨道，再乘全局 alpha
    float outA = arcA + trkA * (1.0 - arcA);
    vec3  outRGB = outA > 0.001
            ? (arcC * arcA + TRACK * trkA * (1.0 - arcA)) / outA
            : vec3(0.0);

    fragColor = vec4(outRGB, outA * gAlpha) * ColorModulator;
    if (fragColor.a < 0.004) {
        discard;
    }
}

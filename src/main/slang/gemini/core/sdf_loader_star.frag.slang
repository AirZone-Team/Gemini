// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ============================================================================
//  启动加载画面四角星（astroid 四尖内摆线）—— 描边 + 四色渐变填充片元着色器
//
//  形状即隐式曲线 |nx|^(2/3) + |ny|^(2/3) = 1（n = 像素相对星心 / 半径 r），
//  标量场 f = (|nx|^(2/3) + |ny|^(2/3))^1.5 − 1 沿射线单调，f=0 为边界，
//  f/fwidth(f) 给出一阶近似的像素距离 —— 与其余 SDF 管线相同的 fwidth
//  屏幕空间反走样方案，曲率尖峰（四个顶点）处为一阶近似，实际边缘平滑。
//
//  ── 顶点通道语义（复用 core/sdf_rounded 顶点着色器）────────────────────
//    elemSize    = (2r, 2r)              星形外接正方形边长
//    shapeParams = (outlineP·10000, fillP·10000)
//    pixelPos    = 元素局部像素坐标（quad 已外扩 AA_MARGIN）
//
//  ── 路径参数 s（描边/填充的揭示顺序）──────────────────────────────────
//    0 = 左顶点，屏幕 y 向下顺时针 L→T→R→B→L：
//    s = (PI − atan2(p.y, p.x)) / TAU ∈ [0,1)，恰在左顶点正下方回卷到 1。
//    揭示测试 clamp((progress − s)·24) 在满进度（≥0.999）时整体置 1，
//    避免软边残留卡在起笔接缝上。
//
//  ── 颜色（常量固化于此，须与 Java 端 GeminiLoadingOverlay 的锚色一致）──
//    左橙 0xFFF2A93B / 上红 0xFFE8434A / 右蓝 0xFF2E7CE6 / 下绿 0xFF2FA356
//    边界色按 s 在相邻锚色间插值，再以 f 场（0=星心, 1=边界）与中心均值色
//    径向混合 —— 复刻参考图的四角辐射渐变。描边为近白色细带（半宽常量须与
//    Java 端 OUTLINE_HALF_WIDTH 一致）。
// ============================================================================

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec4 vertexColor;   // 未使用（管线复用 SDF 顶点格式，颜色在着色器内常量化）
in vec2 pixelPos;      // 元素局部像素坐标
in vec2 elemSize;      // (2r, 2r)
in vec2 shapeParams;   // (outlineP·10000, fillP·10000)

out vec4 fragColor;

const float PI = 3.141592653589793;

const float OUTLINE_HALF_WIDTH = 0.9;  // 须与 Java 端 OUTLINE_HALF_WIDTH 一致
const float REVEAL_SOFTNESS    = 24.0; // 揭示软边宽度（占整圈比例的倒数）

// 左橙 / 上红 / 右蓝 / 下绿 / 中心均值
const vec3 ANCHOR_L = vec3(0.949, 0.663, 0.231);
const vec3 ANCHOR_T = vec3(0.910, 0.263, 0.290);
const vec3 ANCHOR_R = vec3(0.180, 0.486, 0.902);
const vec3 ANCHOR_B = vec3(0.184, 0.639, 0.337);
const vec3 CENTER   = vec3(0.556, 0.513, 0.440);
const vec3 OUTLINE  = vec3(0.910, 0.925, 0.945);  // 0xFFE8ECF1

[shader("fragment")]

void main() {
    float r = elemSize.x * 0.5;
    float outlineP = clamp(shapeParams.x / 10000.0, 0.0, 1.0);
    float fillP    = clamp(shapeParams.y / 10000.0, 0.0, 1.0);

    vec2 n = (pixelPos - elemSize * 0.5) / max(r, 1.0);

    // astroid 标量场：f=0 边界，f>0 外部；t=1+f 即 0(星心)→1(边界) 的径向参数
    float g = pow(abs(n.x), 2.0 / 3.0) + pow(abs(n.y), 2.0 / 3.0);
    float f = pow(g, 1.5) - 1.0;
    float aa   = max(fwidth(f), 1.0e-5);
    float dPix = f / aa;  // 一阶像素距离（fwidth 归一）

    // 路径参数：0=左顶点，屏幕 y 向下顺时针 L→T→R→B——必须与 CPU 网格兜底的
    // starX/starY 参数化一致（θ 连续支取 [π,3π)，s=(θ−π)/TAU，即 fract((θ+π)/TAU)；
    // 若写 (π−θ)/TAU 会上下颠倒：顶点绿、底点红，且扫向逆时针）
    float theta = atan(n.y, n.x);
    float s     = fract((theta + PI) / (2.0 * PI));

    // ── 填充：四色渐变 + 顺时针扫入揭示 ──
    float q = s * 4.0;
    int   qi = int(floor(q)) % 4;
    float qf = fract(q);
    vec3  a0 = qi == 0 ? ANCHOR_L : qi == 1 ? ANCHOR_T : qi == 2 ? ANCHOR_R : ANCHOR_B;
    vec3  a1 = qi == 0 ? ANCHOR_T : qi == 1 ? ANCHOR_R : qi == 2 ? ANCHOR_B : ANCHOR_L;
    vec3  boundary = mix(a0, a1, qf);
    vec3  fillRGB  = mix(CENTER, boundary, sqrt(clamp(1.0 + f, 0.0, 1.0)));

    float inside = clamp(0.5 - dPix, 0.0, 1.0);
    float fillA;
    if (fillP >= 0.999) {
        fillA = inside;
    } else {
        fillA = inside * clamp((fillP - s) * REVEAL_SOFTNESS, 0.0, 1.0);
    }

    // ── 描边：|d| ≲ 半宽的细带 + 同向揭示 ──
    float lineA = 0.0;
    if (outlineP > 0.0) {
        float band = 1.0 - smoothstep(OUTLINE_HALF_WIDTH - 0.5,
                                      OUTLINE_HALF_WIDTH + 0.5, abs(dPix));
        float reveal = outlineP >= 0.999
                ? 1.0
                : clamp((outlineP - s) * REVEAL_SOFTNESS, 0.0, 1.0);
        lineA = band * reveal;
    }

    // 线压在填充之上（over 合成）
    float outA = lineA + fillA * (1.0 - lineA);
    vec3  outRGB = outA > 0.001
            ? (OUTLINE * lineA + fillRGB * fillA * (1.0 - lineA)) / outA
            : vec3(0.0);

    fragColor = vec4(outRGB, outA) * ColorModulator;
    if (fragColor.a < 0.004) {
        discard;
    }
}

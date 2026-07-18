#version 330

// ============================================================================
//  Material 3 Expressive 圆形波浪进度条 —— 进度带（progress band）片元着色器
//
//  只绘制“进度带”本体；平坦的整圈轨道由 SdfUIRenderer.drawRing() 单独绘制
//  （复用 sdf_rounded_rect 的内描边环）。两者叠合即 M3E 的
//  CircularWavyProgressIndicator 视觉效果。
//
//  ── 几何定义（GUI px，y 轴向下）────────────────────────────────────────
//    中线半径  midR       = elemSize.x        （SHORT 顶点通道，逐元素）
//    带厚      thickness  = elemSize.y        （SHORT 顶点通道，逐元素）
//    标称内径 = midR - thickness/2，标称外径 = midR + thickness/2
//    波浪使中线沿径向 ±AMPLITUDE 偏移：
//      波峰外凸至 midR + thickness/2 + AMPLITUDE（元素包围盒半径）
//      波谷内收至 midR - thickness/2 - AMPLITUDE
//    起始角度：12 点方向（START = -90°）；扫描方向：顺时针
//    （屏幕 y 向下，atan(p.y, p.x) 增大即顺时针）
//    进度弧：theta ∈ [0, progress·TAU)，两端为半径 thickness/2 的圆角帽
//
//  ── 波浪动画参数 ──────────────────────────────────────────────────────
//    振幅 AMPLITUDE = 1.5 px   —— 中线径向 excursion（常量，须与 Java 端
//                                  SdfUIRenderer.WAVE_AMPLITUDE 保持一致）
//    频率 WAVES     = 8 / 圈   —— 每圈扇贝数；取整数保证 12 点接缝处中线
//                                  连续（rc(0) == rc(TAU)），满环无断纹
//    相位 phase     ∈ [0, TAU) —— shapeParams.y / 32767 · TAU；由 Java 端
//                                  按 WAVE_PERIOD_MS 周期推进，波峰随相位
//                                  增长沿顺时针方向漂移（与填充同向）
//    进度 progress  ∈ [0, 1]   —— shapeParams.x / 10000
//
//  ── 填充与颜色策略 ────────────────────────────────────────────────────
//    进度以弧长表达（扫描填充）；颜色为垂直双线性渐变（vertexColor：
//    TL/TR = 顶部浅紫，BL/BR = 底部深紫），弧自顶部生长，视觉上随进度
//    “加深”，与 Mellow 主题的粉彩紫罗兰调色板一致。
//
//  ── SDF 构造 ──────────────────────────────────────────────────────────
//    将像素角 theta 钳制到进度弧 [0, arc] 得到最近中线角 tC，再求像素到
//    波浪中线上对应点的距离并减去半带厚 —— 钳制天然给出两端圆角帽。
//    反走样沿用现有 SDF 管线的 fwidth 屏幕空间导数方案。
// ============================================================================

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;   // 垂直渐变：上 = 弧起始浅紫，下 = 深紫
in vec2 pixelPos;      // 元素局部像素坐标（quad 外扩 AA_MARGIN）
in vec2 elemSize;      // (中线半径, 带厚)
in vec2 shapeParams;   // (progress·10000, phase01·32767)

out vec4 fragColor;

const float PI  = 3.141592653589793;
const float TAU = 6.283185307179586;

const float AMPLITUDE = 1.2;        // 波浪振幅 px —— 必须等于 Java 端 WAVE_AMPLITUDE
const float WAVES     = 10.0;       // 每圈波浪数 —— 必须等于 Java 端 WAVE_COUNT
const float START     = -PI * 0.5;  // 起始角：12 点方向

void main() {
    float midR      = elemSize.x;
    float thickness = elemSize.y;
    float progress  = clamp(shapeParams.x / 10000.0, 0.0, 1.0);
    float phase     = shapeParams.y / 32767.0 * TAU;

    float bound = midR + thickness * 0.5 + AMPLITUDE;  // 元素包围盒半边长
    vec2  p     = pixelPos - vec2(bound);              // 圆心相对坐标

    // 相对起始角、顺时针为正的像素方位角，归一化到 [0, TAU)。
    // 注意必须归一到进度弧的定义域 [0, TAU)：若归一到 [-PI, PI)，弧后半段
    // （theta > PI）的像素会回卷成负角并被钳制到弧起点，导致满环时半圆缺失。
    float theta = atan(p.y, p.x) - START;
    theta -= TAU * floor(theta / TAU);

    // 最近中线角（钳制到进度弧 → 两端圆角帽）
    float arc = progress * TAU;
    float tC  = clamp(theta, 0.0, arc);

    // 波浪中线：径向余弦调制，相位前移 → 波峰顺时针漂移。
    // 注意 tC 是相对起始角的坐标，重建中线点时必须加回 START 转换到 atan 坐标系。
    float rc = midR + AMPLITUDE * cos(WAVES * tC - phase);
    vec2  cc = rc * vec2(cos(tC + START), sin(tC + START));

    // 到波浪中线的距离 − 半带厚
    float d     = length(p - cc) - thickness * 0.5;
    float edge  = max(fwidth(d), 1e-4);
    float alpha = 1.0 - smoothstep(0.0, edge, d);

    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * alpha);

    if (fragColor.a < 0.004) {
        discard;
    }
}

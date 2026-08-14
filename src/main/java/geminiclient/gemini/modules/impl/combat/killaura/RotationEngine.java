package geminiclient.gemini.modules.impl.combat.killaura;

import geminiclient.gemini.base.MinecraftInstance;
import geminiclient.gemini.utils.MathHelper;
import geminiclient.gemini.utils.animation.SpringAnimation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 旋转引擎：持有服务器视角（serverYaw/serverPitch）并负责所有旋转模式
 * （Linear / Exponential / Smooth / Bezier / Adaptive / Perlin）、
 * 每 tick 速度噪声、距离相关 Jitter 与 Perlin 噪声表。
 * 每 tick 由模块按“更新 Jitter → 叠加 SettleWobble → apply”的顺序驱动。
 *
 * <p>Smooth 模式（Eased / Direct 两种样式）内置人化瞄准：
 * 动态基础速度（每 tick 随机波动 + 距离联动）、独立噪声源（yaw/pitch 各一张
 * Perlin 表 + 微分白噪声）、瞄准延迟缓冲（50~150ms 反应时间 ± 波动）、
 * 偶发抖动脉冲（某轴增量瞬时放大 1.5~3.0 倍）、段内随机缓动（可混合两条曲线）、
 * 高频微颤（幅度 0.01~0.05°）与偶发帧跳过（<5%，模拟“愣神”）。</p>
 */
public final class RotationEngine implements MinecraftInstance {
    private final KillAuraSettings settings;

    float serverYaw, serverPitch;

    // Smooth mode state
    private float smoothStartYaw, smoothStartPitch, smoothProgress;
    private float smoothEndYaw, smoothEndPitch;
    private Entity smoothTarget;
    private int smoothEasingIndex;      // 本段随机选中的缓动曲线
    private int smoothEasingBlendIndex; // 混入的第二条曲线（可选）
    private float smoothEasingBlend;    // 两条曲线的混合权重 (0 = 不混合)

    // 抖动脉冲：偶发把某轴增量放大 1.5~3.0 倍，模拟手部微校正
    private int smoothPulseCooldown;
    private int smoothPulseTicks;
    private boolean smoothPulseYawAxis;
    private float smoothPulseBoost;
    private int smoothFrameSkipTicks; // 偶发跳过一帧（模拟“愣神”），跳过后有冷却

    // 瞄准延迟环形缓冲：目标角滞后 latencyTicks 个 tick（模拟反应时间）
    private static final int SMOOTH_LATENCY_BUFFER = 8;
    private final float[] latencyYawBuffer = new float[SMOOTH_LATENCY_BUFFER];
    private final float[] latencyPitchBuffer = new float[SMOOTH_LATENCY_BUFFER];
    private int latencyHead;
    private int latencyTicks;
    private int latencyReevalTicks;
    private int latencyWarmup; // 缓冲未填满时直接读当前目标，避免用到陈旧角度

    // 独立噪声源：Smooth 模式的 yaw / pitch 各用一张排列表 + 各轴独立采样时间
    // （非相位偏移，两轴速度变化完全解耦）
    private final int[] humanPermutationYaw = new int[256];
    private final int[] humanPermutationDoubledYaw = new int[512];
    private final int[] humanPermutationPitch = new int[256];
    private final int[] humanPermutationDoubledPitch = new int[512];
    private boolean humanTableReady;
    private float humanNoiseTimeYaw;
    private float humanNoiseTimePitch;
    private float stepNoiseTime; // 用于调制噪声采样步长的次级噪声时间

    // Bezier mode state
    private float bezierStartYaw, bezierStartPitch, bezierProgress;
    private float bezierControl1Yaw, bezierControl1Pitch;
    private float bezierControl2Yaw, bezierControl2Pitch;
    private float bezierEndYaw, bezierEndPitch;
    private Entity bezierTarget;

    private float jitterYaw, jitterPitch, jitterTargetYaw, jitterTargetPitch;
    private int jitterRefreshTicks;
    private Entity jitterTarget;

    // Perlin 噪声旋转状态（采样位置每 tick 随机推进，相位按轴错开）
    private float perlinNoiseTime;
    private float perlinYawPhase;
    private float perlinPitchPhase;

    // 每 tick 旋转速度噪声的时间推进量（用于驱动 speedNoiseScale 采样）
    private float rotationNoiseTime;

    private static final int JITTER_MIN_REFRESH_INTERVAL = 3;
    private static final int JITTER_MAX_REFRESH_INTERVAL = 7;
    private static final float JITTER_LERP_FACTOR = 0.25f;
    private static final float SMOOTH_REPLAN_THRESHOLD = 3f;
    private static final float BEZIER_MIN_CONTROL_POSITION = 0.20f;
    private static final float BEZIER_MAX_CONTROL_POSITION = 0.40f;
    private static final float BEZIER_CONTROL_DISTANCE_CAP = 90f;
    private static final float BEZIER_SHORT_SEGMENT_SCALE = 12f;

    // Smooth 人化瞄准常量
    private static final float SMOOTH_SPEED_VARIATION = 0.70f;      // 基础速度波动下限倍率 0.7
    private static final float SMOOTH_PULSE_MIN_BOOST = 1.5f;       // 脉冲增量放大 1.5~3.0 倍
    private static final float SMOOTH_PULSE_MAX_BOOST = 3.0f;
    private static final int SMOOTH_PULSE_MIN_INTERVAL = 40;        // 每次脉冲间隔 tick 范围
    private static final int SMOOTH_PULSE_MAX_INTERVAL = 90;
    private static final float SMOOTH_FRAME_SKIP_CHANCE = 0.04f;    // <5% 概率跳过一帧（愣神）
    private static final int EASING_COUNT = 4;                      // applyBaseEasing 曲线数量

    // 256 项 + 2 层取模环绕的排列表；每次模块启用时重新洗牌，让轨迹不可预测。
    private final int[] perlinPermutation = new int[256];
    private final int[] perlinPermutationDoubled = new int[512];
    private boolean perlinTableReady;

    public RotationEngine(KillAuraSettings settings) {
        this.settings = settings;
    }

    // ---------------------------------------------------------------------
    // 对外接口（模块编排用）
    // ---------------------------------------------------------------------

    public float yaw() {
        return serverYaw;
    }

    public float pitch() {
        return serverPitch;
    }

    public float jitterYaw() {
        return jitterYaw;
    }

    public float jitterPitch() {
        return jitterPitch;
    }

    /** 把服务器视角同步回玩家当前朝向（启用 / 复位时调用）。 */
    public void syncToPlayer() {
        if (mc.player != null) {
            serverYaw = mc.player.getYRot();
            serverPitch = mc.player.getXRot();
        }
    }

    /** 启用时重排 Perlin 表并复位噪声采样位置。 */
    public void reseedPerlin() {
        reseedPerlinPermutation();
        reseedHumanPermutation();
        resetPerlinNoise();
        resetHumanNoiseTime();
    }

    /** 复位全部旋转状态（smooth / bezier / perlin / jitter / 噪声时间）并同步服务器视角。 */
    public void reset() {
        smoothTarget = null;
        smoothProgress = 0f;
        smoothEndYaw = 0f;
        smoothEndPitch = 0f;
        smoothEasingIndex = 0;
        smoothEasingBlendIndex = 0;
        smoothEasingBlend = 0f;
        smoothPulseCooldown = 0;
        smoothPulseTicks = 0;
        smoothPulseYawAxis = false;
        smoothPulseBoost = 1f;
        smoothFrameSkipTicks = 0;
        latencyHead = 0;
        latencyTicks = 0;
        latencyReevalTicks = 0;
        latencyWarmup = SMOOTH_LATENCY_BUFFER;
        resetBezier();
        resetPerlinNoise();
        resetHumanNoiseTime();
        syncToPlayer();
        for (int i = 0; i < SMOOTH_LATENCY_BUFFER; i++) {
            latencyYawBuffer[i] = serverYaw;
            latencyPitchBuffer[i] = serverPitch;
        }
        rotationNoiseTime = 0f;
        resetJitter();
    }

    /** 距离相关的手部微颤更新；目标变化时自动复位。 */
    public void updateJitter(Entity target, double distance) {
        float baseAmount = settings.rotationJitter.getValue();
        if (baseAmount <= 0f) {
            resetJitter();
            return;
        }
        if (target != jitterTarget) {
            resetJitter();
            jitterTarget = target;
        }

        float normalizedDistance = Mth.clamp(
                (float) (distance / Math.max(settings.range.getValue(), 0.001f)), 0f, 1f);
        float amount = baseAmount * (1f
                + settings.distanceSpread.getValue() * normalizedDistance * normalizedDistance);
        if (--jitterRefreshTicks <= 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            jitterTargetYaw = centeredRandom(random, amount);
            jitterTargetPitch = centeredRandom(random, amount * 0.65f);
            jitterRefreshTicks = random.nextInt(
                    JITTER_MIN_REFRESH_INTERVAL, JITTER_MAX_REFRESH_INTERVAL + 1);
        }
        jitterTargetYaw = Mth.clamp(jitterTargetYaw, -amount, amount);
        jitterTargetPitch = Mth.clamp(
                jitterTargetPitch, -amount * 0.65f, amount * 0.65f);
        jitterYaw += (jitterTargetYaw - jitterYaw) * JITTER_LERP_FACTOR;
        jitterPitch += (jitterTargetPitch - jitterPitch) * JITTER_LERP_FACTOR;
    }

    /**
     * 按当前模式向目标角度逼近一步，并写入 serverYaw/serverPitch。
     * targetYaw/targetPitch 为已经叠加 jitter + settleWobble 的最终目标角；
     * distance 为与目标瞄准点的距离（用于 Smooth 距离联动减速）。
     */
    public void apply(float targetYaw, float targetPitch, Entity target, float distance) {
        float rotYawDiff = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw);
        float rotPitchDiff = targetPitch - serverPitch;
        float baseSpeed = settings.rotationSpeed.getValue();

        // 瞄准延迟：每 tick 把目标角写入环形缓冲；Smooth 模式读取 latencyTicks 之前的
        // 角度作为“当前瞄准点”（50~150ms 反应时间 + ±30ms 随机波动）
        if (settings.rotationMode.is("Smooth")) {
            latencyYawBuffer[latencyHead] = targetYaw;
            latencyPitchBuffer[latencyHead] = targetPitch;
            latencyHead = (latencyHead + 1) % SMOOTH_LATENCY_BUFFER;
            if (latencyWarmup > 0) {
                latencyWarmup--;
            }
            if (--latencyReevalTicks <= 0) {
                latencyReevalTicks = 20; // 每秒重新评估一次延迟（含随机波动）
                int latencyMs = settings.aimLatency.getValue();
                if (latencyMs <= 0) {
                    latencyTicks = 0;
                } else {
                    int halfVariance = Math.max(Math.round(settings.latencyVariance.getValue() / 50f), 0);
                    latencyTicks = Mth.clamp(Math.round(latencyMs / 50f)
                                    + ThreadLocalRandom.current().nextInt(-halfVariance, halfVariance + 1),
                            0, SMOOTH_LATENCY_BUFFER - 1);
                }
            }
        }

        // 每 tick 旋转增量带噪声：对 yaw / pitch 取相位错开的噪声样本缩放速度，
        // 让 deltaYaw / deltaPitch 每个 tick 都不同（模拟人手的速度波动）。
        advanceRotationNoiseTime();
        float speedYaw = baseSpeed * speedNoiseScale(0f);
        float speedPitch = baseSpeed * speedNoiseScale(0.37f);
        float speedAdvance = speedNoiseScale(0.19f);
        if (!settings.rotationMode.is("Bezier")) {
            resetBezier();
        }

        // 记录本 tick 叠加前的服务器视角：switch 结束后用“增量”而非“绝对值”
        // 做 GCD 量化，保证 Pulse / Noise / Tremor 及所有模式的步进都被对齐。
        float prevYaw = serverYaw;
        float prevPitch = serverPitch;

        switch (settings.rotationMode.get()) {
            case "Linear": {
                serverYaw += Math.copySign(Math.min(Math.abs(rotYawDiff), speedYaw), rotYawDiff);
                serverPitch += Math.copySign(Math.min(Math.abs(rotPitchDiff), speedPitch), rotPitchDiff);
                break;
            }
            case "Exponential": {
                float factorYaw = Math.min(speedYaw / 180f, 0.99f);
                float factorPitch = Math.min(speedPitch / 180f, 0.99f);
                serverYaw += rotYawDiff * factorYaw;
                serverPitch += rotPitchDiff * factorPitch;
                break;
            }
            case "Smooth": {
                ThreadLocalRandom random = ThreadLocalRandom.current();

                // 偶发“愣神”：<5% 概率跳过一帧不更新旋转，之后冷却数 tick 避免连续跳过
                if (smoothFrameSkipTicks > 0) {
                    smoothFrameSkipTicks--;
                } else if (random.nextFloat() < SMOOTH_FRAME_SKIP_CHANCE) {
                    smoothFrameSkipTicks = 2 + random.nextInt(6);
                    break;
                }

                // 读取延迟后的瞄准点（反应时间滞后）
                int readIndex = (latencyHead - 1 - latencyTicks + SMOOTH_LATENCY_BUFFER)
                        % SMOOTH_LATENCY_BUFFER;
                if (latencyWarmup > 0) {
                    readIndex = (latencyHead - 1 + SMOOTH_LATENCY_BUFFER) % SMOOTH_LATENCY_BUFFER;
                }
                float latchedYaw = latencyYawBuffer[readIndex];
                float latchedPitch = latencyPitchBuffer[readIndex];

                // 本 tick 人化速度标量：随机波动（0.7~1.3 于全强度）× 距离联动（越近越慢，
                // 但叠加随机扰动避免严格反比）
                float fluctuation = settings.speedFluctuation.getValue();
                float speedMultiplier = 1f - fluctuation
                        + fluctuation * (SMOOTH_SPEED_VARIATION
                        + (1f - SMOOTH_SPEED_VARIATION) * random.nextFloat());
                float normalizedDistance = Mth.clamp(
                        distance / Math.max(settings.range.getValue(), 0.001f), 0f, 1f);
                float distanceFactor = Mth.clamp(
                        1f - 0.5f * (1f - normalizedDistance) * (0.9f + 0.2f * random.nextFloat()),
                        0.5f, 1f);

                // 独立噪声源：两轴各用自己的 Perlin 表 + 独立采样时间，外加每 tick
                // 两轴独立的高频微分白噪声，彻底打乱增量间的整数关系
                advanceHumanNoiseTime();
                float axisYawFactor = 1f + 0.5f * fluctuation * humanSpeedFactor(false);
                float axisPitchFactor = 1f + 0.5f * fluctuation * humanSpeedFactor(true);
                float diffYaw = (random.nextFloat() - 0.5f) * 0.16f;
                float diffPitch = (random.nextFloat() - 0.5f) * 0.16f;

                // 抖动脉冲：每隔随机间隔，使一个轴的增量短暂放大 1.5~3.0 倍再恢复
                if (smoothPulseCooldown > 0) {
                    smoothPulseCooldown--;
                }
                if (smoothPulseCooldown <= 0) {
                    smoothPulseCooldown = random.nextInt(
                            SMOOTH_PULSE_MIN_INTERVAL, SMOOTH_PULSE_MAX_INTERVAL + 1);
                    smoothPulseYawAxis = random.nextBoolean();
                    smoothPulseBoost = SMOOTH_PULSE_MIN_BOOST
                            + random.nextFloat() * (SMOOTH_PULSE_MAX_BOOST - SMOOTH_PULSE_MIN_BOOST);
                    smoothPulseTicks = 1 + random.nextInt(2); // 持续 1~2 tick
                }
                float pulseScaleYaw = 1f;
                float pulseScalePitch = 1f;
                if (smoothPulseTicks > 0) {
                    smoothPulseTicks--;
                    if (settings.pulseStrength.getValue() > 0f) {
                        float boost = 1f + (smoothPulseBoost - 1f) * settings.pulseStrength.getValue();
                        if (smoothPulseYawAxis) {
                            pulseScaleYaw = boost;
                        } else {
                            pulseScalePitch = boost;
                        }
                    }
                }

                float endpointYawDiff = Math.abs(
                        MathHelper.wrapAngleTo180_float(latchedYaw - smoothEndYaw));
                float endpointPitchDiff = Math.abs(latchedPitch - smoothEndPitch);
                if (target != smoothTarget || smoothProgress >= 1f
                        || endpointYawDiff + endpointPitchDiff > SMOOTH_REPLAN_THRESHOLD) {
                    smoothTarget = target;
                    smoothStartYaw = serverYaw;
                    smoothStartPitch = serverPitch;
                    smoothEndYaw = latchedYaw;
                    smoothEndPitch = latchedPitch;
                    smoothProgress = 0f;
                    // 随机化插值方式：本段随机选缓动曲线，必要时混入第二条曲线
                    smoothEasingIndex = random.nextInt(EASING_COUNT);
                    smoothEasingBlend = random.nextFloat() < 0.35f ? random.nextFloat() : 0f;
                    smoothEasingBlendIndex = smoothEasingBlend > 0f
                            ? random.nextInt(EASING_COUNT - 1) : 0;
                    if (smoothEasingBlendIndex >= smoothEasingIndex) {
                        smoothEasingBlendIndex++;
                    }
                }
                float totalYawDiff = MathHelper.wrapAngleTo180_float(smoothEndYaw - smoothStartYaw);
                float totalPitchDiff = smoothEndPitch - smoothStartPitch;
                float totalDist = Math.abs(totalYawDiff) + Math.abs(totalPitchDiff);
                if (totalDist <= 0.5f) {
                    serverYaw = smoothEndYaw;
                    serverPitch = smoothEndPitch;
                    smoothProgress = 1f;
                } else if (settings.smoothStyle.is("Direct")) {
                    // 无顺滑样式：不用缓动曲线，按本 tick 的人化速度直接向段目标逼近，
                    // 每轴增量 = 基础速度 × 随机波动 × 距离联动 × 独立轴噪声 × 微分白噪声 × 脉冲
                    float remYaw = MathHelper.wrapAngleTo180_float(smoothEndYaw - serverYaw);
                    float remPitch = smoothEndPitch - serverPitch;
                    float stepYaw = baseSpeed * speedMultiplier * distanceFactor
                            * axisYawFactor * (1f + diffYaw) * pulseScaleYaw;
                    float stepPitch = baseSpeed * speedMultiplier * distanceFactor
                            * axisPitchFactor * (1f + diffPitch) * pulseScalePitch;
                    serverYaw += Math.copySign(Math.min(Math.abs(remYaw), stepYaw), remYaw);
                    serverPitch += Math.copySign(Math.min(Math.abs(remPitch), stepPitch), remPitch);
                    if (Math.abs(remYaw) <= stepYaw && Math.abs(remPitch) <= stepPitch) {
                        serverYaw = smoothEndYaw;
                        serverPitch = smoothEndPitch;
                        smoothProgress = 1f;
                    }
                } else {
                    // 顺滑样式：缓动曲线包络路径形状，本 tick 的人化速度只调制进度推进
                    // 速度（时间轴非线性），并随机选择曲线 / 混合曲线 + 过冲
                    float duration = Math.max(totalDist / baseSpeed, 3f);
                    float tickScale = speedMultiplier * distanceFactor
                            * (0.5f + 0.5f * (axisYawFactor * pulseScaleYaw
                            + axisPitchFactor * pulseScalePitch));
                    smoothProgress = Math.min(
                            smoothProgress + speedAdvance * tickScale / duration, 1f);
                    float eased = applyRandomEasing(smoothProgress);
                    serverYaw = smoothStartYaw + totalYawDiff * eased;
                    serverPitch = smoothStartPitch + totalPitchDiff * eased;
                }
                break;
            }
            case "Bezier": {
                float endpointYawDiff = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - bezierEndYaw));
                float endpointPitchDiff = Math.abs(targetPitch - bezierEndPitch);
                if (target != bezierTarget || bezierProgress >= 1f
                        || endpointYawDiff + endpointPitchDiff > SMOOTH_REPLAN_THRESHOLD) {
                    planBezierSegment(targetYaw, targetPitch, target);
                }

                float totalYawDiff = bezierEndYaw - bezierStartYaw;
                float totalPitchDiff = bezierEndPitch - bezierStartPitch;
                float totalDist = (float) Math.hypot(totalYawDiff, totalPitchDiff);
                if (totalDist <= 0.5f) {
                    serverYaw = bezierEndYaw;
                    serverPitch = bezierEndPitch;
                    bezierProgress = 1f;
                } else {
                    float duration = Math.max(totalDist / baseSpeed, 3f);
                    bezierProgress = Math.min(bezierProgress + speedAdvance / duration, 1f);
                    float eased = applyBaseEasing(bezierProgress);
                    serverYaw = cubicBezier(bezierStartYaw, bezierControl1Yaw,
                            bezierControl2Yaw, bezierEndYaw, eased);
                    serverPitch = cubicBezier(bezierStartPitch, bezierControl1Pitch,
                            bezierControl2Pitch, bezierEndPitch, eased);
                    if (bezierProgress >= 1f) {
                        serverYaw = bezierEndYaw;
                        serverPitch = bezierEndPitch;
                    }
                }
                break;
            }
            case "Adaptive": {
                float yawDist = Math.abs(rotYawDiff);
                float pitchDist = Math.abs(rotPitchDiff);
                float yawStep = speedYaw * (0.5f + yawDist / 90f);
                float pitchStep = speedPitch * (0.5f + pitchDist / 90f);
                serverYaw += Math.copySign(Math.min(yawDist, yawStep), rotYawDiff);
                serverPitch += Math.copySign(Math.min(pitchDist, pitchStep), rotPitchDiff);
                break;
            }
            case "Perlin": {
                // 柏林噪音驱动旋转：每 tick 的步长 = RotationSpeed × 柏林噪声速度倍率。
                // RotationSpeed 是唯一的主控——噪声只在其基础上做连续的速度起伏，
                // 步长下限也按 RotationSpeed 等比缩放，任何情况下都不脱离旋转速度。
                advancePerlinNoiseTime();

                // 相邻 tick 采样点足够近 → 噪声信号平滑过渡；
                // 分形叠加（octaves）提供细节层次，相位按轴错开使 yaw/pitch 相关但不同。
                float maxStepYaw = baseSpeed * Math.max(perlinSpeedFactor(perlinYawPhase), 0.05f);
                float maxStepPitch = baseSpeed * Math.max(perlinSpeedFactor(perlinPitchPhase), 0.05f);

                serverYaw += Math.copySign(Math.min(Math.abs(rotYawDiff), maxStepYaw), rotYawDiff);
                serverPitch += Math.copySign(Math.min(Math.abs(rotPitchDiff), maxStepPitch), rotPitchDiff);
                break;
            }
        }

        // 微颤（Micro-tremor）：Smooth 模式在最终角度上叠加幅度 0.01~0.05° 的高频噪声，
        // 模拟肌肉震颤，使增量永不“静止”（幅度远小于反作弊阈值）
        if (settings.rotationMode.is("Smooth")) {
            float tremor = settings.microTremor.getValue();
            if (tremor > 0f) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                serverYaw += (random.nextFloat() - 0.5f) * 2f * tremor;
                serverPitch += (random.nextFloat() - 0.5f) * 2f * tremor * 0.7f;
            }
        }

        // GCD 量化：把本 tick 的全部增量（含 Pulse / Noise / Tremor）对齐到当前
        // 灵敏度的整数倍——真实鼠标只能产生 k·GCD 的角度步进，亚格增量在物理上
        // 不可达；小于半格的修正会被取整为 0（人手同样无法做出亚格微动）。
        float gcd = gcdStep();
        if (gcd > 0f) {
            serverYaw = prevYaw + quantize(serverYaw - prevYaw, gcd);
            serverPitch = prevPitch + quantize(serverPitch - prevPitch, gcd);
        }

        // [修复] 强制限制最终的 serverPitch 避免平滑计算越界翻转引发的反作弊拦截
        serverPitch = Mth.clamp(serverPitch, -90f, 90f);
    }

    // ---------------------------------------------------------------------
    // Smooth / Bezier 辅助
    // ---------------------------------------------------------------------

    private void planBezierSegment(float targetYaw, float targetPitch, Entity target) {
        bezierTarget = target;
        bezierStartYaw = serverYaw;
        bezierStartPitch = serverPitch;

        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw);
        float pitchDiff = Mth.clamp(targetPitch, -90f, 90f) - serverPitch;
        bezierEndYaw = serverYaw + yawDiff;
        bezierEndPitch = serverPitch + pitchDiff;
        bezierProgress = 0f;

        float distance = (float) Math.hypot(yawDiff, pitchDiff);
        if (distance <= 0.001f) {
            bezierControl1Yaw = bezierEndYaw;
            bezierControl1Pitch = bezierEndPitch;
            bezierControl2Yaw = bezierEndYaw;
            bezierControl2Pitch = bezierEndPitch;
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        float control1Position = BEZIER_MIN_CONTROL_POSITION
                + random.nextFloat() * (BEZIER_MAX_CONTROL_POSITION - BEZIER_MIN_CONTROL_POSITION);
        float control2Position = 1f - (BEZIER_MIN_CONTROL_POSITION
                + random.nextFloat() * (BEZIER_MAX_CONTROL_POSITION - BEZIER_MIN_CONTROL_POSITION));

        float perpendicularYaw = -pitchDiff / distance;
        float perpendicularPitch = yawDiff / distance;
        float shortSegmentScale = Mth.clamp(
                distance / BEZIER_SHORT_SEGMENT_SCALE, 0f, 1f);
        float maximumOffset = settings.bezierRandomness.getValue()
                * Math.min(distance, BEZIER_CONTROL_DISTANCE_CAP) * shortSegmentScale;
        float control1Offset = centeredRandom(random, maximumOffset);
        float control2Offset = centeredRandom(random, maximumOffset);

        bezierControl1Yaw = bezierStartYaw + yawDiff * control1Position
                + perpendicularYaw * control1Offset;
        bezierControl1Pitch = Mth.clamp(
                bezierStartPitch + pitchDiff * control1Position
                        + perpendicularPitch * control1Offset,
                -90f, 90f);
        bezierControl2Yaw = bezierStartYaw + yawDiff * control2Position
                + perpendicularYaw * control2Offset;
        bezierControl2Pitch = Mth.clamp(
                bezierStartPitch + pitchDiff * control2Position
                        + perpendicularPitch * control2Offset,
                -90f, 90f);
    }

    private static float cubicBezier(float start, float control1, float control2, float end, float t) {
        float inverse = 1f - t;
        float inverseSquared = inverse * inverse;
        float tSquared = t * t;
        return inverseSquared * inverse * start
                + 3f * inverseSquared * t * control1
                + 3f * inverse * tSquared * control2
                + tSquared * t * end;
    }

    private float applyBaseEasing(float t) {
        return switch (settings.easingCurve.get()) {
            case "EaseInOutCubic" -> SpringAnimation.easeInOutCubic(t);
            case "EaseOutCubic" -> SpringAnimation.easeOutCubic(t);
            case "EaseOutExpo" -> SpringAnimation.easeOutExpo(t);
            case "Smoothstep" -> t * t * (3f - 2f * t);
            default -> t;
        };
    }

    /**
     * Smooth 顺滑样式的缓动：本段随机选一条曲线（必要时混入第二条），
     * 让速度比例在时间轴上不断变化；随后叠加可调大的过冲（overshoot）。
     * settings.randomEasing 关闭时退化为原固定曲线 + 过冲。
     */
    private float applyRandomEasing(float t) {
        float eased;
        if (settings.randomEasing.enabled) {
            eased = easingAt(smoothEasingIndex, t);
            if (smoothEasingBlend > 0f) {
                eased += (easingAt(smoothEasingBlendIndex, t) - eased) * smoothEasingBlend;
            }
        } else {
            eased = applyBaseEasing(t);
        }
        float overshootStrength = settings.overshoot.getValue();
        if (overshootStrength > 0f) {
            float back = SpringAnimation.easeOutBack(t);
            eased += (back - eased) * overshootStrength;
        }
        return eased;
    }

    /** 按索引取缓动曲线（与 EasingCurve 列表顺序一致）。 */
    private static float easingAt(int index, float t) {
        return switch (index) {
            case 0 -> SpringAnimation.easeInOutCubic(t);
            case 1 -> SpringAnimation.easeOutCubic(t);
            case 2 -> SpringAnimation.easeOutExpo(t);
            default -> t * t * (3f - 2f * t); // Smoothstep
        };
    }

    private void resetBezier() {
        bezierTarget = null;
        bezierProgress = 0f;
        bezierStartYaw = 0f;
        bezierStartPitch = 0f;
        bezierControl1Yaw = 0f;
        bezierControl1Pitch = 0f;
        bezierControl2Yaw = 0f;
        bezierControl2Pitch = 0f;
        bezierEndYaw = 0f;
        bezierEndPitch = 0f;
    }

    // ---------------------------------------------------------------------
    // 旋转速度噪声
    // ---------------------------------------------------------------------

    /** 每 tick 推进噪声采样时间；速度噪声以 2π 为周期重复，防止缓慢累积到不可见的高频。
     *  步长从固定 0.8 改为每 tick 随机，避免噪声以固定的 ~7.85 tick 周期重复（时间间隔异常）。 */
    private void advanceRotationNoiseTime() {
        float step = 0.45f + ThreadLocalRandom.current().nextFloat() * 0.70f;
        rotationNoiseTime = (rotationNoiseTime + step) % (float) (Math.PI * 2.0);
    }

    /**
     * 采样一个带噪声的速度倍率：围绕 1.0 上下摆动，幅度受 speedNoise 控制，
     * smoothness 越高摆动越收敛（更平滑）。phase 为各通道的相位偏移，
     * 确保 yaw / pitch / 进度三条噪声相互独立。
     *
     * 纯余弦噪声在波峰/波谷导数为 0，会连续多 tick 停留在极小值/极大值；
     * 混入每 tick 的随机微扰后增量每 tick 都有变化，不再长时间卡在极限速度。
     */
    private float speedNoiseScale(float phase) {
        float strength = settings.speedNoise.getValue();
        if (strength <= 0f)
            return 1f;
        float smoothness = settings.noiseSmoothness.getValue();
        float s = smoothNoise(rotationNoiseTime + phase);
        // smoothness 越高随机成分越弱（更平滑），越低越接近纯随机（更“抖”）
        float randomBlend = (1f - smoothness) * 0.45f;
        if (randomBlend > 0f) {
            s = s * (1f - randomBlend)
                    + randomBlend * ThreadLocalRandom.current().nextFloat();
        }
        // s ∈ [0,1]；amplitude ∈ [0.5, 1.0] × strength，smoothness 越高幅度越小
        float amplitude = strength * (0.5f + 0.5f * smoothness);
        return 1f + amplitude * (2f * s - 1f);
    }

    /** 0..1 范围内的平滑伪噪声：两个异相余弦叠加，无突兀跳变。 */
    private static float smoothNoise(float time) {
        return 0.5f + 0.5f * (
                0.62f * (float) Math.cos(time)
                        + 0.38f * (float) Math.cos(time * 2.3f + 0.71f));
    }

    private float centeredRandom(ThreadLocalRandom random, float amount) {
        return ((random.nextFloat() + random.nextFloat()) - 1f) * amount;
    }

    private void resetJitter() {
        jitterTarget = null;
        jitterYaw = 0f;
        jitterPitch = 0f;
        jitterTargetYaw = 0f;
        jitterTargetPitch = 0f;
        jitterRefreshTicks = 0;
    }

    // -------------------------------------------------------------------------
    // GCD 量化：把旋转增量对齐到当前灵敏度下真实鼠标的“最小可达步进”
    // -------------------------------------------------------------------------

    /**
     * 当前灵敏度下的 GCD（度/像素）：真实鼠标移动 1 像素（dpi=1）产生的角度增量。
     * 公式沿 {@code MouseHandler.turnPlayer} × {@code Entity.turn} 推导：
     * 原始 delta 经 ({@code (0.6·sensitivity+0.2)³·8}) 换算成角度后又乘以 0.15。
     * 灵敏度变化后无需缓存，每次读取 OptionInstance 的值，开销可忽略。
     */
    private float gcdStep() {
        double sensitivity = mc.options.sensitivity().get();
        double ss = sensitivity * 0.6 + 0.2;
        return (float) (ss * ss * ss * 8.0 * 0.15);
    }

    /**
     * 把增量强制对齐到 GCD 的整数倍：返回 {@code round(delta/gcd)·gcd}。
     * 小于半格的方向修正会取整为 0（人手无法做出亚格步进，接近目标时自然“停住”），
     * 因此不会出现围绕目标 ±GCD 的持续振荡。
     */
    private static float quantize(float delta, float gcd) {
        if (delta == 0f) {
            return 0f;
        }
        return Math.round(delta / gcd) * gcd;
    }

    // -------------------------------------------------------------------------
    // Perlin 噪声旋转：每 tick 的步长由 1D 柏林噪声 + 分形叠加驱动
    // -------------------------------------------------------------------------

    private void reseedPerlinPermutation() {
        for (int i = 0; i < 256; i++) {
            perlinPermutation[i] = i;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 255; i > 0; i--) {
            int swap = rng.nextInt(i + 1);
            int temp = perlinPermutation[i];
            perlinPermutation[i] = perlinPermutation[swap];
            perlinPermutation[swap] = temp;
        }
        for (int i = 0; i < 512; i++) {
            perlinPermutationDoubled[i] = perlinPermutation[i & 255];
        }
        perlinTableReady = true;
    }

    /**
     * 每 tick 推进噪声采样位置：步长每 tick 随机，时间间隔本身不固定。
     * 基础步长范围扩大到 0.3~2.0，且步长本身再被一个次级噪声调制（调制频率也随机），
     * 使噪声的“节奏”不可预测。
     */
    private void advancePerlinNoiseTime() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float baseStep = 0.3f + random.nextFloat() * 1.7f;
        if (!perlinTableReady) {
            reseedPerlinPermutation();
        }
        float modulator = 0.7f + 0.6f * perlin1D(stepNoiseTime);
        stepNoiseTime += 0.04f + random.nextFloat() * 0.10f;
        float step = baseStep * modulator;
        perlinNoiseTime += step * settings.perlinFrequency.getValue();
        if (perlinNoiseTime > 4096f) {
            perlinNoiseTime %= 4096f;
        }
        if (perlinNoiseTime < 0f) {
            perlinNoiseTime += 4096f;
        }
    }

    /** 采样该轴当前的噪声速度倍率（fBm 叠加，波动幅度受 perlinAmplitude 控制）。 */
    private float perlinSpeedFactor(float phase) {
        float strength = settings.perlinAmplitude.getValue();
        float octaves = Math.max(settings.perlinOctaves.getValue(), 1);
        float persistence = settings.perlinPersistence.getValue();
        float lacunarity = Math.max(settings.perlinLacunarity.getValue(), 1.0001f);

        // fBm 归一化因子：避免不同 octaves 配置下平均幅度漂移
        float amp = 1f;
        float amplitudeSum = 0f;
        float octaveFrequency = 1f;
        float value = 0f;
        for (int i = 0; i < octaves; i++) {
            value += perlin1D(perlinNoiseTime + phase) * amp;
            amplitudeSum += amp;
            amp *= persistence;
            octaveFrequency *= lacunarity;
            phase *= octaveFrequency;
        }
        value /= Math.max(amplitudeSum, 0.0001f);

        // 叠加一层纯随机微扰：让过零点不完全由格点间距决定，避免间隔特征过于规律
        float jitter = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.06f;
        return 1f + strength * value + jitter;
    }

    /** 标准 1D 柏林噪声，输出近似 ∈ [-1, 1]。 */
    private float perlin1D(float coordinate) {
        int x0 = (int) Math.floor(coordinate);
        float frac = coordinate - x0;
        float u = frac * frac * (3f - 2f * frac);
        int hashA = perlinPermutationDoubled[x0 & 255];
        int hashB = perlinPermutationDoubled[(x0 + 1) & 255];
        return lerpPerlin(u, gradient1D(hashA, frac), gradient1D(hashB, frac - 1f));
    }

    private static float lerpPerlin(float amount, float from, float to) {
        return from + amount * (to - from);
    }

    /** 用 hash 从 8 个固定梯度里取一个，避免对负坐标取模。 */
    private static float gradient1D(int hash, double coordinate) {
        return switch (hash & 7) {
            case 0 -> (float) coordinate;
            case 1 -> (float) -coordinate;
            case 2 -> (float) (coordinate * 0.70710678f);
            case 3 -> (float) (-coordinate * 0.70710678f);
            case 4 -> (float) (coordinate * 0.35355339f);
            case 5 -> (float) (-coordinate * 0.35355339f);
            case 6 -> (float) (coordinate * 0.17677670f);
            default -> (float) (-coordinate * 0.17677670f);
        };
    }

    private void resetPerlinNoise() {
        perlinNoiseTime = 0f;
        perlinYawPhase = 0f;
        // 相位按轴错开（yaw/pitch 采样点不同），保证两轴速度起伏不完全同步
        perlinPitchPhase = 3.7f;
        perlinTableReady = false;
        stepNoiseTime = 0f;
    }

    // -------------------------------------------------------------------------
    // Smooth 人化噪声：yaw / pitch 各用一张独立排列表 + 独立采样时间，
    // 两轴速度变化完全解耦；采样步长受次级噪声调制，节奏不可预测
    // -------------------------------------------------------------------------

    private void reseedHumanPermutation() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int[] perm : new int[][]{humanPermutationYaw, humanPermutationPitch}) {
            for (int i = 0; i < 256; i++) {
                perm[i] = i;
            }
            for (int i = 255; i > 0; i--) {
                int swap = rng.nextInt(i + 1);
                int temp = perm[i];
                perm[i] = perm[swap];
                perm[swap] = temp;
            }
        }
        for (int i = 0; i < 512; i++) {
            humanPermutationDoubledYaw[i] = humanPermutationYaw[i & 255];
            humanPermutationDoubledPitch[i] = humanPermutationPitch[i & 255];
        }
        humanTableReady = true;
    }

    /** 每 tick 推进两轴噪声采样位置：步长在 0.3~2.0 随机基础上再被次级噪声调制。 */
    private void advanceHumanNoiseTime() {
        if (!humanTableReady) {
            reseedHumanPermutation();
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float baseStep = 0.3f + random.nextFloat() * 1.7f;
        stepNoiseTime += 0.05f + random.nextFloat() * 0.12f;
        float modulator = 0.7f + 0.6f * humanPerlin1D(humanPermutationDoubledYaw, stepNoiseTime);
        float step = baseStep * modulator;
        humanNoiseTimeYaw += step;
        // 两轴推进快慢略不同，进一步破坏两轴速度变化的时间关联
        humanNoiseTimePitch += step * (0.85f + 0.3f * random.nextFloat());
        if (humanNoiseTimeYaw > 4096f) {
            humanNoiseTimeYaw %= 4096f;
        }
        if (humanNoiseTimePitch > 4096f) {
            humanNoiseTimePitch %= 4096f;
        }
    }

    /** 采样指定轴的噪声速度倍率：fBm 叠加，输出近似 [-1, 1]。 */
    private float humanSpeedFactor(boolean pitchAxis) {
        int[] table = pitchAxis ? humanPermutationDoubledPitch : humanPermutationDoubledYaw;
        float time = pitchAxis ? humanNoiseTimePitch : humanNoiseTimeYaw;
        float octaves = Math.max(settings.perlinOctaves.getValue(), 1);
        float persistence = settings.perlinPersistence.getValue();
        float lacunarity = Math.max(settings.perlinLacunarity.getValue(), 1.0001f);

        float amp = 1f;
        float amplitudeSum = 0f;
        float octaveFrequency = 1f;
        float value = 0f;
        for (int i = 0; i < octaves; i++) {
            value += humanPerlin1D(table, time) * amp;
            amplitudeSum += amp;
            amp *= persistence;
            octaveFrequency *= lacunarity;
            time *= octaveFrequency;
        }
        value /= Math.max(amplitudeSum, 0.0001f);
        return value;
    }

    /** 用指定排列表采样的 1D 柏林噪声，输出近似 ∈ [-1, 1]。 */
    private static float humanPerlin1D(int[] doubledTable, float coordinate) {
        int x0 = (int) Math.floor(coordinate);
        float frac = coordinate - x0;
        float u = frac * frac * (3f - 2f * frac);
        int hashA = doubledTable[x0 & 255];
        int hashB = doubledTable[(x0 + 1) & 255];
        return lerpPerlin(u, gradient1D(hashA, frac), gradient1D(hashB, frac - 1f));
    }

    private void resetHumanNoiseTime() {
        humanNoiseTimeYaw = 0f;
        humanNoiseTimePitch = 0f;
        stepNoiseTime = 0f;
        humanTableReady = false;
    }
}

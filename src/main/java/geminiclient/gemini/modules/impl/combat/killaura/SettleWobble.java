package geminiclient.gemini.modules.impl.combat.killaura;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 瞄准稳定微颤：在准星接近目标时叠加阻尼弹簧振荡与偶发微校正脉冲，
 * 模拟人手握持鼠标时的稳定过程与残余震颤；大角度转向时自动停用。
 * 每 tick 调用一次 {@link #update(float, float, float)}。
 */
public final class SettleWobble {
    private static final float EXIT_ERROR_SCALE = 1.6f; // 退出阈值 = 进入阈值 * 该系数（滞回）
    private static final float SPRING_K = 90f;           // 弹簧刚度 → 自然频率 ≈ 9.5 rad/s
    private static final float SPRING_DAMPING = 7f;      // 阻尼 → 阻尼比 ≈ 0.37，1~2 秒收敛
    private static final int FLICK_MIN_TICKS = 4;
    private static final int FLICK_MAX_TICKS = 16;
    private static final float DT = 0.05f;               // 20 ticks/s

    private float yaw, pitch;
    private float velYaw, velPitch;
    private int flickTicks;
    private boolean active;

    /** 每 tick 调用；errorDegrees 为服务器视角与目标方向的角度误差（度）。 */
    public void update(float errorDegrees, float strength, float enterRange) {
        if (strength <= 0f || errorDegrees > enterRange * EXIT_ERROR_SCALE) {
            reset();
            return;
        }
        if (!active) {
            if (errorDegrees < enterRange) {
                active = true; // 进入瞄准区，手部微动开始
            } else {
                return;
            }
        }

        // 阻尼弹簧向零收敛（半隐式欧拉）
        velYaw += (-SPRING_K * yaw - SPRING_DAMPING * velYaw) * DT;
        velPitch += (-SPRING_K * pitch - SPRING_DAMPING * velPitch) * DT;
        yaw += velYaw * DT;
        pitch += velPitch * DT;

        // 偶发微校正脉冲：速度脉冲 1.0~3.0 °/s，经弹簧转化为约 0.1~0.35° 的手部震颤
        if (--flickTicks <= 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            float flick = strength * (1.0f + random.nextFloat() * 2.0f);
            velYaw += random.nextBoolean() ? flick : -flick;
            velPitch += random.nextBoolean() ? flick * 0.6f : -flick * 0.6f;
            flickTicks = FLICK_MIN_TICKS
                    + random.nextInt(FLICK_MAX_TICKS - FLICK_MIN_TICKS + 1);
        }

        // 限制幅度，防止异常抖动
        float maxWobble = strength * 1.5f;
        yaw = Math.max(-maxWobble, Math.min(maxWobble, yaw));
        pitch = Math.max(-maxWobble * 0.7f, Math.min(maxWobble * 0.7f, pitch));
    }

    public void reset() {
        active = false;
        yaw = 0f;
        pitch = 0f;
        velYaw = 0f;
        velPitch = 0f;
        flickTicks = 0;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}

package geminiclient.gemini.modules.impl.combat.killaura;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 人类化点击节奏机：三角形分布的 CPS + 自然波动 + 偶发双击 + 呼吸停顿 + 距离肾上腺素。
 * 独立于 Minecraft 类型的纯状态机，参数通过 {@link Config} 注入，便于模块侧实时调节。
 */
public final class HumanClickPattern {
    /** 每次点击的基础间隔范围（毫秒），兜底防止极端值。 */
    private static final double MIN_INTERVAL_SCALE = 0.7;
    private static final double MAX_INTERVAL_SCALE = 1.3;

    private final Config config;

    private int clickStreak;
    private int nextPauseAfterClicks;
    private int doubleClickCooldown;
    private boolean pendingDoubleClick;

    public HumanClickPattern(Config config) {
        this.config = config;
        reset();
    }

    public void reset() {
        clickStreak = 0;
        nextPauseAfterClicks = randomPauseEvery();
        doubleClickCooldown = 0;
        pendingDoubleClick = false;
    }

    /**
     * 计算下一次点击的延迟（毫秒）。
     *
     * @param distanceToTarget 与目标的距离（用于肾上腺素调节）
     * @param closeRange       贴身阈值（低于它点击加速）
     * @param farRange         远距阈值（高于它点击放缓）
     */
    public long nextDelay(double distanceToTarget, double closeRange, double farRange) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 双击的第二下：极短间隔
        if (pendingDoubleClick) {
            pendingDoubleClick = false;
            doubleClickCooldown = config.doubleClickCooldownClicks;
            return (long) (config.doubleClickGapMin
                    + random.nextDouble() * (config.doubleClickGapMax - config.doubleClickGapMin));
        }

        // 三角形分布：CPS 偏向区间中部，而不是均匀噪声
        double pickedCps = config.minCps
                + (random.nextDouble() + random.nextDouble()) * 0.5
                * (config.maxCps - config.minCps);
        double interval = 1000.0 / Math.max(1.0, pickedCps);

        // 人手节奏不是均匀的：±variance 自然波动
        interval *= 1.0 - config.variance + random.nextDouble() * 2.0 * config.variance;

        // 距离肾上腺素：贴身狂点、远了求稳
        if (distanceToTarget < closeRange) {
            interval *= 1.0 - config.adrenaline;
        } else if (distanceToTarget > farRange) {
            interval *= 1.0 + config.adrenaline;
        }

        // 周期性呼吸停顿（调整握持/鼠标手势）
        if (++clickStreak >= nextPauseAfterClicks) {
            clickStreak = 0;
            nextPauseAfterClicks = randomPauseEvery();
            interval += config.pauseMin
                    + random.nextDouble() * (config.pauseMax - config.pauseMin);
        }

        // 偶发双击（人类点击常见的意外连点）
        if (doubleClickCooldown > 0) {
            doubleClickCooldown--;
        } else if (random.nextDouble() < config.doubleClickChance) {
            pendingDoubleClick = true;
        }

        // 兜底限制，避免极端值
        double minInterval = 1000.0 / Math.max(config.maxCps, 1);
        double maxInterval = 1000.0 / Math.min(config.minCps, 1);
        return (long) Math.max(minInterval * MIN_INTERVAL_SCALE,
                Math.min(maxInterval * MAX_INTERVAL_SCALE, interval));
    }

    private int randomPauseEvery() {
        return config.pauseEveryMin + ThreadLocalRandom.current()
                .nextInt(config.pauseEveryMax - config.pauseEveryMin + 1);
    }

    /** 可调参数集合；模块侧每次调用前填充。 */
    public static final class Config {
        public int minCps = 10;
        public int maxCps = 18;
        public double variance = 0.12;
        public double doubleClickChance = 0.06;
        public double doubleClickGapMin = 30;
        public double doubleClickGapMax = 80;
        public int doubleClickCooldownClicks = 8;
        public int pauseEveryMin = 12;
        public int pauseEveryMax = 24;
        public double pauseMin = 100;
        public double pauseMax = 220;
        public double adrenaline = 0.12;
    }
}

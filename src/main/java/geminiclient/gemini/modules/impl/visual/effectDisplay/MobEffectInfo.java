package geminiclient.gemini.modules.impl.visual.effectDisplay;

// 确保 SmoothAnimationTimer 也在同一个包下
public class MobEffectInfo {
    // 动画计时器，使用 0.2F 的速度进行平滑
    public SmoothAnimationTimer xTimer = new SmoothAnimationTimer(-60.0F, 0.2F);
    public SmoothAnimationTimer yTimer = new SmoothAnimationTimer(-1.0F, 0.2F);
    public SmoothAnimationTimer durationTimer = new SmoothAnimationTimer(-1.0F, 0.2F);

    public int maxDuration = -1;
    public int duration = 0;
    public int amplifier = 0;
    public boolean shouldDisappear = false;
    public float width;
}
package geminiclient.gemini.modules.impl.visual.effectDisplay;

public class SmoothAnimationTimer {
    public float value;
    public float target;
    private final float speed = 0.2f; // 插值速度 (例如 0.2F)

    public SmoothAnimationTimer(float initialValue, float speed) {
        this.value = initialValue;
        this.target = initialValue;
    }

    public void update(boolean smooth) {
        if (smooth) {
            // 线性插值 (Lerp)
            this.value += (this.target - this.value) * this.speed;

            // 检查是否足够接近目标，防止浮点数无限接近
            if (Math.abs(this.target - this.value) < 0.001F) {
                this.value = this.target;
            }
        } else {
            this.value = this.target;
        }
    }
}
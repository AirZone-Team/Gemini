package geminiclient.gemini.modules.impl.visual.clickgui.md3;

/**
 * Time-based tween animation with Material standard easing (ease-in-out cubic).
 * Replaces spring physics in the MD3 GUI: animations run for a fixed duration
 * and are fully deterministic / controllable.
 */
public final class Md3Anim {

    /** MD3 duration short4 — switches, hearts. */
    public static final long DURATION_SHORT = 200;
    /** MD3 duration medium2 — expansion, pill slide, scroll. */
    public static final long DURATION_MEDIUM = 300;

    private final long durationMs;

    private float from = 0f;
    private float target = 0f;
    private long startMs = 0;

    public Md3Anim(long durationMs) {
        this.durationMs = Math.max(1, durationMs);
    }

    public static Md3Anim shortAnim() {
        return new Md3Anim(DURATION_SHORT);
    }

    public static Md3Anim mediumAnim() {
        return new Md3Anim(DURATION_MEDIUM);
    }

    /** Retarget; the animation continues from the current displayed value. */
    public void setTarget(float newTarget) {
        if (newTarget == target) return;
        this.from = getValue();
        this.target = newTarget;
        this.startMs = System.currentTimeMillis();
    }

    /** Jump immediately to a value with no transition. */
    public void snap(float value) {
        this.from = value;
        this.target = value;
        this.startMs = System.currentTimeMillis() - durationMs;
    }

    public float getTarget() {
        return target;
    }

    /** Current eased value in [from, target]. */
    public float getValue() {
        float p = (System.currentTimeMillis() - startMs) / (float) durationMs;
        if (p >= 1.0f) return target;
        if (p <= 0.0f) return from;
        return from + (target - from) * easeInOutCubic(p);
    }

    public boolean isSettled() {
        return System.currentTimeMillis() - startMs >= durationMs;
    }

    /** Material standard easing (cubic ease-in-out). */
    public static float easeInOutCubic(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    /** Material emphasized decelerate (cubic ease-out). */
    public static float easeOutCubic(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }
}

package geminiclient.gemini.utils.animation;

/**
 * Spring-physics animation with weight and inertia, plus filmic easing functions.
 *
 * <p>Replaces simple lerp with a damped spring simulation.
 * Produces natural-feeling motion with overshoot and settle,
 * similar to Linear, Apple VisionOS, and other modern UIs.</p>
 *
 * <h3>Usage — Spring</h3>
 * <pre>{@code
 * SpringAnimation spring = new SpringAnimation(0.0f)
 *     .stiffness(0.18f)
 *     .damping(0.72f);
 *
 * // Each frame:
 * spring.setTarget(isHovered ? 1.0f : 0.0f);
 * spring.update(partialTicks);
 * float value = spring.getValue();
 * }</pre>
 *
 * <h3>Usage — Easing (no spring state needed)</h3>
 * <pre>{@code
 * float eased = SpringAnimation.easeOutCubic(rawProgress);
 * float alpha = rawProgress;                     // 0→1 fade
 * float offsetY = (1f - rawProgress) * 8f;       // float up from below
 * }</pre>
 *
 * <h3>Presets</h3>
 * <ul>
 *   <li>{@link #smooth()} — gentle, no overshoot (hover transitions)</li>
 *   <li>{@link #snappy()} — responsive, slight bounce (toggle switches)</li>
 *   <li>{@link #bouncy()} — playful, noticeable bounce (expands)</li>
 * </ul>
 *
 * <h3>Filmic easing functions</h3>
 * <ul>
 *   <li>{@link #easeOutCubic(float)} — fast start, slow end (expand/collapse)</li>
 *   <li>{@link #easeInOutCubic(float)} — smooth both ends (hover lift)</li>
 *   <li>{@link #easeOutBack(float)} — overshoot and settle (dramatic reveals)</li>
 * </ul>
 */
public class SpringAnimation {

    private float value;
    private float target;
    private float velocity;

    private float stiffness = 0.15f;
    private float damping = 0.75f;
    private float precision = 0.001f;

    /**
     * Create a spring starting at the given value.
     */
    public SpringAnimation(float initialValue) {
        this.value = initialValue;
        this.target = initialValue;
        this.velocity = 0.0f;
    }

    /**
     * Create a spring starting at 0.
     */
    public SpringAnimation() {
        this(0.0f);
    }

    // ── Fluent configuration ────────────────────────────

    public SpringAnimation stiffness(float s) { this.stiffness = s; return this; }
    public SpringAnimation damping(float d)    { this.damping = d;    return this; }
    public SpringAnimation precision(float p)  { this.precision = p;  return this; }

    // ── Presets ─────────────────────────────────────────

    /** Gentle transition, no overshoot. Good for hover states. */
    public static SpringAnimation smooth() {
        return new SpringAnimation(0.0f).stiffness(0.14f).damping(0.78f);
    }

    /** Responsive with light bounce. Good for toggle switches. */
    public static SpringAnimation snappy() {
        return new SpringAnimation(0.0f).stiffness(0.22f).damping(0.68f);
    }

    /** Playful bounce. Good for expand/collapse. */
    public static SpringAnimation bouncy() {
        return new SpringAnimation(0.0f).stiffness(0.18f).damping(0.62f);
    }

    // ── Core simulation ─────────────────────────────────

    /**
     * Set the target value toward which the spring pulls.
     */
    public void setTarget(float target) {
        this.target = target;
    }

    /**
     * Instantly snap to a value (no animation).
     */
    public void snap(float value) {
        this.value = value;
        this.target = value;
        this.velocity = 0.0f;
    }

    /**
     * Advance the spring simulation by one frame.
     *
     * @param partialTicks frame delta from Minecraft's render loop
     */
    public void update(float partialTicks) {
        // Scale stiffness by frame delta for frame-rate independence
        float dt = Math.min(partialTicks, 3.0f);  // cap to avoid explosion on lag spikes
        float scaledStiffness = stiffness * dt;

        // Spring force: pulls toward target
        float force = (target - value) * scaledStiffness;

        // Apply force to velocity
        velocity += force;

        // Apply damping (energy loss)
        velocity *= Math.pow(damping, dt);

        // Update position
        value += velocity;

        // Snap if close enough to target and nearly stopped
        if (Math.abs(target - value) < precision && Math.abs(velocity) < precision) {
            value = target;
            velocity = 0.0f;
        }
    }

    // ── Accessors ───────────────────────────────────────

    /**
     * @return current spring value (0.0 – 1.0 range for normalized springs)
     */
    public float getValue() {
        return value;
    }

    /**
     * @return true if the spring has settled at its target
     */
    public boolean isSettled() {
        return value == target && velocity == 0.0f;
    }

    /**
     * @return current velocity (for visual effects that respond to motion)
     */
    public float getVelocity() {
        return velocity;
    }

    // ── Filmic easing functions (stateless) ────────────

    /**
     * Ease-out cubic: fast start, gentle deceleration.
     * Ideal for expand/collapse, panel reveals.
     *
     * @param t raw progress 0..1
     * @return eased progress 0..1
     */
    public static float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0f - t, 3);
    }

    /**
     * Ease-out expo: slow start makes the begin subtle, then a quick snap to final.
     * Ideal for dramatic expand/collapse — starts slow, finishes fast.
     *
     * @param t raw progress 0..1
     * @return eased progress 0..1
     */
    public static float easeOutExpo(float t) {
        return t >= 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0f, -10.0f * t);
    }

    /**
     * Ease-in-out cubic: smooth acceleration and deceleration.
     * Ideal for hover lift, subtle movements.
     *
     * @param t raw progress 0..1
     * @return eased progress 0..1
     */
    public static float easeInOutCubic(float t) {
        return t < 0.5f
                ? 4.0f * t * t * t
                : 1.0f - (float) Math.pow(-2.0f * t + 2.0f, 3) / 2.0f;
    }

    /**
     * Ease-out back: overshoots slightly then settles.
     * Ideal for dramatic reveals, modal pop-ups.
     *
     * @param t raw progress 0..1
     * @return eased progress with potential overshoot
     */
    public static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(t - 1.0f, 3)
                + c1 * (float) Math.pow(t - 1.0f, 2);
    }
}

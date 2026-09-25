package geminiclient.gemini.customRenderer.glsl.modules;

/**
 * Orientation maths for the Thaumaturgy Strike sigil planes.
 *
 * <p>Deliberately free of Minecraft and JOML types so it can be exercised in a
 * plain JVM test — the same reasoning that keeps {@code SlugGeometry} out of
 * the render path.</p>
 */
final class ThaumaturgySigilGeometry {

    private ThaumaturgySigilGeometry() {}

    /**
     * In-plane basis of a disc that lies in the world XZ plane, spun by
     * {@code yaw} about the vertical and then nodded by {@code pitch} about its
     * own horizontal axis. Because the pitch axis travels with the yaw, the
     * lean sweeps round like a gyroscope as the disc turns rather than the
     * disc sitting dead flat.
     *
     * <p>At {@code pitch = 0} this degenerates to the untouched horizontal
     * plane: e1 = (cos yaw, 0, −sin yaw), e2 = (sin yaw, 0, cos yaw).</p>
     *
     * @param out six floats to write: e1x, e1y, e1z, then e2x, e2y, e2z
     */
    static void axes(float yaw, float pitch, float[] out) {
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        out[0] = cy;
        out[1] = 0f;
        out[2] = -sy;
        out[3] = sy * cp;
        out[4] = sp;
        out[5] = cy * cp;
    }
}

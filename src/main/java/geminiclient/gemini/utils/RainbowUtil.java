package geminiclient.gemini.utils;

import java.awt.Color;

public class RainbowUtil {

    public static int getRainbow(long delay, float saturation, float brightness, int alpha) {
        float hue = (System.currentTimeMillis() % delay) / (float) delay;
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    public static int getRainbow(long delay, float hueOffset, float saturation, float brightness, int alpha) {
        float hue = ((System.currentTimeMillis() % delay) / (float) delay + hueOffset) % 1.0f;
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    public static int getRainbow(long delay, int alpha) {
        return getRainbow(delay, 1.0f, 1.0f, alpha);
    }
}

package geminiclient.gemini.utils;

import java.util.concurrent.ThreadLocalRandom;

public class MathHelper {
    public static float wrapDegrees(float value) {
        value %= 360.0F;
        if (value >= 180.0F) {
            value -= 360.0F;
        }

        if (value < -180.0F) {
            value += 360.0F;
        }

        return value;
    }

    public static float sqrt_double(double value)
    {
        return (float)Math.sqrt(value);
    }

    public static float wrapAngleTo180_float(float value)
    {
        value = value % 360.0F;

        if (value >= 180.0F)
        {
            value -= 360.0F;
        }

        if (value < -180.0F)
        {
            value += 360.0F;
        }

        return value;
    }

    public static double getRandom(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(Math.min(min,max), Math.max(min,max));
    }
}

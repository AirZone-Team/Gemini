package net.minecraft.client.model.geom.builders;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record UVPair(float u, float v) {
    @Override
    public String toString() {
        return "(" + this.u + "," + this.v + ")";
    }

    public static long pack(float u, float v) {
        long high = Float.floatToIntBits(u) & 4294967295L;
        long low = Float.floatToIntBits(v) & 4294967295L;
        return high << 32 | low;
    }

    public static float unpackU(long packedUV) {
        int bits = (int)(packedUV >> 32);
        return Float.intBitsToFloat(bits);
    }

    public static float unpackV(long packedUV) {
        return Float.intBitsToFloat((int)packedUV);
    }
}

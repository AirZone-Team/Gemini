package com.mojang.blaze3d;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum PrimitiveTopology {
    LINES(2, 2, false),
    DEBUG_LINES(2, 2, false),
    DEBUG_LINE_STRIP(2, 1, true),
    POINTS(1, 1, false),
    TRIANGLES(3, 3, false),
    TRIANGLE_STRIP(3, 1, true),
    TRIANGLE_FAN(3, 1, true),
    QUADS(4, 4, false);

    public final int primitiveLength;
    public final int primitiveStride;
    public final boolean connectedPrimitives;

    PrimitiveTopology(int primitiveLength, int primitiveStride, boolean connectedPrimitives) {
        this.primitiveLength = primitiveLength;
        this.primitiveStride = primitiveStride;
        this.connectedPrimitives = connectedPrimitives;
    }

    public int indexCount(int vertexCount) {
        return switch (this) {
            case LINES, QUADS -> vertexCount / 4 * 6;
            case DEBUG_LINES, DEBUG_LINE_STRIP, POINTS, TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN -> vertexCount;
            default -> 0;
        };
    }
}

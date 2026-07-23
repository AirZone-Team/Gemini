package com.mojang.blaze3d;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum IndexType {
    SHORT(2),
    INT(4);

    public final int bytes;

    IndexType(int bytes) {
        this.bytes = bytes;
    }

    public static IndexType least(int length) {
        return (length & -65536) != 0 ? INT : SHORT;
    }
}

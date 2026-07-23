package com.mojang.blaze3d;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GpuOutOfMemoryException extends RuntimeException {
    public GpuOutOfMemoryException(String message) {
        super(message);
    }
}

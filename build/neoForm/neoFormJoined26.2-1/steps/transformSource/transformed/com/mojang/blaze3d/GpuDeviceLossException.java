package com.mojang.blaze3d;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GpuDeviceLossException extends RuntimeException {
    public GpuDeviceLossException(String message) {
        super(message);
    }
}

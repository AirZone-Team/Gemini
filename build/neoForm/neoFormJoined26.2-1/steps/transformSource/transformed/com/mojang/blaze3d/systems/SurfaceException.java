package com.mojang.blaze3d.systems;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SurfaceException extends Exception {
    public SurfaceException(String message) {
        super(message);
    }

    public SurfaceException(Throwable cause) {
        super(cause);
    }
}

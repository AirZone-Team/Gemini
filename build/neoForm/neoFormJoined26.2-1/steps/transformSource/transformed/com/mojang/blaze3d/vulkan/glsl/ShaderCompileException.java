package com.mojang.blaze3d.vulkan.glsl;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ShaderCompileException extends Exception {
    public ShaderCompileException(String message) {
        super(message);
    }
}

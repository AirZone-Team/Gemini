package com.mojang.blaze3d.shaders;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
@net.neoforged.neoforge.internal.NonExhaustiveEnum(reason = "Further uniform types such as SSBO and StorageTexelBuffer may be added")
public enum UniformType {
    UNIFORM_BUFFER,
    TEXEL_BUFFER;
}

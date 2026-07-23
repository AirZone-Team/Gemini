package com.mojang.blaze3d.shaders;

import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
@OnlyIn(Dist.CLIENT)
public interface ShaderSource {
    @Nullable String get(Identifier id, ShaderType type);
}

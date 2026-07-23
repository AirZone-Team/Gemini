package com.mojang.blaze3d.vulkan.glsl;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
record SpvUniformBuffer(String name, int bindingOffset) {
}

package com.mojang.blaze3d.platform;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum PolygonMode {
    FILL,
    WIREFRAME;
}

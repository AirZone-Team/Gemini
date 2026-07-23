package com.mojang.blaze3d.systems;

import java.util.OptionalLong;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface GpuQuery extends AutoCloseable {
    OptionalLong getValue();

    @Override
    void close();
}

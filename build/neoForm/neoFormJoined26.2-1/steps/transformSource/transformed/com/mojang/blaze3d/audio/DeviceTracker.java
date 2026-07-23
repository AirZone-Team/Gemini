package com.mojang.blaze3d.audio;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface DeviceTracker {
    DeviceList currentDevices();

    void tick();

    void forceRefresh();
}

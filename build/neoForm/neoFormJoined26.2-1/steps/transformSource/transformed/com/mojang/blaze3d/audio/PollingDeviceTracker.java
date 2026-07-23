package com.mojang.blaze3d.audio;

import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PollingDeviceTracker extends AbstractDeviceTracker {
    private static final long DEFAULT_DEVICE_CHECK_INTERVAL_MS = 1000L;
    private long lastDeviceCheckTime;

    public PollingDeviceTracker(DeviceList deviceList) {
        super(deviceList);
    }

    @Override
    protected boolean isUpdateRequested() {
        return Util.getMillis() - this.lastDeviceCheckTime >= 1000L;
    }

    @Override
    protected void discardUpdateRequest() {
        this.lastDeviceCheckTime = Util.getMillis();
    }
}

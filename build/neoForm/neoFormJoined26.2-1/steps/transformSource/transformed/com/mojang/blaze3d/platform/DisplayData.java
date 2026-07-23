package com.mojang.blaze3d.platform;

import java.util.OptionalInt;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record DisplayData(int width, int height, OptionalInt fullscreenWidth, OptionalInt fullscreenHeight, boolean isFullscreen) {
    public DisplayData withSize(int width, int height) {
        return new DisplayData(width, height, this.fullscreenWidth, this.fullscreenHeight, this.isFullscreen);
    }

    public DisplayData withFullscreen(boolean isFullscreen) {
        return new DisplayData(this.width, this.height, this.fullscreenWidth, this.fullscreenHeight, isFullscreen);
    }
}

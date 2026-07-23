package net.minecraft.client.gui.components;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.BossEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LerpingBossEvent extends BossEvent {
    private static final long LERP_MILLISECONDS = 100L;
    protected float targetPercent;
    protected long setTime;

    public LerpingBossEvent(
        UUID id,
        Component name,
        float progress,
        BossEvent.BossBarColor color,
        BossEvent.BossBarOverlay overlay,
        boolean darkenScreen,
        boolean playMusic,
        boolean createWorldFog
    ) {
        super(id, name, color, overlay);
        this.targetPercent = progress;
        this.progress = progress;
        this.setTime = Util.getMillis();
        this.setDarkenScreen(darkenScreen);
        this.setPlayBossMusic(playMusic);
        this.setCreateWorldFog(createWorldFog);
    }

    @Override
    public void setProgress(float progress) {
        this.progress = this.getProgress();
        this.targetPercent = progress;
        this.setTime = Util.getMillis();
    }

    @Override
    public float getProgress() {
        long timeSinceSet = Util.getMillis() - this.setTime;
        float lerpPercent = Mth.clamp((float)timeSinceSet / 100.0F, 0.0F, 1.0F);
        return Mth.lerp(lerpPercent, this.progress, this.targetPercent);
    }
}

package net.minecraft.client.sounds;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface SoundEventListener {
    void onPlaySound(SoundInstance sound, WeighedSoundEvents soundEvent, float range);
}

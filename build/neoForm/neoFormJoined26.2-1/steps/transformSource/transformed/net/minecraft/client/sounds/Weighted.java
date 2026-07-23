package net.minecraft.client.sounds;

import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface Weighted<T> {
    int getWeight();

    T getSound(RandomSource random);

    void preloadIfRequired(SoundEngine soundEngine);
}

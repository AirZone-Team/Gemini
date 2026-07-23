package net.minecraft.client.resources.sounds;

import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class SoundEventRegistration {
    private final List<Sound> sounds;
    private final boolean replace;
    private final @Nullable String subtitle;

    public SoundEventRegistration(List<Sound> sounds, boolean replace, @Nullable String subtitle) {
        this.sounds = sounds;
        this.replace = replace;
        this.subtitle = subtitle;
    }

    public List<Sound> getSounds() {
        return this.sounds;
    }

    public boolean isReplace() {
        return this.replace;
    }

    public @Nullable String getSubtitle() {
        return this.subtitle;
    }
}

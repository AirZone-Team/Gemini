package net.minecraft.client.entity;

import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public interface ClientAvatarEntity {
    ClientAvatarState avatarState();

    PlayerSkin getSkin();

    Parrot.@Nullable Variant getParrotVariantOnShoulder(boolean left);

    boolean showExtraEars();
}

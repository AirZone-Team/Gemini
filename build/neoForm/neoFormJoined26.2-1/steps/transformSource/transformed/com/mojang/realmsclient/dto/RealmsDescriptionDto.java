package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public record RealmsDescriptionDto(@SerializedName("name") @Nullable String name, @SerializedName("description") String description)
    implements ReflectionBasedSerialization {
}

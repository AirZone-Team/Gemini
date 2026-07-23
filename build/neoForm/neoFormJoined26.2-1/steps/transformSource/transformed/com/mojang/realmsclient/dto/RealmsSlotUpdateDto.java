package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public record RealmsSlotUpdateDto(
    @SerializedName("slotId") int slotId,
    @SerializedName("spawnProtection") int spawnProtection,
    @SerializedName("forceGameMode") boolean forceGameMode,
    @SerializedName("difficulty") int difficulty,
    @SerializedName("gameMode") int gameMode,
    @SerializedName("slotName") String slotName,
    @SerializedName("version") String version,
    @SerializedName("compatibility") RealmsServer.Compatibility compatibility,
    @SerializedName("worldTemplateId") long templateId,
    @SerializedName("worldTemplateImage") @Nullable String templateImage,
    @SerializedName("hardcore") boolean hardcore
) implements ReflectionBasedSerialization {
    public RealmsSlotUpdateDto(int slotId, RealmsWorldOptions options, boolean hardcore) {
        this(
            slotId,
            options.spawnProtection,
            options.forceGameMode,
            options.difficulty,
            options.gameMode,
            options.getSlotName(slotId),
            options.version,
            options.compatibility,
            options.templateId,
            options.templateImage,
            hardcore
        );
    }
}

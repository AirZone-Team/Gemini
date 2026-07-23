package net.minecraft.client;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum CloudStatus implements StringRepresentable {
    OFF("false", "options.off"),
    FAST("fast", "options.clouds.fast"),
    FANCY("true", "options.clouds.fancy");

    public static final Codec<CloudStatus> CODEC = StringRepresentable.fromEnum(CloudStatus::values);
    private final String legacyName;
    private final Component caption;

    CloudStatus(String legacyName, String key) {
        this.legacyName = legacyName;
        this.caption = Component.translatable(key);
    }

    public Component caption() {
        return this.caption;
    }

    @Override
    public String getSerializedName() {
        return this.legacyName;
    }
}

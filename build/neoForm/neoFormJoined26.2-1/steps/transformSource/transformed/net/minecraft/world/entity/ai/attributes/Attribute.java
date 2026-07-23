package net.minecraft.world.entity.ai.attributes;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import net.neoforged.neoforge.common.extensions.IAttributeExtension;

public class Attribute implements IAttributeExtension {
    public static final Codec<Holder<Attribute>> CODEC = BuiltInRegistries.ATTRIBUTE.holderByNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Attribute>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.ATTRIBUTE);
    private final double defaultValue;
    private boolean syncable;
    private final String descriptionId;
    private Attribute.Sentiment sentiment = Attribute.Sentiment.POSITIVE;

    protected Attribute(String descriptionId, double defaultValue) {
        this.defaultValue = defaultValue;
        this.descriptionId = descriptionId;
    }

    public double getDefaultValue() {
        return this.defaultValue;
    }

    public boolean isClientSyncable() {
        return this.syncable;
    }

    public Attribute setSyncable(boolean syncable) {
        this.syncable = syncable;
        return this;
    }

    public Attribute setSentiment(Attribute.Sentiment sentiment) {
        this.sentiment = sentiment;
        return this;
    }

    public double sanitizeValue(double value) {
        return value;
    }

    public String getDescriptionId() {
        return this.descriptionId;
    }

    public ChatFormatting getStyle(boolean valueIncrease) {
        return this.sentiment.getStyle(valueIncrease);
    }

    // Neo: Patch in the default implementation of IAttributeExtension#getMergedStyle since we need access to Attribute#sentiment

    protected static final net.minecraft.network.chat.TextColor MERGED_RED = net.minecraft.network.chat.TextColor.fromRgb(0xF93131);
    protected static final net.minecraft.network.chat.TextColor MERGED_BLUE = net.minecraft.network.chat.TextColor.fromRgb(0x7A7AF9);
    protected static final net.minecraft.network.chat.TextColor MERGED_GRAY = net.minecraft.network.chat.TextColor.fromRgb(0xCCCCCC);

    @Override
    public net.minecraft.network.chat.TextColor getMergedStyle(boolean isPositive) {
        return switch (this.sentiment) {
            case POSITIVE -> isPositive ? MERGED_BLUE : MERGED_RED;
            case NEGATIVE -> isPositive ? MERGED_RED : MERGED_BLUE;
            case NEUTRAL -> MERGED_GRAY;
        };
    }

    public enum Sentiment {
        POSITIVE,
        NEUTRAL,
        NEGATIVE;

        public ChatFormatting getStyle(boolean valueIncrease) {
            return switch (this) {
                case POSITIVE -> valueIncrease ? ChatFormatting.BLUE : ChatFormatting.RED;
                case NEUTRAL -> ChatFormatting.GRAY;
                case NEGATIVE -> valueIncrease ? ChatFormatting.RED : ChatFormatting.BLUE;
            };
        }
    }
}

package net.minecraft.commands.synchronization.brigadier;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.network.FriendlyByteBuf;

public class LongArgumentInfo implements ArgumentTypeInfo<LongArgumentType, LongArgumentInfo.Template> {
    public void serializeToNetwork(LongArgumentInfo.Template template, FriendlyByteBuf out) {
        boolean hasMin = template.min != Long.MIN_VALUE;
        boolean hasMax = template.max != Long.MAX_VALUE;
        out.writeByte(ArgumentUtils.createNumberFlags(hasMin, hasMax));
        if (hasMin) {
            out.writeLong(template.min);
        }

        if (hasMax) {
            out.writeLong(template.max);
        }
    }

    public LongArgumentInfo.Template deserializeFromNetwork(FriendlyByteBuf in) {
        byte flags = in.readByte();
        long min = ArgumentUtils.numberHasMin(flags) ? in.readLong() : Long.MIN_VALUE;
        long max = ArgumentUtils.numberHasMax(flags) ? in.readLong() : Long.MAX_VALUE;
        return new LongArgumentInfo.Template(min, max);
    }

    public void serializeToJson(LongArgumentInfo.Template template, JsonObject out) {
        if (template.min != Long.MIN_VALUE) {
            out.addProperty("min", template.min);
        }

        if (template.max != Long.MAX_VALUE) {
            out.addProperty("max", template.max);
        }
    }

    public LongArgumentInfo.Template unpack(LongArgumentType argument) {
        return new LongArgumentInfo.Template(argument.getMinimum(), argument.getMaximum());
    }

    public final class Template implements ArgumentTypeInfo.Template<LongArgumentType> {
        private final long min;
        private final long max;

        private Template(long min, long max) {
            this.min = min;
            this.max = max;
        }

        public LongArgumentType instantiate(CommandBuildContext context) {
            return LongArgumentType.longArg(this.min, this.max);
        }

        @Override
        public ArgumentTypeInfo<LongArgumentType, ?> type() {
            return LongArgumentInfo.this;
        }
    }
}

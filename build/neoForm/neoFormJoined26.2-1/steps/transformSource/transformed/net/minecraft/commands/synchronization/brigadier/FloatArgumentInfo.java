package net.minecraft.commands.synchronization.brigadier;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.network.FriendlyByteBuf;

public class FloatArgumentInfo implements ArgumentTypeInfo<FloatArgumentType, FloatArgumentInfo.Template> {
    public void serializeToNetwork(FloatArgumentInfo.Template template, FriendlyByteBuf out) {
        boolean hasMin = template.min != -Float.MAX_VALUE;
        boolean hasMax = template.max != Float.MAX_VALUE;
        out.writeByte(ArgumentUtils.createNumberFlags(hasMin, hasMax));
        if (hasMin) {
            out.writeFloat(template.min);
        }

        if (hasMax) {
            out.writeFloat(template.max);
        }
    }

    public FloatArgumentInfo.Template deserializeFromNetwork(FriendlyByteBuf in) {
        byte flags = in.readByte();
        float min = ArgumentUtils.numberHasMin(flags) ? in.readFloat() : -Float.MAX_VALUE;
        float max = ArgumentUtils.numberHasMax(flags) ? in.readFloat() : Float.MAX_VALUE;
        return new FloatArgumentInfo.Template(min, max);
    }

    public void serializeToJson(FloatArgumentInfo.Template template, JsonObject out) {
        if (template.min != -Float.MAX_VALUE) {
            out.addProperty("min", template.min);
        }

        if (template.max != Float.MAX_VALUE) {
            out.addProperty("max", template.max);
        }
    }

    public FloatArgumentInfo.Template unpack(FloatArgumentType argument) {
        return new FloatArgumentInfo.Template(argument.getMinimum(), argument.getMaximum());
    }

    public final class Template implements ArgumentTypeInfo.Template<FloatArgumentType> {
        private final float min;
        private final float max;

        private Template(float min, float max) {
            this.min = min;
            this.max = max;
        }

        public FloatArgumentType instantiate(CommandBuildContext context) {
            return FloatArgumentType.floatArg(this.min, this.max);
        }

        @Override
        public ArgumentTypeInfo<FloatArgumentType, ?> type() {
            return FloatArgumentInfo.this;
        }
    }
}

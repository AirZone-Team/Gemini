package net.minecraft.commands.synchronization.brigadier;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.network.FriendlyByteBuf;

public class DoubleArgumentInfo implements ArgumentTypeInfo<DoubleArgumentType, DoubleArgumentInfo.Template> {
    public void serializeToNetwork(DoubleArgumentInfo.Template template, FriendlyByteBuf out) {
        boolean hasMin = template.min != -Double.MAX_VALUE;
        boolean hasMax = template.max != Double.MAX_VALUE;
        out.writeByte(ArgumentUtils.createNumberFlags(hasMin, hasMax));
        if (hasMin) {
            out.writeDouble(template.min);
        }

        if (hasMax) {
            out.writeDouble(template.max);
        }
    }

    public DoubleArgumentInfo.Template deserializeFromNetwork(FriendlyByteBuf in) {
        byte flags = in.readByte();
        double min = ArgumentUtils.numberHasMin(flags) ? in.readDouble() : -Double.MAX_VALUE;
        double max = ArgumentUtils.numberHasMax(flags) ? in.readDouble() : Double.MAX_VALUE;
        return new DoubleArgumentInfo.Template(min, max);
    }

    public void serializeToJson(DoubleArgumentInfo.Template template, JsonObject out) {
        if (template.min != -Double.MAX_VALUE) {
            out.addProperty("min", template.min);
        }

        if (template.max != Double.MAX_VALUE) {
            out.addProperty("max", template.max);
        }
    }

    public DoubleArgumentInfo.Template unpack(DoubleArgumentType argument) {
        return new DoubleArgumentInfo.Template(argument.getMinimum(), argument.getMaximum());
    }

    public final class Template implements ArgumentTypeInfo.Template<DoubleArgumentType> {
        private final double min;
        private final double max;

        private Template(double min, double max) {
            this.min = min;
            this.max = max;
        }

        public DoubleArgumentType instantiate(CommandBuildContext context) {
            return DoubleArgumentType.doubleArg(this.min, this.max);
        }

        @Override
        public ArgumentTypeInfo<DoubleArgumentType, ?> type() {
            return DoubleArgumentInfo.this;
        }
    }
}

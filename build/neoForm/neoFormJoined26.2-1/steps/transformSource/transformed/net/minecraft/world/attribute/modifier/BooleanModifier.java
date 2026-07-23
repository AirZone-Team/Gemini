package net.minecraft.world.attribute.modifier;

import com.mojang.serialization.Codec;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.LerpFunction;

public enum BooleanModifier implements AttributeModifier<Boolean, Boolean> {
    AND,
    NAND,
    OR,
    NOR,
    XOR,
    XNOR;

    public Boolean apply(Boolean subject, Boolean argument) {
        return switch (this) {
            case AND -> argument && subject;
            case NAND -> !argument || !subject;
            case OR -> argument || subject;
            case NOR -> !argument && !subject;
            case XOR -> argument ^ subject;
            case XNOR -> argument == subject;
        };
    }

    @Override
    public Codec<Boolean> argumentCodec(EnvironmentAttribute<Boolean> type) {
        return Codec.BOOL;
    }

    @Override
    public LerpFunction<Boolean> argumentKeyframeLerp(EnvironmentAttribute<Boolean> type) {
        return LerpFunction.ofConstant();
    }
}

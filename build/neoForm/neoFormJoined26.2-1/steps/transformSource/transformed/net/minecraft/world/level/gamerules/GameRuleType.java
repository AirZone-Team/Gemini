package net.minecraft.world.level.gamerules;

import net.minecraft.util.StringRepresentable;

import net.neoforged.fml.common.asm.enumextension.IExtensibleEnum;

@net.neoforged.fml.common.asm.enumextension.NamedEnum
public enum GameRuleType implements StringRepresentable, IExtensibleEnum {
    INT("integer"),
    BOOL("boolean");

    private final String name;

    GameRuleType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public static net.neoforged.fml.common.asm.enumextension.ExtensionInfo getExtensionInfo() {
        return net.neoforged.fml.common.asm.enumextension.ExtensionInfo.nonExtended(GameRuleType.class);
    }
}

package net.minecraft.client.gui.components.debug;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum DebugScreenEntryStatus implements StringRepresentable {
    ALWAYS_ON("alwaysOn"),
    IN_OVERLAY("inOverlay"),
    NEVER("never");

    public static final Codec<DebugScreenEntryStatus> CODEC = StringRepresentable.fromEnum(DebugScreenEntryStatus::values);
    private final String name;

    DebugScreenEntryStatus(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}

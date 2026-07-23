package net.minecraft.client.renderer.texture;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum MipmapStrategy implements StringRepresentable {
    AUTO("auto"),
    MEAN("mean"),
    CUTOUT("cutout"),
    STRICT_CUTOUT("strict_cutout"),
    DARK_CUTOUT("dark_cutout");

    public static final Codec<MipmapStrategy> CODEC = StringRepresentable.fromValues(MipmapStrategy::values);
    private final String name;

    MipmapStrategy(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}

package net.minecraft.client.renderer;

import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record SpriteMapper(Identifier sheet, String prefix) {
    public SpriteId apply(Identifier path) {
        return new SpriteId(this.sheet, path.withPrefix(this.prefix + "/"));
    }

    public SpriteId defaultNamespaceApply(String path) {
        return this.apply(Identifier.withDefaultNamespace(path));
    }
}

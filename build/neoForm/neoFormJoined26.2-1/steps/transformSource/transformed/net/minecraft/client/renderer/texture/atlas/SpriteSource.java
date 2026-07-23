package net.minecraft.client.renderer.texture.atlas;

import com.mojang.serialization.MapCodec;
import java.util.function.Predicate;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

import net.neoforged.neoforge.client.extensions.SpriteSourceExtension;

@OnlyIn(Dist.CLIENT)
public interface SpriteSource extends SpriteSourceExtension {
    FileToIdConverter TEXTURE_ID_CONVERTER = new FileToIdConverter("textures", ".png");

    void run(ResourceManager resourceManager, SpriteSource.Output output);

    MapCodec<? extends SpriteSource> codec();

    interface DiscardableLoader extends SpriteSource.Loader {
        default void discard() {
        }
    }

    @FunctionalInterface
    interface Loader {
        @Nullable SpriteContents get(SpriteResourceLoader loader);
    }

    interface Output {
        default void add(Identifier id, Resource resource) {
            this.add(id, loader -> loader.loadSprite(id, resource));
        }

        void add(Identifier id, SpriteSource.DiscardableLoader sprite);

        void removeAll(Predicate<Identifier> predicate);
    }
}

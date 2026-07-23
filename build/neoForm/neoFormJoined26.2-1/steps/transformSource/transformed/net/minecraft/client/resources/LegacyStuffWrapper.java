package net.minecraft.client.resources;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LegacyStuffWrapper {
    @Deprecated
    public static int[] getPixels(ResourceManager resourceManager, Identifier location) throws IOException {
        try (
            InputStream resource = resourceManager.open(location);
            NativeImage image = NativeImage.read(resource);
        ) {
            return image.makePixelArray();
        }
    }
}

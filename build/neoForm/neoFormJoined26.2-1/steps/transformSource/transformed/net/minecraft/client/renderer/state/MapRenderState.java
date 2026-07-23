package net.minecraft.client.renderer.state;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class MapRenderState extends net.neoforged.neoforge.client.renderstate.BaseRenderState {
    public @Nullable Identifier texture;
    public final List<MapRenderState.MapDecorationRenderState> decorations = new ArrayList<>();

    public static class MapDecorationRenderState extends net.neoforged.neoforge.client.renderstate.BaseRenderState {
        public net.minecraft.core.Holder<net.minecraft.world.level.saveddata.maps.MapDecorationType> type;
        public @Nullable TextureAtlasSprite atlasSprite;
        public byte x;
        public byte y;
        public byte rot;
        public boolean renderOnFrame;
        public @Nullable Component name;
    }
}

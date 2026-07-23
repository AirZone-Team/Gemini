package net.minecraft.client.gui.font;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4fc;

@OnlyIn(Dist.CLIENT)
public class PlayerGlyphProvider {
    private static final GlyphInfo GLYPH_INFO = GlyphInfo.simple(8.0F);
    private final PlayerSkinRenderCache playerSkinRenderCache;
    private final LoadingCache<FontDescription.PlayerSprite, GlyphSource> wrapperCache = CacheBuilder.newBuilder()
        .expireAfterAccess(PlayerSkinRenderCache.CACHE_DURATION)
        .build(new CacheLoader<FontDescription.PlayerSprite, GlyphSource>() {
            public GlyphSource load(FontDescription.PlayerSprite playerInfo) {
                final Supplier<PlayerSkinRenderCache.RenderInfo> skin = PlayerGlyphProvider.this.playerSkinRenderCache.createLookup(playerInfo.profile());
                final boolean hat = playerInfo.hat();
                return new SingleSpriteSource(new BakedGlyph() {
                    @Override
                    public GlyphInfo info() {
                        return PlayerGlyphProvider.GLYPH_INFO;
                    }

                    @Override
                    public TextRenderable.Styled createGlyph(float x, float y, int color, int shadowColor, Style style, float boldOffset, float shadowOffset) {
                        return new PlayerGlyphProvider.Instance(skin, hat, x, y, color, shadowColor, shadowOffset, style);
                    }
                });
            }
        });

    public PlayerGlyphProvider(PlayerSkinRenderCache playerSkinRenderCache) {
        this.playerSkinRenderCache = playerSkinRenderCache;
    }

    public GlyphSource sourceForPlayer(FontDescription.PlayerSprite playerInfo) {
        return this.wrapperCache.getUnchecked(playerInfo);
    }

    private record Instance(
        Supplier<PlayerSkinRenderCache.RenderInfo> skin, boolean hat, float x, float y, int color, int shadowColor, float shadowOffset, Style style
    ) implements PlainTextRenderable {
        @Override
        public void renderSprite(Matrix4fc pose, VertexConsumer buffer, int packedLightCoords, float offsetX, float offsetY, float z, int color) {
            float x0 = offsetX + this.left();
            float x1 = offsetX + this.right();
            float y0 = offsetY + this.top();
            float y1 = offsetY + this.bottom();
            renderQuad(pose, buffer, packedLightCoords, x0, x1, y0, y1, z, color, 8.0F, 8.0F, 8, 8, 64, 64);
            if (this.hat) {
                renderQuad(pose, buffer, packedLightCoords, x0, x1, y0, y1, z, color, 40.0F, 8.0F, 8, 8, 64, 64);
            }
        }

        private static void renderQuad(
            Matrix4fc pose,
            VertexConsumer buffer,
            int packedLightCoords,
            float x0,
            float x1,
            float y0,
            float y1,
            float z,
            int color,
            float u,
            float v,
            int srcWidth,
            int srcHeight,
            int textureWidth,
            int textureHeight
        ) {
            float u0 = (u + 0.0F) / textureWidth;
            float u1 = (u + srcWidth) / textureWidth;
            float v0 = (v + 0.0F) / textureHeight;
            float v1 = (v + srcHeight) / textureHeight;
            buffer.addVertex(pose, x0, y0, z).setUv(u0, v0).setColor(color).setLight(packedLightCoords);
            buffer.addVertex(pose, x0, y1, z).setUv(u0, v1).setColor(color).setLight(packedLightCoords);
            buffer.addVertex(pose, x1, y1, z).setUv(u1, v1).setColor(color).setLight(packedLightCoords);
            buffer.addVertex(pose, x1, y0, z).setUv(u1, v0).setColor(color).setLight(packedLightCoords);
        }

        @Override
        public RenderType renderType(Font.DisplayMode displayMode) {
            return this.skin.get().glyphRenderTypes().select(displayMode);
        }

        @Override
        public RenderPipeline guiPipeline() {
            return this.skin.get().glyphRenderTypes().guiPipeline();
        }

        @Override
        public GpuTextureView textureView() {
            return this.skin.get().textureView();
        }
    }
}

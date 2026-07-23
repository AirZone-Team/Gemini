package net.minecraft.client.gui.font.glyphs;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.UnbakedGlyph;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class EmptyGlyph implements UnbakedGlyph {
    private final GlyphInfo info;

    public EmptyGlyph(float advance) {
        this.info = GlyphInfo.simple(advance);
    }

    @Override
    public GlyphInfo info() {
        return this.info;
    }

    @Override
    public BakedGlyph bake(UnbakedGlyph.Stitcher stitcher) {
        return new BakedGlyph() {
            @Override
            public GlyphInfo info() {
                return EmptyGlyph.this.info;
            }

            @Override
            public TextRenderable.@Nullable Styled createGlyph(float x, float y, int color, int shadowColor, Style style, float boldOffset, float shadowOffset) {
                return null;
            }
        };
    }
}

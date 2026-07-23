package net.minecraft.client.gui;

import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface GlyphSource {
    BakedGlyph getGlyph(int codepoint);

    BakedGlyph getRandomGlyph(RandomSource random, int width);
}

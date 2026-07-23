package net.minecraft.client.gui;

import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum TextAlignment {
    LEFT {
        @Override
        public int calculateLeft(int anchor, int width) {
            return anchor;
        }

        @Override
        public int calculateLeft(int anchor, Font font, FormattedCharSequence text) {
            return anchor;
        }
    },
    CENTER {
        @Override
        public int calculateLeft(int anchor, int width) {
            return anchor - width / 2;
        }
    },
    RIGHT {
        @Override
        public int calculateLeft(int anchor, int width) {
            return anchor - width;
        }
    };

    public abstract int calculateLeft(int anchor, int width);

    public int calculateLeft(int anchor, Font font, FormattedCharSequence text) {
        return this.calculateLeft(anchor, font.width(text));
    }
}

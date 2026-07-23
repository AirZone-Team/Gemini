package net.minecraft.client.gui.screens.advancements;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum AdvancementTabType {
    ABOVE(
        new AdvancementTabType.Sprites(
            Identifier.withDefaultNamespace("advancements/tab_above_left_selected"),
            Identifier.withDefaultNamespace("advancements/tab_above_middle_selected"),
            Identifier.withDefaultNamespace("advancements/tab_above_right_selected")
        ),
        new AdvancementTabType.Sprites(
            Identifier.withDefaultNamespace("advancements/tab_above_left"),
            Identifier.withDefaultNamespace("advancements/tab_above_middle"),
            Identifier.withDefaultNamespace("advancements/tab_above_right")
        ),
        28,
        32,
        8
    ),
    BELOW(
        new AdvancementTabType.Sprites(
            Identifier.withDefaultNamespace("advancements/tab_below_left_selected"),
            Identifier.withDefaultNamespace("advancements/tab_below_middle_selected"),
            Identifier.withDefaultNamespace("advancements/tab_below_right_selected")
        ),
        new AdvancementTabType.Sprites(
            Identifier.withDefaultNamespace("advancements/tab_below_left"),
            Identifier.withDefaultNamespace("advancements/tab_below_middle"),
            Identifier.withDefaultNamespace("advancements/tab_below_right")
        ),
        28,
        32,
        8
    ),
    LEFT(
        new AdvancementTabType.Sprites(
            Identifier.withDefaultNamespace("advancements/tab_left_top_selected"),
            Identifier.withDefaultNamespace("advancements/tab_left_middle_selected"),
            Identifier.withDefaultNamespace("advancements/tab_left_bottom_selected")
        ),
        new AdvancementTabType.Sprites(
            Identifier.withDefaultNamespace("advancements/tab_left_top"),
            Identifier.withDefaultNamespace("advancements/tab_left_middle"),
            Identifier.withDefaultNamespace("advancements/tab_left_bottom")
        ),
        32,
        28,
        5
    ),
    RIGHT(
        new AdvancementTabType.Sprites(
            Identifier.withDefaultNamespace("advancements/tab_right_top_selected"),
            Identifier.withDefaultNamespace("advancements/tab_right_middle_selected"),
            Identifier.withDefaultNamespace("advancements/tab_right_bottom_selected")
        ),
        new AdvancementTabType.Sprites(
            Identifier.withDefaultNamespace("advancements/tab_right_top"),
            Identifier.withDefaultNamespace("advancements/tab_right_middle"),
            Identifier.withDefaultNamespace("advancements/tab_right_bottom")
        ),
        32,
        28,
        5
    );

    private final AdvancementTabType.Sprites selectedSprites;
    private final AdvancementTabType.Sprites unselectedSprites;
    public static final int MAX_TABS = java.util.Arrays.stream(values()).mapToInt(AdvancementTabType::getMax).sum();
    private final int width;
    private final int height;
    private final int max;

    AdvancementTabType(AdvancementTabType.Sprites selectedSprites, AdvancementTabType.Sprites unselectedSprites, int width, int height, int max) {
        this.selectedSprites = selectedSprites;
        this.unselectedSprites = unselectedSprites;
        this.width = width;
        this.height = height;
        this.max = max;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getMax() {
        return this.max;
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int tabX, int tabY, boolean selected, int index) {
        AdvancementTabType.Sprites sprites = selected ? this.selectedSprites : this.unselectedSprites;
        Identifier sprite;
        if (index == 0) {
            sprite = sprites.first();
        } else if (index == this.max - 1) {
            sprite = sprites.last();
        } else {
            sprite = sprites.middle();
        }

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, tabX, tabY, this.width, this.height);
    }

    public void extractIcon(GuiGraphicsExtractor graphics, int xo, int yo, int index, ItemStack icon) {
        int x = xo + this.getX(index);
        int y = yo + this.getY(index);
        switch (this) {
            case ABOVE:
                x += 6;
                y += 9;
                break;
            case BELOW:
                x += 6;
                y += 6;
                break;
            case LEFT:
                x += 10;
                y += 5;
                break;
            case RIGHT:
                x += 6;
                y += 5;
        }

        graphics.fakeItem(icon, x, y);
    }

    public int getX(int index) {
        return switch (this) {
            case ABOVE -> (this.width + 4) * index;
            case BELOW -> (this.width + 4) * index;
            case LEFT -> -this.width + 4;
            case RIGHT -> 248;
        };
    }

    public int getY(int index) {
        return switch (this) {
            case ABOVE -> -this.height + 4;
            case BELOW -> 136;
            case LEFT -> this.height * index;
            case RIGHT -> this.height * index;
        };
    }

    public boolean isMouseOver(int xo, int yo, int index, double mx, double my) {
        int x = xo + this.getX(index);
        int y = yo + this.getY(index);
        return mx > x && mx < x + this.width && my > y && my < y + this.height;
    }

    private record Sprites(Identifier first, Identifier middle, Identifier last) {
    }
}

package geminiclient.gemini.modules.impl.visual.clickgui.md3;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Expressive category header built from Material colour roles and shape
 * tokens. It provides context and summary information without behaving like
 * an interactive card.
 */
public class Md3HeroCard {

    public int x, y, width, height;

    public Md3HeroCard(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * @param category selected category, or {@code null} for search/favourites
     * @param resultCount number of modules in the current view
     * @param enabledCount enabled modules in the current view
     * @param favoritesView whether the favourites destination is selected
     */
    public void render(GuiGraphicsExtractor gui, ModuleEnum category, int resultCount,
                       int enabledCount, boolean favoritesView) {
        String titleText;
        int accent;

        if (category != null) {
            int[] gradient = Md3Theme.heroGradient(category);
            CustomRoundedRectRenderer.drawRoundedRectHorizGrad(gui, x, y, width, height,
                    Md3Theme.R_LARGE, gradient[0], gradient[1]);
            String name = category.name();
            titleText = name.charAt(0) + name.substring(1).toLowerCase();
            accent = Md3Theme.categoryAccent(category);
        } else if (favoritesView) {
            CustomRoundedRectRenderer.drawRoundedRectHorizGrad(gui, x, y, width, height,
                    Md3Theme.R_LARGE, Md3Theme.TERTIARY_CONTAINER, rgb(0xF4C8D6));
            titleText = "Favorites";
            accent = Md3Theme.TERTIARY;
        } else {
            CustomRoundedRectRenderer.drawRoundedRectHorizGrad(gui, x, y, width, height,
                    Md3Theme.R_LARGE, Md3Theme.PRIMARY_CONTAINER, rgb(0xD8C8F3));
            titleText = "Search results";
            accent = Md3Theme.PRIMARY;
        }

        var displayFont = Md3Fonts.display();
        Md3Fonts.drawText(gui, displayFont, titleText, x + 20, y + 12,
                Md3Theme.ON_SURFACE);

        int chipY = y + 48;
        int nextX = x + 20;
        nextX += drawSummaryChip(gui, nextX, chipY,
                resultCount + (resultCount == 1 ? " module" : " modules"),
                Md3Theme.SURFACE_CONTAINER_LOWEST, Md3Theme.ON_SURFACE_VARIANT) + 6;
        drawSummaryChip(gui, nextX, chipY, enabledCount + " active",
                Md3Theme.withAlpha(accent, 0.12f), accent);
    }

    private int drawSummaryChip(GuiGraphicsExtractor gui, int chipX, int chipY,
                                String text, int container, int content) {
        var font = Md3Fonts.label();
        int width = Math.round(Md3Fonts.width(font, text)) + 14;
        int height = 20;
        CustomRoundedRectRenderer.drawRoundedRect(gui, chipX, chipY, width, height,
                height / 2, container);
        Md3Fonts.drawText(gui, font, text, chipX + 7,
                chipY + (height - Md3Fonts.lineHeight(font)) / 2f, content);
        return width;
    }

    private static int rgb(int value) {
        return 0xFF000000 | value;
    }
}

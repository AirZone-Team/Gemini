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
        String eyebrow;
        String titleText;
        String subtitleText;
        int accent;

        if (category != null) {
            int[] gradient = Md3Theme.heroGradient(category);
            CustomRoundedRectRenderer.drawRoundedRectHorizGrad(gui, x, y, width, height,
                    Md3Theme.R_LARGE, gradient[0], gradient[1]);
            String name = category.name();
            eyebrow = "CATEGORY";
            titleText = name.charAt(0) + name.substring(1).toLowerCase();
            subtitleText = Md3Theme.categoryDescription(category);
            accent = Md3Theme.categoryAccent(category);
        } else if (favoritesView) {
            CustomRoundedRectRenderer.drawRoundedRectHorizGrad(gui, x, y, width, height,
                    Md3Theme.R_LARGE, Md3Theme.TERTIARY_CONTAINER, rgb(0xF4C8D6));
            eyebrow = "COLLECTION";
            titleText = "Favorites";
            subtitleText = resultCount == 0
                    ? "Save modules here for quick access"
                    : "Your pinned modules in one place";
            accent = Md3Theme.TERTIARY;
        } else {
            CustomRoundedRectRenderer.drawRoundedRectHorizGrad(gui, x, y, width, height,
                    Md3Theme.R_LARGE, Md3Theme.PRIMARY_CONTAINER, rgb(0xD8C8F3));
            eyebrow = "SEARCH";
            titleText = "Search results";
            subtitleText = resultCount == 0
                    ? "Try another module name"
                    : "Matching modules across every category";
            accent = Md3Theme.PRIMARY;
        }

        // A narrow accent keyline gives the large container a clear anchor.
        CustomRoundedRectRenderer.drawRoundedRect(gui, x + 12, y + 14, 4,
                height - 28, 2, Md3Theme.withAlpha(accent, 0.82f));

        var labelFont = Md3Fonts.label();
        var displayFont = Md3Fonts.display();
        Md3Fonts.drawText(gui, labelFont, eyebrow, x + 26, y + 14,
                Md3Theme.withAlpha(accent, 0.9f));
        Md3Fonts.drawText(gui, displayFont, titleText, x + 24, y + 27,
                Md3Theme.ON_SURFACE);
        Md3Fonts.drawText(gui, labelFont, subtitleText, x + 26, y + 58,
                Md3Theme.ON_SURFACE_VARIANT);

        int chipY = y + height - 28;
        int nextX = x + 24;
        nextX += drawSummaryChip(gui, nextX, chipY,
                resultCount + (resultCount == 1 ? " module" : " modules"),
                Md3Theme.SURFACE_CONTAINER_LOWEST, Md3Theme.ON_SURFACE_VARIANT) + 6;
        drawSummaryChip(gui, nextX, chipY, enabledCount + " active",
                Md3Theme.withAlpha(accent, 0.12f), accent);

        // Large low-emphasis destination icon on the trailing side.
        int iconCenterX = x + width - 48;
        int iconCenterY = y + height / 2;
        CustomRoundedRectRenderer.drawCircle(gui, iconCenterX, iconCenterY, 62,
                Md3Theme.withAlpha(accent, 0.10f));
        if (category != null) {
            Md3RenderUtils.drawCategoryIcon(gui, category, iconCenterX, iconCenterY,
                    34, Md3Theme.withAlpha(accent, 0.72f));
        } else {
            Md3RenderUtils.drawHeartPlusIcon(gui, iconCenterX, iconCenterY,
                    34, Md3Theme.withAlpha(accent, 0.72f));
        }
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

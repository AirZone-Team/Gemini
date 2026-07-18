package geminiclient.gemini.modules.impl.visual.clickgui.md3;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Hero card at the top of the content area: tonal gradient, large category
 * title, one-line description, and a faint oversized watermark icon.
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
     * @param category the selected category, or {@code null} when showing search results
     * @param resultCount number of matched modules (only used when category == null)
     */
    public void render(GuiGraphicsExtractor gui, ModuleEnum category, int resultCount) {
        int r = Md3Theme.R_CARD;

        String titleText;
        String subtitleText;
        if (category != null) {
            int[] gradient = Md3Theme.heroGradient(category);
            CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, x, y, width, height, r,
                    gradient[0], gradient[1]);
            String name = category.name();
            titleText = name.charAt(0) + name.substring(1).toLowerCase();
            subtitleText = Md3Theme.categoryDescription(category);
        } else {
            CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, x, y, width, height, r,
                    Md3Theme.PRIMARY_CONTAINER, Md3Theme.SURFACE_CONTAINER);
            titleText = "Search results";
            subtitleText = resultCount + " module" + (resultCount == 1 ? "" : "s") + " found";
        }

        // Title (display font, bottom-left of the card)
        var displayFont = Md3Fonts.display();
        float displayLh = Md3Fonts.lineHeight(displayFont);
        var labelFont = Md3Fonts.label();
        float labelLh = Md3Fonts.lineHeight(labelFont);

        float subtitleY = y + height - 10 - labelLh;
        float titleY = subtitleY - 2 - displayLh;

        Md3Fonts.drawText(gui, displayFont, titleText, x + 16, titleY, Md3Theme.ON_SURFACE);
        if (!subtitleText.isEmpty()) {
            Md3Fonts.drawText(gui, labelFont, subtitleText, x + 16, subtitleY,
                    Md3Theme.ON_SURFACE_VARIANT);
        }

        // Watermark icon (bottom-right, faint)
        if (category != null) {
            int wmSize = 56;
            int wmColor = Md3Theme.withAlpha(Md3Theme.ON_SURFACE, 0.08f);
            Md3RenderUtils.drawCategoryIcon(gui, category,
                    x + width - wmSize / 2 - 18, y + height - wmSize / 2 - 12, wmSize, wmColor);
        }
    }
}

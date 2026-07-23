package net.minecraft.client.gui.screens.inventory.tooltip;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector2i;
import org.joml.Vector2ic;

@OnlyIn(Dist.CLIENT)
public class DefaultTooltipPositioner implements ClientTooltipPositioner {
    public static final ClientTooltipPositioner INSTANCE = new DefaultTooltipPositioner();

    private DefaultTooltipPositioner() {
    }

    @Override
    public Vector2ic positionTooltip(int screenWidth, int screenHeight, int x, int y, int tooltipWidth, int tooltipHeight) {
        Vector2i result = new Vector2i(x, y).add(12, -12);
        this.positionTooltip(screenWidth, screenHeight, result, tooltipWidth, tooltipHeight);
        return result;
    }

    private void positionTooltip(int screenWidth, int screenHeight, Vector2i result, int tooltipWidth, int tooltipHeight) {
        if (result.x + tooltipWidth > screenWidth) {
            result.x = Math.max(result.x - 24 - tooltipWidth, 4);
        }

        int paddedHeight = tooltipHeight + 3;
        if (result.y + paddedHeight > screenHeight) {
            result.y = screenHeight - paddedHeight;
        }
    }
}

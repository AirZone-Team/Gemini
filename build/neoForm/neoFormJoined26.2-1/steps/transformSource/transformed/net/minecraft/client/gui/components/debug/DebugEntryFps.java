package net.minecraft.client.gui.components.debug;

import com.mojang.blaze3d.systems.GpuSurface;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class DebugEntryFps implements DebugScreenEntry {
    @Override
    public void display(DebugScreenDisplayer displayer, @Nullable Level serverOrClientLevel, @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
        Minecraft minecraft = Minecraft.getInstance();
        int framerateLimit = minecraft.getFramerateLimitTracker().getFramerateLimit();
        Optional<GpuSurface.Configuration> surfaceConfiguration = minecraft.windowSurface().currentConfiguration();
        displayer.addPriorityLine(
            String.format(
                Locale.ROOT,
                "%d fps T: %s%s",
                minecraft.getFps(),
                framerateLimit == 260 ? "inf" : framerateLimit,
                presentModeName(surfaceConfiguration.map(GpuSurface.Configuration::presentMode).orElse(null))
            )
        );
    }

    @Override
    public boolean isAllowed(boolean reducedDebugInfo) {
        return true;
    }

    private static String presentModeName(GpuSurface.@Nullable PresentMode mode) {
        return switch (mode) {
            case null -> "";
            case IMMEDIATE -> " (immediate)";
            case MAILBOX -> " (mailbox)";
            case FIFO -> " (fifo)";
            case FIFO_RELAXED -> " (fifo relaxed)";
        };
    }
}

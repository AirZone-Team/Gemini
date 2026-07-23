package net.minecraft.client.gui.components.debug;

import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.timeline.Timelines;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class DebugEntryDayCount implements DebugScreenEntry {
    @Override
    public void display(DebugScreenDisplayer displayer, @Nullable Level serverOrClientLevel, @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
        if (serverOrClientLevel != null) {
            ClockManager clockManager = serverOrClientLevel.clockManager();
            serverOrClientLevel.registryAccess()
                .get(Timelines.OVERWORLD_DAY)
                .ifPresent(timeline -> displayer.addLine("Day #" + timeline.value().getPeriodCount(clockManager)));
        }
    }
}

package net.minecraft.client.gui.components.debug;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class DebugEntryDetailedMemory implements DebugScreenEntry {
    public static final Identifier GROUP = Identifier.withDefaultNamespace("memory");
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    @Override
    public void display(DebugScreenDisplayer displayer, @Nullable Level serverOrClientLevel, @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
        displayer.addToGroup(
            GROUP,
            List.of(printMemoryUsage(this.memoryBean.getHeapMemoryUsage(), "heap"), printMemoryUsage(this.memoryBean.getNonHeapMemoryUsage(), "non-heap"))
        );
    }

    private static long bytesToMebibytes(long used) {
        return used / 1024L / 1024L;
    }

    private static String printMemoryUsage(MemoryUsage memoryUsage, String type) {
        return String.format(
            Locale.ROOT,
            "Memory (%s): i=%03dMiB u=%03dMiB c=%03dMiB m=%03dMiB",
            type,
            bytesToMebibytes(memoryUsage.getInit()),
            bytesToMebibytes(memoryUsage.getUsed()),
            bytesToMebibytes(memoryUsage.getCommitted()),
            bytesToMebibytes(memoryUsage.getMax())
        );
    }

    @Override
    public boolean isAllowed(boolean reducedDebugInfo) {
        return true;
    }
}

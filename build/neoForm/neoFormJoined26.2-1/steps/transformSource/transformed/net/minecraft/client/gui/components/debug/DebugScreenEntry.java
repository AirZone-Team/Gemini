package net.minecraft.client.gui.components.debug;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public interface DebugScreenEntry {
    void display(DebugScreenDisplayer displayer, @Nullable Level serverOrClientLevel, @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk);

    default boolean isAllowed(boolean reducedDebugInfo) {
        return !reducedDebugInfo;
    }

    default DebugEntryCategory category() {
        return DebugEntryCategory.SCREEN_TEXT;
    }
}

package net.minecraft.server.level.progress;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public interface LevelLoadListener {
    static LevelLoadListener compose(LevelLoadListener first, LevelLoadListener second) {
        return new LevelLoadListener() {
            @Override
            public void start(LevelLoadListener.Stage stage, int totalChunks) {
                first.start(stage, totalChunks);
                second.start(stage, totalChunks);
            }

            @Override
            public void update(LevelLoadListener.Stage stage, int currentChunks, int totalChunks) {
                first.update(stage, currentChunks, totalChunks);
                second.update(stage, currentChunks, totalChunks);
            }

            @Override
            public void finish(LevelLoadListener.Stage stage) {
                first.finish(stage);
                second.finish(stage);
            }

            @Override
            public void updateFocus(ResourceKey<Level> dimension, ChunkPos chunkPos) {
                first.updateFocus(dimension, chunkPos);
                second.updateFocus(dimension, chunkPos);
            }
        };
    }

    void start(LevelLoadListener.Stage stage, int totalChunks);

    void update(LevelLoadListener.Stage stage, int currentChunks, int totalChunks);

    void finish(LevelLoadListener.Stage stage);

    void updateFocus(ResourceKey<Level> dimension, ChunkPos chunkPos);

    enum Stage {
        START_SERVER,
        PREPARE_GLOBAL_SPAWN,
        LOAD_INITIAL_CHUNKS,
        LOAD_PLAYER_CHUNKS;
    }
}

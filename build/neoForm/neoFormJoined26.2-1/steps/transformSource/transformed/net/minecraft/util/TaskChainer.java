package net.minecraft.util;

import com.mojang.logging.LogUtils;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.slf4j.Logger;

@FunctionalInterface
public interface TaskChainer {
    Logger LOGGER = LogUtils.getLogger();

    static TaskChainer immediate(Executor executor) {
        return new TaskChainer() {
            @Override
            public <T> void append(CompletableFuture<T> preparation, Consumer<T> chainedTask) {
                preparation.thenAcceptAsync(chainedTask, executor).exceptionally(e -> {
                    LOGGER.error("Task failed", e);
                    return null;
                });
            }
        };
    }

    default void append(Runnable task) {
        this.append(CompletableFuture.completedFuture(null), ignored -> task.run());
    }

    <T> void append(CompletableFuture<T> preparation, Consumer<T> chainedTask);
}

package net.minecraft.util.thread;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public interface TaskScheduler<R extends Runnable> extends AutoCloseable {
    String name();

    void schedule(final R r);

    @Override
    default void close() {
    }

    R wrapRunnable(final Runnable runnable);

    default <Source> CompletableFuture<Source> scheduleWithResult(Consumer<CompletableFuture<Source>> futureConsumer) {
        CompletableFuture<Source> future = new CompletableFuture<>();
        this.schedule(this.wrapRunnable(() -> futureConsumer.accept(future)));
        return future;
    }

    static TaskScheduler<Runnable> wrapExecutor(String name, Executor executor) {
        return new TaskScheduler<Runnable>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void schedule(Runnable runnable) {
                executor.execute(runnable);
            }

            @Override
            public Runnable wrapRunnable(Runnable runnable) {
                return runnable;
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }
}

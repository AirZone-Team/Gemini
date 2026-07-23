package net.minecraft.realms;

import com.google.common.util.concurrent.RateLimiter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.GameNarrator;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class RepeatedNarrator {
    private final float permitsPerSecond;
    private final AtomicReference<RepeatedNarrator.@Nullable Params> params = new AtomicReference<>();

    public RepeatedNarrator(Duration repeatDelay) {
        this.permitsPerSecond = 1000.0F / (float)repeatDelay.toMillis();
    }

    public void narrate(GameNarrator narrator, Component narration) {
        RepeatedNarrator.Params params = this.params
            .updateAndGet(
                existing -> existing != null && narration.equals(existing.narration)
                    ? existing
                    : new RepeatedNarrator.Params(narration, RateLimiter.create(this.permitsPerSecond))
            );
        if (params.rateLimiter.tryAcquire(1)) {
            narrator.saySystemNow(narration);
        }
    }

    private record Params(Component narration, RateLimiter rateLimiter) {
    }
}

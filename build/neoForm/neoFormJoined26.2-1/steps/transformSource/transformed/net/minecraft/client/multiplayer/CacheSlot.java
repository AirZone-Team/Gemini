package net.minecraft.client.multiplayer;

import java.util.function.Function;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class CacheSlot<C extends CacheSlot.Cleaner<C>, D> {
    private final Function<C, D> operation;
    private @Nullable C context;
    private @Nullable D value;

    public CacheSlot(Function<C, D> operation) {
        this.operation = operation;
    }

    public D compute(C context) {
        if (context == this.context && this.value != null) {
            return this.value;
        }

        D newValue = this.operation.apply(context);
        this.value = newValue;
        this.context = context;
        context.registerForCleaning(this);
        return newValue;
    }

    public void clear() {
        this.value = null;
        this.context = null;
    }

    @FunctionalInterface
    public interface Cleaner<C extends CacheSlot.Cleaner<C>> {
        void registerForCleaning(CacheSlot<C, ?> slot);
    }
}

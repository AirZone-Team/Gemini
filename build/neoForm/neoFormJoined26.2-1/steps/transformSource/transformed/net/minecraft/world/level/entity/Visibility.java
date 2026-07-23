package net.minecraft.world.level.entity;

import net.minecraft.server.level.FullChunkStatus;

public enum Visibility {
    HIDDEN(false, false),
    TRACKED(true, false),
    TICKING(true, true);

    private final boolean accessible;
    private final boolean ticking;

    Visibility(boolean accessible, boolean ticking) {
        this.accessible = accessible;
        this.ticking = ticking;
    }

    public boolean isTicking() {
        return this.ticking;
    }

    public boolean isAccessible() {
        return this.accessible;
    }

    public static Visibility fromFullChunkStatus(FullChunkStatus status) {
        if (status.isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
            return TICKING;
        } else {
            return status.isOrAfter(FullChunkStatus.FULL) ? TRACKED : HIDDEN;
        }
    }
}

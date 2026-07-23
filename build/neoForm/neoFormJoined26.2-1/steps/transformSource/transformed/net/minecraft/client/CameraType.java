package net.minecraft.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum CameraType {
    FIRST_PERSON(true, false),
    THIRD_PERSON_BACK(false, false),
    THIRD_PERSON_FRONT(false, true);

    private static final CameraType[] VALUES = values();
    private final boolean firstPerson;
    private final boolean mirrored;

    CameraType(boolean firstPerson, boolean mirrored) {
        this.firstPerson = firstPerson;
        this.mirrored = mirrored;
    }

    public boolean isFirstPerson() {
        return this.firstPerson;
    }

    public boolean isMirrored() {
        return this.mirrored;
    }

    public CameraType cycle() {
        return VALUES[(this.ordinal() + 1) % VALUES.length];
    }
}

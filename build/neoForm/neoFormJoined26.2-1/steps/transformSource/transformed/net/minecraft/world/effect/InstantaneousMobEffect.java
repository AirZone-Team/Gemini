package net.minecraft.world.effect;

public class InstantaneousMobEffect extends MobEffect {
    public InstantaneousMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean isInstantaneous() {
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingDuration, int amplification) {
        return remainingDuration >= 1;
    }
}

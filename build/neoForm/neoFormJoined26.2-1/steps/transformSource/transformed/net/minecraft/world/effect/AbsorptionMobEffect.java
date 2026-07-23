package net.minecraft.world.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

class AbsorptionMobEffect extends MobEffect {
    protected AbsorptionMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        return mob.getAbsorptionAmount() > 0.0F;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return true;
    }

    @Override
    public void onEffectStarted(LivingEntity mob, int amplifier) {
        super.onEffectStarted(mob, amplifier);
        mob.setAbsorptionAmount(Math.max(mob.getAbsorptionAmount(), 4 * (1 + amplifier)));
    }
}

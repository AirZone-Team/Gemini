package net.minecraft.world.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public class PoisonMobEffect extends MobEffect {
    public static final int DAMAGE_INTERVAL = 25;

    protected PoisonMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        if (mob.getHealth() > 1.0F) {
            // Neo: Replace DamageSources#magic() with neoforge:poison to allow differentiating poison damage.
            // Fallback to minecraft:magic in client code when connecting to a vanilla server.
            // LivingEntity#hurt(DamageSource) will no-op in client code immediately, but the holder is resolved before the no-op.
            var dTypeReg = mob.damageSources().damageTypes;
            var dType = dTypeReg.get(net.neoforged.neoforge.common.NeoForgeMod.POISON_DAMAGE).orElse(dTypeReg.getOrThrow(net.minecraft.world.damagesource.DamageTypes.MAGIC));
            mob.hurtServer(level, new net.minecraft.world.damagesource.DamageSource(dType), 1.0F);
        }

        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        int interval = 25 >> amplification;
        return interval > 0 ? tickCount % interval == 0 : true;
    }
}

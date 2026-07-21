package geminiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.AttackSlowDownEvent;
import geminiclient.gemini.event.events.impl.moveFixEvent.AttackYawEvent;
import geminiclient.gemini.modules.impl.visual.SweepingAttackVFX;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class MixinPlayer {
    @ModifyExpressionValue(method = {"causeExtraKnockback", "doSweepAttack*"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
    private float modifyAttackYaw(float original) {
        AttackYawEvent event = new AttackYawEvent(original);
        Gemini.eventManager.call(event);
        return event.getYaw();
    }

    @Inject(method = "doSweepAttack(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;FLnet/minecraft/world/phys/AABB;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"), cancellable = true)
    private void cleanPar(Entity entity, float baseDamage, DamageSource damageSource, float attackStrengthScale, AABB sweepHitBox, CallbackInfo ci) {
        SweepingAttackVFX module = Gemini.moduleManager.getModule(SweepingAttackVFX.class);
        if (module.enabled) {
            // Spawn custom sweep VFX toward the target entity
            module.spawnSweepEffect((Player)(Object)this, entity.getX(), entity.getZ());
            ci.cancel();
        }
    }

    @Inject(method = "causeExtraKnockback", at = @At("HEAD"), cancellable = true)
    private void onCauseExtraKnockback(Entity entity, float knockbackAmount, Vec3 oldMovement, DamageSource damageSource, float damage, boolean comesFromEffect, CallbackInfo ci) {
        AttackSlowDownEvent event = new AttackSlowDownEvent(entity, knockbackAmount);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}

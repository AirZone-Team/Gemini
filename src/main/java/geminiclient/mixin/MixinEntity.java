package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.EventTypes;
import geminiclient.gemini.event.events.impl.BlockingEvent;
import geminiclient.gemini.event.events.impl.EntityRemoveEvent;
import geminiclient.gemini.event.events.impl.moveFixEvent.RayTraceEvent;
import geminiclient.gemini.event.events.impl.StrafeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @Shadow
    protected Vec3 stuckSpeedMultiplier;
    @Shadow
    public abstract float getViewXRot(float a);

    @Shadow
    public abstract float getViewYRot(float a);

    @Shadow
    public abstract Vec3 calculateViewVector(float xRot, float yRot);
    @ModifyArg(method = "moveRelative",at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getInputVector(Lnet/minecraft/world/phys/Vec3;FF)Lnet/minecraft/world/phys/Vec3;",ordinal = 0),index = 2)
    public float strafe(float motionScaler) {
        StrafeEvent strafeEvent = new StrafeEvent(motionScaler);
        Gemini.eventManager.post(EventTypes.STRAFE, strafeEvent);
        return strafeEvent.getYaw();
    }

    @Inject(method = "makeStuckInBlock", at = @At("RETURN"))
    private void Blocking(BlockState blockState, Vec3 speedMultiplier, CallbackInfo ci) {
        Entity thisEntity = (Entity)(Object)this;
        if (Minecraft.getInstance().player == thisEntity) {
            BlockingEvent event = new BlockingEvent(blockState, speedMultiplier);
            Gemini.eventManager.post(EventTypes.BLOCKING, event);
            if (event.isCancelled()) {
                this.stuckSpeedMultiplier = Vec3.ZERO;
                return;
            }
            this.stuckSpeedMultiplier = event.getVec3();
        }
    }

    /**
     * @author XeContrast
     * @reason MoveFix
     */
    @Overwrite
    public final Vec3 getViewVector(float p_20253_) {
        float pitch = this.getViewXRot(p_20253_);
        float yaw = this.getViewYRot(p_20253_);
        Entity thisEntity = (Entity)(Object)this;
        if (thisEntity == Minecraft.getInstance().player) {
            RayTraceEvent lookEvent = new RayTraceEvent(thisEntity, yaw, pitch);
            Gemini.eventManager.post(EventTypes.RAY_TRACE, lookEvent);
            yaw = lookEvent.yaw;
            pitch = lookEvent.pitch;
        }

        return this.calculateViewVector(pitch, yaw);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity thisEntity = (Entity)(Object)this;
        Gemini.eventManager.post(EventTypes.ENTITY_REMOVE,
                new EntityRemoveEvent(thisEntity, reason == Entity.RemovalReason.KILLED));
    }
}

package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.moveFixEvent.RayTraceEvent;
import geminiclient.gemini.event.events.impl.moveFixEvent.StrafeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @Shadow
    public abstract float getViewXRot(float var1);

    @Shadow
    public abstract float getViewYRot(float var1);

    @Shadow
    public abstract Vec3 calculateViewVector(float var1, float var2);
    @ModifyArg(method = "moveRelative",at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getInputVector(Lnet/minecraft/world/phys/Vec3;FF)Lnet/minecraft/world/phys/Vec3;",ordinal = 0),index = 2)
    public float strafe(float motionScaler) {
        StrafeEvent strafeEvent = new StrafeEvent(motionScaler);
        Gemini.eventManager.call(strafeEvent);
        return strafeEvent.getYaw();
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
            Gemini.eventManager.call(lookEvent);
            yaw = lookEvent.yaw;
            pitch = lookEvent.pitch;
        }

        return this.calculateViewVector(pitch, yaw);
    }
}

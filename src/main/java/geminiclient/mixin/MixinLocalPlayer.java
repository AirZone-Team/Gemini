package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.SlowDownEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.impl.movement.NoSlow;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer extends AbstractClientPlayer {
    public MixinLocalPlayer(ClientLevel clientLevel, ClientPacketListener connection) {
        super(clientLevel, connection.getLocalGameProfile());
    }

    @Unique
    Vec2 gemini$move;

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V",shift = At.Shift.BEFORE,ordinal = 0))
    private void registerUpdateEvent(CallbackInfo ci) {
        Gemini.eventManager.call(new UpdateEvent());
    }

    @Inject(method = "sendPosition",at = @At("HEAD"), cancellable = true)
    public void motion(CallbackInfo ci) {
        MotionEvent motionEvent = new MotionEvent(this.position(),this.getYRot(), this.getXRot(), this.onGround(), this.horizontalCollision, TimeEnum.Pre);
        Gemini.eventManager.call(motionEvent);
        if (motionEvent.isCancelled())
            ci.cancel();
    }

    @Inject(method = "sendPosition",at = @At("TAIL"))
    public void motionP(CallbackInfo ci) {
        MotionEvent motionEvent = new MotionEvent(this.position(),this.getYRot(), this.getXRot(), this.onGround(), this.horizontalCollision, TimeEnum.Post);
        Gemini.eventManager.call(motionEvent);
    }

    @Inject(method = "modifyInput",at = @At("HEAD"))
    public void setMove(Vec2 moveVector, CallbackInfoReturnable<Vec2> cir) {
        this.gemini$move = moveVector;
    }

    @ModifyVariable(method = "modifyInput",at = @At("STORE"),ordinal = 1)
    private Vec2 inject(Vec2 value) {
        SlowDownEvent slowDownEvent = new SlowDownEvent(0.2f);
        Gemini.eventManager.call(slowDownEvent);
        if (this.isUsingItem() && !this.isPassenger()) {
            if (Gemini.moduleManager.getModule(NoSlow.class).enabled) {
                if (slowDownEvent.isCancelled()) {
                    return this.gemini$move.scale(0.98f);
                } else return this.gemini$move.scale(slowDownEvent.getFactor());
            } else {
                return this.gemini$move.scale(0.2f);
            }
        }
        return this.gemini$move.scale(0.98f);
    }
}
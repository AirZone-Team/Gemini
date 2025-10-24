package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.StrafeEvent;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class MixinKeyboardInput extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    public void callStrafe(CallbackInfo ci) {
        StrafeEvent event = new StrafeEvent(this.moveVector.y, this.moveVector.x);
        Gemini.eventManager.call(event);
        this.moveVector = new Vec2(event.getStrafe(), event.getForward());
    }
}

package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.moveFixEvent.StrafeEvent;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class MixinKeyboardInput extends ClientInput {
    @Inject(method = "tick",at = @At("TAIL"))
    public void callStrafe(CallbackInfo ci) {
//        this.moveVector = new Vec2(this.keyPresses.left() == this.keyPresses.right() ? 0.0F : (this.keyPresses.left() ? 1.0F : -1.0F),this.keyPresses.forward() == this.keyPresses.backward() ? 0.0F : (this.keyPresses.forward() ? 1.0F : -1.0F));
        StrafeEvent event = new StrafeEvent(this.moveVector.y,this.moveVector.x);
        Gemini.eventManager.call(event);
        this.moveVector = new Vec2(event.getStrafe(),event.getForward());
    }
}

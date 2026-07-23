package geminiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.EventTypes;
import geminiclient.gemini.event.events.impl.MoveInputEvent;
import geminiclient.gemini.event.events.impl.StrafeEvent;
import geminiclient.gemini.event.impl.Event;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class MixinKeyboardInput extends ClientInput {
    @ModifyExpressionValue(method = "tick", at = @At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/world/entity/player/Input;"))
    private Input redirectKeyPresses(Input original) {
        MoveInputEvent event = new MoveInputEvent(
                original.forward(),
                original.backward(),
                original.left(),
                original.right(),
                original.jump(),
                original.shift(),
                original.sprint())
        ;
        Gemini.eventManager.post(EventTypes.MOVE_INPUT, event);
        return event.toNewInput();
    }
}

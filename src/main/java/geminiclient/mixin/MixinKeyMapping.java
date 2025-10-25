package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.modules.impl.movement.InvMove;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.extensions.IKeyMappingExtension;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(KeyMapping.class)
public abstract class MixinKeyMapping implements IKeyMappingExtension {
    @Override
    public boolean isConflictContextAndModifierActive() {
        if (Gemini.moduleManager.getModule(InvMove.class).enabled) {
            return true;
        } else {
            return IKeyMappingExtension.super.isConflictContextAndModifierActive();
        }
    }
}

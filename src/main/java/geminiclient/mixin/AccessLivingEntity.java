package geminiclient.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface AccessLivingEntity {
    @Accessor("noJumpDelay")
    void setNoJumpDelay(int delay);
}

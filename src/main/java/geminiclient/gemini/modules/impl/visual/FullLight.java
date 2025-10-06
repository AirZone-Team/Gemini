package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class FullLight extends Module {
    public FullLight() {
        super("FullLight",ModuleEnum.Visual);
    }

    @Override
    public void onEnabled() {
        if (player != null) {
            MobEffectInstance instance = new MobEffectInstance(MobEffects.NIGHT_VISION,999,1,false,true,true);
            player.removeEffect(MobEffects.NIGHT_VISION);
            player.addEffect(instance);
        }
    }
}

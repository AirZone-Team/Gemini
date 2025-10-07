package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class FullLight extends Module {
    public FullLight() {
        super("FullLight", ModuleEnum.Visual);
    }

    @Override
    public void onEnabled() {
        if (mc.player != null && !mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
            MobEffectInstance instance = new MobEffectInstance(
                    MobEffects.NIGHT_VISION,
                    Integer.MAX_VALUE,
                    0, // LEVEL 1
                    false, // ambient
                    false, // showParticles
                    true // showIcon
            );
            mc.player.addEffect(instance);
        }
    }

    @Override
    public void onDisabled() {
        if (mc.player != null && mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }
}

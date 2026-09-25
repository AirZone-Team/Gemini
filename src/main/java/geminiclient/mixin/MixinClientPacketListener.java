package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.modules.impl.visual.SweepingAttackVFX;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {

    /**
     * Replace the vanilla sweep-attack particle with the custom SweepingAttackVFX.
     *
     * <p>Vanilla {@code Player#doSweepAttack} only emits the particle (and its extra
     * damage) inside a {@code level() instanceof ServerLevel} branch, so a client-side
     * mixin on that method can never fire in multiplayer. The particle packet is the
     * one signal every sweep produces on the client regardless of world type, so we
     * spawn the effect here and cancel the original particle.</p>
     *
     * <p>Injected after {@code PacketUtils.ensureRunningOnSameThread} so the effect
     * is always spawned from the render thread, never the network thread.</p>
     */
    @Inject(method = "handleParticleEvent", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void onSweepParticle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (packet.getParticle().getType() != ParticleTypes.SWEEP_ATTACK) {
            return;
        }
        SweepingAttackVFX module = Gemini.moduleManager.getModule(SweepingAttackVFX.class);
        if (module == null || !module.enabled) {
            return;
        }
        module.spawnSweepEffectFromParticle(
                packet.getX(), packet.getY(), packet.getZ(),
                packet.getXDist(), packet.getZDist());
        ci.cancel();
    }
}

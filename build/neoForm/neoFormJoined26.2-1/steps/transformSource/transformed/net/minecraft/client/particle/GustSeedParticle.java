package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GustSeedParticle extends NoRenderParticle {
    private final double scale;
    private final int tickDelayInBetween;

    public GustSeedParticle(ClientLevel level, double x, double y, double z, double scale, int lifetime, int tickDelayInBetween) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.scale = scale;
        this.lifetime = lifetime;
        this.tickDelayInBetween = tickDelayInBetween;
    }

    @Override
    public void tick() {
        if (this.age % (this.tickDelayInBetween + 1) == 0) {
            for (int i = 0; i < 3; i++) {
                double x = this.x + (this.random.nextDouble() - this.random.nextDouble()) * this.scale;
                double y = this.y + (this.random.nextDouble() - this.random.nextDouble()) * this.scale;
                double z = this.z + (this.random.nextDouble() - this.random.nextDouble()) * this.scale;
                this.level.addParticle(ParticleTypes.GUST, x, y, z, (float)this.age / this.lifetime, 0.0, 0.0);
            }
        }

        if (this.age++ == this.lifetime) {
            this.remove();
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final double scale;
        private final int lifetime;
        private final int tickDelayInBetween;

        public Provider(double scale, int lifetime, int tickDelayInBetween) {
            this.scale = scale;
            this.lifetime = lifetime;
            this.tickDelayInBetween = tickDelayInBetween;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            return new GustSeedParticle(level, x, y, z, this.scale, this.lifetime, this.tickDelayInBetween);
        }
    }
}

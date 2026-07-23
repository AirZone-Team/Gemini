package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class DustParticle extends DustParticleBase<DustParticleOptions> {
    public DustParticle(
        ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, DustParticleOptions options, SpriteSet sprites
    ) {
        super(level, x, y, z, xAux, yAux, zAux, options, sprites);
        float baseFactor = this.random.nextFloat() * 0.4F + 0.6F;
        Vector3f color = options.getColor();
        this.rCol = this.randomizeColor(color.x(), baseFactor);
        this.gCol = this.randomizeColor(color.y(), baseFactor);
        this.bCol = this.randomizeColor(color.z(), baseFactor);
    }

    public static class Provider implements ParticleProvider<DustParticleOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(
            DustParticleOptions options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            return new DustParticle(level, x, y, z, xAux, yAux, zAux, options, this.sprites);
        }
    }
}

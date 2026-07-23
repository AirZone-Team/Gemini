package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PlayerCloudParticle extends SingleQuadParticle {
    private final SpriteSet sprites;

    public PlayerCloudParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0, sprites.first());
        this.friction = 0.96F;
        this.sprites = sprites;
        float scale = 2.5F;
        this.xd *= 0.1F;
        this.yd *= 0.1F;
        this.zd *= 0.1F;
        this.xd += xa;
        this.yd += ya;
        this.zd += za;
        float col = 1.0F - this.random.nextFloat() * 0.3F;
        this.rCol = col;
        this.gCol = col;
        this.bCol = col;
        this.quadSize *= 1.875F;
        int baseLifetime = (int)(8.0 / (this.random.nextFloat() * 0.8 + 0.3));
        this.lifetime = (int)Math.max(baseLifetime * 2.5F, 1.0F);
        this.hasPhysics = false;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public float getQuadSize(float a) {
        return this.quadSize * Mth.clamp((this.age + a) / this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            this.setSpriteFromAge(this.sprites);
            Player player = this.level.getNearestPlayer(this.x, this.y, this.z, 2.0, false);
            if (player != null) {
                double playerY = player.getY();
                if (this.y > playerY) {
                    this.y = this.y + (playerY - this.y) * 0.2;
                    this.yd = this.yd + (player.getDeltaMovement().y - this.yd) * 0.2;
                    this.setPos(this.x, this.y, this.z);
                }
            }
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            return new PlayerCloudParticle(level, x, y, z, xAux, yAux, zAux, this.sprites);
        }
    }

    public static class SneezeProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public SneezeProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            PlayerCloudParticle particle = new PlayerCloudParticle(level, x, y, z, xAux, yAux, zAux, this.sprites);
            particle.setColor(0.22F, 1.0F, 0.53F);
            particle.setAlpha(0.4F);
            return particle;
        }
    }
}

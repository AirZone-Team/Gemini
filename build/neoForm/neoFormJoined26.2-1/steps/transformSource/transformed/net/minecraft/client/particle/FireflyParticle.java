package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FireflyParticle extends SingleQuadParticle {
    private static final float PARTICLE_FADE_OUT_LIGHT_TIME = 0.3F;
    private static final float PARTICLE_FADE_IN_LIGHT_TIME = 0.1F;
    private static final float PARTICLE_FADE_OUT_ALPHA_TIME = 0.5F;
    private static final float PARTICLE_FADE_IN_ALPHA_TIME = 0.3F;
    private static final int PARTICLE_MIN_LIFETIME = 200;
    private static final int PARTICLE_MAX_LIFETIME = 300;

    public FireflyParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, TextureAtlasSprite sprite) {
        super(level, x, y, z, xa, ya, za, sprite);
        this.speedUpWhenYMotionIsBlocked = true;
        this.friction = 0.96F;
        this.quadSize *= 0.75F;
        this.yd *= 0.8F;
        this.xd *= 0.8F;
        this.zd *= 0.8F;
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public int getLightCoords(float a) {
        return (int)(255.0F * getFadeAmount(this.getLifetimeProgress(this.age + a), 0.1F, 0.3F));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level.getBlockState(BlockPos.containing(this.x, this.y, this.z)).isAir()) {
            this.remove();
        } else {
            this.setAlpha(getFadeAmount(this.getLifetimeProgress(this.age), 0.3F, 0.5F));
            if (this.random.nextFloat() > 0.95F || this.age == 1) {
                this.setParticleSpeed(-0.05F + 0.1F * this.random.nextFloat(), -0.05F + 0.1F * this.random.nextFloat(), -0.05F + 0.1F * this.random.nextFloat());
            }
        }
    }

    private float getLifetimeProgress(float currentAge) {
        return Mth.clamp(currentAge / this.lifetime, 0.0F, 1.0F);
    }

    private static float getFadeAmount(float lifetimeProgress, float fadeInTime, float fadeOutTime) {
        if (lifetimeProgress >= 1.0F - fadeInTime) {
            return (1.0F - lifetimeProgress) / fadeInTime;
        } else {
            return lifetimeProgress <= fadeOutTime ? lifetimeProgress / fadeOutTime : 1.0F;
        }
    }

    public static class FireflyProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public FireflyProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            FireflyParticle particle = new FireflyParticle(
                level, x, y, z, 0.5 - random.nextDouble(), random.nextBoolean() ? yAux : -yAux, 0.5 - random.nextDouble(), this.sprite.get(random)
            );
            particle.setLifetime(random.nextIntBetweenInclusive(200, 300));
            particle.scale(1.5F);
            particle.setAlpha(0.0F);
            return particle;
        }
    }
}

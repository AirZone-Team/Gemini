package net.minecraft.world.entity.decoration;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class BlockAttachedEntity extends Entity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private int checkInterval;
    protected BlockPos pos;

    protected BlockAttachedEntity(EntityType<? extends BlockAttachedEntity> type, Level level) {
        super(type, level);
    }

    protected BlockAttachedEntity(EntityType<? extends BlockAttachedEntity> type, Level level, BlockPos pos) {
        this(type, level);
        this.pos = pos;
    }

    protected abstract void recalculateBoundingBox();

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel level) {
            this.checkBelowWorld();
            if (this.checkInterval++ == 100) {
                this.checkInterval = 0;
                if (!this.isRemoved() && !this.survives()) {
                    this.discard();
                    this.dropItem(level, null);
                }
            }
        }
    }

    public abstract boolean survives();

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity source) {
        if (source instanceof Player player) {
            return !this.level().mayInteract(player, this.pos) ? true : this.hurtOrSimulate(this.damageSources().playerAttack(player), 0.0F);
        } else {
            return false;
        }
    }

    @Override
    public boolean hurtClient(DamageSource source) {
        return !this.isInvulnerableToBase(source);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isInvulnerableToBase(source)) {
            return false;
        }

        if (!level.getGameRules().get(GameRules.MOB_GRIEFING) && source.getEntity() instanceof Mob) {
            return false;
        }

        if (!this.isRemoved()) {
            this.kill(level);
            this.markHurt();
            this.dropItem(level, source.getEntity());
        }

        return true;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        Entity directEntity = explosion.getDirectSourceEntity();
        if (directEntity != null && directEntity.isInWater()) {
            return true;
        } else {
            return explosion.shouldAffectBlocklikeEntities() ? super.ignoreExplosion(explosion) : true;
        }
    }

    @Override
    public void move(MoverType moverType, Vec3 delta) {
        if (this.level() instanceof ServerLevel level && !this.isRemoved() && delta.lengthSqr() > 0.0) {
            this.kill(level);
            this.dropItem(level, null);
        }
    }

    @Override
    public void push(double xa, double ya, double za) {
        if (this.level() instanceof ServerLevel level && !this.isRemoved() && xa * xa + ya * ya + za * za > 0.0) {
            this.kill(level);
            this.dropItem(level, null);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("block_pos", BlockPos.CODEC, this.getPos());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        BlockPos storedPos = input.read("block_pos", BlockPos.CODEC).orElse(null);
        if (storedPos != null && storedPos.closerThan(this.blockPosition(), 16.0)) {
            this.pos = storedPos;
        } else {
            LOGGER.error("Block-attached entity at invalid position: {}", storedPos);
        }
    }

    public abstract void dropItem(ServerLevel level, @Nullable Entity causedBy);

    @Override
    protected boolean repositionEntityAfterLoad() {
        return false;
    }

    @Override
    public void setPos(double x, double y, double z) {
        this.pos = BlockPos.containing(x, y, z);
        this.recalculateBoundingBox();
        this.needsSync = true;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightningBolt) {
    }

    @Override
    public void refreshDimensions() {
    }
}

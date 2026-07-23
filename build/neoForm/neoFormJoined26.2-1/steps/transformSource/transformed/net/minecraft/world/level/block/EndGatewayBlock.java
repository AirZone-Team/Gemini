package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class EndGatewayBlock extends BaseEntityBlock implements Portal {
    public static final MapCodec<EndGatewayBlock> CODEC = simpleCodec(EndGatewayBlock::new);

    @Override
    public MapCodec<EndGatewayBlock> codec() {
        return CODEC;
    }

    public EndGatewayBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new TheEndGatewayBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        return createTickerHelper(
            type, BlockEntityTypes.END_GATEWAY, level.isClientSide() ? TheEndGatewayBlockEntity::beamAnimationTick : TheEndGatewayBlockEntity::portalTick
        );
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof TheEndGatewayBlockEntity theEndGatewayBlockEntity) {
            int particleCount = theEndGatewayBlockEntity.getParticleAmount();

            for (int i = 0; i < particleCount; i++) {
                double x = pos.getX() + random.nextDouble();
                double y = pos.getY() + random.nextDouble();
                double z = pos.getZ() + random.nextDouble();
                double xa = (random.nextDouble() - 0.5) * 0.5;
                double ya = (random.nextDouble() - 0.5) * 0.5;
                double za = (random.nextDouble() - 0.5) * 0.5;
                int flip = random.nextInt(2) * 2 - 1;
                if (random.nextBoolean()) {
                    z = pos.getZ() + 0.5 + 0.25 * flip;
                    za = random.nextFloat() * 2.0F * flip;
                } else {
                    x = pos.getX() + 0.5 + 0.25 * flip;
                    xa = random.nextFloat() * 2.0F * flip;
                }

                level.addParticle(ParticleTypes.PORTAL, x, y, z, xa, ya, za);
            }
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (entity.canUsePortal(false)
            && !level.isClientSide()
            && level.getBlockEntity(pos) instanceof TheEndGatewayBlockEntity endGatewayBlockEntity
            && !endGatewayBlockEntity.isCoolingDown()) {
            entity.setAsInsidePortal(this, pos);
            TheEndGatewayBlockEntity.triggerCooldown(level, pos, state, endGatewayBlockEntity);
        }
    }

    @Override
    public @Nullable TeleportTransition getPortalDestination(ServerLevel currentLevel, Entity entity, BlockPos portalEntryPos) {
        if (currentLevel.getBlockEntity(portalEntryPos) instanceof TheEndGatewayBlockEntity endGatewayBlockEntity) {
            Vec3 teleportPosition = endGatewayBlockEntity.getPortalPosition(currentLevel, portalEntryPos);
            if (teleportPosition == null) {
                return null;
            } else {
                return entity instanceof ThrownEnderpearl
                    ? new TeleportTransition(currentLevel, teleportPosition, Vec3.ZERO, 0.0F, 0.0F, Set.of(), TeleportTransition.PLACE_PORTAL_TICKET)
                    : new TeleportTransition(
                        currentLevel,
                        teleportPosition,
                        Vec3.ZERO,
                        0.0F,
                        0.0F,
                        Relative.union(Relative.DELTA, Relative.ROTATION),
                        TeleportTransition.PLACE_PORTAL_TICKET
                    );
            }
        } else {
            return null;
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}

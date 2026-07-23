package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class StructureBlock extends BaseEntityBlock implements GameMasterBlock {
    public static final MapCodec<StructureBlock> CODEC = simpleCodec(StructureBlock::new);
    public static final EnumProperty<StructureMode> MODE = BlockStateProperties.STRUCTUREBLOCK_MODE;

    @Override
    public MapCodec<StructureBlock> codec() {
        return CODEC;
    }

    public StructureBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(MODE, StructureMode.LOAD));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new StructureBlockEntity(worldPosition, blockState);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof StructureBlockEntity structureBlockEntity) {
            return structureBlockEntity.usedBy(player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        if (!level.isClientSide()) {
            if (by != null && level.getBlockEntity(pos) instanceof StructureBlockEntity structureBlockEntity) {
                structureBlockEntity.createdBy(by);
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel) {
            if (level.getBlockEntity(pos) instanceof StructureBlockEntity structureBlock) {
                boolean shouldTrigger = level.hasNeighborSignal(pos);
                boolean isPowered = structureBlock.isPowered();
                if (shouldTrigger && !isPowered) {
                    structureBlock.setPowered(true);
                    this.trigger(serverLevel, structureBlock);
                } else if (!shouldTrigger && isPowered) {
                    structureBlock.setPowered(false);
                }
            }
        }
    }

    private void trigger(ServerLevel level, StructureBlockEntity structureBlock) {
        switch (structureBlock.getMode()) {
            case SAVE:
                structureBlock.saveStructure(false);
                break;
            case LOAD:
                structureBlock.placeStructure(level);
                break;
            case CORNER:
                structureBlock.unloadStructure();
            case DATA:
        }
    }
}

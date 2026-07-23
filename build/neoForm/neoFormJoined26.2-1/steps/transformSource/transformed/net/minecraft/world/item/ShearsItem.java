package net.minecraft.world.item;

import java.util.List;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class ShearsItem extends Item {
    public ShearsItem(Item.Properties properties) {
        super(properties);
    }

    public static Tool createToolProperties() {
        HolderGetter<Block> registrationLookup = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
        return new Tool(
            List.of(
                Tool.Rule.minesAndDrops(HolderSet.direct(Blocks.COBWEB.builtInRegistryHolder()), 15.0F),
                Tool.Rule.overrideSpeed(registrationLookup.getOrThrow(BlockTags.SHEARS_EXTREME_BREAKING_SPEED), 15.0F),
                Tool.Rule.overrideSpeed(registrationLookup.getOrThrow(BlockTags.SHEARS_MAJOR_BREAKING_SPEED), 5.0F),
                Tool.Rule.overrideSpeed(registrationLookup.getOrThrow(BlockTags.SHEARS_MINOR_BREAKING_SPEED), 2.0F)
            ),
            1.0F,
            1,
            true
        );
    }

    @Override
    public boolean mineBlock(ItemStack itemStack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        Tool tool = itemStack.get(DataComponents.TOOL);
        if (tool == null) {
            return false;
        }

        if (!level.isClientSide() && !state.is(BlockTags.FIRE) && tool.damagePerBlock() > 0) {
            itemStack.hurtAndBreak(tool.damagePerBlock(), miner, EquipmentSlot.MAINHAND);
        }

        return true;
    }

    /**
     * Neo: Migrate shear behavior into {@link ShearsItem#interactLivingEntity} to call into IShearable instead of relying on {@link net.minecraft.world.entity.Mob#mobInteract}
     * <p>
     * To preserve vanilla behavior, this method retains the original flow shared by the various mobInteract overrides.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, net.minecraft.world.InteractionHand hand) {
        if (entity instanceof net.neoforged.neoforge.common.IShearable target) {
            BlockPos pos = entity.blockPosition();
            boolean isClient = entity.level().isClientSide();
            // Check isShearable on both sides (mirrors vanilla readyForShearing())
            if (target.isShearable(player, stack, entity.level(), pos)) {
                // Call onSheared on both sides (mirrors vanilla shear())
                List<ItemStack> drops = target.onSheared(player, stack, entity.level(), pos);
                // Spawn drops on the server side using spawnShearedDrop to retain vanilla mob-specific behavior
                if (entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    for(ItemStack drop : drops) {
                        target.spawnShearedDrop(serverLevel, pos, drop);
                    }
                }
                // Call GameEvent.SHEAR on both sides
                entity.gameEvent(GameEvent.SHEAR, player);
                // Damage the shear item stack by 1 on the server side
                if (!isClient) {
                    stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
                }
                // Return sided success if the entity was shearable
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean canPerformAction(ItemInstance stack, net.neoforged.neoforge.common.ItemAbility itemAbility) {
        return net.neoforged.neoforge.common.ItemAbilities.DEFAULT_SHEARS_ACTIONS.contains(itemAbility);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        BlockState newState = state.getToolModifiedState(context, net.neoforged.neoforge.common.ItemAbilities.SHEARS_TRIM, false);
        if (newState != null) {
            Player player = context.getPlayer();
            ItemStack itemInHand = context.getItemInHand();
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, pos, itemInHand);
            }

            level.setBlockAndUpdate(pos, newState);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(context.getPlayer(), newState));
            if (player != null) {
                itemInHand.hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
            }

            return InteractionResult.SUCCESS;
        } else {
            return super.useOn(context);
        }
    }
}

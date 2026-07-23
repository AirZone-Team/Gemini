package net.minecraft.world.item;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SpawnEggItem extends Item {
    public SpawnEggItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack itemStack = context.getItemInHand();
        EntityType<?> type = getType(itemStack);
        if (type == null || !type.canSpawn(level)) {
            return InteractionResult.FAIL;
        } else if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        } else {
            BlockPos pos = context.getClickedPos();
            Direction clickedFace = context.getClickedFace();
            BlockState blockState = level.getBlockState(pos);
            if (level.getBlockEntity(pos) instanceof Spawner spawnerHolder) {
                if (!serverLevel.isSpawnerBlockEnabled()) {
                    if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                        serverPlayer.sendSystemMessage(Component.translatable("advMode.notEnabled.spawner"));
                    }

                    return InteractionResult.FAIL;
                } else {
                    spawnerHolder.setEntityId(type, level.getRandom());
                    level.sendBlockUpdated(pos, blockState, blockState, 3);
                    level.gameEvent(context.getPlayer(), GameEvent.BLOCK_CHANGE, pos);
                    itemStack.shrink(1);
                    return InteractionResult.SUCCESS;
                }
            } else {
                BlockPos spawnPos;
                if (blockState.getCollisionShape(level, pos).isEmpty()) {
                    spawnPos = pos;
                } else {
                    spawnPos = pos.relative(clickedFace);
                }

                return spawnMob(
                    type, context.getPlayer(), itemStack, serverLevel, spawnPos, true, !Objects.equals(pos, spawnPos) && clickedFace == Direction.UP
                );
            }
        }
    }

    private static InteractionResult spawnMob(
        EntityType<?> type, @Nullable LivingEntity user, ItemStack itemStack, ServerLevel level, BlockPos spawnPos, boolean tryMoveDown, boolean movedUp
    ) {
        if (type.spawn(level, itemStack, user, spawnPos, EntitySpawnReason.SPAWN_ITEM_USE, tryMoveDown, movedUp) != null) {
            itemStack.consume(1, user);
            level.gameEvent(user, GameEvent.ENTITY_PLACE, spawnPos);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.FAIL;
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        BlockHitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        }

        EntityType<?> type = getType(itemStack);
        if (type == null || !type.canSpawn(level)) {
            return InteractionResult.FAIL;
        }

        if (level instanceof ServerLevel serverLevel) {
            BlockPos pos = hitResult.getBlockPos();
            if (!(level.getBlockState(pos).getBlock() instanceof LiquidBlock)) {
                return InteractionResult.PASS;
            }

            if (level.mayInteract(player, pos) && player.mayUseItemAt(pos, hitResult.getDirection(), itemStack)) {
                InteractionResult result = spawnMob(type, player, itemStack, serverLevel, pos, false, false);
                if (result == InteractionResult.SUCCESS) {
                    player.awardStat(Stats.ITEM_USED.get(this));
                }

                return result;
            } else {
                return InteractionResult.FAIL;
            }
        } else {
            return InteractionResult.SUCCESS;
        }
    }

    public static boolean spawnsEntity(ItemStack itemStack, EntityType<?> type) {
        return Objects.equals(getType(itemStack), type);
    }

    public static Optional<Holder<Item>> byId(EntityType<?> type) {
        return BuiltInRegistries.ITEM.componentLookup().findMatching(DataComponents.ENTITY_DATA, c -> c.type() == type).findAny();
    }

    public static @Nullable EntityType<?> getType(ItemStack itemStack) {
        TypedEntityData<EntityType<?>> entityData = itemStack.get(DataComponents.ENTITY_DATA);
        return entityData != null ? entityData.type() : null;
    }

    public static Optional<Mob> spawnOffspringFromSpawnEgg(
        Player player, Mob parent, EntityType<? extends Mob> type, ServerLevel level, Vec3 pos, ItemStack spawnEggStack
    ) {
        if (!spawnsEntity(spawnEggStack, type)) {
            return Optional.empty();
        }

        Mob offspring;
        if (parent instanceof AgeableMob ageableMob) {
            offspring = ageableMob.getBreedOffspring(level, ageableMob);
        } else {
            offspring = type.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        }

        if (offspring == null) {
            return Optional.empty();
        }

        offspring.setBaby(true);
        if (!offspring.isBaby()) {
            return Optional.empty();
        }

        offspring.snapTo(pos.x(), pos.y(), pos.z(), 0.0F, 0.0F);
        offspring.applyComponentsFromItemStack(spawnEggStack);
        level.addFreshEntityWithPassengers(offspring);
        spawnEggStack.consume(1, player);
        return Optional.of(offspring);
    }

    @Override
    public boolean shouldPrintOpWarning(ItemStack stack, @Nullable Player player) {
        if (player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            TypedEntityData<EntityType<?>> entityData = stack.get(DataComponents.ENTITY_DATA);
            if (entityData != null) {
                return entityData.type().onlyOpCanSetNbt();
            }
        }

        return false;
    }

    /**
     * {@return the dispense behavior to register or null to register the default}
     */
    protected net.minecraft.core.dispenser.@Nullable DispenseItemBehavior createDispenseBehavior() {
        return null;
    }

    // TODO 26.1: It's questionable that this is even needed anymore
    @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    private static void registerDispenseBehavior(final net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> BuiltInRegistries.ITEM.forEach(item -> {
            if (item instanceof SpawnEggItem egg && !net.minecraft.world.level.block.DispenserBlock.DISPENSER_REGISTRY.containsKey(egg)) {
                var beh = egg.createDispenseBehavior();
                if (beh != null) {
                    net.minecraft.world.level.block.DispenserBlock.registerBehavior(egg, beh);
                }
            }
        }));
    }
}

package net.minecraft.world.entity.item;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ItemEntity extends Entity implements TraceableEntity {
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(ItemEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final float FLOAT_HEIGHT = 0.1F;
    public static final float EYE_HEIGHT = 0.2125F;
    private static final int LIFETIME = 6000;
    private static final int INFINITE_PICKUP_DELAY = 32767;
    private static final int INFINITE_LIFETIME = -32768;
    public static final int DEFAULT_HEALTH = 5;
    private static final short DEFAULT_AGE = 0;
    private static final short DEFAULT_PICKUP_DELAY = 0;
    private int age = 0;
    private int pickupDelay = 0;
    private int health = 5;
    private @Nullable EntityReference<Entity> thrower;
    private @Nullable UUID target;
    public final float bobOffs = this.random.nextFloat() * (float) Math.PI * 2.0F;
    /**
     * The maximum age of this EntityItem. The item is expired once this is reached.
     */
    public int lifespan = ItemEntity.LIFETIME;

    public ItemEntity(EntityType<? extends ItemEntity> type, Level level) {
        super(type, level);
        this.setYRot(this.random.nextFloat() * 360.0F);
    }

    public ItemEntity(Level level, double x, double y, double z, ItemStack itemStack) {
        this(EntityTypes.ITEM, level);
        this.setPos(x, y, z);
        this.setItem(itemStack);
        this.setDeltaMovement(this.random.nextDouble() * 0.2 - 0.1, 0.2, this.random.nextDouble() * 0.2 - 0.1);
        this.lifespan = (itemStack.getItem() == null ? LIFETIME : itemStack.getEntityLifespan(level));
    }

    public ItemEntity(Level level, double x, double y, double z, ItemStack itemStack, double deltaX, double deltaY, double deltaZ) {
        this(EntityTypes.ITEM, level);
        this.setPos(x, y, z);
        this.setItem(itemStack);
        this.setDeltaMovement(deltaX, deltaY, deltaZ);
        this.lifespan = (itemStack.getItem() == null ? LIFETIME : itemStack.getEntityLifespan(level));
    }

    @Override
    public boolean dampensVibrations() {
        return this.getItem().is(ItemTags.DAMPENS_VIBRATIONS);
    }

    @Override
    public @Nullable Entity getOwner() {
        return EntityReference.getEntity(this.thrower, this.level());
    }

    @Override
    public void restoreFrom(Entity oldEntity) {
        super.restoreFrom(oldEntity);
        if (oldEntity instanceof ItemEntity item) {
            this.thrower = item.thrower;
        }
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(DATA_ITEM, ItemStack.EMPTY);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }

    @Override
    public void tick() {
        if (getItem().onEntityItemUpdate(this)) return;
        if (this.getItem().isEmpty()) {
            this.discard();
        } else {
            super.tick();
            if (this.pickupDelay > 0 && this.pickupDelay != 32767) {
                this.pickupDelay--;
            }

            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
            Vec3 oldMovement = this.getDeltaMovement();
            if (this.isInWater() && this.getFluidHeight(FluidTags.WATER) > 0.1F) {
                this.setUnderwaterMovement();
            } else if (this.isInLava() && this.getFluidHeight(FluidTags.LAVA) > 0.1F) {
                this.setUnderLavaMovement();
            } else {
                this.applyGravity();
            }

            if (this.level().isClientSide()) {
                this.noPhysics = false;
            } else {
                this.noPhysics = !this.level().noCollision(this, this.getBoundingBox().deflate(1.0E-7));
                if (this.noPhysics) {
                    this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0, this.getZ());
                }
            }

            if (this.onGround() && !(this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-5F) && (this.tickCount + this.getId()) % 4 != 0) {
                this.applyEffectsFromBlocksForLastMovements();
            } else {
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.applyEffectsFromBlocks();
                float airDrag = this.getAirDrag();
                float groundFriction = airDrag;
                if (this.onGround()) {
                    BlockPos groundPos = getBlockPosBelowThatAffectsMyMovement();
                    groundFriction *= this.level().getBlockState(groundPos).getFriction(level(), groundPos, this);
                }

                this.setDeltaMovement(this.getDeltaMovement().multiply(groundFriction, airDrag, groundFriction));
                if (this.onGround()) {
                    Vec3 movement = this.getDeltaMovement();
                    if (movement.y < 0.0) {
                        this.setDeltaMovement(movement.multiply(1.0, -0.5, 1.0));
                    }
                }
            }

            boolean moved = Mth.floor(this.xo) != Mth.floor(this.getX())
                || Mth.floor(this.yo) != Mth.floor(this.getY())
                || Mth.floor(this.zo) != Mth.floor(this.getZ());
            int rate = moved ? 2 : 40;
            if (this.tickCount % rate == 0 && !this.level().isClientSide() && this.isMergable()) {
                this.mergeWithNeighbours();
            }

            if (this.age != -32768) {
                this.age++;
            }

            this.needsSync = this.needsSync | this.updateFluidInteraction();
            if (!this.level().isClientSide()) {
                double value = this.getDeltaMovement().subtract(oldMovement).lengthSqr();
                if (value > 0.01) {
                    this.needsSync = true;
                }
            }

            ItemStack item = this.getItem();
            if (!this.level().isClientSide() && this.age >= lifespan) {
            	  // Clamping to MAX_VALUE -1 as age is a Short and going above that would produce an infinite lifespan implicitly (accidentally)
                this.lifespan = Mth.clamp(lifespan + net.neoforged.neoforge.event.EventHooks.onItemExpire(this), 0, Short.MAX_VALUE - 1);
                if (this.age >= lifespan) {
                    this.discard();
                }
            }
            if (item.isEmpty() && !this.isRemoved()) {
                this.discard();
            }
        }
    }

    @Override
    public BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.999999F);
    }

    private void setUnderwaterMovement() {
        this.setFluidMovement(0.99F);
    }

    private void setUnderLavaMovement() {
        this.setFluidMovement(0.95F);
    }

    private void setFluidMovement(double multiplier) {
        Vec3 movement = this.getDeltaMovement();
        this.setDeltaMovement(movement.x * multiplier, movement.y + (movement.y < 0.06F ? 5.0E-4F : 0.0F), movement.z * multiplier);
    }

    private void mergeWithNeighbours() {
        if (this.isMergable()) {
            for (ItemEntity entity : this.level()
                .getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(0.5, 0.0, 0.5), other -> other != this && other.isMergable())) {
                if (entity.isMergable()) {
                    this.tryToMerge(entity);
                    if (this.isRemoved()) {
                        break;
                    }
                }
            }
        }
    }

    private boolean isMergable() {
        ItemStack item = this.getItem();
        return this.isAlive() && this.pickupDelay != 32767 && this.age != -32768 && this.age < 6000 && item.getCount() < item.getMaxStackSize();
    }

    private void tryToMerge(ItemEntity other) {
        ItemStack thisItemStack = this.getItem();
        ItemStack otherItemStack = other.getItem();
        if (Objects.equals(this.target, other.target) && areMergable(thisItemStack, otherItemStack)) {
            if (otherItemStack.getCount() < thisItemStack.getCount()) {
                merge(this, thisItemStack, other, otherItemStack);
            } else {
                merge(other, otherItemStack, this, thisItemStack);
            }
        }
    }

    public static boolean areMergable(ItemStack thisItemStack, ItemStack otherItemStack) {
        return otherItemStack.getCount() + thisItemStack.getCount() > otherItemStack.getMaxStackSize()
            ? false
            : ItemStack.isSameItemSameComponents(thisItemStack, otherItemStack);
    }

    public static ItemStack merge(ItemStack toStack, ItemStack fromStack, int maxCount) {
        int delta = Math.min(Math.min(toStack.getMaxStackSize(), maxCount) - toStack.getCount(), fromStack.getCount());
        ItemStack newToStack = toStack.copyWithCount(toStack.getCount() + delta);
        fromStack.shrink(delta);
        return newToStack;
    }

    private static void merge(ItemEntity toItem, ItemStack toStack, ItemStack fromStack) {
        ItemStack newToStack = merge(toStack, fromStack, 64);
        toItem.setItem(newToStack);
    }

    private static void merge(ItemEntity toItem, ItemStack toStack, ItemEntity fromItem, ItemStack fromStack) {
        merge(toItem, toStack, fromStack);
        toItem.pickupDelay = Math.max(toItem.pickupDelay, fromItem.pickupDelay);
        toItem.age = Math.min(toItem.age, fromItem.age);
        if (fromStack.isEmpty()) {
            fromItem.discard();
        }
    }

    @Override
    public boolean fireImmune() {
        return !this.getItem().canBeHurtBy(this.damageSources().inFire()) || super.fireImmune();
    }

    @Override
    protected boolean shouldPlayLavaHurtSound() {
        return this.health <= 0 ? true : this.tickCount % 10 == 0;
    }

    @Override
    public final boolean hurtClient(DamageSource source) {
        return this.isInvulnerableToBase(source) ? false : this.getItem().canBeHurtBy(source);
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isInvulnerableToBase(source)) {
            return false;
        }

        if (!level.getGameRules().get(GameRules.MOB_GRIEFING) && source.getEntity() instanceof Mob) {
            return false;
        }

        if (!this.getItem().canBeHurtBy(source)) {
            return false;
        }

        this.markHurt();
        this.health = (int)(this.health - damage);
        this.gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
        if (this.health <= 0) {
            this.getItem().onDestroyed(this, source);
            this.discard();
        }

        return true;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return explosion.shouldAffectBlocklikeEntities() ? super.ignoreExplosion(explosion) : true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putShort("Health", (short)this.health);
        output.putShort("Age", (short)this.age);
        output.putShort("PickupDelay", (short)this.pickupDelay);
        output.putInt("Lifespan", this.lifespan);
        EntityReference.store(this.thrower, output, "Thrower");
        output.storeNullable("Owner", UUIDUtil.CODEC, this.target);
        if (!this.getItem().isEmpty()) {
            output.store("Item", ItemStack.CODEC, this.getItem());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.health = input.getShortOr("Health", (short)5);
        this.age = input.getShortOr("Age", (short)0);
        this.pickupDelay = input.getShortOr("PickupDelay", (short)0);
        this.lifespan = input.getIntOr("Lifespan", ItemEntity.LIFETIME);
        this.target = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        this.thrower = EntityReference.read(input, "Thrower");
        this.setItem(input.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        if (this.getItem().isEmpty()) {
            this.discard();
        }
    }

    @Override
    public void playerTouch(Player player) {
        if (!this.level().isClientSide()) {
            ItemStack itemStack = this.getItem();
            Item item = itemStack.getItem();
            int orgCount = itemStack.getCount();

            // Neo: Fire item pickup pre/post and adjust handling logic to adhere to the event result.
            var result = net.neoforged.neoforge.event.EventHooks.fireItemPickupPre(this, player).canPickup();
            if (result.isFalse()) {
                return;
            }

            // Make a copy of the original stack for use in ItemEntityPickupEvent.Post
            ItemStack originalCopy = itemStack.copy();
            // Subvert the vanilla conditions (pickup delay and target check) if the result is true.
            if ((result.isTrue() || this.pickupDelay == 0 && (this.target == null || this.target.equals(player.getUUID()))) && player.getInventory().add(itemStack)) {
                // Fire ItemEntityPickupEvent.Post
                net.neoforged.neoforge.event.EventHooks.fireItemPickupPost(this, player, originalCopy);
                // Update `orgCount` to reflect the actual pickup amount. Vanilla is wrong here and always reports the whole stack.
                orgCount = originalCopy.getCount() - itemStack.getCount();

                player.take(this, orgCount);
                if (itemStack.isEmpty()) {
                    this.discard();
                    itemStack.setCount(orgCount);
                }

                player.awardStat(Stats.ITEM_PICKED_UP.get(item), orgCount);
                player.onItemPickup(this);
            }
        }
    }

    @Override
    public Component getName() {
        Component name = this.getCustomName();
        return name != null ? name : this.getItem().getItemName();
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public @Nullable Entity teleport(TeleportTransition transition) {
        Entity entity = super.teleport(transition);
        if (!this.level().isClientSide() && entity instanceof ItemEntity item) {
            item.mergeWithNeighbours();
        }

        return entity;
    }

    public ItemStack getItem() {
        return this.getEntityData().get(DATA_ITEM);
    }

    public void setItem(ItemStack itemStack) {
        this.getEntityData().set(DATA_ITEM, itemStack);
    }

    public void setTarget(@Nullable UUID target) {
        this.target = target;
    }

    /**
     * Neo: Add getter for ItemEntity's {@link ItemEntity#target target}.
     * @return The {@link ItemEntity#target target} that can pick up this item entity (if {@code null}, anyone can pick it up)
     */
    @Nullable
    public UUID getTarget() {
        return this.target;
    }

    public void setThrower(Entity thrower) {
        this.thrower = EntityReference.of(thrower);
    }

    public int getAge() {
        return this.age;
    }

    public void setDefaultPickUpDelay() {
        this.pickupDelay = 10;
    }

    public void setNoPickUpDelay() {
        this.pickupDelay = 0;
    }

    public void setNeverPickUp() {
        this.pickupDelay = 32767;
    }

    public void setPickUpDelay(int ticks) {
        this.pickupDelay = ticks;
    }

    public boolean hasPickUpDelay() {
        return this.pickupDelay > 0;
    }

    public void setUnlimitedLifetime() {
        this.age = -32768;
    }

    public void setExtendedLifetime() {
        this.age = -6000;
    }

    public void makeFakeItem() {
        this.setNeverPickUp();
        this.age = getItem().getEntityLifespan(this.level()) - 1;
    }

    public static float getSpin(float ageInTicks, float bobOffset) {
        return ageInTicks / 20.0F + bobOffset;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.AMBIENT;
    }

    @Override
    public float getVisualRotationYInDegrees() {
        return 180.0F - getSpin(this.getAge() + 0.5F, this.bobOffs) / (float) (Math.PI * 2) * 360.0F;
    }

    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        return slot == 0 ? SlotAccess.of(this::getItem, this::setItem) : super.getSlot(slot);
    }
}

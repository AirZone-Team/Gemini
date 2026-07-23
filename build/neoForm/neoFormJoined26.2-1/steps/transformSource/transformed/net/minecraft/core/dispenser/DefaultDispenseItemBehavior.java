package net.minecraft.core.dispenser;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;

public class DefaultDispenseItemBehavior implements DispenseItemBehavior {
    private static final int DEFAULT_ACCURACY = 6;

    @Override
    public final ItemStack dispense(BlockSource source, ItemStack dispensed) {
        ItemStack result = this.execute(source, dispensed);
        this.playSound(source);
        this.playAnimation(source, source.state().getValue(DispenserBlock.FACING));
        return result;
    }

    protected ItemStack execute(BlockSource source, ItemStack dispensed) {
        Direction direction = source.state().getValue(DispenserBlock.FACING);
        Position position = DispenserBlock.getDispensePosition(source);
        ItemStack itemStack = dispensed.split(1);
        spawnItem(source.level(), itemStack, 6, direction, position);
        return dispensed;
    }

    public static void spawnItem(Level level, ItemStack itemStack, int accuracy, Direction direction, Position position) {
        double spawnX = position.x();
        double spawnY = position.y();
        double spawnZ = position.z();
        if (direction.getAxis() == Direction.Axis.Y) {
            spawnY -= 0.125;
        } else {
            spawnY -= 0.15625;
        }

        ItemEntity itemEntity = new ItemEntity(level, spawnX, spawnY, spawnZ, itemStack);
        RandomSource random = level.getRandom();
        double pow = random.nextDouble() * 0.1 + 0.2;
        itemEntity.setDeltaMovement(
            random.triangle(direction.getStepX() * pow, 0.0172275 * accuracy),
            random.triangle(0.2, 0.0172275 * accuracy),
            random.triangle(direction.getStepZ() * pow, 0.0172275 * accuracy)
        );
        level.addFreshEntity(itemEntity);
    }

    protected void playSound(BlockSource source) {
        playDefaultSound(source);
    }

    protected void playAnimation(BlockSource source, Direction direction) {
        playDefaultAnimation(source, direction);
    }

    private static void playDefaultSound(BlockSource source) {
        source.level().levelEvent(1000, source.pos(), 0);
    }

    private static void playDefaultAnimation(BlockSource source, Direction direction) {
        source.level().levelEvent(2000, source.pos(), direction.get3DDataValue());
    }

    protected ItemStack consumeWithRemainder(BlockSource source, ItemStack dispensed, ItemStack remainder) {
        dispensed.shrink(1);
        if (dispensed.isEmpty()) {
            return remainder;
        }

        this.addToInventoryOrDispense(source, remainder);
        return dispensed;
    }

    private void addToInventoryOrDispense(BlockSource source, ItemStack itemStack) {
        ItemStack remainder = source.blockEntity().insertItem(itemStack);
        if (!remainder.isEmpty()) {
            Direction direction = source.state().getValue(DispenserBlock.FACING);
            spawnItem(source.level(), remainder, 6, direction, DispenserBlock.getDispensePosition(source));
            playDefaultSound(source);
            playDefaultAnimation(source, direction);
        }
    }
}

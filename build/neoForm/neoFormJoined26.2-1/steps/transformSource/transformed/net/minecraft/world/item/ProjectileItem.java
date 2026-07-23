package net.minecraft.world.item;

import java.util.OptionalInt;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;

public interface ProjectileItem {
    Projectile asProjectile(final Level level, final Position position, final ItemStack itemStack, final Direction direction);

    default ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.DEFAULT;
    }

    default void shoot(Projectile projectile, double xd, double yd, double zd, float pow, float uncertainty) {
        projectile.shoot(xd, yd, zd, pow, uncertainty);
    }

    record DispenseConfig(ProjectileItem.PositionFunction positionFunction, float uncertainty, float power, OptionalInt overrideDispenseEvent) {
        public static final ProjectileItem.DispenseConfig DEFAULT = builder().build();

        public static ProjectileItem.DispenseConfig.Builder builder() {
            return new ProjectileItem.DispenseConfig.Builder();
        }

        public static class Builder {
            private ProjectileItem.PositionFunction positionFunction = (source, direction) -> DispenserBlock.getDispensePosition(
                source, 0.7, new Vec3(0.0, 0.1, 0.0)
            );
            private float uncertainty = 6.0F;
            private float power = 1.1F;
            private OptionalInt overrideDispenseEvent = OptionalInt.empty();

            public ProjectileItem.DispenseConfig.Builder positionFunction(ProjectileItem.PositionFunction positionFunction) {
                this.positionFunction = positionFunction;
                return this;
            }

            public ProjectileItem.DispenseConfig.Builder uncertainty(float uncertainty) {
                this.uncertainty = uncertainty;
                return this;
            }

            public ProjectileItem.DispenseConfig.Builder power(float power) {
                this.power = power;
                return this;
            }

            public ProjectileItem.DispenseConfig.Builder overrideDispenseEvent(int dispenseEvent) {
                this.overrideDispenseEvent = OptionalInt.of(dispenseEvent);
                return this;
            }

            public ProjectileItem.DispenseConfig build() {
                return new ProjectileItem.DispenseConfig(this.positionFunction, this.uncertainty, this.power, this.overrideDispenseEvent);
            }
        }
    }

    @FunctionalInterface
    interface PositionFunction {
        Position getDispensePosition(final BlockSource source, final Direction direction);
    }
}

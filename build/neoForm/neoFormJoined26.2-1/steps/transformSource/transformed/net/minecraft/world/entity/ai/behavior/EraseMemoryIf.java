package net.minecraft.world.entity.ai.behavior;

import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class EraseMemoryIf {
    public static <E extends LivingEntity> BehaviorControl<E> create(Predicate<E> predicate, MemoryModuleType<?> memoryType) {
        return BehaviorBuilder.create(i -> i.group(i.present(memoryType)).apply(i, memory -> (level, body, timestamp) -> {
            if (predicate.test(body)) {
                memory.erase();
                return true;
            } else {
                return false;
            }
        }));
    }
}

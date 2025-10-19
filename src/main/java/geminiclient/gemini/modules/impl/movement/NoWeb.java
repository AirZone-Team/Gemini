package geminiclient.gemini.modules.impl.movement;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.BlockingEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.world.level.block.Blocks;

public class NoWeb extends Module {
    public NoWeb() {
        super("NoWeb", ModuleEnum.Movement);
    }

    @EventTarget
    public void onBlocking(BlockingEvent event) {
        if (event.getBlockState().getBlock() == Blocks.COBWEB) {
            event.setCancelled(true);
        }
    }
}

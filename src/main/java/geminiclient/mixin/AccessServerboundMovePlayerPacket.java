package geminiclient.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMovePlayerPacket.class)
public interface AccessServerboundMovePlayerPacket {
    @Accessor
    double getX();
    @Accessor
    double getY();
    @Accessor
    double getZ();
    @Accessor("x")
    public void setX(double x);
    @Accessor("y")
    public void setY(double y);
    @Accessor("z")
    public void setZ(double z);
    @Accessor("onGround")
    public void setOnGround(boolean onGround);
}

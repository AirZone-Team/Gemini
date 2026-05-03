package geminiclient.gemini.modules.impl.player.nofalls;

import geminiclient.gemini.event.events.impl.PacketEvent;
import geminiclient.gemini.modules.impl.Mode;
import geminiclient.mixin.AccessServerboundMovePlayerPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public class SpoofLandingNoFall extends Mode {
    public SpoofLandingNoFall() {
        super("SpoofLanding");
    }
    private final Vec3 vec3 = new Vec3(1337.0,0.0,1337.0);

    private double prevFallDistance = 0.0;

    private boolean prevOnGround = false;

    private boolean flag = false;

    @Override
    public void onPacket(PacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (mc.player == null)
            return;

        if (packet instanceof ServerboundMovePlayerPacket) {
            if ((((ServerboundMovePlayerPacket) packet).isOnGround() && !prevOnGround && prevFallDistance >= 3.0)) {
                flag = true;
                double dx = vec3.x;
                double dy = vec3.y;
                double dz = vec3.z;
                ((AccessServerboundMovePlayerPacket) mc).setX(dx);
                ((AccessServerboundMovePlayerPacket) mc).setY(dy);
                ((AccessServerboundMovePlayerPacket) mc).setZ(dz);
                ((AccessServerboundMovePlayerPacket) mc).setOnGround(false);
                mc.player.fallDistance = 0.0;
            }
            prevOnGround = ((ServerboundMovePlayerPacket) packet).isOnGround();
            prevFallDistance = mc.player.fallDistance;
        }

        if (flag && mc.player.onGround()) {
            flag = false;
        }
    }
}

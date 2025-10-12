package geminiclient.gemini.modules.impl.player.nofalls;

import geminiclient.gemini.event.events.impl.PacketEvent;
import geminiclient.gemini.modules.impl.Mode;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public class SpoofLandingNoFall extends Mode {
    public SpoofLandingNoFall() {
        super("SpoofLanding");
    }
    private Vec3 vec3 = new Vec3(1337.0,0.0,1337.0);

    private double prevFallDistance = 0.0;

    private boolean prevOnGround = false;

    private boolean flag = false;

    @Override
    public void onPacket(PacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (mc.player == null)
            return;

        if (packet instanceof ServerboundMovePlayerPacket) {
            if (((ServerboundMovePlayerPacket) packet).onGround && !prevOnGround && prevFallDistance >= 3.0) {
                flag = true;
                double dx = vec3.x;
                double dy = vec3.y;
                double dz = vec3.z;
                ((ServerboundMovePlayerPacket) packet).x = dx;
                ((ServerboundMovePlayerPacket) packet).y = dy;
                ((ServerboundMovePlayerPacket) packet).z = dz;
                ((ServerboundMovePlayerPacket) packet).onGround = false;
                mc.player.fallDistance = 0.0;
            }
            prevOnGround = ((ServerboundMovePlayerPacket) packet).onGround;
            prevFallDistance = mc.player.fallDistance;
        }
    }
}

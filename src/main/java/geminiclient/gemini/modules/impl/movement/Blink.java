package geminiclient.gemini.modules.impl.movement;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.PacketEvent;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.RenderUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.phys.AABB;

import java.util.concurrent.LinkedBlockingDeque;

public class Blink extends Module {
    public Blink() {
        super("Blink", ModuleEnum.Movement);
    }

    private final LinkedBlockingDeque<Packet<?>> packets = new LinkedBlockingDeque<>();
    public static LinkedBlockingDeque<Packet<?>> connP = new LinkedBlockingDeque<>();

    /**
     * 模块启用瞬间玩家所在位置的碰撞箱，用于在原地渲染一个人物大小的 3D 矩形
     * （显示服务器眼中玩家所处的位置）。模块关闭时置空，矩形随之消失。
     */
    private AABB ghostBox;

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.level == null) {
            if (!packets.isEmpty()) {
                packets.clear();
            }
            return;
        }

        Packet<?> packet = event.getPacket();

        // 拦截玩家运动与行为相关的数据包：移动（含潜行/疾跑状态）、骑乘移动、
        // 玩家指令（疾跑/潜行/停止睡觉）、按键输入、方块/物品行为、手臂挥动、
        // 实体交互、使用物品、热键栏切换、能力变更与划船。全部暂存，禁用时按
        // 原始顺序一次性补发给服务器，还原 Blink 期间玩家的一整套动作。
        if (isPlayerPacket(packet)) {
            packets.add(packet);
            event.setCancelled(true);
        }
    }

    private static boolean isPlayerPacket(Packet<?> packet) {
        return packet instanceof ServerboundMovePlayerPacket
                || packet instanceof ServerboundMoveVehiclePacket
                || packet instanceof ServerboundPlayerCommandPacket
                || packet instanceof ServerboundPlayerInputPacket
                || packet instanceof ServerboundPlayerActionPacket
                || packet instanceof ServerboundSwingPacket
                || packet instanceof ServerboundInteractPacket
                || packet instanceof ServerboundUseItemPacket
                || packet instanceof ServerboundUseItemOnPacket
                || packet instanceof ServerboundSetCarriedItemPacket
                || packet instanceof ServerboundPlayerAbilitiesPacket
                || packet instanceof ServerboundPaddleBoatPacket;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (ghostBox == null || mc.level == null) return;

        RenderUtils.drawFilledBox(ghostBox, 0x40FFFFFF);
        RenderUtils.drawOutlineBox(ghostBox, 0xCCFFFFFF);
    }

    @Override
    public void onEnabled() {
        if (mc.player != null) {
            ghostBox = mc.player.getBoundingBox();
        }
    }

    @Override
    public void onDisabled() {
        ghostBox = null;
        if (mc.getConnection() == null)
            return;

        if (!packets.isEmpty()) {
            for (Packet<?> packet : packets) {
                mc.getConnection().send(packet);
                packets.remove(packet);
            }
        }
    }
}

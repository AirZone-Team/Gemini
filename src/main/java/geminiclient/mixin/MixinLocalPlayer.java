package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.EventTypes;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.SendPositionEvent;
import geminiclient.gemini.event.events.impl.SlowDownEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends AbstractClientPlayer {
    public MixinLocalPlayer(ClientLevel clientLevel, ClientPacketListener connection) {
        super(clientLevel, connection.getLocalGameProfile());
    }

    @Shadow
    @Final
    public ClientPacketListener connection;

    @Shadow
    protected abstract void sendIsSprintingIfNeeded();

    @Shadow
    protected abstract boolean isControlledCamera();

    @Shadow
    private double xLast;
    @Shadow
    private double yLast;
    @Shadow
    private double zLast;
    @Shadow
    private float yRotLast;
    @Shadow
    private float xRotLast;
    @Shadow
    private boolean lastOnGround;
    @Shadow
    private boolean lastHorizontalCollision;
    @Shadow
    private int positionReminder;
    @Shadow
    private boolean autoJumpEnabled = true;
    @Shadow
    @Final
    protected Minecraft minecraft;

    @Shadow
    public abstract boolean isMovingSlowly();

    @Shadow
    private static Vec2 modifyInputSpeedForSquareMovement(Vec2 moveVector) {
        return null;
    }

    @Shadow public abstract boolean isLocalPlayer();

    @Unique
    Vec2 gemini$move;

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V", shift = At.Shift.BEFORE, ordinal = 0))
    private void registerUpdateEvent(CallbackInfo ci) {
        Gemini.eventManager.post(EventTypes.UPDATE, new UpdateEvent());
    }

    /**
     * @author XeContrast
     * @reason callMotion
     */
    @Overwrite
    private void sendPosition() {
        MotionEvent motionEvent = new MotionEvent(this.position(), this.getYRot(), this.getXRot(), this.onGround(),
                this.horizontalCollision, TimeEnum.Pre);
        Gemini.eventManager.post(EventTypes.MOTION, motionEvent);

        this.sendIsSprintingIfNeeded();
        if (this.isControlledCamera()) {
            double d0 = motionEvent.getX() - this.xLast;
            double d1 = motionEvent.getY() - this.yLast;
            double d2 = motionEvent.getZ() - this.zLast;
            double d3 = (double) (motionEvent.getyRot() - this.yRotLast);
            double d4 = (double) (motionEvent.getxRot() - this.xRotLast);
            ++this.positionReminder;
            boolean flag = Mth.lengthSquared(d0, d1, d2) > Mth.square(2.0E-4) || this.positionReminder >= 20;
            boolean flag1 = d3 != (double) 0.0F || d4 != (double) 0.0F;
            if (flag && flag1) {
                this.connection.send(new ServerboundMovePlayerPacket.PosRot(this.position(), motionEvent.getyRot(),
                        motionEvent.getxRot(), motionEvent.isOnGround(), motionEvent.isHorizontalCollision()));
            } else if (flag) {
                this.connection.send(new ServerboundMovePlayerPacket.Pos(
                        new Vec3(motionEvent.getX(), motionEvent.getY(), motionEvent.getZ()), motionEvent.isOnGround(),
                        motionEvent.isHorizontalCollision()));
            } else if (flag1) {
                this.connection.send(new ServerboundMovePlayerPacket.Rot(motionEvent.getyRot(), motionEvent.getxRot(),
                        motionEvent.isOnGround(), motionEvent.isHorizontalCollision()));
            } else if (this.lastOnGround != motionEvent.isOnGround()
                    || this.lastHorizontalCollision != motionEvent.isHorizontalCollision()) {
                this.connection.send(new ServerboundMovePlayerPacket.StatusOnly(motionEvent.isOnGround(),
                        motionEvent.isHorizontalCollision()));
            }

            if (flag) {
                this.xLast = motionEvent.getX();
                this.yLast = motionEvent.getY();
                this.zLast = motionEvent.getZ();
                this.positionReminder = 0;
            }

            if (flag1) {
                this.yRotLast = motionEvent.getyRot();
                this.xRotLast = motionEvent.getxRot();
            }

            this.lastOnGround = motionEvent.isOnGround();
            this.lastHorizontalCollision = motionEvent.isHorizontalCollision();
            this.autoJumpEnabled = (Boolean) this.minecraft.options.autoJump().get();
        }
        MotionEvent motionEvent1 = new MotionEvent(this.getYRot(), this.getXRot(), TimeEnum.Post);
        Gemini.eventManager.post(EventTypes.MOTION, motionEvent1);

    }

    @Inject(method = "modifyInput", at = @At("HEAD"))
    public void setMove(Vec2 moveVector, CallbackInfoReturnable<Vec2> cir) {
        this.gemini$move = moveVector;
    }

    /**
     * @author XeContrst
     * @reason 操您妈的Mixin怎么这么难用
     */
    @Overwrite
    private Vec2 modifyInput(Vec2 moveVector) {
        if (moveVector.lengthSquared() == 0.0F) {
            return moveVector;
        } else {
            Vec2 vec2 = moveVector.scale(0.98F);
            if (this.isUsingItem() && !this.isPassenger()) {
                SlowDownEvent slowDownEvent = new SlowDownEvent(0.2f);
                Gemini.eventManager.post(EventTypes.SLOW_DOWN, slowDownEvent);
                vec2 = vec2.scale(slowDownEvent.getFactor());
            }

            if (this.isMovingSlowly()) {
                float f = (float) this.getAttributeValue(Attributes.SNEAKING_SPEED);
                vec2 = vec2.scale(f);
            }

            return modifyInputSpeedForSquareMovement(vec2);
        }
    }
}

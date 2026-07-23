package geminiclient.gemini.event;

import geminiclient.gemini.event.events.impl.AttackEvent;
import geminiclient.gemini.event.events.impl.AttackSlowDownEvent;
import geminiclient.gemini.event.events.impl.BlockingEvent;
import geminiclient.gemini.event.events.impl.ChatEvent;
import geminiclient.gemini.event.events.impl.EntityRemoveEvent;
import geminiclient.gemini.event.events.impl.KeyInputEvent;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.MoveInputEvent;
import geminiclient.gemini.event.events.impl.PacketEvent;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.RotationAnimationEvent;
import geminiclient.gemini.event.events.impl.ShutdownEvent;
import geminiclient.gemini.event.events.impl.SlowDownEvent;
import geminiclient.gemini.event.events.impl.StrafeEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.event.events.impl.moveFixEvent.AttackYawEvent;
import geminiclient.gemini.event.events.impl.moveFixEvent.FallFlyingEvent;
import geminiclient.gemini.event.events.impl.moveFixEvent.JumpEvent;
import geminiclient.gemini.event.events.impl.moveFixEvent.RayTraceEvent;
import geminiclient.gemini.event.events.impl.moveFixEvent.UseItemRaytraceEvent;

/** Event keys cached by producers; no key lookup occurs while publishing. */
public final class EventTypes {
    public static final EventType<AttackEvent> ATTACK = EventType.of(AttackEvent.class);
    public static final EventType<AttackSlowDownEvent> ATTACK_SLOW_DOWN = EventType.of(AttackSlowDownEvent.class);
    public static final EventType<BlockingEvent> BLOCKING = EventType.of(BlockingEvent.class);
    public static final EventType<ChatEvent> CHAT = EventType.of(ChatEvent.class);
    public static final EventType<EntityRemoveEvent> ENTITY_REMOVE = EventType.of(EntityRemoveEvent.class);
    public static final EventType<KeyInputEvent> KEY_INPUT = EventType.of(KeyInputEvent.class);
    public static final EventType<MotionEvent> MOTION = EventType.of(MotionEvent.class);
    public static final EventType<MoveInputEvent> MOVE_INPUT = EventType.of(MoveInputEvent.class);
    public static final EventType<PacketEvent> PACKET = EventType.of(PacketEvent.class);
    public static final EventType<Render2DEvent> RENDER_2D = EventType.of(Render2DEvent.class);
    public static final EventType<Render3DEvent> RENDER_3D = EventType.of(Render3DEvent.class);
    public static final EventType<RotationAnimationEvent> ROTATION_ANIMATION = EventType.of(RotationAnimationEvent.class);
    public static final EventType<ShutdownEvent> SHUTDOWN = EventType.of(ShutdownEvent.class);
    public static final EventType<SlowDownEvent> SLOW_DOWN = EventType.of(SlowDownEvent.class);
    public static final EventType<StrafeEvent> STRAFE = EventType.of(StrafeEvent.class);
    public static final EventType<UpdateEvent> UPDATE = EventType.of(UpdateEvent.class);
    public static final EventType<AttackYawEvent> ATTACK_YAW = EventType.of(AttackYawEvent.class);
    public static final EventType<FallFlyingEvent> FALL_FLYING = EventType.of(FallFlyingEvent.class);
    public static final EventType<JumpEvent> JUMP = EventType.of(JumpEvent.class);
    public static final EventType<RayTraceEvent> RAY_TRACE = EventType.of(RayTraceEvent.class);
    public static final EventType<UseItemRaytraceEvent> USE_ITEM_RAY_TRACE = EventType.of(UseItemRaytraceEvent.class);

    private EventTypes() {}
}

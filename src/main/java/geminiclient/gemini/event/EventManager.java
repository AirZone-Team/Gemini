package geminiclient.gemini.event;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.impl.Event;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Allocation-free event dispatcher optimized for the publish path.
 *
 * <p>Reflection and listener linking happen only while an owner is registered.
 * Publishing is an integer-indexed array lookup followed by direct interface
 * calls over a compact immutable array.</p>
 */
public final class EventManager {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodType ERASED_LISTENER_METHOD =
            MethodType.methodType(void.class, Event.class);
    private static final Dispatch[] EMPTY_TABLE = new Dispatch[0];
    private static final ClassValue<ListenerFactory[]> LISTENER_FACTORIES = new ClassValue<>() {
        @Override
        protected ListenerFactory[] computeValue(Class<?> ownerClass) {
            ArrayList<ListenerFactory> factories = new ArrayList<>();
            for (Method method : ownerClass.getDeclaredMethods()) {
                EventTarget target = method.getAnnotation(EventTarget.class);
                if (target == null) {
                    continue;
                }

                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length != 1
                        || !Event.class.isAssignableFrom(parameters[0])
                        || method.getReturnType() != void.class) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Class<? extends Event> eventClass = (Class<? extends Event>) parameters[0];
                try {
                    factories.add(createFactory(ownerClass, method, eventClass, target.value()));
                } catch (Throwable throwable) {
                    System.err.println("EventManager: cannot link " + ownerClass.getName()
                            + '#' + method.getName());
                    throwable.printStackTrace();
                }
            }
            return factories.toArray(ListenerFactory[]::new);
        }
    };

    /** Published with one volatile write after a registration change. */
    private volatile Dispatch[] dispatchTable = EMPTY_TABLE;

    /** Cold-path state, guarded by this manager's monitor. */
    private final Map<EventType<?>, List<Handler>> registry = new HashMap<>();
    private final IdentityHashMap<Object, Handler[]> objectHandlers = new IdentityHashMap<>();

    /**
     * Registers every valid {@link EventTarget} method declared by {@code owner}.
     * Registering the same object twice is a no-op.
     */
    public synchronized void register(Object owner) {
        if (owner == null || objectHandlers.containsKey(owner)) {
            return;
        }

        ArrayList<Handler> collected = new ArrayList<>();
        HashSet<EventType<?>> changedTypes = new HashSet<>();

        for (ListenerFactory factory : LISTENER_FACTORIES.get(owner.getClass())) {
            try {
                Handler handler = new Handler(
                        factory.bind(owner), owner, factory.type, factory.priority);
                registry.computeIfAbsent(factory.type, ignored -> new ArrayList<>()).add(handler);
                collected.add(handler);
                changedTypes.add(factory.type);
            } catch (Throwable throwable) {
                System.err.println("EventManager: cannot bind " + owner.getClass().getName()
                        + '#' + factory.methodName);
                throwable.printStackTrace();
            }
        }

        if (!collected.isEmpty()) {
            objectHandlers.put(owner, collected.toArray(Handler[]::new));
            publish(changedTypes);
        }
    }

    /** Removes all listeners belonging to {@code owner}. */
    public synchronized void unregister(Object owner) {
        Handler[] handlers = objectHandlers.remove(owner);
        if (handlers == null) {
            return;
        }

        HashSet<EventType<?>> changedTypes = new HashSet<>();
        for (Handler handler : handlers) {
            List<Handler> registered = registry.get(handler.type);
            if (registered == null) {
                continue;
            }

            registered.remove(handler);
            if (registered.isEmpty()) {
                registry.remove(handler.type);
            }
            changedTypes.add(handler.type);
        }
        publish(changedTypes);
    }

    /**
     * Publishes an event without hashing, reflection, iteration objects, or
     * per-call allocation. The type/event pairing is checked by the compiler.
     */
    public <T extends Event> T post(EventType<T> type, T event) {
        Dispatch[] table = dispatchTable;
        int typeId = type.id();
        if (typeId >= table.length) {
            return event;
        }

        Dispatch dispatch = table[typeId];
        if (dispatch == null) {
            return event;
        }

        EventListener[] listeners = dispatch.listeners;
        for (int index = 0, length = listeners.length; index < length; index++) {
            try {
                listeners[index].invoke(event);
            } catch (Throwable throwable) {
                reportFailure(type, dispatch.owners[index], throwable);
            }
        }
        return event;
    }

    private static ListenerFactory createFactory(
            Class<?> ownerClass,
            Method method,
            Class<? extends Event> eventClass,
            int priority
    ) throws Throwable {
        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(ownerClass, LOOKUP);
        MethodHandle implementation = privateLookup.unreflect(method);
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        MethodType factoryType = isStatic
                ? MethodType.methodType(EventListener.class)
                : MethodType.methodType(EventListener.class, ownerClass);

        CallSite factory = LambdaMetafactory.metafactory(
                privateLookup,
                "invoke",
                factoryType,
                ERASED_LISTENER_METHOD,
                implementation,
                MethodType.methodType(void.class, eventClass)
        );
        MethodHandle target = factory.getTarget();
        if (!isStatic) {
            target = target.asType(MethodType.methodType(EventListener.class, Object.class));
        }
        return new ListenerFactory(
                target, EventType.of(eventClass), priority, method.getName(), isStatic);
    }

    /** Rebuilds only the event types affected by a registration change. */
    private void publish(Set<EventType<?>> changedTypes) {
        if (changedTypes.isEmpty()) {
            return;
        }

        Dispatch[] previous = dispatchTable;
        int requiredLength = previous.length;
        for (EventType<?> type : changedTypes) {
            requiredLength = Math.max(requiredLength, type.id() + 1);
        }

        Dispatch[] next = Arrays.copyOf(previous, requiredLength);
        for (EventType<?> type : changedTypes) {
            List<Handler> handlers = registry.get(type);
            if (handlers == null || handlers.isEmpty()) {
                next[type.id()] = null;
                continue;
            }

            handlers.sort(Comparator.comparingInt(handler -> handler.priority));
            EventListener[] listeners = new EventListener[handlers.size()];
            Object[] owners = new Object[handlers.size()];
            for (int index = 0; index < handlers.size(); index++) {
                Handler handler = handlers.get(index);
                listeners[index] = handler.listener;
                owners[index] = handler.owner;
            }
            next[type.id()] = new Dispatch(listeners, owners);
        }

        dispatchTable = next;
    }

    private static void reportFailure(EventType<?> type, Object owner, Throwable throwable) {
        System.err.println("EventManager: error in " + type.eventClass().getSimpleName()
                + " -> " + owner.getClass().getSimpleName());
        throwable.printStackTrace();
    }

    private record Handler(
            EventListener listener,
            Object owner,
            EventType<?> type,
            int priority
    ) {}

    private record Dispatch(EventListener[] listeners, Object[] owners) {}

    private record ListenerFactory(
            MethodHandle factory,
            EventType<?> type,
            int priority,
            String methodName,
            boolean isStatic
    ) {
        EventListener bind(Object owner) throws Throwable {
            return isStatic
                    ? (EventListener) factory.invokeExact()
                    : (EventListener) factory.invokeExact(owner);
        }
    }
}

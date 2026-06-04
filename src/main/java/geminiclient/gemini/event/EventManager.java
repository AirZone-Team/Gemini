package geminiclient.gemini.event;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.impl.Event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.*;

public final class EventManager {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodType EVENT_SINK = MethodType.methodType(void.class, Event.class);

    // Hot path: volatile ensures visibility of rebuilt table to all threads
    private volatile Map<Class<? extends Event>, Handler[]> dispatchTable = Collections.emptyMap();

    // Cold-path mutable storage — only touched during register/unregister
    private final Map<Class<? extends Event>, List<Handler>> registry = new HashMap<>();
    private final IdentityHashMap<Object, Handler[]> objectHandlers = new IdentityHashMap<>();

    private static final class Handler {
        final MethodHandle handle;
        final Object owner;
        final Class<? extends Event> eventClass;
        final int priority;

        Handler(MethodHandle handle, Object owner, Class<? extends Event> eventClass, int priority) {
            this.handle = handle;
            this.owner = owner;
            this.eventClass = eventClass;
            this.priority = priority;
        }
    }

    // -------------------------------------------------------------------------
    //  Public API
    // -------------------------------------------------------------------------

    public void register(Object... objects) {
        boolean changed = false;
        for (Object obj : objects) {
            if (registerOne(obj)) changed = true;
        }
        if (changed) rebuild();
    }

    public void unregister(Object obj) {
        Handler[] handlers = objectHandlers.remove(obj);
        if (handlers == null || handlers.length == 0) return;

        for (Handler h : handlers) {
            List<Handler> list = registry.get(h.eventClass);
            if (list != null) {
                list.remove(h);
                if (list.isEmpty()) registry.remove(h.eventClass);
            }
        }
        rebuild();
    }

    public Event call(Event event) {
        Handler[] handlers = dispatchTable.get(event.getClass());
        if (handlers == null) return event;

        for (int i = 0, n = handlers.length; i < n; i++) {
            Handler h = handlers[i];
            try {
                h.handle.invokeExact(event);
            } catch (Throwable t) {
                System.err.println(
                    "EventManager: error in " + event.getClass().getSimpleName() +
                    " -> " + h.owner.getClass().getSimpleName());
                t.printStackTrace();
            }
        }
        return event;
    }

    // -------------------------------------------------------------------------
    //  Internal
    // -------------------------------------------------------------------------

    private boolean registerOne(Object obj) {
        Class<?> clazz = obj.getClass();
        List<Handler> collected = null;

        for (Method method : clazz.getDeclaredMethods()) {
            EventTarget ann = method.getAnnotation(EventTarget.class);
            if (ann == null) continue;

            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1) continue;
            if (!Event.class.isAssignableFrom(params[0])) continue;

            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) params[0];

            try {
                MethodHandle mh = MethodHandles.privateLookupIn(clazz, LOOKUP)
                    .unreflect(method)
                    .bindTo(obj)
                    .asType(EVENT_SINK);

                Handler h = new Handler(mh, obj, eventClass, ann.value());
                registry.computeIfAbsent(eventClass, k -> new ArrayList<>()).add(h);

                if (collected == null) collected = new ArrayList<>();
                collected.add(h);
            } catch (Exception e) {
                System.err.println("EventManager: cannot link " + method.getName());
                e.printStackTrace();
            }
        }

        if (collected != null && !collected.isEmpty()) {
            objectHandlers.put(obj, collected.toArray(new Handler[0]));
            return true;
        }
        return false;
    }

    private void rebuild() {
        Map<Class<? extends Event>, Handler[]> table = new HashMap<>();

        for (Map.Entry<Class<? extends Event>, List<Handler>> e : registry.entrySet()) {
            List<Handler> list = e.getValue();
            list.sort(Comparator.comparingInt(h -> h.priority));
            table.put(e.getKey(), list.toArray(new Handler[0]));
        }

        dispatchTable = table; // volatile write — happens-before for call() reads
    }
}

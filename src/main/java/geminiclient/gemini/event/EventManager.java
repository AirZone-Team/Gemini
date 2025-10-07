package geminiclient.gemini.event;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.impl.Event;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class EventManager {
    // 映射：事件处理方法 -> 事件类型（Class<? extends Event>）
    private final Map<Method, Class<? extends Event>> registeredMethodMap;
    // 映射：事件处理方法 -> 监听器对象
    private final Map<Method, Object> methodObjectMap;
    // 映射：事件类型 -> 具有该事件处理注解的已排序方法列表
    private final Map<Class<? extends Event>, List<Method>> priorityMethodMap;
    // 优化：对象 -> 其注册的方法列表，用于快速注销
    private final Map<Object, List<Method>> objectMethodsMap;

    public EventManager() {
        // 使用 ConcurrentHashMap 保证多线程下的基本操作安全
        registeredMethodMap = new ConcurrentHashMap<>();
        methodObjectMap = new ConcurrentHashMap<>();
        priorityMethodMap = new ConcurrentHashMap<>();
        // 新增的映射
        objectMethodsMap = new ConcurrentHashMap<>();
    }

    /**
     * Registers one or more objects to associate their methods with event annotations and stores them in the event handler.
     *
     * @param obj One or more objects to register.
     */
    public void register(Object... obj) {
        for (Object object : obj) {
            register(object);
        }
    }

    /**
     * Registers an object to associate its methods with event annotations and stores them in the event handler.
     *
     * @param obj The object to register.
     */
    public void register(Object obj) {
        Class<?> clazz = obj.getClass();
        // 仅获取声明的方法，比 getMethods() 性能更高
        Method[] methods = clazz.getDeclaredMethods();
        List<Method> registeredMethodsForObject = new LinkedList<>();

        for (Method method : methods) {
            // **修复 1：严格的类型和注解检查**
            // 1. 检查是否有 EventTarget 注解
            if (!method.isAnnotationPresent(EventTarget.class)) {
                continue;
            }

            // 2. 检查参数数量是否为 1
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != 1) {
                continue;
            }

            Class<?> eventCandidate = paramTypes[0];

            // 3. 检查参数类型是否是 Event 的子类
            if (Event.class.isAssignableFrom(eventCandidate)) {
                @SuppressWarnings("unchecked")
                Class<? extends Event> eventClass = (Class<? extends Event>) eventCandidate;

                registeredMethodMap.put(method, eventClass);
                methodObjectMap.put(method, obj);
                registeredMethodsForObject.add(method); // 记录该对象注册的方法

                // 使用 CopyOnWriteArrayList 保证多线程环境下对列表的读写安全
                // computeIfAbsent 是线程安全的，但列表本身的添加操作也应线程安全
                priorityMethodMap.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(method);
            }
        }

        // **修复 3 的准备工作：只在列表变化时排序**
        // 将新注册的方法加入列表后，立即对列表进行排序
        for (Class<? extends Event> eventClass : priorityMethodMap.keySet()) {
            sortMethods(eventClass);
        }

        // 记录对象及其注册的方法，用于高效注销
        if (!registeredMethodsForObject.isEmpty()) {
            objectMethodsMap.put(obj, registeredMethodsForObject);
        }
    }

    /**
     * 对特定事件类型的方法列表进行排序（按 EventTarget 的值）
     *
     * @param eventClass 事件类型
     */
    private void sortMethods(Class<? extends Event> eventClass) {
        List<Method> methods = priorityMethodMap.get(eventClass);
        if (methods == null || methods.size() <= 1) {
            return;
        }

        // 由于 CopyOnWriteArrayList 不支持直接排序，需要先转为 List，排序后再替换
        List<Method> sortedMethods = methods.stream()
                .sorted(Comparator.comparingInt(method -> {
                    // 确保方法上有 EventTarget 注解，避免 NPE
                    EventTarget priority = method.getAnnotation(EventTarget.class);
                    // 如果没有注解，默认值设为 10 (或任何预期的默认优先级)
                    return (priority != null) ? priority.value() : 10;
                }))
                .collect(Collectors.toList());

        // 替换为排序后的新列表，保持线程安全
        priorityMethodMap.put(eventClass, new CopyOnWriteArrayList<>(sortedMethods));
    }


    /**
     * Unregisters an object, removing its associated methods from the event handler.
     *
     * @param obj The object to unregister.
     */
    public void unregister(Object obj) {
        // **修复 2：使用 objectMethodsMap 进行高效查找和移除**
        List<Method> methodsToRemove = objectMethodsMap.remove(obj);

        if (methodsToRemove == null || methodsToRemove.isEmpty()) {
            return; // 该对象没有注册任何方法
        }

        // 遍历需要移除的方法
        for (Method method : methodsToRemove) {
            Class<? extends Event> eventClass = registeredMethodMap.remove(method);
            methodObjectMap.remove(method);

            if (eventClass != null) {
                List<Method> priorityMethods = priorityMethodMap.get(eventClass);
                if (priorityMethods != null) {
                    priorityMethods.remove(method);
                    // 如果列表变空，可以移除 Map 中的键
                    if (priorityMethods.isEmpty()) {
                        priorityMethodMap.remove(eventClass);
                    }
                    // **修复 3 的准备工作：注销后无需排序，因为移除元素不会改变剩余元素的相对顺序**
                }
            }
        }
    }

    /**
     * Calls the registered methods associated with the provided event, respecting their priorities.
     *
     * @param event The event to call the registered methods for.
     * @return The modified or processed event after calling the methods.
     */
    public Event call(Event event) {
        Class<? extends Event> eventClass = event.getClass();

        // **修复 3：移除重复的排序操作**
        // 由于方法列表在 register 时已经排序，这里直接获取已排序的列表
        List<Method> methods = priorityMethodMap.get(eventClass);

        if (methods != null) {
            for (Method method : methods) {
                Object obj = methodObjectMap.get(method);

                // 检查 obj 是否为 null，以防方法被注销但 map 中残留
                if (obj == null) {
                    continue;
                }

                // 确保可以访问私有方法
                method.setAccessible(true);
                try {
                    method.invoke(obj, event);
                } catch (Exception e) {
                    // 打印详细错误信息，包含是哪个方法/对象调用失败
                    System.err.println("Failed to invoke event method: " + method.getName() + " in object: " + obj.getClass().getName());
                    e.printStackTrace();
                }
            }
        }

        return event;
    }
}
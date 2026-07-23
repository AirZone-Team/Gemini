package net.minecraft.util;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import com.google.common.util.concurrent.MoreExecutors;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.jtracy.TracyClient;
import com.mojang.jtracy.Zone;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceImmutableList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.CharPredicate;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.SuppressForbidden;
import net.minecraft.TracingExecutor;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Util {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_MAX_THREADS = 255;
    private static final int DEFAULT_SAFE_FILE_OPERATION_RETRIES = 10;
    private static final String MAX_THREADS_SYSTEM_PROPERTY = "max.bg.threads";
    private static final TracingExecutor BACKGROUND_EXECUTOR = makeExecutor("Main");
    private static final TracingExecutor IO_POOL = makeIoExecutor("IO-Worker-", false);
    private static final TracingExecutor DOWNLOAD_POOL = makeIoExecutor("Download-", true);
    private static final DateTimeFormatter FILENAME_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);
    public static final int LINEAR_LOOKUP_THRESHOLD = 8;
    private static final Set<String> ALLOWED_UNTRUSTED_LINK_PROTOCOLS = Set.of("http", "https");
    public static final long NANOS_PER_MILLI = 1000000L;
    private static TimeSource.NanoTimeSource timeSource = System::nanoTime;
    private static final TimeSource.NanoTimeSource INDIRECT_TIME_SOURCE = () -> timeSource.getAsLong();
    public static final Ticker TICKER = new Ticker() {
        @Override
        public long read() {
            return Util.timeSource.getAsLong();
        }
    };
    public static final UUID NIL_UUID = new UUID(0L, 0L);
    public static final FileSystemProvider ZIP_FILE_SYSTEM_PROVIDER = FileSystemProvider.installedProviders()
        .stream()
        .filter(p -> p.getScheme().equalsIgnoreCase("jar"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No jar file system provider found"));
    public static final Escaper CONTROL_CHARACTER_ESCAPER = make(Escapers.builder(), escaper -> {
        HexFormat hexFormat = HexFormat.of().withUpperCase();

        for (char c = 0; c <= 255; c++) {
            if (Character.isISOControl(c)) {
                String replacement = switch (c) {
                    case '\u0007' -> "\\a";
                    case '\b' -> "\\b";
                    case '\t' -> "\\t";
                    case '\n' -> "\\n";
                    case '\u000b' -> "\\v";
                    case '\f' -> "\\f";
                    case '\r' -> "\\r";
                    default -> "\\x" + hexFormat.toHexDigits(c, 2);
                };
                escaper.addEscape(c, replacement);
            }
        }

        escaper.addEscape('\\', "\\\\");
    }).build();
    private static Consumer<String> thePauser = msg -> {};

    public static <K, V> Collector<Entry<? extends K, ? extends V>, ?, Map<K, V>> toMap() {
        return Collectors.toMap(Entry::getKey, Entry::getValue);
    }

    public static <T> Collector<T, ?, List<T>> toMutableList() {
        return Collectors.toCollection(Lists::newArrayList);
    }

    public static <T extends Comparable<T>> String getPropertyName(Property<T> key, Object value) {
        return key.getName((T)value);
    }

    public static String makeDescriptionId(String prefix, @Nullable Identifier location) {
        return location == null ? prefix + ".unregistered_sadface" : prefix + "." + location.getNamespace() + "." + location.getPath().replace('/', '.');
    }

    public static TimeSource.NanoTimeSource timeSource() {
        return INDIRECT_TIME_SOURCE;
    }

    public static void setTimeSource(TimeSource.NanoTimeSource timeSource) {
        Util.timeSource = timeSource;
    }

    public static void shutdownTimeSource() {
        long currentTime = timeSource.getAsLong();
        timeSource = TimeSource.constant(currentTime);
    }

    public static long getMillis() {
        return getNanos() / 1000000L;
    }

    public static long getNanos() {
        return timeSource.getAsLong();
    }

    public static long getEpochMillis() {
        return Instant.now().toEpochMilli();
    }

    public static String getFilenameFormattedDateTime() {
        return FILENAME_DATE_TIME_FORMATTER.format(ZonedDateTime.now());
    }

    private static TracingExecutor makeExecutor(String name) {
        int threads = maxAllowedExecutorThreads();
        ExecutorService executor;
        if (threads <= 0) {
            executor = MoreExecutors.newDirectExecutorService();
        } else {
            AtomicInteger workerCount = new AtomicInteger(1);
            executor = new ForkJoinPool(threads, pool -> {
                final String threadName = "Worker-" + name + "-" + workerCount.getAndIncrement();
                ForkJoinWorkerThread thread = new ForkJoinWorkerThread(pool) {
                    @Override
                    protected void onStart() {
                        TracyClient.setThreadName(threadName, name.hashCode());
                        super.onStart();
                    }

                    @Override
                    protected void onTermination(@Nullable Throwable exception) {
                        if (exception != null) {
                            Util.LOGGER.warn("{} died", this.getName(), exception);
                        } else {
                            Util.LOGGER.debug("{} shutdown", this.getName());
                        }

                        super.onTermination(exception);
                    }
                };
                thread.setName(threadName);
                return thread;
            }, Util::onThreadException, true);
        }

        return new TracingExecutor(executor);
    }

    public static int maxAllowedExecutorThreads() {
        return Mth.clamp(Runtime.getRuntime().availableProcessors() - 1, 1, getMaxThreads());
    }

    private static int getMaxThreads() {
        String maxThreadsString = System.getProperty("max.bg.threads");
        if (maxThreadsString != null) {
            try {
                int maxThreads = Integer.parseInt(maxThreadsString);
                if (maxThreads >= 1 && maxThreads <= 255) {
                    return maxThreads;
                }

                LOGGER.error("Wrong {} property value '{}'. Should be an integer value between 1 and {}.", "max.bg.threads", maxThreadsString, 255);
            } catch (NumberFormatException e) {
                LOGGER.error("Could not parse {} property value '{}'. Should be an integer value between 1 and {}.", "max.bg.threads", maxThreadsString, 255);
            }
        }

        return 255;
    }

    public static TracingExecutor backgroundExecutor() {
        return BACKGROUND_EXECUTOR;
    }

    public static TracingExecutor ioPool() {
        return IO_POOL;
    }

    public static TracingExecutor nonCriticalIoPool() {
        return DOWNLOAD_POOL;
    }

    public static void shutdownExecutors() {
        BACKGROUND_EXECUTOR.shutdownAndAwait(3L, TimeUnit.SECONDS);
        IO_POOL.shutdownAndAwait(3L, TimeUnit.SECONDS);
    }

    private static TracingExecutor makeIoExecutor(String prefix, boolean daemon) {
        AtomicInteger workerCount = new AtomicInteger(1);
        return new TracingExecutor(Executors.newCachedThreadPool(runnable -> {
            String name = prefix + workerCount.getAndIncrement();
            Thread thread = new Thread(runnable, name);
            TracyClient.setThreadName(name, prefix.hashCode());
            thread.setDaemon(daemon);
            thread.setUncaughtExceptionHandler(Util::onThreadException);
            return thread;
        }));
    }

    public static void throwAsRuntime(Throwable throwable) {
        throw throwable instanceof RuntimeException runtimeException ? runtimeException : new RuntimeException(throwable);
    }

    private static void onThreadException(Thread thread, Throwable throwable) {
        pauseInIde(throwable);
        if (throwable instanceof CompletionException) {
            throwable = throwable.getCause();
        }

        LOGGER.error("Caught exception in thread {}", thread, throwable);
        CrashReport report;
        if (throwable instanceof ReportedException reportedException) {
            report = reportedException.getReport();
        } else {
            report = CrashReport.forThrowable(throwable, "Exception on worker thread");
        }

        CrashReportCategory threadInfo = report.addCategory("ThreadInfo");
        threadInfo.setDetail("Name", thread.getName());
        BlockableEventLoop.relayDelayCrash(report);
    }

    public static @Nullable Type<?> fetchChoiceType(TypeReference reference, String name) {
        return !SharedConstants.CHECK_DATA_FIXER_SCHEMA ? null : doFetchChoiceType(reference, name);
    }

    private static @Nullable Type<?> doFetchChoiceType(TypeReference reference, String name) {
        Type<?> dataType = null;

        try {
            dataType = DataFixers.getDataFixer()
                .getSchema(DataFixUtils.makeKey(SharedConstants.getCurrentVersion().dataVersion().version()))
                .getChoiceType(reference, name);
        } catch (IllegalArgumentException e) {
            LOGGER.debug("No data fixer registered for {}", name);
            if (SharedConstants.IS_RUNNING_IN_IDE && false) {
                throw e;
            }
        }

        return dataType;
    }

    public static void runNamed(Runnable runnable, String name) {
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            Thread thread = Thread.currentThread();
            String oldName = thread.getName();
            thread.setName(name);

            try (Zone ignored = TracyClient.beginZone(name, SharedConstants.IS_RUNNING_IN_IDE)) {
                runnable.run();
            } finally {
                thread.setName(oldName);
            }
        } else {
            try (Zone ignored = TracyClient.beginZone(name, SharedConstants.IS_RUNNING_IN_IDE)) {
                runnable.run();
            }
        }
    }

    public static <T> String getRegisteredName(Registry<T> registry, T entry) {
        Identifier key = registry.getKey(entry);
        return key == null ? "[unregistered]" : key.toString();
    }

    public static <T> Predicate<T> allOf() {
        return context -> true;
    }

    public static <T> Predicate<T> allOf(Predicate<? super T> condition) {
        return (Predicate<T>)condition;
    }

    public static <T> Predicate<T> allOf(Predicate<? super T> condition1, Predicate<? super T> condition2) {
        return context -> condition1.test(context) && condition2.test(context);
    }

    public static <T> Predicate<T> allOf(Predicate<? super T> condition1, Predicate<? super T> condition2, Predicate<? super T> condition3) {
        return context -> condition1.test(context) && condition2.test(context) && condition3.test(context);
    }

    public static <T> Predicate<T> allOf(
        Predicate<? super T> condition1, Predicate<? super T> condition2, Predicate<? super T> condition3, Predicate<? super T> condition4
    ) {
        return context -> condition1.test(context) && condition2.test(context) && condition3.test(context) && condition4.test(context);
    }

    public static <T> Predicate<T> allOf(
        Predicate<? super T> condition1,
        Predicate<? super T> condition2,
        Predicate<? super T> condition3,
        Predicate<? super T> condition4,
        Predicate<? super T> condition5
    ) {
        return context -> condition1.test(context)
            && condition2.test(context)
            && condition3.test(context)
            && condition4.test(context)
            && condition5.test(context);
    }

    @SafeVarargs
    public static <T> Predicate<T> allOf(Predicate<? super T>... conditions) {
        return context -> {
            for (Predicate<? super T> entry : conditions) {
                if (!entry.test(context)) {
                    return false;
                }
            }

            return true;
        };
    }

    public static <T> Predicate<T> allOf(List<? extends Predicate<? super T>> conditions) {
        return switch (conditions.size()) {
            case 0 -> allOf();
            case 1 -> allOf((Predicate<? super T>)conditions.get(0));
            case 2 -> allOf((Predicate<? super T>)conditions.get(0), (Predicate<? super T>)conditions.get(1));
            case 3 -> allOf((Predicate<? super T>)conditions.get(0), (Predicate<? super T>)conditions.get(1), (Predicate<? super T>)conditions.get(2));
            case 4 -> allOf(
                (Predicate<? super T>)conditions.get(0),
                (Predicate<? super T>)conditions.get(1),
                (Predicate<? super T>)conditions.get(2),
                (Predicate<? super T>)conditions.get(3)
            );
            case 5 -> allOf(
                (Predicate<? super T>)conditions.get(0),
                (Predicate<? super T>)conditions.get(1),
                (Predicate<? super T>)conditions.get(2),
                (Predicate<? super T>)conditions.get(3),
                (Predicate<? super T>)conditions.get(4)
            );
            default -> {
                Predicate<? super T>[] conditionsCopy = conditions.toArray(Predicate[]::new);
                yield allOf(conditionsCopy);
            }
        };
    }

    public static <T> Predicate<T> anyOf() {
        return context -> false;
    }

    public static <T> Predicate<T> anyOf(Predicate<? super T> condition1) {
        return (Predicate<T>)condition1;
    }

    public static <T> Predicate<T> anyOf(Predicate<? super T> condition1, Predicate<? super T> condition2) {
        return context -> condition1.test(context) || condition2.test(context);
    }

    public static <T> Predicate<T> anyOf(Predicate<? super T> condition1, Predicate<? super T> condition2, Predicate<? super T> condition3) {
        return context -> condition1.test(context) || condition2.test(context) || condition3.test(context);
    }

    public static <T> Predicate<T> anyOf(
        Predicate<? super T> condition1, Predicate<? super T> condition2, Predicate<? super T> condition3, Predicate<? super T> condition4
    ) {
        return context -> condition1.test(context) || condition2.test(context) || condition3.test(context) || condition4.test(context);
    }

    public static <T> Predicate<T> anyOf(
        Predicate<? super T> condition1,
        Predicate<? super T> condition2,
        Predicate<? super T> condition3,
        Predicate<? super T> condition4,
        Predicate<? super T> condition5
    ) {
        return context -> condition1.test(context)
            || condition2.test(context)
            || condition3.test(context)
            || condition4.test(context)
            || condition5.test(context);
    }

    @SafeVarargs
    public static <T> Predicate<T> anyOf(Predicate<? super T>... conditions) {
        return context -> {
            for (Predicate<? super T> entry : conditions) {
                if (entry.test(context)) {
                    return true;
                }
            }

            return false;
        };
    }

    public static <T> Predicate<T> anyOf(List<? extends Predicate<? super T>> conditions) {
        return switch (conditions.size()) {
            case 0 -> anyOf();
            case 1 -> anyOf((Predicate<? super T>)conditions.get(0));
            case 2 -> anyOf((Predicate<? super T>)conditions.get(0), (Predicate<? super T>)conditions.get(1));
            case 3 -> anyOf((Predicate<? super T>)conditions.get(0), (Predicate<? super T>)conditions.get(1), (Predicate<? super T>)conditions.get(2));
            case 4 -> anyOf(
                (Predicate<? super T>)conditions.get(0),
                (Predicate<? super T>)conditions.get(1),
                (Predicate<? super T>)conditions.get(2),
                (Predicate<? super T>)conditions.get(3)
            );
            case 5 -> anyOf(
                (Predicate<? super T>)conditions.get(0),
                (Predicate<? super T>)conditions.get(1),
                (Predicate<? super T>)conditions.get(2),
                (Predicate<? super T>)conditions.get(3),
                (Predicate<? super T>)conditions.get(4)
            );
            default -> {
                Predicate<? super T>[] conditionsCopy = conditions.toArray(Predicate[]::new);
                yield anyOf(conditionsCopy);
            }
        };
    }

    public static <T> boolean isSymmetrical(int width, int height, List<T> ingredients) {
        if (width == 1) {
            return true;
        }

        int centerX = width / 2;

        for (int y = 0; y < height; y++) {
            for (int leftX = 0; leftX < centerX; leftX++) {
                int rightX = width - 1 - leftX;
                T left = ingredients.get(leftX + y * width);
                T right = ingredients.get(rightX + y * width);
                if (!left.equals(right)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static int growByHalf(int currentSize, int minimalNewSize) {
        return (int)Math.max(Math.min((long)currentSize + (currentSize >> 1), 2147483639L), minimalNewSize);
    }

    @SuppressForbidden(reason = "Intentional use of default locale for user-visible date")
    public static DateTimeFormatter localizedDateFormatter(FormatStyle formatStyle) {
        return DateTimeFormatter.ofLocalizedDateTime(formatStyle);
    }

    public static ThreadInfo[] dumpThreadInfo() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        return threadMXBean.dumpAllThreads(threadMXBean.isObjectMonitorUsageSupported(), threadMXBean.isSynchronizerUsageSupported());
    }

    public static Util.OS getPlatform() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return Util.OS.WINDOWS;
        } else if (osName.contains("mac")) {
            return Util.OS.OSX;
        } else if (osName.contains("solaris")) {
            return Util.OS.SOLARIS;
        } else if (osName.contains("sunos")) {
            return Util.OS.SOLARIS;
        } else if (osName.contains("linux")) {
            return Util.OS.LINUX;
        } else {
            return osName.contains("unix") ? Util.OS.LINUX : Util.OS.UNKNOWN;
        }
    }

    public static boolean isAarch64() {
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        return arch.equals("aarch64");
    }

    public static URI parseAndValidateUntrustedUri(String uri) throws URISyntaxException {
        URI parsedUri = new URI(uri);
        String scheme = parsedUri.getScheme();
        if (scheme == null) {
            throw new URISyntaxException(uri, "Missing protocol in URI: " + uri);
        } else {
            String protocol = scheme.toLowerCase(Locale.ROOT);
            if (!ALLOWED_UNTRUSTED_LINK_PROTOCOLS.contains(protocol)) {
                throw new URISyntaxException(uri, "Unsupported protocol in URI: " + uri);
            } else {
                return parsedUri;
            }
        }
    }

    public static <T> T findNextInIterable(Iterable<T> collection, @Nullable T current) {
        Iterator<T> iterator = collection.iterator();
        T first = iterator.next();
        if (current != null) {
            T property = first;

            while (property != current) {
                if (iterator.hasNext()) {
                    property = iterator.next();
                }
            }

            if (iterator.hasNext()) {
                return iterator.next();
            }
        }

        return first;
    }

    public static <T> T findPreviousInIterable(Iterable<T> collection, @Nullable T current) {
        Iterator<T> iterator = collection.iterator();
        T last = null;

        while (iterator.hasNext()) {
            T next = iterator.next();
            if (next == current) {
                if (last == null) {
                    last = iterator.hasNext() ? Iterators.getLast(iterator) : current;
                }
                break;
            }

            last = next;
        }

        return last;
    }

    public static <T> T make(Supplier<T> factory) {
        return factory.get();
    }

    public static <T> T make(T t, Consumer<? super T> consumer) {
        consumer.accept(t);
        return t;
    }

    public static <K extends Enum<K>, V> Map<K, V> makeEnumMap(Class<K> keyType, Function<K, V> function) {
        EnumMap<K, V> map = new EnumMap<>(keyType);

        for (K key : keyType.getEnumConstants()) {
            map.put(key, function.apply(key));
        }

        return map;
    }

    public static <K, V1, V2> Map<K, V2> mapValues(Map<K, V1> map, Function<? super V1, V2> valueMapper) {
        return map.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> valueMapper.apply(e.getValue())));
    }

    public static <K, V1, V2> Map<K, V2> mapValuesLazy(Map<K, V1> map, com.google.common.base.Function<V1, V2> valueMapper) {
        return Maps.transformValues(map, valueMapper);
    }

    public static <T extends Enum<T>> Set<T> allOfEnumExcept(T value) {
        return EnumSet.complementOf(EnumSet.of(value));
    }

    public static <V> CompletableFuture<List<V>> sequence(List<? extends CompletableFuture<V>> futures) {
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        if (futures.size() == 1) {
            return futures.getFirst().thenApply(ObjectLists::singleton);
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return all.thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
    }

    public static <V> CompletableFuture<List<V>> sequenceFailFast(List<? extends CompletableFuture<? extends V>> futures) {
        CompletableFuture<List<V>> failureFuture = new CompletableFuture<>();
        return fallibleSequence(futures, failureFuture::completeExceptionally).applyToEither(failureFuture, Function.identity());
    }

    public static <V> CompletableFuture<List<V>> sequenceFailFastAndCancel(List<? extends CompletableFuture<? extends V>> futures) {
        CompletableFuture<List<V>> failureFuture = new CompletableFuture<>();
        return fallibleSequence(futures, exception -> {
            if (failureFuture.completeExceptionally(exception)) {
                for (CompletableFuture<? extends V> future : futures) {
                    future.cancel(true);
                }
            }
        }).applyToEither(failureFuture, Function.identity());
    }

    private static <V> CompletableFuture<List<V>> fallibleSequence(List<? extends CompletableFuture<? extends V>> futures, Consumer<Throwable> failureHandler) {
        ObjectArrayList<V> results = new ObjectArrayList<>();
        results.size(futures.size());
        CompletableFuture<?>[] decoratedFutures = new CompletableFuture[futures.size()];

        for (int i = 0; i < futures.size(); i++) {
            int index = i;
            decoratedFutures[i] = futures.get(i).whenComplete((result, exception) -> {
                if (exception != null) {
                    failureHandler.accept(exception);
                } else {
                    results.set(index, (V)result);
                }
            });
        }

        return CompletableFuture.allOf(decoratedFutures).thenApply(nothing -> results);
    }

    public static <T> Optional<T> ifElse(Optional<T> input, Consumer<T> onTrue, Runnable onFalse) {
        if (input.isPresent()) {
            onTrue.accept(input.get());
        } else {
            onFalse.run();
        }

        return input;
    }

    public static <T> Supplier<T> name(Supplier<T> task, Supplier<String> nameGetter) {
        if (SharedConstants.DEBUG_NAMED_RUNNABLES) {
            final String name = nameGetter.get();
            return new Supplier<T>() {
                @Override
                public T get() {
                    return task.get();
                }

                @Override
                public String toString() {
                    return name;
                }
            };
        } else {
            return task;
        }
    }

    public static Runnable name(Runnable task, Supplier<String> nameGetter) {
        if (SharedConstants.DEBUG_NAMED_RUNNABLES) {
            final String name = nameGetter.get();
            return new Runnable() {
                @Override
                public void run() {
                    task.run();
                }

                @Override
                public String toString() {
                    return name;
                }
            };
        } else {
            return task;
        }
    }

    public static void logAndPauseIfInIde(String message) {
        LOGGER.error(message);
        if (SharedConstants.IS_RUNNING_WITH_JDWP) {
            doPause(message);
        }
    }

    public static void logAndPauseIfInIde(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
        if (SharedConstants.IS_RUNNING_WITH_JDWP) {
            doPause(message);
        }
    }

    public static <T extends Throwable> T pauseInIde(T t) {
        if (SharedConstants.IS_RUNNING_WITH_JDWP) {
            LOGGER.error("Trying to throw a fatal exception, pausing in IDE", t);
            doPause(t.getMessage());
        }

        return t;
    }

    public static void setPause(Consumer<String> pauseFunction) {
        thePauser = pauseFunction;
    }

    private static void doPause(String message) {
        Instant preLog = Instant.now();
        LOGGER.warn("Did you remember to set a breakpoint here?");
        boolean dontBotherWithPause = Duration.between(preLog, Instant.now()).toMillis() > 500L;
        if (!dontBotherWithPause) {
            thePauser.accept(message);
        }
    }

    public static String describeError(Throwable err) {
        if (err.getCause() != null) {
            return describeError(err.getCause());
        } else {
            return err.getMessage() != null ? err.getMessage() : err.toString();
        }
    }

    public static <T> T getRandom(T[] array, RandomSource random) {
        return array[random.nextInt(array.length)];
    }

    public static int getRandom(int[] array, RandomSource random) {
        return array[random.nextInt(array.length)];
    }

    public static <T> T getRandom(List<T> list, RandomSource random) {
        return list.get(random.nextInt(list.size()));
    }

    public static <T> Optional<T> getRandomSafe(List<T> list, RandomSource random) {
        return list.isEmpty() ? Optional.empty() : Optional.of(getRandom(list, random));
    }

    private static BooleanSupplier createRenamer(Path from, Path to, CopyOption... options) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                try {
                    Files.move(from, to, options);
                    return true;
                } catch (IOException e) {
                    Util.LOGGER.error("Failed to rename", e);
                    return false;
                }
            }

            @Override
            public String toString() {
                return "rename " + from + " to " + to;
            }
        };
    }

    private static BooleanSupplier createDeleter(Path target) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                try {
                    Files.deleteIfExists(target);
                    return true;
                } catch (IOException e) {
                    Util.LOGGER.warn("Failed to delete", e);
                    return false;
                }
            }

            @Override
            public String toString() {
                return "delete old " + target;
            }
        };
    }

    private static BooleanSupplier createFileDeletedCheck(Path target) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return !Files.exists(target);
            }

            @Override
            public String toString() {
                return "verify that " + target + " is deleted";
            }
        };
    }

    private static BooleanSupplier createFileCreatedCheck(Path target) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return Files.isRegularFile(target);
            }

            @Override
            public String toString() {
                return "verify that " + target + " is present";
            }
        };
    }

    private static boolean executeInSequence(BooleanSupplier... operations) {
        for (BooleanSupplier operation : operations) {
            if (!operation.getAsBoolean()) {
                LOGGER.warn("Failed to execute {}", operation);
                return false;
            }
        }

        return true;
    }

    private static boolean runWithRetries(int numberOfRetries, String description, BooleanSupplier... operations) {
        for (int retry = 0; retry < numberOfRetries; retry++) {
            if (executeInSequence(operations)) {
                return true;
            }

            LOGGER.error("Failed to {}, retrying {}/{}", description, retry, numberOfRetries);
        }

        LOGGER.error("Failed to {}, aborting, progress might be lost", description);
        return false;
    }

    public static boolean safeMoveFile(Path fromPath, Path toPath, CopyOption... options) {
        return runWithRetries(10, "move from  " + fromPath + " to " + toPath, createRenamer(fromPath, toPath, options), createFileCreatedCheck(toPath));
    }

    public static void safeReplaceFile(Path targetPath, Path newPath, Path backupPath) {
        safeReplaceOrMoveFile(targetPath, newPath, backupPath, false);
    }

    public static boolean safeReplaceOrMoveFile(Path targetPath, Path newPath, Path backupPath, boolean noRollback) {
        if (Files.exists(targetPath)
            && !runWithRetries(
                10, "create backup " + backupPath, createDeleter(backupPath), createRenamer(targetPath, backupPath), createFileCreatedCheck(backupPath)
            )) {
            return false;
        } else if (!runWithRetries(10, "remove old " + targetPath, createDeleter(targetPath), createFileDeletedCheck(targetPath))) {
            return false;
        } else if (!runWithRetries(10, "replace " + targetPath + " with " + newPath, createRenamer(newPath, targetPath), createFileCreatedCheck(targetPath))
            && !noRollback) {
            runWithRetries(10, "restore " + targetPath + " from " + backupPath, createRenamer(backupPath, targetPath), createFileCreatedCheck(targetPath));
            return false;
        } else {
            return true;
        }
    }

    public static int offsetByCodepoints(String input, int pos, int offset) {
        int length = input.length();
        if (offset >= 0) {
            for (int i = 0; pos < length && i < offset; i++) {
                if (Character.isHighSurrogate(input.charAt(pos++)) && pos < length && Character.isLowSurrogate(input.charAt(pos))) {
                    pos++;
                }
            }
        } else {
            for (int i = offset; pos > 0 && i < 0; i++) {
                pos--;
                if (Character.isLowSurrogate(input.charAt(pos)) && pos > 0 && Character.isHighSurrogate(input.charAt(pos - 1))) {
                    pos--;
                }
            }
        }

        return pos;
    }

    public static Consumer<String> prefix(String prefix, Consumer<String> consumer) {
        return s -> consumer.accept(prefix + s);
    }

    public static DataResult<int[]> fixedSize(IntStream stream, int size) {
        int[] ints = stream.limit(size + 1).toArray();
        if (ints.length != size) {
            Supplier<String> message = () -> "Input is not a list of " + size + " ints";
            return ints.length >= size ? DataResult.error(message, Arrays.copyOf(ints, size)) : DataResult.error(message);
        } else {
            return DataResult.success(ints);
        }
    }

    public static DataResult<long[]> fixedSize(LongStream stream, int size) {
        long[] longs = stream.limit(size + 1).toArray();
        if (longs.length != size) {
            Supplier<String> message = () -> "Input is not a list of " + size + " longs";
            return longs.length >= size ? DataResult.error(message, Arrays.copyOf(longs, size)) : DataResult.error(message);
        } else {
            return DataResult.success(longs);
        }
    }

    public static <T> DataResult<List<T>> fixedSize(List<T> list, int size) {
        if (list.size() != size) {
            Supplier<String> message = () -> "Input is not a list of " + size + " elements";
            return list.size() >= size ? DataResult.error(message, list.subList(0, size)) : DataResult.error(message);
        } else {
            return DataResult.success(list);
        }
    }

    public static void startTimerHackThread() {
        Thread timerThread = new Thread("Timer hack thread") {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(2147483647L);
                    } catch (InterruptedException e) {
                        Util.LOGGER.warn("Timer hack thread interrupted, that really should not happen");
                        return;
                    }
                }
            }
        };
        timerThread.setDaemon(true);
        timerThread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
        timerThread.start();
    }

    public static void copyBetweenDirs(Path sourceDir, Path targetDir, Path sourcePath) throws IOException {
        Path relative = sourceDir.relativize(sourcePath);
        Path target = targetDir.resolve(relative);
        Files.copy(sourcePath, target);
    }

    public static String sanitizeName(String value, CharPredicate isAllowedChar) {
        return value.toLowerCase(Locale.ROOT)
            .chars()
            .mapToObj(c -> isAllowedChar.test((char)c) ? Character.toString((char)c) : "_")
            .collect(Collectors.joining());
    }

    public static <K, V> SingleKeyCache<K, V> singleKeyCache(Function<K, V> computeValueFunction) {
        return new SingleKeyCache<>(computeValueFunction);
    }

    public static <T, R> Function<T, R> memoize(Function<T, R> function) {
        return new Function<T, R>() {
            private final Map<T, R> cache = new ConcurrentHashMap<>();

            @Override
            public R apply(T arg) {
                return this.cache.computeIfAbsent(arg, function);
            }

            @Override
            public String toString() {
                return "memoize/1[function=" + function + ", size=" + this.cache.size() + "]";
            }
        };
    }

    public static <T, U, R> BiFunction<T, U, R> memoize(BiFunction<T, U, R> function) {
        return new BiFunction<T, U, R>() {
            private final Map<Pair<T, U>, R> cache = new ConcurrentHashMap<>();

            @Override
            public R apply(T a, U b) {
                return this.cache.computeIfAbsent(Pair.of(a, b), args -> function.apply(args.getFirst(), args.getSecond()));
            }

            @Override
            public String toString() {
                return "memoize/2[function=" + function + ", size=" + this.cache.size() + "]";
            }
        };
    }

    public static <T> List<T> toShuffledList(Stream<T> stream, RandomSource random) {
        ObjectArrayList<T> result = stream.collect(ObjectArrayList.toList());
        shuffle(result, random);
        return result;
    }

    public static IntArrayList toShuffledList(IntStream stream, RandomSource random) {
        IntArrayList result = IntArrayList.wrap(stream.toArray());
        int size = result.size();

        for (int i = size; i > 1; i--) {
            int swapTo = random.nextInt(i);
            result.set(i - 1, result.set(swapTo, result.getInt(i - 1)));
        }

        return result;
    }

    public static <T> List<T> shuffledCopy(T[] array, RandomSource random) {
        ObjectArrayList<T> copy = new ObjectArrayList<>(array);
        shuffle(copy, random);
        return copy;
    }

    public static <T> List<T> shuffledCopy(ObjectArrayList<T> list, RandomSource random) {
        ObjectArrayList<T> copy = new ObjectArrayList<>(list);
        shuffle(copy, random);
        return copy;
    }

    public static <T> void shuffle(List<T> list, RandomSource random) {
        int size = list.size();

        for (int i = size; i > 1; i--) {
            int swapTo = random.nextInt(i);
            list.set(i - 1, list.set(swapTo, list.get(i - 1)));
        }
    }

    public static <T> CompletableFuture<T> blockUntilDone(Function<Executor, CompletableFuture<T>> task) {
        return blockUntilDone(task, CompletableFuture::isDone);
    }

    public static <T> T blockUntilDone(Function<Executor, T> task, Predicate<T> completionCheck) {
        BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
        T result = task.apply(tasks::add);

        while (!completionCheck.test(result)) {
            try {
                Runnable runnable = tasks.poll(100L, TimeUnit.MILLISECONDS);
                if (runnable != null) {
                    runnable.run();
                }
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted wait");
                break;
            }
        }

        int remainingSize = tasks.size();
        if (remainingSize > 0) {
            LOGGER.warn("Tasks left in queue: {}", remainingSize);
        }

        return result;
    }

    public static <T> ToIntFunction<T> createIndexLookup(List<T> values) {
        int size = values.size();
        if (size < 8) {
            return values::indexOf;
        }

        Object2IntMap<T> lookup = new Object2IntOpenHashMap<>(size);
        lookup.defaultReturnValue(-1);

        for (int i = 0; i < size; i++) {
            lookup.put(values.get(i), i);
        }

        return lookup;
    }

    public static <T> ToIntFunction<T> createIndexIdentityLookup(List<T> values) {
        int size = values.size();
        if (size < 8) {
            ReferenceList<T> referenceLookup = new ReferenceImmutableList<>(values);
            return referenceLookup::indexOf;
        }

        Reference2IntMap<T> lookup = new Reference2IntOpenHashMap<>(size);
        lookup.defaultReturnValue(-1);

        for (int i = 0; i < size; i++) {
            lookup.put(values.get(i), i);
        }

        return lookup;
    }

    public static <A, B> Typed<B> writeAndReadTypedOrThrow(Typed<A> typed, Type<B> newType, UnaryOperator<Dynamic<?>> function) {
        Dynamic<?> dynamic = (Dynamic<?>)typed.write().getOrThrow();
        return readTypedOrThrow(newType, function.apply(dynamic), true);
    }

    public static <T> Typed<T> readTypedOrThrow(Type<T> type, Dynamic<?> dynamic) {
        return readTypedOrThrow(type, dynamic, false);
    }

    public static <T> Typed<T> readTypedOrThrow(Type<T> type, Dynamic<?> dynamic, boolean acceptPartial) {
        DataResult<Typed<T>> result = type.readTyped(dynamic).map(Pair::getFirst);

        try {
            return acceptPartial ? result.getPartialOrThrow(IllegalStateException::new) : result.getOrThrow(IllegalStateException::new);
        } catch (IllegalStateException e) {
            CrashReport report = CrashReport.forThrowable(e, "Reading type");
            CrashReportCategory category = report.addCategory("Info");
            category.setDetail("Data", dynamic);
            category.setDetail("Type", type);
            throw new ReportedException(report);
        }
    }

    public static <T> List<T> copyAndAdd(List<T> list, T element) {
        return ImmutableList.<T>builderWithExpectedSize(list.size() + 1).addAll(list).add(element).build();
    }

    public static <T> List<T> copyAndAdd(List<T> list, T... elements) {
        return ImmutableList.<T>builderWithExpectedSize(list.size() + elements.length).addAll(list).add(elements).build();
    }

    public static <T> List<T> copyAndAdd(T element, List<T> list) {
        return ImmutableList.<T>builderWithExpectedSize(list.size() + 1).add(element).addAll(list).build();
    }

    public static <T> List<T> join(List<T> first, List<T> second) {
        Builder<T> builder = ImmutableList.builderWithExpectedSize(first.size() + second.size());
        builder.addAll(first);
        builder.addAll(second);
        return builder.build();
    }

    public static <T> List<T> join(List<T>... lists) {
        int size = 0;

        for (List<T> list : lists) {
            size += list.size();
        }

        Builder<T> builder = ImmutableList.builderWithExpectedSize(size);

        for (List<T> list : lists) {
            builder.addAll(list);
        }

        return builder.build();
    }

    public static <K, V> Map<K, V> copyAndPut(Map<K, V> map, K key, V value) {
        return ImmutableMap.<K, V>builderWithExpectedSize(map.size() + 1).putAll(map).put(key, value).buildKeepingLast();
    }

    public enum OS {
        LINUX("linux"),
        SOLARIS("solaris"),
        WINDOWS("windows") {
            @Override
            protected String[] getOpenUriArguments(URI uri) {
                return new String[]{"rundll32", "url.dll,FileProtocolHandler", uri.toString()};
            }
        },
        OSX("mac") {
            @Override
            protected String[] getOpenUriArguments(URI uri) {
                return new String[]{"open", uri.toString()};
            }
        },
        UNKNOWN("unknown");

        private final String telemetryName;

        OS(String telemetryName) {
            this.telemetryName = telemetryName;
        }

        public void openUri(URI uri) {
            try {
                Process process = Runtime.getRuntime().exec(this.getOpenUriArguments(uri));
                process.getInputStream().close();
                process.getErrorStream().close();
                process.getOutputStream().close();
            } catch (IOException e) {
                Util.LOGGER.error("Couldn't open location '{}'", uri, e);
            }
        }

        public void openFile(File file) {
            this.openUri(file.toURI());
        }

        public void openPath(Path path) {
            this.openUri(path.toUri());
        }

        protected String[] getOpenUriArguments(URI uri) {
            String string = uri.toString();
            if ("file".equals(uri.getScheme())) {
                string = string.replace("file:", "file://");
            }

            return new String[]{"xdg-open", string};
        }

        public void openUri(String uri) {
            try {
                this.openUri(new URI(uri));
            } catch (URISyntaxException | IllegalArgumentException e) {
                Util.LOGGER.error("Couldn't open uri '{}'", uri, e);
            }
        }

        public String telemetryName() {
            return this.telemetryName;
        }
    }
}

package net.minecraft.client.renderer.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class FeatureRenderDispatcher implements AutoCloseable {
    private final ModelManager modelManager;
    private final AtlasManager atlasManager;
    private final Font font;
    private final GameRenderState gameRenderState;
    private final StagedVertexBuffer stagedVertexBuffer;
    private final FeatureRendererMap featureRenderers = new FeatureRendererMap();
    private final FeatureRenderDispatcher.PreparedFrame preparedFrame = new FeatureRenderDispatcher.PreparedFrame();

    public FeatureRenderDispatcher(
        RenderBuffers renderBuffers, ModelManager modelManager, AtlasManager atlasManager, Font font, GameRenderState gameRenderState
    ) {
        this.modelManager = modelManager;
        this.atlasManager = atlasManager;
        this.font = font;
        this.gameRenderState = gameRenderState;
        this.stagedVertexBuffer = renderBuffers.stagedVertexBuffer();
        this.featureRenderers.put(ShadowFeatureRenderer.TYPE, new ShadowFeatureRenderer());
        this.featureRenderers.put(FlameFeatureRenderer.TYPE, new FlameFeatureRenderer());
        this.featureRenderers.put(ModelFeatureRenderer.TYPE, new ModelFeatureRenderer());
        this.featureRenderers.put(NameTagFeatureRenderer.TYPE, new NameTagFeatureRenderer());
        this.featureRenderers.put(TextFeatureRenderer.TYPE, new TextFeatureRenderer());
        this.featureRenderers.put(LeashFeatureRenderer.TYPE, new LeashFeatureRenderer());
        this.featureRenderers.put(ItemFeatureRenderer.TYPE, new ItemFeatureRenderer());
        this.featureRenderers.put(CustomFeatureRenderer.TYPE, new CustomFeatureRenderer());
        this.featureRenderers.put(BlockModelFeatureRenderer.TYPE, new BlockModelFeatureRenderer());
        this.featureRenderers.put(MovingBlockFeatureRenderer.TYPE, new MovingBlockFeatureRenderer());
        this.featureRenderers.put(QuadParticleFeatureRenderer.TYPE, new QuadParticleFeatureRenderer());
        this.featureRenderers.put(ShapeOutlineFeatureRenderer.TYPE, new ShapeOutlineFeatureRenderer());
        this.featureRenderers.put(GizmoFeatureRenderer.TYPE, new GizmoFeatureRenderer());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.RegisterFeatureRenderersEvent(this.featureRenderers));
    }

    public FeatureRenderDispatcher.PreparedFrame prepareFrame(SubmitNodeStorage submitNodeStorage) {
        Minecraft minecraft = Minecraft.getInstance();
        return this.prepareFrameWithContext(
            new FeatureFrameContext(
                this.gameRenderState.optionsRenderState,
                this.font,
                this.modelManager.getBlockStateModelSet(),
                minecraft.getBlockColors(),
                minecraft.getTextureManager(),
                this.atlasManager,
                minecraft.gameRenderer.lightmap(),
                this.stagedVertexBuffer
            ),
            submitNodeStorage
        );
    }

    private FeatureRenderDispatcher.PreparedFrame prepareFrameWithContext(FeatureFrameContext context, SubmitNodeStorage submitNodeStorage) {
        FeatureRenderDispatcher.PreparedFrame frame = this.preparedFrame.begin(context, submitNodeStorage);
        ProfilerFiller profiler = Profiler.get();
        profiler.push("sort");
        submitNodeStorage.drainPhases(phase -> phase.sortInto(new FeatureRenderDispatcher.PhaseSubmitGrouper(frame, phase)));
        profiler.popPush("beginPrepare");

        for (FeatureRenderer<?> renderer : this.featureRenderers.values()) {
            renderer.beginPrepare(context);
        }

        profiler.popPush("prepare");

        for (Entry<FeatureRendererType<?>, List<FeatureRenderDispatcher.PreparedGroup<?>>> entry : frame.groupsByFeature.entrySet()) {
            profiler.push(entry.getKey().toString());

            for (FeatureRenderDispatcher.PreparedGroup<?> group : entry.getValue()) {
                group.prepare(context, this.featureRenderers, frame.allSubmits);
            }

            profiler.pop();
        }

        profiler.popPush("finishPrepare");

        for (FeatureRenderer<?> renderer : this.featureRenderers.values()) {
            renderer.finishPrepare(context);
        }

        profiler.popPush("uploadSharedVertexBuffer");
        this.stagedVertexBuffer.upload();
        profiler.pop();
        return frame;
    }

    public void renderAllFeatures(SubmitNodeStorage submitNodeStorage) {
        try (FeatureRenderDispatcher.PreparedFrame frame = this.prepareFrame(submitNodeStorage)) {
            frame.executeSolid();
            frame.executeTranslucent();
            frame.executeTranslucentAfterTerrain();
            frame.executeAlwaysOnTop();
        }
    }

    @Override
    public void close() {
        this.featureRenderers.close();
    }

    private static class PhaseSubmitGrouper implements FeatureRenderPhase.Output {
        private final FeatureRenderDispatcher.PreparedFrame frame;
        private final List<SubmitNode> allSubmits;
        private final List<FeatureRenderDispatcher.PreparedGroup<?>> phaseGroups;
        private FeatureRenderDispatcher.@Nullable PreparedGroup<?> lastGroup;

        public PhaseSubmitGrouper(FeatureRenderDispatcher.PreparedFrame frame, FeatureRenderPhase<?> phase) {
            this.frame = frame;
            this.allSubmits = frame.allSubmits;
            this.phaseGroups = frame.groupsByPhase.computeIfAbsent(phase, var0 -> new ArrayList<>());
        }

        @Override
        public void accept(SubmitNode submit, boolean strictlyOrdered) {
            int index = this.allSubmits.size();
            this.allSubmits.add(submit);
            this.addOrExtendGroup(submit.featureType(), strictlyOrdered, index, index);
        }

        @Override
        public <Submit extends SubmitNode> void acceptFeatureGroup(FeatureRendererType<Submit> featureType, Collection<Submit> submits, boolean strictlyOrdered) {
            if (!submits.isEmpty()) {
                for (Submit submit : submits) {
                    if (submit.featureType() != featureType) {
                        throw new IllegalArgumentException(submit + " was not of feature type " + featureType);
                    }
                }

                int fromInclusive = this.allSubmits.size();
                this.allSubmits.addAll(submits);
                int toInclusive = this.allSubmits.size() - 1;
                this.addOrExtendGroup(featureType, strictlyOrdered, fromInclusive, toInclusive);
            }
        }

        private <Submit extends SubmitNode> void addOrExtendGroup(
            FeatureRendererType<Submit> featureType, boolean strictlyOrdered, int fromInclusive, int toInclusive
        ) {
            if (this.lastGroup != null && this.lastGroup.featureType == featureType && this.lastGroup.strictlyOrdered == strictlyOrdered) {
                this.lastGroup.toInclusive = toInclusive;
            } else {
                List<FeatureRenderDispatcher.PreparedGroup<?>> featureGroups = this.frame
                    .groupsByFeature
                    .computeIfAbsent(featureType, var0 -> new ArrayList<>());
                FeatureRenderDispatcher.PreparedGroup<Submit> group = new FeatureRenderDispatcher.PreparedGroup<>(
                    featureGroups.size(), featureType, strictlyOrdered, fromInclusive, toInclusive
                );
                this.phaseGroups.add(group);
                featureGroups.add(group);
                this.lastGroup = group;
            }
        }
    }

    public class PreparedFrame implements AutoCloseable {
        private @Nullable FeatureFrameContext context;
        private @Nullable SubmitNodeStorage submitNodeStorage;
        private final List<SubmitNode> allSubmits = new ArrayList<>();
        private final Map<FeatureRenderPhase<?>, List<FeatureRenderDispatcher.PreparedGroup<?>>> groupsByPhase = new IdentityHashMap<>();
        private final Map<FeatureRendererType<?>, List<FeatureRenderDispatcher.PreparedGroup<?>>> groupsByFeature = new IdentityHashMap<>();

        private FeatureRenderDispatcher.PreparedFrame begin(FeatureFrameContext context, SubmitNodeStorage submitNodeStorage) {
            if (this.context != null) {
                throw new IllegalStateException("PreparedFrame already in use");
            }

            this.context = context;
            this.submitNodeStorage = submitNodeStorage;
            return this;
        }

        public void executeSolid() {
            FeatureFrameContext context = Objects.requireNonNull(this.context);
            SubmitNodeStorage submitNodeStorage = Objects.requireNonNull(this.submitNodeStorage);

            for (SubmitNodeCollection collection : submitNodeStorage.getSubmitsPerOrder().values()) {
                this.executePhase(collection.solid, context);
            }
        }

        public void executeTranslucent() {
            FeatureFrameContext context = Objects.requireNonNull(this.context);
            SubmitNodeStorage submitNodeStorage = Objects.requireNonNull(this.submitNodeStorage);

            for (SubmitNodeCollection collection : submitNodeStorage.getSubmitsPerOrder().values()) {
                this.executePhase(collection.shadows, context);
                this.executePhase(collection.translucentModels, context);
                this.executePhase(collection.seeThroughNameTags, context);
                this.executePhase(collection.nameTags, context);
                this.executePhase(collection.texts, context);
                this.executePhase(collection.translucentCustomGeometry, context);
            }

            for (SubmitNodeCollection collection : submitNodeStorage.getSubmitsPerOrder().values()) {
                this.executePhase(collection.shapeOutlines, context);
                this.executePhase(collection.gizmos, context);
            }

            for (SubmitNodeCollection collection : submitNodeStorage.getSubmitsPerOrder().values()) {
                this.executePhase(collection.translucentBlocksAndItems, context);
                this.executePhase(collection.breakingOverlay, context);
                this.executePhase(collection.waterMask, context);
            }
        }

        public void executeOutline() {
            FeatureFrameContext context = Objects.requireNonNull(this.context);
            SubmitNodeStorage submitNodeStorage = Objects.requireNonNull(this.submitNodeStorage);

            for (SubmitNodeCollection collection : submitNodeStorage.getSubmitsPerOrder().values()) {
                this.executePhase(collection.outline, context);
            }
        }

        public void executeTranslucentAfterTerrain() {
            FeatureFrameContext context = Objects.requireNonNull(this.context);
            SubmitNodeStorage submitNodeStorage = Objects.requireNonNull(this.submitNodeStorage);

            for (SubmitNodeCollection collection : submitNodeStorage.getSubmitsPerOrder().values()) {
                this.executePhase(collection.afterTerrain, context);
            }
        }

        public void executeAlwaysOnTop() {
            FeatureFrameContext context = Objects.requireNonNull(this.context);
            SubmitNodeStorage submitNodeStorage = Objects.requireNonNull(this.submitNodeStorage);

            for (SubmitNodeCollection collection : submitNodeStorage.getSubmitsPerOrder().values()) {
                this.executePhase(collection.alwaysOnTop, context);
            }
        }

        private void executePhase(FeatureRenderPhase<?> phase, FeatureFrameContext context) {
            ProfilerFiller profiler = Profiler.get();

            for (FeatureRenderDispatcher.PreparedGroup<?> group : this.groupsByPhase.getOrDefault(phase, List.of())) {
                profiler.push(group.featureType.toString());
                group.execute(context, FeatureRenderDispatcher.this.featureRenderers, this.allSubmits);
                profiler.pop();
            }
        }

        public boolean hasAnyAlwaysOnTop() {
            SubmitNodeStorage submitNodeStorage = Objects.requireNonNull(this.submitNodeStorage);

            for (SubmitNodeCollection collection : submitNodeStorage.getSubmitsPerOrder().values()) {
                if (!this.groupsByPhase.getOrDefault(collection.alwaysOnTop, List.of()).isEmpty()) {
                    return true;
                }
            }

            return false;
        }

        public boolean hasAnyOutline() {
            SubmitNodeStorage submitNodeStorage = Objects.requireNonNull(this.submitNodeStorage);

            for (SubmitNodeCollection collection : submitNodeStorage.getSubmitsPerOrder().values()) {
                if (!this.groupsByPhase.getOrDefault(collection.outline, List.of()).isEmpty()) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void close() {
            FeatureFrameContext context = Objects.requireNonNull(this.context, "Frame not in use");
            this.context = null;
            this.submitNodeStorage = null;

            for (FeatureRenderer<?> featureRenderer : FeatureRenderDispatcher.this.featureRenderers.values()) {
                featureRenderer.finishExecute(context);
            }

            FeatureRenderDispatcher.this.stagedVertexBuffer.endDraw();
            this.allSubmits.clear();
            clearGroups(this.groupsByPhase.values());
            clearGroups(this.groupsByFeature.values());
        }

        private static void clearGroups(Collection<List<FeatureRenderDispatcher.PreparedGroup<?>>> groupsSet) {
            groupsSet.removeIf(groups -> {
                if (groups.isEmpty()) {
                    return true;
                }

                groups.clear();
                return false;
            });
        }
    }

    private static class PreparedGroup<Submit extends SubmitNode> {
        private final int featureGroupIndex;
        private final FeatureRendererType<Submit> featureType;
        private final boolean strictlyOrdered;
        private final int fromInclusive;
        private int toInclusive;

        public PreparedGroup(int featureGroupIndex, FeatureRendererType<Submit> featureType, boolean strictlyOrdered, int fromInclusive, int toInclusive) {
            this.featureGroupIndex = featureGroupIndex;
            this.featureType = featureType;
            this.strictlyOrdered = strictlyOrdered;
            this.fromInclusive = fromInclusive;
            this.toInclusive = toInclusive;
        }

        public void prepare(FeatureFrameContext context, FeatureRendererMap featureRenderers, List<SubmitNode> submits) {
            FeatureRenderer<Submit> featureRenderer = featureRenderers.getOrThrow(this.featureType);
            featureRenderer.prepareGroup(context, this.sliceUnchecked(submits), this.strictlyOrdered);
        }

        public void execute(FeatureFrameContext context, FeatureRendererMap featureRenderers, List<SubmitNode> submits) {
            FeatureRenderer<Submit> featureRenderer = featureRenderers.getOrThrow(this.featureType);
            featureRenderer.executeGroup(context, this.featureGroupIndex, this.sliceUnchecked(submits), this.strictlyOrdered);
        }

        private List<Submit> sliceUnchecked(List<SubmitNode> submits) {
            return (List<Submit>)submits.subList(this.fromInclusive, this.toInclusive + 1);
        }
    }
}

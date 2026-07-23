package net.minecraft.server.packs.repository;

import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FeatureFlagsMetadataSection;
import net.minecraft.server.packs.OverlayMetadataSection;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.world.flag.FeatureFlagSet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Pack {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackLocationInfo location;
    private final Pack.ResourcesSupplier resources;
    private final Pack.Metadata metadata;
    private final PackSelectionConfig selectionConfig;
    private final boolean hidden; // Neo: Allow packs to be hidden from the UI entirely
    private final List<Pack> children; // Neo: Allows packs to specify packs which will always be placed beneath them; must be hidden
    private static final PackSource CHILD_SOURCE = PackSource.create(
            name -> Component.translatable(
                    "pack.nameAndSource",
                    name,
                    Component.translatable("pack.neoforge.source.child")
            ).withStyle(net.minecraft.ChatFormatting.GRAY),
            false
    ); // Neo: Pack source for child packs; should not be otherwise used

    public static @Nullable Pack readMetaAndCreate(
        PackLocationInfo location, Pack.ResourcesSupplier resources, PackType packType, PackSelectionConfig selectionConfig
    ) {
        PackFormat currentPackVersion = SharedConstants.getCurrentVersion().packVersion(packType);
        Pack.Metadata meta = readPackMetadata(location, resources, currentPackVersion, packType);
        return meta != null ? new Pack(location, resources, meta, selectionConfig) : null;
    }

    public Pack(PackLocationInfo location, Pack.ResourcesSupplier resources, Pack.Metadata metadata, PackSelectionConfig selectionConfig) {
        this(location, resources, metadata, selectionConfig, List.of());
    }

    private Pack(PackLocationInfo location, Pack.ResourcesSupplier resources, Pack.Metadata metadata, PackSelectionConfig selectionConfig, List<Pack> children) {
        List<Pack> flattenedChildren = new java.util.ArrayList<>();
        List<Pack> remainingChildren = children;
        // recursively flatten children
        while (!remainingChildren.isEmpty()) {
            List<Pack> oldChildren = remainingChildren;
            remainingChildren = new java.util.ArrayList<>();
            for (Pack child : oldChildren) {
                // Adapts the child pack with the following changes:
                // - Must be hidden
                // - Must have no children
                // - Has a pack source of CHILD_SOURCE, which is not automatically added
                Pack adaptedChild = new Pack(
                        new PackLocationInfo(child.location.id(), child.location.title(), CHILD_SOURCE, child.location.knownPackInfo()),
                        child.resources,
                        new Metadata(child.metadata.description, child.metadata.compatibility, child.metadata.requestedFeatures, child.metadata.overlays, true),
                        new PackSelectionConfig(false, child.selectionConfig.defaultPosition(), child.selectionConfig.fixedPosition()),
                        List.of()
                );
                flattenedChildren.add(adaptedChild);
                remainingChildren.addAll(child.getChildren());
            }
        }
        this.children = List.copyOf(flattenedChildren);
        this.hidden = metadata.isHidden();
        this.location = location;
        this.resources = resources;
        this.metadata = metadata;
        this.selectionConfig = selectionConfig;
    }

    public static Pack.@Nullable Metadata readPackMetadata(
        PackLocationInfo location, Pack.ResourcesSupplier resources, PackFormat currentPackVersion, PackType type
    ) {
        try (PackResources pack = resources.openPrimary(location)) {
            PackMetadataSection meta;
            try {
                meta = pack.getMetadataSection(PackMetadataSection.forPackType(type));
            } catch (JsonParseException e) {
                LOGGER.warn("Error reading pack metadata, attempting fallback type", e);
                meta = pack.getMetadataSection(PackMetadataSection.FALLBACK_TYPE);
            }

            if (meta == null) {
                LOGGER.warn("Missing metadata in pack {}", location.id());
                return null;
            } else {
                FeatureFlagsMetadataSection featureFlagMeta = pack.getMetadataSection(FeatureFlagsMetadataSection.TYPE);
                FeatureFlagSet requiredFlags = featureFlagMeta != null ? featureFlagMeta.flags() : FeatureFlagSet.of();
                PackCompatibility packCompatibility = PackCompatibility.forVersion(meta.supportedFormats(), currentPackVersion);
                OverlayMetadataSection overlays = pack.getMetadataSection(OverlayMetadataSection.forPackType(type));
                List<String> overlaySet = overlays != null ? overlays.overlaysForVersion(currentPackVersion) : List.of();

                // Neo: add `neoforge:overlays` overlays as well
                var neoOverlays = pack.getMetadataSection(OverlayMetadataSection.forPackTypeNeoForge(type));
                if (neoOverlays != null) {
                    overlaySet = new java.util.ArrayList<>(overlaySet);
                    overlaySet.addAll(neoOverlays.overlaysForVersion(currentPackVersion));
                    overlaySet = List.copyOf(overlaySet);
                }
                return new Pack.Metadata(meta.description(), packCompatibility, requiredFlags, overlaySet, pack.isHidden());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read pack {} metadata", location.id(), e);
            return null;
        }
    }

    public PackLocationInfo location() {
        return this.location;
    }

    public Component getTitle() {
        return this.location.title();
    }

    public Component getDescription() {
        return this.metadata.description();
    }

    public Component getChatLink(boolean enabled) {
        return this.location.createChatLink(enabled, this.metadata.description);
    }

    public PackCompatibility getCompatibility() {
        return this.metadata.compatibility();
    }

    public FeatureFlagSet getRequestedFeatures() {
        return this.metadata.requestedFeatures();
    }

    public PackResources open() {
        return this.resources.openFull(this.location, this.metadata);
    }

    public String getId() {
        return this.location.id();
    }

    public PackSelectionConfig selectionConfig() {
        return this.selectionConfig;
    }

    public boolean isRequired() {
        return this.selectionConfig.required();
    }

    public boolean isFixedPosition() {
        return this.selectionConfig.fixedPosition();
    }

    public Pack.Position getDefaultPosition() {
        return this.selectionConfig.defaultPosition();
    }

    public PackSource getPackSource() {
        return this.location.source();
    }

    public boolean isHidden() {
        return hidden;
    }

    public List<Pack> getChildren() {
        return children;
    }

    public java.util.stream.Stream<Pack> streamSelfAndChildren() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(this), children.stream());
    }

    /**
     * {@return a copy of the pack with the provided children in place of any children this pack currently has}
     */
    public Pack withChildren(List<Pack> children) {
        return new Pack(this.location, this.resources, this.metadata, this.selectionConfig, children);
    }

    /**
     * {@return a copy of the pack that is hidden}
     */
    public Pack hidden() {
        return new Pack(
                new PackLocationInfo(this.location.id(), this.location.title(), this.location.source(), this.location.knownPackInfo()),
                this.resources,
                new Metadata(this.metadata.description, this.metadata.compatibility, this.metadata.requestedFeatures, this.metadata.overlays, true),
                new PackSelectionConfig(this.selectionConfig.required(), this.selectionConfig.defaultPosition(), this.selectionConfig.fixedPosition()),
                this.children
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else {
            return o instanceof Pack that ? this.location.equals(that.location) : false;
        }
    }

    @Override
    public int hashCode() {
        return this.location.hashCode();
    }

    public static record Metadata(Component description, PackCompatibility compatibility, FeatureFlagSet requestedFeatures, List<String> overlays, boolean isHidden) {
        /** @deprecated Neo: use {@link #Metadata(Component,PackCompatibility,FeatureFlagSet,List,boolean)} instead */
        @Deprecated
        public Metadata(Component description, PackCompatibility compatibility, FeatureFlagSet requestedFeatures, List<String> overlays) {
            this(description, compatibility, requestedFeatures, overlays, false);
        }
    }

    public enum Position {
        TOP,
        BOTTOM;

        public <T> int insert(List<T> list, T value, Function<T, PackSelectionConfig> converter, boolean reverse) {
            Pack.Position self = reverse ? this.opposite() : this;
            if (self == BOTTOM) {
                int index;
                for (index = 0; index < list.size(); index++) {
                    PackSelectionConfig pack = converter.apply(list.get(index));
                    if (!pack.fixedPosition() || pack.defaultPosition() != this) {
                        break;
                    }
                }

                list.add(index, value);
                return index;
            } else {
                int index;
                for (index = list.size() - 1; index >= 0; index--) {
                    PackSelectionConfig pack = converter.apply(list.get(index));
                    if (!pack.fixedPosition() || pack.defaultPosition() != this) {
                        break;
                    }
                }

                list.add(index + 1, value);
                return index + 1;
            }
        }

        public Pack.Position opposite() {
            return this == TOP ? BOTTOM : TOP;
        }
    }

    public interface ResourcesSupplier {
        PackResources openPrimary(PackLocationInfo location);

        PackResources openFull(PackLocationInfo location, Pack.Metadata metadata);
    }
}

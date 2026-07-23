package net.minecraft.advancements;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public class AdvancementNode {
    private final AdvancementHolder holder;
    private final @Nullable AdvancementNode parent;
    private final Set<AdvancementNode> children = new ReferenceOpenHashSet<>();

    @VisibleForTesting
    public AdvancementNode(AdvancementHolder holder, @Nullable AdvancementNode parent) {
        this.holder = holder;
        this.parent = parent;
    }

    public Advancement advancement() {
        return this.holder.value();
    }

    public AdvancementHolder holder() {
        return this.holder;
    }

    public @Nullable AdvancementNode parent() {
        return this.parent;
    }

    public AdvancementNode root() {
        return getRoot(this);
    }

    public static AdvancementNode getRoot(AdvancementNode advancement) {
        AdvancementNode root = advancement;

        while (true) {
            AdvancementNode parent = root.parent();
            if (parent == null) {
                return root;
            }

            root = parent;
        }
    }

    public Iterable<AdvancementNode> children() {
        return this.children;
    }

    @VisibleForTesting
    public void addChild(AdvancementNode child) {
        this.children.add(child);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj ? true : obj instanceof AdvancementNode that && this.holder.equals(that.holder);
    }

    @Override
    public int hashCode() {
        return this.holder.hashCode();
    }

    @Override
    public String toString() {
        return this.holder.id().toString();
    }
}

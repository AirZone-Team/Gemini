package net.minecraft.server.permissions;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

public class PermissionSetUnion implements PermissionSet {
    private final ReferenceSet<PermissionSet> permissions = new ReferenceArraySet<>();

    PermissionSetUnion(PermissionSet first, PermissionSet second) {
        this.permissions.add(first);
        this.permissions.add(second);
        this.ensureNoUnionsWithinUnions();
    }

    private PermissionSetUnion(ReferenceSet<PermissionSet> oldPermissions, PermissionSet other) {
        this.permissions.addAll(oldPermissions);
        this.permissions.add(other);
        this.ensureNoUnionsWithinUnions();
    }

    private PermissionSetUnion(ReferenceSet<PermissionSet> oldPermissions, ReferenceSet<PermissionSet> other) {
        this.permissions.addAll(oldPermissions);
        this.permissions.addAll(other);
        this.ensureNoUnionsWithinUnions();
    }

    @Override
    public boolean hasPermission(Permission permission) {
        for (PermissionSet set : this.permissions) {
            if (set.hasPermission(permission)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public PermissionSet union(PermissionSet other) {
        return other instanceof PermissionSetUnion otherUnion
            ? new PermissionSetUnion(this.permissions, otherUnion.permissions)
            : new PermissionSetUnion(this.permissions, other);
    }

    @VisibleForTesting
    public ReferenceSet<PermissionSet> getPermissions() {
        return new ReferenceArraySet<>(this.permissions);
    }

    private void ensureNoUnionsWithinUnions() {
        for (PermissionSet set : this.permissions) {
            if (set instanceof PermissionSetUnion) {
                throw new IllegalArgumentException("Cannot have PermissionSetUnion within another PermissionSetUnion");
            }
        }
    }
}

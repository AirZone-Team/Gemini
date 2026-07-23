package net.minecraft.world.flag;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.Arrays;
import java.util.Collection;
import org.jspecify.annotations.Nullable;

public final class FeatureFlagSet {
    private static final FeatureFlagSet EMPTY = new FeatureFlagSet(null, 0L);
    private static final long[] EMPTY_EXT_MASK = new long[0];
    public static final int MAX_CONTAINER_SIZE = 64;
    private final @Nullable FeatureFlagUniverse universe;
    private final long mask;
    private final long[] extendedMask;

    private FeatureFlagSet(@Nullable FeatureFlagUniverse universe, long mask) {
        this(universe, mask, EMPTY_EXT_MASK);
    }

    private FeatureFlagSet(@Nullable FeatureFlagUniverse universe, long mask, long[] extendedMask) {
        this.universe = universe;
        this.mask = mask;
        this.extendedMask = extendedMask;
    }

    static FeatureFlagSet create(FeatureFlagUniverse universe, Collection<FeatureFlag> flags) {
        if (flags.isEmpty()) {
            return EMPTY;
        }

        long mask = computeMask(universe, 0L, flags);
        long[] extMask = computeExtendedMask(universe, 0, 0L, flags);
        return new FeatureFlagSet(universe, mask, extMask);
    }

    public static FeatureFlagSet of() {
        return EMPTY;
    }

    public static FeatureFlagSet of(FeatureFlag flag) {
        long[] extMask = computeExtendedMask(flag.universe, flag.extMaskIndex, flag.mask, java.util.List.of());
        return new FeatureFlagSet(flag.universe, flag.extMaskIndex >= 0 ? 0L : flag.mask, extMask);
    }

    public static FeatureFlagSet of(FeatureFlag flag, FeatureFlag... flags) {
        long i = flags.length == 0 ? (flag.extMaskIndex >= 0 ? 0L : flag.mask) : computeMask(flag.universe, flag.extMaskIndex >= 0 ? 0L : flag.mask, Arrays.asList(flags));
        long[] extMask = computeExtendedMask(flag.universe, flag.extMaskIndex, flag.mask, flags.length == 0 ? java.util.List.of() : Arrays.asList(flags));
        return new FeatureFlagSet(flag.universe, i, extMask);
    }

    private static long computeMask(FeatureFlagUniverse universe, long mask, Iterable<FeatureFlag> flags) {
        for (FeatureFlag f : flags) {
            if (f.extMaskIndex >= 0) continue;
            if (universe != f.universe) {
                throw new IllegalStateException("Mismatched feature universe, expected '" + universe + "', but got '" + f.universe + "'");
            }

            mask |= f.mask;
        }

        return mask;
    }

    private static long[] computeExtendedMask(FeatureFlagUniverse universe, int firstExtIndex, long firstMask, Iterable<FeatureFlag> otherFlags) {
        long[] extMask = EMPTY_EXT_MASK;
        if (firstExtIndex >= 0) {
            extMask = new long[firstExtIndex + 1];
            extMask[firstExtIndex] |= firstMask;
        }
        for (FeatureFlag flag : otherFlags) {
            if (flag.extMaskIndex < 0) continue;
            if (universe != flag.universe) {
                throw new IllegalStateException("Mismatched feature universe, expected '" + universe + "', but got '" + flag.universe + "'");
            }
            if (flag.extMaskIndex >= extMask.length) {
                extMask = Arrays.copyOfRange(extMask, 0, flag.extMaskIndex + 1);
            }
            extMask[flag.extMaskIndex] |= flag.mask;
        }
        return extMask;
    }

    public boolean contains(FeatureFlag flag) {
        if (this.universe != flag.universe) {
            return false;
        }
        if (flag.extMaskIndex < 0) {
            return (this.mask & flag.mask) != 0L;
        }
        if (this.extendedMask.length > flag.extMaskIndex) {
            return (this.extendedMask[flag.extMaskIndex] & flag.mask) != 0L;
        }
        return false;
    }

    public boolean isEmpty() {
        return this.equals(EMPTY);
    }

    public boolean isSubsetOf(FeatureFlagSet set) {
        if (this.universe == null) {
            return true;
        } else if (this.universe == set.universe) {
            int len = Math.max(this.extendedMask.length, set.extendedMask.length);
            for (int i = 0; i < len; i++) {
                long thisMask = i < this.extendedMask.length ? this.extendedMask[i] : 0L;
                long otherMask = i < set.extendedMask.length ? set.extendedMask[i] : 0L;
                if ((thisMask & ~otherMask) != 0L) {
                    return false;
                }
            }
            return (this.mask & ~set.mask) == 0L;
        }
        return false;
    }

    public boolean intersects(FeatureFlagSet set) {
        if (this.universe == null || set.universe == null || this.universe != set.universe) {
            return false;
        }
        int len = Math.min(this.extendedMask.length, set.extendedMask.length);
        for (int i = 0; i < len; i++) {
            long thisMask = this.extendedMask[i];
            long otherMask = set.extendedMask[i];
            if ((thisMask & otherMask) != 0L) {
                return true;
            }
        }
       return (this.mask & set.mask) != 0L;
    }

    public FeatureFlagSet join(FeatureFlagSet other) {
        if (this.universe == null) {
            return other;
        } else if (other.universe == null) {
            return this;
        } else if (this.universe != other.universe) {
            throw new IllegalArgumentException("Mismatched set elements: '" + this.universe + "' != '" + other.universe + "'");
        } else {
            long[] extMask = EMPTY_EXT_MASK;
            if (this.extendedMask.length > 0 || other.extendedMask.length > 0) {
                extMask = new long[Math.max(this.extendedMask.length, other.extendedMask.length)];
                for (int i = 0; i < extMask.length; i++) {
                    long thisMask = i < this.extendedMask.length ? this.extendedMask[i] : 0L;
                    long otherMask = i < other.extendedMask.length ? other.extendedMask[i] : 0L;
                    extMask[i] = thisMask | otherMask;
                }
            }
            return new FeatureFlagSet(this.universe, this.mask | other.mask, extMask);
        }
    }

    public FeatureFlagSet subtract(FeatureFlagSet other) {
        if (this.universe == null || other.universe == null) {
            return this;
        }

        if (this.universe != other.universe) {
            throw new IllegalArgumentException("Mismatched set elements: '" + this.universe + "' != '" + other.universe + "'");
        }

        long newMask = this.mask & ~other.mask;
        long[] extMask = EMPTY_EXT_MASK;
        if (this.extendedMask.length > 0 || other.extendedMask.length > 0) {
            extMask = new long[this.extendedMask.length];
            for (int idx = 0; idx < extMask.length; idx++) {
                long otherMask = idx < other.extendedMask.length ? other.extendedMask[idx] : 0L;
                extMask[idx] = this.extendedMask[idx] & ~otherMask;
            }
        }
        return newMask == 0L && extMask.length == 0 ? EMPTY : new FeatureFlagSet(this.universe, newMask, extMask);
    }

    @Override
    public boolean equals(Object o) {
        return this == o ? true : o instanceof FeatureFlagSet that && this.universe == that.universe && this.mask == that.mask && Arrays.equals(this.extendedMask, that.extendedMask);
    }

    @Override
    public int hashCode() {
        int hash = (int)HashCommon.mix(this.mask);
        for (long extMask : this.extendedMask) {
            hash = 13 * hash + (int) HashCommon.mix(extMask);
        }
        return hash;
    }
}

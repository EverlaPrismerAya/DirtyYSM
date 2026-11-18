package net.everla.dirtyYsm.api.network;

/** Upload limits must not be applied to the complete server model catalog. */
public final class FragmentLimits {
    public static final int DATA_SIZE = 30_000;
    public static final int CLIENTBOUND_SIZE = 64 * 1024 * 1024;
    public static final int SERVERBOUND_SIZE = 2 * 1024 * 1024;
    public static int countLimit(int maxSize) { return (maxSize + DATA_SIZE - 1) / DATA_SIZE; }
    public static void validateCount(int count, int maxSize) {
        if (count <= 0 || count > countLimit(maxSize))
            throw new IllegalArgumentException("Invalid YSM fragment count: " + count + " (limit " + countLimit(maxSize) + ", bytes " + maxSize + ")");
    }
}

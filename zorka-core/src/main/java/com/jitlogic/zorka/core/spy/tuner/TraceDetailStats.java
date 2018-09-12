package com.jitlogic.zorka.core.spy.tuner;

public class TraceDetailStats {

    public static final long CALL_MASK = 0x0000000000FFFFFFL;
    private static final long CALL_INV = 0xFFFFFFFFFF000000L;
    private static final long CALL_MAX = 0xFFFFFFL;

    public static final long DROP_BITS = 24;
    public static final long DROP_MASK = 0x0000FFFFFF000000L;
    private static final long DROP_INV  = 0xFFFF000000FFFFFFL;
    private static final long DROP_MAX  = 0xFFFFFFL;

    public static final long ERR_BITS = 48;
    public static final long ERR_MASK = 0x00FF000000000000L;
    private static final long ERR_INV  = 0xFF00FFFFFFFFFFFFL;
    private static final long ERR_MAX  = 0xFFL;

    public static final long LONG_BITS = 56;
    public static final long LONG_MASK = 0xFF00000000000000L;
    private static final long LONG_INV  = 0x00FFFFFFFFFFFFFFL;
    private static final long LONG_MAX  = 0xFFL;

    private int size;
    private long[] stats;

    public TraceDetailStats(int size) {
        this.size = size;
        this.stats = new long[size];
    }

    public int getSize() {
        return size;
    }

    public long[] getStats() {
        return stats;
    }

    public boolean markCall(int mid) {
        if (mid < size) {
            long l = stats[mid];
            long c = (l & CALL_MASK) + 1;
            if (c <= CALL_MAX) {
                stats[mid] = (l & CALL_INV) | c;
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean markDrop(int mid) {
        if (mid < size) {
            long l = stats[mid];
            long c = ((l & DROP_MASK) >>> DROP_BITS) + 1;
            if (c <= DROP_MAX) {
                stats[mid] = (l & DROP_INV) | (c << DROP_BITS);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean markError(int mid) {
        if (mid < size) {
            long l = stats[mid];
            long c = ((l & ERR_MASK) >>> ERR_BITS) + 1;
            if (c <= ERR_MAX) {
                stats[mid] = (l & ERR_INV) | (c << ERR_BITS);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean markLong(int mid) {
        if (mid < size) {
            long l = stats[mid];
            long c = ((l & LONG_MASK) >>> LONG_BITS) + 1;
            if (c <= LONG_MAX) {
                stats[mid] = (l & LONG_INV) | (c << LONG_BITS);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
}

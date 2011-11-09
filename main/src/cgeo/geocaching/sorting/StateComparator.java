package cgeo.geocaching.sorting;

import cgeo.geocaching.cgCache;

/**
 * sort caches by state (normal, disabled, archived)
 *
 */
public class StateComparator extends AbstractCacheComparator {

    @Override
    protected boolean canCompare(final cgCache cache1, final cgCache cache2) {
        return true;
    }

    @Override
    protected int compareCaches(final cgCache cache1, final cgCache cache2) {
        return getState(cache1) - getState(cache2);
    }

    private static int getState(final cgCache cache) {
        if (cache.isDisabled()) {
            return 1;
        }
        if (cache.isArchived()) {
            return 2;
        }
        return 0;
    }

}

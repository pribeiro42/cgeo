package cgeo.geocaching.sorting;

import cgeo.geocaching.models.Geocache;

/**
 * sorts caches by last visited date
 *
 */
public class VisitComparator extends AbstractCacheComparator {

    public static final VisitComparator singleton = new VisitComparator();

    @Override
    protected int compareCaches(final Geocache cache1, final Geocache cache2) {
        return compare(cache2.getVisitedDate(), cache1.getVisitedDate());
    }

    /**
     * copy of Long#compare to avoid boxing
     */
    public static int compare(final long lhs, final long rhs) {
        return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
    }

}

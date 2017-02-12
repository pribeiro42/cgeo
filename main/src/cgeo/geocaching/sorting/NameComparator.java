package cgeo.geocaching.sorting;

import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.utils.TextUtils;

import org.apache.commons.lang3.StringUtils;

/**
 * sorts caches by name
 *
 */
public class NameComparator extends AbstractCacheComparator {

    @Override
    protected boolean canCompare(final Geocache cache) {
        return StringUtils.isNotBlank(cache.getName());
    }

    @Override
    protected int compareCaches(final Geocache cache1, final Geocache cache2) {
        return TextUtils.COLLATOR.compare(cache1.getNameForSorting(), cache2.getNameForSorting());
    }
}

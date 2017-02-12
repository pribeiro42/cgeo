package cgeo.geocaching.filter;

import junit.framework.TestCase;

import cgeo.geocaching.models.Geocache;

import static org.assertj.core.api.Assertions.assertThat;

public class StateNotFoundFilterTest extends TestCase {

    private StateFilterFactory.StateNotFoundFilter notFoundFilter;
    private Geocache foundCache;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        notFoundFilter = new StateFilterFactory.StateNotFoundFilter();
        foundCache = new Geocache();
        foundCache.setFound(true);
    }

    public void testAccepts() {
        assertThat(notFoundFilter.accepts(foundCache)).isFalse();
        assertThat(notFoundFilter.accepts(new Geocache())).isTrue();
    }

}

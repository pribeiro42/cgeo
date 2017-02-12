package cgeo.geocaching.filter;

import junit.framework.TestCase;

import cgeo.geocaching.models.Geocache;

import static org.assertj.core.api.Assertions.assertThat;

public class StateArchivedFilterTest extends TestCase {

    private StateFilterFactory.StateArchivedFilter archivedFilter;
    private Geocache archivedCache;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // members can only be setup here, after application is initialized
        archivedFilter = new StateFilterFactory.StateArchivedFilter();
        archivedCache = new Geocache();
        archivedCache.setArchived(true);
    }

    public void testAccepts() {
        assertThat(archivedFilter.accepts(archivedCache)).isTrue();
        assertThat(archivedFilter.accepts(new Geocache())).isFalse();
    }

}

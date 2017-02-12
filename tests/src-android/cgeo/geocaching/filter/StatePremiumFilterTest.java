package cgeo.geocaching.filter;

import junit.framework.TestCase;

import cgeo.geocaching.models.Geocache;

import static org.assertj.core.api.Assertions.assertThat;

public class StatePremiumFilterTest extends TestCase {

    private StateFilterFactory.StatePremiumFilter premiumFilter;
    private Geocache premiumCache;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        premiumFilter = new StateFilterFactory.StatePremiumFilter();
        premiumCache = new Geocache();
        premiumCache.setPremiumMembersOnly(true);
    }

    public void testAccepts() {
        assertThat(premiumFilter.accepts(premiumCache)).isTrue();
        assertThat(premiumFilter.accepts(new Geocache())).isFalse();
    }

}

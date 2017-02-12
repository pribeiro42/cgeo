package cgeo.geocaching.filter;

import junit.framework.TestCase;

import cgeo.geocaching.models.Geocache;

import static org.assertj.core.api.Assertions.assertThat;

public class DifficultyFilterTest extends TestCase {

    public static void testTerrainFilter() {
        final Geocache easy = new Geocache();
        easy.setDifficulty(1.5f);

        final Geocache hard = new Geocache();
        hard.setDifficulty(5f);

        final DifficultyFilter easyFilter = (DifficultyFilter) new DifficultyFilter.Factory().getFilters().get(0);

        assertThat(easyFilter.accepts(easy)).isTrue();
        assertThat(easyFilter.accepts(hard)).isFalse();
    }

    public static void testAllFilters() {
        assertThat(new DifficultyFilter.Factory().getFilters()).hasSize(5); // difficulty ranges from 1 to 5
    }
}

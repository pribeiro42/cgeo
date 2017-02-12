package cgeo.geocaching.sensors;

import static org.assertj.core.api.Assertions.assertThat;

import cgeo.geocaching.MainActivity;
import cgeo.geocaching.utils.AngleUtils;

import android.test.ActivityInstrumentationTestCase2;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;

public class SensorsTest extends ActivityInstrumentationTestCase2<MainActivity> {

    private Sensors sensors;

    public SensorsTest() {
        super(MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        sensors = Sensors.getInstance();
    }

    public static void testGetDirectionNow() {
        final float angle = AngleUtils.getDirectionNow(1.0f);
        assertThat(angle == 1.0f || angle == 91.0f || angle == 181.0f || angle == 271.0f).isTrue();
    }

    private static <T> void assertDataAvailability(final Observable<T> observable) {
        try {
            observable.timeout(2, TimeUnit.SECONDS).blockingFirst();
        } catch (final Exception ignored) {
            fail("timeout while waiting for sensor data");
        }
    }

    public void testDirectionObservable() {
        assertDataAvailability(sensors.directionObservable());
    }

    public void testGeodataObservable() {
        assertDataAvailability(sensors.geoDataObservable(false));
        assertDataAvailability(sensors.geoDataObservable(true));
    }

}

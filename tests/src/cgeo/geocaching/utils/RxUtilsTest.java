package cgeo.geocaching.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Function;
import io.reactivex.subjects.PublishSubject;
import junit.framework.TestCase;

public class RxUtilsTest extends TestCase {

    public static void testRememberLast() {
        final PublishSubject<String> rawObservable = PublishSubject.create();
        final Observable<String> observable = RxUtils.rememberLast(rawObservable, "initial");

        // Check that the initial value is present, and is kept there
        assertThat(observable.blockingFirst()).isEqualTo("initial");
        assertThat(observable.blockingFirst()).isEqualTo("initial");

        // Check that if the observable is not subscribed, changes are not propagated (similar to not keeping the
        // inner disposable active).
        rawObservable.onNext("without subscribers");
        assertThat(observable.blockingFirst()).isEqualTo("initial");

        // Check that new values are propagated and cached
        final Disposable disposable = observable.subscribe();
        rawObservable.onNext("first");
        assertThat(observable.blockingFirst()).isEqualTo("first");
        disposable.dispose();
        assertThat(observable.blockingFirst()).isEqualTo("first");
    }

    public static void testObservableCache() {
        final AtomicInteger counter = new AtomicInteger(0);
        final RxUtils.ObservableCache<String, Integer> cache = new RxUtils.ObservableCache<>(new Function<String, Observable<Integer>>() {
            @Override
            public Observable<Integer> apply(final String s) {
                counter.incrementAndGet();
                return Observable.just(s.length());
            }
        });
        assertThat(cache.get("a").blockingSingle()).isEqualTo(1);
        assertThat(counter.get()).isEqualTo(1);
        assertThat(cache.get("a").blockingSingle()).isEqualTo(1);
        assertThat(counter.get()).isEqualTo(1);
        assertThat(cache.get("bb").blockingSingle()).isEqualTo(2);
        assertThat(counter.get()).isEqualTo(2);
        assertThat(cache.get("bb").blockingSingle()).isEqualTo(2);
        assertThat(counter.get()).isEqualTo(2);
        assertThat(cache.get("a").blockingSingle()).isEqualTo(1);
        assertThat(counter.get()).isEqualTo(2);
    }

    public static void testDelayedUnsubscription() {
        final AtomicBoolean unsubscribed = new AtomicBoolean(false);
        Observable.never().doOnDispose(new Action() {
            @Override
            public void run() {
                unsubscribed.set(true);
            }
        }).lift(new RxUtils.DelayedUnsubscription<>(100, TimeUnit.MILLISECONDS)).subscribe().dispose();
        assertThat(unsubscribed.get()).isFalse();
        try {
            Thread.sleep(200);
        } catch (final InterruptedException ignored) {
            // ignore for tests
        }
        assertThat(unsubscribed.get()).isTrue();
    }

}

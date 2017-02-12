package cgeo.geocaching.connector.tc;

import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.connector.AbstractConnector;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.regex.Pattern;

public class TerraCachingConnector extends AbstractConnector {

    @NonNull private static final Pattern PATTERN_GEOCODE = Pattern.compile("(TC|CC|LC)[0-9A-Z]{1,4}", Pattern.CASE_INSENSITIVE);

    @Override
    @NonNull
    public String getName() {
        return "TerraCaching";
    }

    @Override
    @Nullable
    public String getCacheUrl(@NonNull final Geocache cache) {
        return getCacheUrlPrefix() + cache.getGeocode();
    }

    @Override
    @NonNull
    public String getHost() {
        return "www.terracaching.com/";
    }

    @Override
    public boolean getHttps() {
        return false;
    }

    @Override
    public boolean isOwner(@NonNull final Geocache cache) {
        return false;
    }

    @Override
    @NonNull
    protected String getCacheUrlPrefix() {
        return "http://www.terracaching.com/Cache/";
    }

    @Override
    public boolean canHandle(@NonNull final String geocode) {
        return PATTERN_GEOCODE.matcher(geocode).matches();
    }
}

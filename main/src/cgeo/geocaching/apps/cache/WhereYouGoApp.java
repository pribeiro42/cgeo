package cgeo.geocaching.apps.cache;

import cgeo.geocaching.R;
import cgeo.geocaching.enumerations.CacheType;
import cgeo.geocaching.models.Geocache;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class WhereYouGoApp extends AbstractGeneralApp {
    private static final Pattern PATTERN_CARTRIDGE = Pattern.compile("(" + Pattern.quote("http://www.wherigo.com/cartridge/details.aspx?") + ".*?)" + Pattern.quote("\""));

    public WhereYouGoApp() {
        super(getString(R.string.cache_menu_whereyougo), "menion.android.whereyougo");
    }

    @Override
    public boolean isEnabled(@NonNull final Geocache cache) {
        return cache.getType() == CacheType.WHERIGO && StringUtils.isNotEmpty(getWhereIGoUrl(cache));
    }

    @Override
    public void navigate(@NonNull final Activity activity, @NonNull final Geocache cache) {
        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getWhereIGoUrl(cache))));
    }

    /**
     * get the URL of the cartridge.
     *
     * @return {@code null} if there is no link to a cartridge, or if there are multiple different links
     */
    @Nullable
    protected static String getWhereIGoUrl(final Geocache cache) {
        final Matcher matcher = PATTERN_CARTRIDGE.matcher(cache.getShortDescription() + " " + cache.getDescription());
        final Set<String> urls = new HashSet<>();
        while (matcher.find()) {
            urls.add(matcher.group(1));
        }
        if (urls.size() == 1) {
            return urls.iterator().next();
        }
        return null;
    }
}

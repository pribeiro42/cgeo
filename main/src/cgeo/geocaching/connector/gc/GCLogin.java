package cgeo.geocaching.connector.gc;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.R;
import cgeo.geocaching.connector.AbstractLogin;
import cgeo.geocaching.enumerations.StatusCode;
import cgeo.geocaching.network.Cookies;
import cgeo.geocaching.network.HtmlImage;
import cgeo.geocaching.network.Network;
import cgeo.geocaching.network.Parameters;
import cgeo.geocaching.settings.Credentials;
import cgeo.geocaching.settings.DiskCookieStore;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.MatcherWrapper;
import cgeo.geocaching.utils.TextUtils;

import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import okhttp3.Response;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class GCLogin extends AbstractLogin {

    private static final String ENGLISH = "<a href=\"#\">English</a>";

    private static final String LANGUAGE_CHANGE_URI = "https://www.geocaching.com/my/souvenirs.aspx";

    private GCLogin() {
        // singleton
    }

    public static GCLogin getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final GCLogin INSTANCE = new GCLogin();
    }

    private static StatusCode resetGcCustomDate(final StatusCode statusCode) {
        Settings.setGcCustomDate(GCConstants.DEFAULT_GC_DATE);
        return statusCode;
    }

    @Override
    @NonNull
    protected StatusCode login(final boolean retry) {
        return login(retry, Settings.getCredentials(GCConnector.getInstance()));
    }

    @Override
    @NonNull
    protected StatusCode login(final boolean retry, @NonNull final Credentials credentials) {
        if (credentials.isInvalid()) {
            clearLoginInfo();
            Log.w("Login.login: No login information stored");
            return resetGcCustomDate(StatusCode.NO_LOGIN_INFO_STORED);
        }

        final String username = credentials.getUserName();
        final String password = credentials.getPassword();

        setActualStatus(CgeoApplication.getInstance().getString(R.string.init_login_popup_working));
        try {
            final Response tryLoggedInResponse = Network.getRequest("https://www.geocaching.com/login/default.aspx").blockingGet();
            final String tryLoggedInData = Network.getResponseData(tryLoggedInResponse);
            if (tryLoggedInResponse.code() == 503 && TextUtils.matches(tryLoggedInData, GCConstants.PATTERN_MAINTENANCE)) {
                return StatusCode.MAINTENANCE;
            }

            if (StringUtils.isBlank(tryLoggedInData)) {
                Log.e("Login.login: Failed to retrieve login page (1st)");
                return StatusCode.CONNECTION_FAILED; // no login page
            }

            if (getLoginStatus(tryLoggedInData)) {
                Log.i("Already logged in Geocaching.com as " + username + " (" + Settings.getGCMemberStatus() + ')');
                if (switchToEnglish(tryLoggedInData) && retry) {
                    return login(false, credentials);
                }
                setHomeLocation();
                refreshMemberStatus();
                detectGcCustomDate();
                return StatusCode.NO_ERROR; // logged in
            }

            Cookies.clearCookies();
            DiskCookieStore.setCookieStore(null);

            final String[] viewstates = getViewstates(tryLoggedInData);
            if (isEmpty(viewstates)) {
                Log.e("Login.login: Failed to find viewstates");
                return StatusCode.LOGIN_PARSE_ERROR; // no viewstates
            }

            final Parameters params = new Parameters(
                    "__EVENTTARGET", "",
                    "__EVENTARGUMENT", "",
                    "ctl00$ContentBody$tbUsername", username,
                    "ctl00$ContentBody$tbPassword", password,
                    "ctl00$ContentBody$cbRememberMe", "on",
                    "ctl00$ContentBody$btnSignIn", "Login");
            putViewstates(params, viewstates);

            final String loginData = Network.getResponseData(Network.postRequest("https://www.geocaching.com/login/default.aspx", params));

            if (StringUtils.isBlank(loginData)) {
                Log.e("Login.login: Failed to retrieve login page (2nd)");
                // FIXME: should it be CONNECTION_FAILED to match the first attempt?
                return StatusCode.COMMUNICATION_ERROR; // no login page
            }
            assert loginData != null;  // Caught above

            if (getLoginStatus(loginData)) {
                if (switchToEnglish(loginData) && retry) {
                    return login(false, credentials);
                }
                Log.i("Successfully logged in Geocaching.com as " + username + " (" + Settings.getGCMemberStatus() + ')');
                DiskCookieStore.setCookieStore(Cookies.dumpCookieStore());
                setHomeLocation();
                refreshMemberStatus();
                detectGcCustomDate();
                return StatusCode.NO_ERROR; // logged in
            }

            if (loginData.contains("your username or password is incorrect")) {
                Log.i("Failed to log in Geocaching.com as " + username + " because of wrong username/password");
                return resetGcCustomDate(StatusCode.WRONG_LOGIN_DATA); // wrong login
            }

            if (loginData.contains("You must validate your account before you can log in.")) {
                Log.i("Failed to log in Geocaching.com as " + username + " because account needs to be validated first");
                return resetGcCustomDate(StatusCode.UNVALIDATED_ACCOUNT);
            }

            Log.i("Failed to log in Geocaching.com as " + username + " for some unknown reason");
            if (retry) {
                switchToEnglish(loginData);
                return login(false, credentials);
            }

            return resetGcCustomDate(StatusCode.UNKNOWN_ERROR); // can't login
        } catch (final Exception ignored) {
            Log.e("Login.login: communication error");
            return StatusCode.CONNECTION_FAILED;
        }
    }

    public StatusCode logout() {
        try {
            final Response logoutResponse = Network.getRequest("https://www.geocaching.com/login/default.aspx?RESET=Y&redir=http%3a%2f%2fwww.geocaching.com%2fdefault.aspx%3f")
                    .blockingGet();
            final String logoutData = Network.getResponseData(logoutResponse);
            if (logoutResponse.code() == 503 && TextUtils.matches(logoutData, GCConstants.PATTERN_MAINTENANCE)) {
                return StatusCode.MAINTENANCE;
            }
        } catch (final Exception ignored) {
        }

        resetLoginStatus();
        return StatusCode.NO_ERROR;
    }

    private static String removeDotAndComma(final String str) {
        return StringUtils.replaceChars(str, ".,", null);
    }

    /**
     * Check if the user has been logged in when he retrieved the data.
     *
     * @return {@code true} if user is logged in, {@code false} otherwise
     */
    boolean getLoginStatus(@Nullable final String page) {
        if (StringUtils.isBlank(page)) {
            Log.e("Login.checkLogin: No page given");
            return false;
        }
        assert page != null;

        if (TextUtils.matches(page, GCConstants.PATTERN_MAP_LOGGED_IN)) {
            return true;
        }

        setActualStatus(CgeoApplication.getInstance().getString(R.string.init_login_popup_ok));

        // on every page except login page
        setActualLoginStatus(TextUtils.matches(page, GCConstants.PATTERN_LOGIN_NAME));
        if (isActualLoginStatus()) {
            setActualUserName(TextUtils.getMatch(page, GCConstants.PATTERN_LOGIN_NAME, true, "???"));
            int cachesCount = 0;
            try {
                cachesCount = Integer.parseInt(removeDotAndComma(TextUtils.getMatch(page, GCConstants.PATTERN_CACHES_FOUND, true, "0")));
            } catch (final NumberFormatException e) {
                Log.e("getLoginStatus: bad cache count", e);
            }
            setActualCachesFound(cachesCount);
            return true;
        }

        // login page
        setActualLoginStatus(TextUtils.matches(page, GCConstants.PATTERN_LOGIN_NAME_LOGIN_PAGE));
        if (isActualLoginStatus()) {
            setActualUserName(Settings.getUserName());
            // number of caches found is not part of this page
            return true;
        }

        setActualStatus(CgeoApplication.getInstance().getString(R.string.init_login_popup_failed));
        return false;
    }

    /**
     * Ensure that the web site is in English.
     *
     * @param previousPage
     *            the content of the last loaded page
     * @return {@code true} if a switch was necessary and successfully performed (non-English -> English)
     */
    private boolean switchToEnglish(final String previousPage) {
        if (previousPage != null && previousPage.contains(ENGLISH)) {
            Log.i("Geocaching.com language already set to English");
            // get find count
            getLoginStatus(Network.getResponseData(Network.getRequest("https://www.geocaching.com/email/")));
        } else {
            final String page = Network.getResponseData(Network.getRequest(LANGUAGE_CHANGE_URI));
            getLoginStatus(page);
            if (page == null) {
                Log.e("Failed to read viewstates to set geocaching.com language");
            }
            final String paramEnglish = TextUtils.getMatch(page, GCConstants.PATTERN_ENGLISH_SELECTION, null);
            if (paramEnglish == null) {
                Log.e("Failed to find English language selector");
            }
            // switch to English
            final Parameters params = new Parameters("__EVENTTARGET", paramEnglish, "__EVENTARGUMENT", "");
            transferViewstates(page, params);
            try {
                Network.completeWithSuccess(Network.postRequest(LANGUAGE_CHANGE_URI, params, new Parameters("Referer", LANGUAGE_CHANGE_URI)));
                Log.i("changed language on geocaching.com to English");
                return true;
            } catch (final Exception ignored) {
                Log.e("Failed to set geocaching.com language to English");
            }
        }
        return false;
    }

    /**
     * Retrieve avatar url from GC
     *
     * @return the avatar url
     */
    public String getAvatarUrl() {
        try {
            final String responseData = StringUtils.defaultString(Network.getResponseData(Network.getRequest("https://www.geocaching.com/my/")));
            final String profile = TextUtils.replaceWhitespace(responseData);

            setActualCachesFound(Integer.parseInt(removeDotAndComma(TextUtils.getMatch(profile, GCConstants.PATTERN_CACHES_FOUND, true, "-1"))));

            final String avatarURL = TextUtils.getMatch(profile, GCConstants.PATTERN_AVATAR_IMAGE_PROFILE_PAGE, false, null);
            if (avatarURL != null) {
                return avatarURL.replace("avatar", "user/large");
            }
            // No match? There may be no avatar set by user.
            Log.d("No avatar set for user");
        } catch (final Exception e) {
            Log.w("Error when retrieving user avatar url", e);
        }
        return StringUtils.EMPTY;
    }

    /**
     * Download the avatar
     *
     * @return the avatar drawable
     */
    public Observable<Drawable> downloadAvatar() {
        try {
            final String avatarURL = getAvatarUrl();
            if (!avatarURL.isEmpty()) {
                final HtmlImage imgGetter = new HtmlImage(HtmlImage.SHARED, false, false, false);
                return imgGetter.fetchDrawable(avatarURL).cast(Drawable.class);
            }
        } catch (final Exception e) {
            Log.w("Error when retrieving user avatar", e);
        }
        return null;
    }

    /**
     * Retrieve the home location
     *
     * @return a Single containing the home location, or IOException
     */
    static Single<String> retrieveHomeLocation() {
        return Network.getResponseDocument(Network.getRequest("https://www.geocaching.com/account/settings/homelocation"))
                .map(new Function<Document, String>() {
                    @Override
                    public String apply(final Document document) {
                        return document.select("input.search-coordinates").attr("value");
                    }
                });
    }

    private static void setHomeLocation() {
        retrieveHomeLocation().subscribe(new Consumer<String>() {
            @Override
            public void accept(final String homeLocationStr) throws Exception {
                if (StringUtils.isNotBlank(homeLocationStr) && !StringUtils.equals(homeLocationStr, Settings.getHomeLocation())) {
                    assert homeLocationStr != null;
                    Log.i("Setting home location to " + homeLocationStr);
                    Settings.setHomeLocation(homeLocationStr);
                }
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(final Throwable throwable) throws Exception {
                Log.w("Unable to retrieve the home location");
            }
        });
    }

    private static void refreshMemberStatus() {
        Network.getResponseDocument(Network.getRequest("https://www.geocaching.com/account/settings/membership"))
                .subscribe(new Consumer<Document>() {
                               @Override
                               public void accept(final Document document) throws Exception {
                                   final Element membership = document.select("dl.membership-details > dd:eq(3)").first();
                                   if (membership != null) {
                                       final GCMemberState memberState = GCMemberState.fromString(membership.text());
                                       Log.d("Setting member status to " + memberState);
                                       Settings.setGCMemberStatus(memberState);
                                   } else {
                                       Log.w("Cannot determine member status");
                                   }
                               }
                           },
                        new Consumer<Throwable>() {
                            @Override
                            public void accept(final Throwable throwable) throws Exception {
                                Log.w("Unable to retrieve member status", throwable);
                            }
                        });
    }

    /**
     * Detect user date settings on geocaching.com
     */
    private static void detectGcCustomDate() {
        try {
            final Document document = Network.getResponseDocument(Network.getRequest("https://www.geocaching.com/account/settings/preferences")).blockingGet();
            final String customDate = document.select("select#SelectedDateFormat option[selected]").attr("value");
            if (StringUtils.isNotBlank(customDate)) {
                Log.d("Setting GC custom date to " + customDate);
                Settings.setGcCustomDate(customDate);
            } else {
                Settings.setGcCustomDate(GCConstants.DEFAULT_GC_DATE);
                Log.w("cannot find custom date format in geocaching.com preferences page, using default");
            }
        } catch (final Exception e) {
            Settings.setGcCustomDate(GCConstants.DEFAULT_GC_DATE);
            Log.w("cannot set custom date from geocaching.com preferences page, using default", e);
        }
    }

    public static Date parseGcCustomDate(final String input, final String format) throws ParseException {
        return new SimpleDateFormat(format, Locale.ENGLISH).parse(input.trim());
    }

    static Date parseGcCustomDate(final String input) throws ParseException {
        return parseGcCustomDate(input, Settings.getGcCustomDate());
    }

    static String formatGcCustomDate(final int year, final int month, final int day) {
        return new SimpleDateFormat(Settings.getGcCustomDate(), Locale.ENGLISH).format(new GregorianCalendar(year, month - 1, day).getTime());
    }

    /**
     * checks if an Array of Strings is empty or not. Empty means:
     * - Array is null
     * - or all elements are null or empty strings
     */
    public static boolean isEmpty(final String[] a) {
        if (a == null) {
            return true;
        }

        for (final String s : a) {
            if (StringUtils.isNotEmpty(s)) {
                return false;
            }
        }
        return true;
    }

    /**
     * read all viewstates from page
     *
     * @return String[] with all view states
     */
    public static String[] getViewstates(final String page) {
        // Get the number of viewstates.
        // If there is only one viewstate, __VIEWSTATEFIELDCOUNT is not present

        if (page == null) { // no network access
            return null;
        }

        int count = 1;
        final MatcherWrapper matcherViewstateCount = new MatcherWrapper(GCConstants.PATTERN_VIEWSTATEFIELDCOUNT, page);
        if (matcherViewstateCount.find()) {
            try {
                count = Integer.parseInt(matcherViewstateCount.group(1));
            } catch (final NumberFormatException e) {
                Log.e("getViewStates", e);
            }
        }

        final String[] viewstates = new String[count];

        // Get the viewstates
        final MatcherWrapper matcherViewstates = new MatcherWrapper(GCConstants.PATTERN_VIEWSTATES, page);
        while (matcherViewstates.find()) {
            final String sno = matcherViewstates.group(1); // number of viewstate
            int no;
            if (StringUtils.isEmpty(sno)) {
                no = 0;
            } else {
                try {
                    no = Integer.parseInt(sno);
                } catch (final NumberFormatException e) {
                    Log.e("getViewStates", e);
                    no = 0;
                }
            }
            viewstates[no] = matcherViewstates.group(2);
        }

        if (viewstates.length != 1 || viewstates[0] != null) {
            return viewstates;
        }
        // no viewstates were present
        return null;
    }

    /**
     * put viewstates into request parameters
     */
    static void putViewstates(final Parameters params, final String[] viewstates) {
        if (ArrayUtils.isEmpty(viewstates)) {
            return;
        }
        params.put("__VIEWSTATE", viewstates[0]);
        if (viewstates.length > 1) {
            for (int i = 1; i < viewstates.length; i++) {
                params.put("__VIEWSTATE" + i, viewstates[i]);
            }
            params.put("__VIEWSTATEFIELDCOUNT", String.valueOf(viewstates.length));
        }
    }

    /**
     * transfers the viewstates variables from a page (response) to parameters
     * (next request)
     */
    static void transferViewstates(final String page, final Parameters params) {
        putViewstates(params, getViewstates(page));
    }

    /**
     * POST HTTP request. Do the request a second time if the user is not logged in
     *
     */
    String postRequestLogged(final String uri, final Parameters params) {
        final String data = Network.getResponseData(Network.postRequest(uri, params));

        if (getLoginStatus(data)) {
            return data;
        }

        if (login() == StatusCode.NO_ERROR) {
            return Network.getResponseData(Network.postRequest(uri, params));
        }

        Log.i("Working as guest.");
        return data;
    }

    /**
     * GET HTTP request. Do the request a second time if the user is not logged in
     *
     */
    @Nullable
    String getRequestLogged(@NonNull final String uri, @Nullable final Parameters params) {
        try {
            final Response response = Network.getRequest(uri, params).blockingGet();
            final String data = Network.getResponseData(response, canRemoveWhitespace(uri));

            // A page not found will not be found if the user logs in either
            if (response.code() == 404 || getLoginStatus(data)) {
                return data;
            }

            if (login() == StatusCode.NO_ERROR) {
                return Network.getResponseData(Network.getRequest(uri, params), canRemoveWhitespace(uri));
            }

            Log.w("Working as guest.");
            return data;
        } catch (final Exception ignored) {
            // FIXME: propagate the exception instead
            return null;
        }
    }

    /**
     * Unfortunately the cache details page contains user generated whitespace in the personal note, therefore we cannot
     * remove the white space from cache details pages.
     *
     */
    private static boolean canRemoveWhitespace(final String uri) {
        return !StringUtils.contains(uri, "cache_details");
    }

    /**
     * Get user session & session token from the Live Map. Needed for following requests.
     *
     * @return first is user session, second is session token
     */
    @NonNull
    public MapTokens getMapTokens() {
        final String data = getRequestLogged(GCConstants.URL_LIVE_MAP, null);
        final String userSession = TextUtils.getMatch(data, GCConstants.PATTERN_USERSESSION, "");
        final String sessionToken = TextUtils.getMatch(data, GCConstants.PATTERN_SESSIONTOKEN, "");
        return new MapTokens(userSession, sessionToken);
    }

}

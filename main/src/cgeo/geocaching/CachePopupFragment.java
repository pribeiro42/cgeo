package cgeo.geocaching;

import cgeo.geocaching.activity.Progress;
import cgeo.geocaching.apps.navi.NavigationAppFactory;
import cgeo.geocaching.compatibility.Compatibility;
import cgeo.geocaching.list.StoredList;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.network.Network;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.ui.CacheDetailsCreator;
import cgeo.geocaching.utils.AndroidRxUtils;
import cgeo.geocaching.utils.DisposableHandler;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.TextUtils;
import cgeo.geocaching.utils.functions.Action1;

import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Set;

import butterknife.ButterKnife;
import io.reactivex.schedulers.Schedulers;
import org.apache.commons.lang3.StringUtils;

public class CachePopupFragment extends AbstractDialogFragment {
    private final Progress progress = new Progress();

    public static DialogFragment newInstance(final String geocode) {

        final Bundle args = new Bundle();
        args.putString(GEOCODE_ARG, geocode);

        final DialogFragment f = new CachePopupFragment();
        f.setStyle(DialogFragment.STYLE_NO_TITLE, 0);
        f.setArguments(args);

        return f;
    }

    private static class StoreCacheHandler extends DisposableHandler {
        private final int progressMessage;
        private final WeakReference<CachePopupFragment> popupRef;

        StoreCacheHandler(final CachePopupFragment popup, final int progressMessage) {
            this.progressMessage = progressMessage;
            popupRef = new WeakReference<>(popup);
        }

        @Override
        public void handleRegularMessage(final Message msg) {
            if (msg.what == UPDATE_LOAD_PROGRESS_DETAIL && msg.obj instanceof String) {
                updateStatusMsg((String) msg.obj);
            } else {
                final CachePopupFragment popup = popupRef.get();
                if (popup != null) {
                    popup.init();
                }
            }
        }

        private void updateStatusMsg(final String msg) {
            final CachePopupFragment popup = popupRef.get();
            if (popup == null) {
                return;
            }
            popup.progress.setMessage(popup.getString(progressMessage)
                    + "\n\n"
                    + msg);
        }
    }

    private static class DropCacheHandler extends Handler {

        private final WeakReference<CachePopupFragment> popupRef;

        DropCacheHandler(final CachePopupFragment popup) {
            this.popupRef = new WeakReference<>(popup);
        }

        @Override
        public void handleMessage(final Message msg) {
            final CachePopupFragment popup = popupRef.get();
            if (popup == null) {
                return;
            }
            popup.getActivity().finish();
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.popup, container, false);
        initCustomActionBar(v);
        return v;
    }

    @Override
    protected void init() {
        super.init();

        try {
            if (StringUtils.isNotBlank(cache.getName())) {
                setTitle(TextUtils.coloredCacheText(cache, cache.getName()));
            } else {
                setTitle(geocode);
            }

            final View view = getView();
            assert view != null;
            final TextView titleView = ButterKnife.findById(view, R.id.actionbar_title);
            titleView.setCompoundDrawablesWithIntrinsicBounds(Compatibility.getDrawable(getResources(), cache.getType().markerId), null, null, null);

            final LinearLayout layout = ButterKnife.findById(view, R.id.details_list);
            details = new CacheDetailsCreator(getActivity(), layout);

            addCacheDetails();

            // offline use
            CacheDetailActivity.updateOfflineBox(view, cache, res, new RefreshCacheClickListener(), new DropCacheClickListener(), new StoreCacheClickListener(), null, new StoreCacheClickListener());

            CacheDetailActivity.updateCacheLists(view, cache, res);
        } catch (final Exception e) {
            Log.e("CachePopupFragment.init", e);
        }

        // cache is loaded. remove progress-popup if any there
        progress.dismiss();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (super.onOptionsItemSelected(item)) {
            return true;
        }

        final int menuItem = item.getItemId();

        switch (menuItem) {
            case R.id.menu_delete:
                new DropCacheClickListener().onClick(getView());
                return true;
        }
        return false;
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        init();
    }

    private class StoreCacheClickListener implements View.OnClickListener, View.OnLongClickListener {
        @Override
        public void onClick(final View arg0) {
            selectListsAndStore(false);
        }

        @Override
        public boolean onLongClick(final View v) {
            selectListsAndStore(true);
            return true;
        }

        private void selectListsAndStore(final boolean fastStoreOnLastSelection) {
            if (progress.isShowing()) {
                showToast(res.getString(R.string.err_detail_still_working));
                return;
            }

            if (Settings.getChooseList() || cache.isOffline()) {
                // let user select list to store cache in
                new StoredList.UserInterface(getActivity()).promptForMultiListSelection(R.string.lists_title,
                        new Action1<Set<Integer>>() {
                            @Override
                            public void call(final Set<Integer> selectedListIds) {
                                storeCacheOnLists(selectedListIds);
                            }
                        }, true, cache.getLists(), fastStoreOnLastSelection);
            } else {
                storeCacheOnLists(Collections.singleton(StoredList.STANDARD_LIST_ID));
            }
        }

        private void storeCacheOnLists(final Set<Integer> listIds) {
            if (cache.isOffline()) {
                // cache already offline, just add to another list
                DataStore.saveLists(Collections.singletonList(cache), listIds);
                CacheDetailActivity.updateOfflineBox(getView(), cache, res,
                        new RefreshCacheClickListener(), new DropCacheClickListener(),
                        new StoreCacheClickListener(), null, new StoreCacheClickListener());
                CacheDetailActivity.updateCacheLists(getView(), cache, res);
            } else {
                final StoreCacheHandler storeCacheHandler = new StoreCacheHandler(CachePopupFragment.this, R.string.cache_dialog_offline_save_message);
                final FragmentActivity activity = getActivity();
                progress.show(activity, res.getString(R.string.cache_dialog_offline_save_title), res.getString(R.string.cache_dialog_offline_save_message), true, storeCacheHandler.disposeMessage());
                AndroidRxUtils.andThenOnUi(Schedulers.io(), new Runnable() {
                    @Override
                    public void run() {
                        cache.store(listIds, storeCacheHandler);
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                        activity.supportInvalidateOptionsMenu();
                        final View view = getView();
                        if (view != null) {
                            CacheDetailActivity.updateOfflineBox(view, cache, res,
                                    new RefreshCacheClickListener(), new DropCacheClickListener(),
                                    new StoreCacheClickListener(), null, new StoreCacheClickListener());
                            CacheDetailActivity.updateCacheLists(view, cache, res);
                        }
                    }
                });
            }
        }
    }

    private class RefreshCacheClickListener implements View.OnClickListener {
        @Override
        public void onClick(final View arg0) {
            if (progress.isShowing()) {
                showToast(res.getString(R.string.err_detail_still_working));
                return;
            }

            if (!Network.isConnected()) {
                showToast(getString(R.string.err_server));
                return;
            }

            final StoreCacheHandler refreshCacheHandler = new StoreCacheHandler(CachePopupFragment.this, R.string.cache_dialog_offline_save_message);
            progress.show(getActivity(), res.getString(R.string.cache_dialog_refresh_title), res.getString(R.string.cache_dialog_refresh_message), true, refreshCacheHandler.disposeMessage());
            cache.refresh(refreshCacheHandler, AndroidRxUtils.networkScheduler);
        }
    }

    private class DropCacheClickListener implements View.OnClickListener {
        @Override
        public void onClick(final View arg0) {
            if (progress.isShowing()) {
                showToast(res.getString(R.string.err_detail_still_working));
                return;
            }

            final DropCacheHandler dropCacheHandler = new DropCacheHandler(CachePopupFragment.this);
            progress.show(getActivity(), res.getString(R.string.cache_dialog_offline_drop_title), res.getString(R.string.cache_dialog_offline_drop_message), true, null);
            cache.drop(dropCacheHandler);
        }
    }


    @Override
    public void navigateTo() {
        NavigationAppFactory.startDefaultNavigationApplication(1, getActivity(), cache);
    }

    @Override
    public void showNavigationMenu() {
        NavigationAppFactory.showNavigationMenu(getActivity(), cache, null, null, true, true);
    }


    /**
     * Tries to navigate to the {@link Geocache} of this activity.
     */
    @Override
    protected void startDefaultNavigation2() {
        if (cache == null || cache.getCoords() == null) {
            showToast(res.getString(R.string.cache_coordinates_no));
            return;
        }
        NavigationAppFactory.startDefaultNavigationApplication(2, getActivity(), cache);
        getActivity().finish();
    }

    @Override
    protected TargetInfo getTargetInfo() {
        if (cache == null) {
            return null;
        }
        return new TargetInfo(cache.getCoords(), cache.getGeocode());
    }


}

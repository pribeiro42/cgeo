package cgeo.geocaching.command;

import android.app.Activity;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Collection;

import cgeo.geocaching.R;
import cgeo.geocaching.list.AbstractList;
import cgeo.geocaching.list.StoredList;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.utils.functions.Action1;

public abstract class CopyToListCommand extends AbstractCachesCommand {

    private final int sourceListId;
    private int targetListId;

    protected CopyToListCommand(@NonNull final Activity context, @NonNull final Collection<Geocache> caches, final int sourceListId) {
        super(context, caches, R.string.command_copy_caches_progress);
        this.sourceListId = sourceListId;
    }

    @Override
    public void execute() {
        // as we cannot show the dialog inside the background doCommand, we override execute to ask in the UI thread
        new StoredList.UserInterface(getContext()).promptForListSelection(R.string.cache_menu_copy_list, new Action1<Integer>() {


            @Override
            public void call(final Integer newListId) {
                CopyToListCommand.this.targetListId = newListId;
                final AbstractList list = AbstractList.getListById(newListId);
                if (list != null) {
                    final String newListName = list.getTitle();
                    setProgressMessage(getContext().getString(R.string.command_copy_caches_progress, newListName));
                    CopyToListCommand.super.execute();
                }
            }
        }, true, sourceListId);
    }

    @Override
    protected void doCommand() {
        DataStore.addToList(getCaches(), targetListId);
    }

    @Override
    protected void undoCommand() {
        DataStore.removeFromList(getCaches(), targetListId);
    }

    @Override
    @Nullable
    protected String getResultMessage() {
        final int size = getCaches().size();
        return getContext().getResources().getQuantityString(R.plurals.command_copy_caches_result, size, size);
    }
}

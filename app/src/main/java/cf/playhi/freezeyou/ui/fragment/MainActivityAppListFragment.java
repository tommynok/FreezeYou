package cf.playhi.freezeyou.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.adapter.MainAppListAdapter;
import cf.playhi.freezeyou.model.AppInfoItem;

/**
 * The application list on the main screen, in either a single column or a grid.
 *
 * <p>It used to host a {@code ListView} or a {@code GridView} and hand them the multi-choice work:
 * {@code CHOICE_MODE_MULTIPLE_MODAL} started an action mode on long press, and the widget reported
 * each toggle through {@code onItemCheckedStateChanged}. {@code RecyclerView} has no such mode, so
 * the choice lives here: a long press or a tap while selecting toggles one package, the first
 * non-empty toggle opens the action mode, and the last one out of it closes it.
 *
 * <p>The selected packages themselves still belong to the activity, which has a dozen batch actions
 * that add to the same list. This fragment is handed that list and only mirrors it into the rows.
 */
public class MainActivityAppListFragment extends Fragment {

    /**
     * The activity's half of the action mode: what the menu holds, and what each entry does.
     * Shaped like {@code ActionMode.Callback} minus the item-checked method, because selection
     * state is no longer the widget's to report.
     */
    public interface SelectionActions {
        boolean onCreateActionMode(@NonNull ActionMode mode, @NonNull Menu menu);

        boolean onPrepareActionMode(@NonNull ActionMode mode, @NonNull Menu menu);

        boolean onActionItemClicked(@NonNull ActionMode mode, @NonNull MenuItem item);

        void onDestroyActionMode(@NonNull ActionMode mode);
    }

    private boolean mUseGridMode = false;

    @Nullable
    private RecyclerView mAppListRecyclerView;

    @Nullable
    private MainAppListAdapter mAppListAdapter;

    @Nullable
    private ActionMode mActionMode;

    @Nullable
    private SelectionActions mSelectionActions;

    @Nullable
    private MainAppListAdapter.OnRowClickListener mRowClickListener;

    /** The activity's own list, so batch actions and this fragment change the same selection. */
    @NonNull
    private Collection<String> mSelectedPackages = new ArrayList<>();

    private final ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mActionMode = mode;
            return mSelectionActions == null
                    || mSelectionActions.onCreateActionMode(mode, menu);
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return mSelectionActions != null && mSelectionActions.onPrepareActionMode(mode, menu);
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return mSelectionActions != null && mSelectionActions.onActionItemClicked(mode, item);
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
            if (mSelectionActions != null) {
                mSelectionActions.onDestroyActionMode(mode);
            }
            refreshSelection();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(
                mUseGridMode ? R.layout.main_app_grid_fragment : R.layout.main_app_list_fragment,
                container, false);
        mAppListRecyclerView = view.findViewById(R.id.main_appList_recyclerView);
        mAppListRecyclerView.setLayoutManager(buildLayoutManager(mAppListRecyclerView.getContext()));
        mAppListRecyclerView.setAdapter(mAppListAdapter);
        if (mAppListAdapter != null) {
            mAppListAdapter.setOnRowClickListener(mRowClickListener);
        }
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mAppListRecyclerView = null;
        if (mActionMode != null) {
            // Leaving without this would hand a dead window token back on the next toggle.
            mActionMode.finish();
            mActionMode = null;
        }
    }

    public void setUseGridMode(boolean useGridMode) {
        mUseGridMode = useGridMode;
    }

    public void setSelectionActions(@Nullable SelectionActions actions) {
        mSelectionActions = actions;
    }

    /**
     * Hands the activity's selection list to the fragment and the adapter, then replaces the rows.
     *
     * <p>The adapter outlives a rebuild of the list, which is the point: DiffUtil can then see that
     * only one row changed instead of being handed a fresh adapter with no previous list to compare
     * against.
     */
    @NonNull
    public MainAppListAdapter bindAdapter(
            @NonNull Context context,
            @NonNull Collection<String> selectedPackages,
            @NonNull List<AppInfoItem> items,
            int disabledDotRes,
            int enabledDotRes,
            boolean greyFrozenIcons
    ) {
        mSelectedPackages = selectedPackages;

        if (mAppListAdapter == null) {
            mAppListAdapter = new MainAppListAdapter(
                    context, mUseGridMode, mSelectedPackages,
                    disabledDotRes, enabledDotRes, greyFrozenIcons);
            mAppListAdapter.setOnRowClickListener(mRowClickListener);
        } else {
            mAppListAdapter.setFrozenDots(disabledDotRes, enabledDotRes);
            mAppListAdapter.setGreyFrozenIcons(greyFrozenIcons);
        }
        mAppListAdapter.submit(items);
        mAppListAdapter.syncSelection(mSelectedPackages);

        if (mAppListRecyclerView != null && mAppListRecyclerView.getAdapter() != mAppListAdapter) {
            mAppListRecyclerView.setAdapter(mAppListAdapter);
        }
        return mAppListAdapter;
    }

    @Nullable
    public MainAppListAdapter getAppListAdapter() {
        return mAppListAdapter;
    }

    public void setOnRowClickListener(@Nullable MainAppListAdapter.OnRowClickListener listener) {
        mRowClickListener = listener;
        if (mAppListAdapter != null) {
            mAppListAdapter.setOnRowClickListener(listener);
        }
    }

    /**
     * Toggles one package. The first selection opens the action mode and the last one out of it
     * closes it, which is what the widget used to do on its own.
     */
    public void toggleSelection(@NonNull AppInfoItem item) {
        if (!item.isPackageRow()) {
            return;
        }
        String packageName = item.getPackageName();
        if (!mSelectedPackages.remove(packageName)) {
            mSelectedPackages.add(packageName);
            if (mActionMode == null) {
                mActionMode = startActionMode();
            }
        }
        if (mSelectedPackages.isEmpty()) {
            if (mActionMode != null) {
                mActionMode.finish();
            }
            mActionMode = null;
            refreshSelection();
            return;
        }
        refreshSelection();
        if (mActionMode != null) {
            mActionMode.setTitle(Integer.toString(mSelectedPackages.size()));
        }
    }

    public boolean isSelecting() {
        return mActionMode != null;
    }

    /** Re-reads the activity's list into the rows, for the batch actions that change it directly. */
    public void refreshSelection() {
        if (mAppListAdapter != null) {
            mAppListAdapter.syncSelection(mSelectedPackages);
        }
        if (mActionMode != null) {
            mActionMode.setTitle(Integer.toString(mSelectedPackages.size()));
        }
    }

    /** Called by the activity's batch actions when they are done with the whole selection. */
    public void finishSelection() {
        if (mActionMode != null) {
            mActionMode.finish();
            mActionMode = null;
        }
        mSelectedPackages.clear();
        refreshSelection();
    }

    @Nullable
    public ActionMode getSelectionActionMode() {
        return mActionMode;
    }

    public void clearSelection() {
        mSelectedPackages.clear();
        refreshSelection();
    }

    @NonNull
    private RecyclerView.LayoutManager buildLayoutManager(@NonNull Context context) {
        if (!mUseGridMode) {
            return new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        }
        return new GridLayoutManager(context, gridColumnCount(context));
    }

    /**
     * Mirrors what {@code GridView} with {@code numColumns="auto_fit"} did: columns one icon wide
     * with room for its label, as many as the width allows.
     */
    private int gridColumnCount(@NonNull Context context) {
        int columnWidth = (int) (context.getResources()
                .getDimension(android.R.dimen.app_icon_size) * 1.6f);
        if (columnWidth <= 0) {
            return 1;
        }
        int width = mAppListRecyclerView != null && mAppListRecyclerView.getWidth() > 0
                ? mAppListRecyclerView.getWidth()
                : context.getResources().getDisplayMetrics().widthPixels;
        return Math.max(1, width / columnWidth);
    }

    @Nullable
    private ActionMode startActionMode() {
        Context context = getContext();
        if (context instanceof AppCompatActivity) {
            return ((AppCompatActivity) context).startSupportActionMode(mActionModeCallback);
        }
        return null;
    }
}

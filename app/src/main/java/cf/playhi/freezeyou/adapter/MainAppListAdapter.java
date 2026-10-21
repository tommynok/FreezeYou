package cf.playhi.freezeyou.adapter;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Future;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.model.AppInfoItem;

/**
 * The main application list.
 *
 * <p>It replaces {@code MainAppListSimpleAdapter}, which was a {@code SimpleAdapter} over
 * {@code Map<String, Object>} rows. Every change to the list — one app frozen, the search box
 * cleared, the greyed-icons setting turned on — went through {@code notifyDataSetChanged()}, so the
 * widget re-bound and re-measured every visible row, and every row kept the app's decoded icon
 * alive whether or not it was on screen.
 *
 * <p>Rows are diffed now, so a single status change re-binds one row, and icons are asked for per
 * binding by {@link AppIconLoader} instead of arriving inside the data.
 */
public class MainAppListAdapter
        extends RecyclerView.Adapter<MainAppListAdapter.AppViewHolder> {

    /**
     * Frozen applications are greyed out by filtering the ImageView rather than by desaturating the
     * icon itself: a filter costs nothing and is shared by every row, while a desaturated copy
     * means one more ARGB_8888 bitmap per frozen application.
     */
    private static final ColorMatrixColorFilter GREYSCALE;

    static {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0f);
        GREYSCALE = new ColorMatrixColorFilter(matrix);
    }

    private static final DiffUtil.ItemCallback<AppInfoItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<AppInfoItem>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull AppInfoItem oldItem, @NonNull AppInfoItem newItem
                ) {
                    return oldItem.getPackageName().equals(newItem.getPackageName());
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull AppInfoItem oldItem, @NonNull AppInfoItem newItem
                ) {
                    return oldItem.sameContentAs(newItem);
                }
            };

    /** What a tap and a long press mean. The adapter has no opinion, the activity decides. */
    public interface OnRowClickListener {
        void onItemClick(@NonNull View view, int position, @NonNull AppInfoItem item);

        void onItemLongClick(@NonNull View view, int position, @NonNull AppInfoItem item);
    }

    private final AsyncListDiffer<AppInfoItem> mDiffer = new AsyncListDiffer<AppInfoItem>(
            this, new AsyncDifferConfig.Builder<AppInfoItem>(DIFF_CALLBACK).build());

    private final LayoutInflater mInflater;
    private final Context mContext;
    private final int mItemLayout;

    /** Every row the current filter produced, before the search box narrowed it. */
    private final ArrayList<AppInfoItem> mFullList = new ArrayList<>();

    /** The rows actually shown; this is what {@link AsyncListDiffer} is fed. */
    private final ArrayList<AppInfoItem> mDisplayList = new ArrayList<>();

    /**
     * Packages the caller has selected. Kept as a set because binding asks per row, and the old
     * adapter asked a {@code List} the same question with {@code contains()}.
     */
    private final Set<String> mSelected = new HashSet<>();

    @Nullable
    private String mQuery;

    @Nullable
    private OnRowClickListener mRowClickListener;

    private int mDisabledDotRes;
    private int mEnabledDotRes;
    private boolean mGreyFrozenIcons;
    private boolean mHasSelection;

    public MainAppListAdapter(
            @NonNull Context context,
            boolean gridMode,
            @NonNull Collection<String> selectedPackages,
            int disabledDotRes,
            int enabledDotRes,
            boolean greyFrozenIcons
    ) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mItemLayout = gridMode ? R.layout.main_grid_main_item : R.layout.app_list_1;
        mDisabledDotRes = disabledDotRes;
        mEnabledDotRes = enabledDotRes;
        mGreyFrozenIcons = greyFrozenIcons;
        mSelected.addAll(selectedPackages);
        mHasSelection = !selectedPackages.isEmpty();
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new AppViewHolder(mInflater.inflate(mItemLayout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        holder.bind(getItemAt(position));
    }

    @Override
    public void onViewRecycled(@NonNull AppViewHolder holder) {
        super.onViewRecycled(holder);
        holder.recycle();
    }

    @Override
    public int getItemCount() {
        return mDiffer.getCurrentList().size();
    }

    @NonNull
    public AppInfoItem getItemAt(int position) {
        return mDiffer.getCurrentList().get(position);
    }

    /** Position of a package in the list being shown, or -1. */
    public int positionOfPackage(@NonNull String packageName) {
        List<AppInfoItem> shown = mDiffer.getCurrentList();
        for (int i = 0; i < shown.size(); i++) {
            if (packageName.equals(shown.get(i).getPackageName())) {
                return i;
            }
        }
        return -1;
    }

    public void setOnRowClickListener(@Nullable OnRowClickListener listener) {
        mRowClickListener = listener;
    }

    /** Replaces the whole list, keeping whatever the search box currently asks for. */
    public void submit(@NonNull List<AppInfoItem> items) {
        // Saved and user-defined lists are comma-joined text and can carry the same package twice.
        // The diff treats the package name as identity, so a second copy of one is reported as an
        // inconsistency rather than as two rows.
        Set<String> seen = new HashSet<>(Math.max(16, items.size() * 2));
        mFullList.clear();
        for (AppInfoItem item : items) {
            if (seen.add(item.getPackageName())) {
                mFullList.add(item);
            }
        }
        publish();
    }

    /**
     * Swaps one row for a changed copy and re-diffs. Mutating a row in place would leave the diff
     * comparing two equal lists and reporting nothing to change.
     */
    public void updateItem(@NonNull AppInfoItem item) {
        for (int i = 0; i < mFullList.size(); i++) {
            if (mFullList.get(i).getPackageName().equals(item.getPackageName())) {
                mFullList.set(i, item);
                publish();
                return;
            }
        }
    }

    /** Produces the replacement for one row. */
    public interface RowUpdater {
        @NonNull
        AppInfoItem update(@NonNull AppInfoItem row);
    }

    /**
     * Replaces rows through {@code updater} and re-diffs once, however many rows changed.
     *
     * @param targetPackages the packages to rewrite, or null to rewrite every row
     */
    public void updateRows(
            @Nullable Collection<String> targetPackages, @NonNull RowUpdater updater
    ) {
        Set<String> targets = targetPackages == null ? null : new HashSet<>(targetPackages);
        for (int i = 0; i < mFullList.size(); i++) {
            AppInfoItem row = mFullList.get(i);
            if (targets == null || targets.contains(row.getPackageName())) {
                mFullList.set(i, updater.update(row));
            }
        }
        publish();
    }

    /**
     * Every row the current filter produced, search box aside. Callers that ask about rows they may
     * have selected need the whole list: a selection made before typing in the search box still has
     * to be understood by the batch actions that run while those rows are hidden.
     */
    @NonNull
    public List<AppInfoItem> getRows() {
        return Collections.unmodifiableList(mFullList);
    }

    /**
     * Narrows the shown rows the way the search box always has: a match on the name you read comes
     * before a match buried in the package name, so typing "droid" does not bury Droid-ify under
     * every {@code com.android.*} package on the device.
     */
    public void applySearch(@Nullable CharSequence query) {
        mQuery = TextUtils.isEmpty(query)
                ? null
                : query.toString().toLowerCase(Locale.ROOT).trim();
        publish();
    }

    public void setGreyFrozenIcons(boolean greyFrozenIcons) {
        if (mGreyFrozenIcons != greyFrozenIcons) {
            mGreyFrozenIcons = greyFrozenIcons;
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    /** The theme's own dots; a trip to the settings can change them without changing the rows. */
    public void setFrozenDots(int disabledDotRes, int enabledDotRes) {
        if (mDisabledDotRes != disabledDotRes || mEnabledDotRes != enabledDotRes) {
            mDisabledDotRes = disabledDotRes;
            mEnabledDotRes = enabledDotRes;
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    /**
     * Re-reads the caller's selection. Selection lives in the activity, because a dozen batch
     * actions add to it directly, so the adapter is told when to look again rather than being the
     * place the choice is stored.
     *
     * <p>Only the rows whose background actually changed are re-bound. Telling the whole range to
     * refresh is what the old adapter did for a single tap, and it cost a re-decode of every icon
     * on screen.
     */
    public void syncSelection(@NonNull Collection<String> selectedPackages) {
        Set<String> selected = new HashSet<>(Math.max(16, selectedPackages.size() * 2));
        selected.addAll(selectedPackages);

        Set<String> added = new HashSet<>(selected);
        added.removeAll(mSelected);
        Set<String> removed = new HashSet<>(mSelected);
        removed.removeAll(selected);

        mSelected.clear();
        mSelected.addAll(selected);
        mHasSelection = !selected.isEmpty();

        for (String packageName : added) {
            notifyRowChanged(positionOfPackage(packageName));
        }
        for (String packageName : removed) {
            notifyRowChanged(positionOfPackage(packageName));
        }
    }

    public boolean isItemSelected(@NonNull String packageName) {
        return mSelected.contains(packageName);
    }

    public void notifyRowChanged(int position) {
        if (position >= 0) {
            notifyItemChanged(position);
        }
    }

    private void publish() {
        if (mQuery == null) {
            mDisplayList.clear();
            mDisplayList.addAll(mFullList);
        } else {
            filterInto(mQuery);
        }
        mDiffer.submitList(new ArrayList<>(mDisplayList));
    }

    private void filterInto(@NonNull String prefix) {
        ArrayList<AppInfoItem> nameStartsWith = new ArrayList<>(mFullList.size());
        ArrayList<AppInfoItem> nameContains = new ArrayList<>(mFullList.size());
        ArrayList<AppInfoItem> packageOnly = new ArrayList<>(mFullList.size());
        for (AppInfoItem item : mFullList) {
            String name = item.getLabel();
            String lowerName = name == null ? null : name.toLowerCase(Locale.ROOT);
            String lowerPackage = item.getPackageName().toLowerCase(Locale.ROOT);
            if (lowerName != null && lowerName.startsWith(prefix)) {
                nameStartsWith.add(item);
            } else if (lowerName != null && lowerName.contains(prefix)) {
                nameContains.add(item);
            } else if (lowerPackage.contains(prefix)) {
                packageOnly.add(item);
            }
        }
        mDisplayList.clear();
        mDisplayList.addAll(nameStartsWith);
        mDisplayList.addAll(nameContains);
        mDisplayList.addAll(packageOnly);
    }

    /** One row of either the list or the grid layout; the grid has neither package name nor dot. */
    public class AppViewHolder extends RecyclerView.ViewHolder {

        @Nullable
        private final ImageView mIcon;
        @Nullable
        private final TextView mName;
        @Nullable
        private final TextView mPackageName;
        @Nullable
        private final ImageView mDot;

        @Nullable
        private Future<?> mIconFuture;

        /**
         * Icon answers arrive from a worker thread, by which time the row that asked may have been
         * rebound. A row that no longer holds this token has no use for the answer.
         */
        private int mBindingToken;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            boolean grid = mItemLayout == R.layout.main_grid_main_item;
            mIcon = itemView.findViewById(grid ? R.id.mgmi_imageView : R.id.img);
            mName = itemView.findViewById(grid ? R.id.mgmi_textView : R.id.name);
            mPackageName = grid ? null : itemView.findViewById(R.id.pkgName);
            mDot = grid ? null : itemView.findViewById(R.id.isFrozen);
            itemView.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && mRowClickListener != null) {
                    mRowClickListener.onItemClick(view, position, getItemAt(position));
                }
            });
            itemView.setOnLongClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && mRowClickListener != null) {
                    mRowClickListener.onItemLongClick(view, position, getItemAt(position));
                    return true;
                }
                return false;
            });
        }

        void bind(@NonNull AppInfoItem item) {
            final int token = ++mBindingToken;
            final boolean frozen = item.isFrozenForDisplay();

            if (mName != null) {
                mName.setText(item.getLabel());
            }
            if (mPackageName != null) {
                mPackageName.setText(item.getPackageName());
            }
            if (mDot != null) {
                mDot.setImageResource(frozen ? mDisabledDotRes : mEnabledDotRes);
            }
            itemView.setBackgroundResource(
                    mHasSelection && mSelected.contains(item.getPackageName())
                            ? R.color.translucentGreyBackground
                            : 0);

            if (mIcon != null) {
                // Views are recycled and setColorFilter leaves the filter in place, so the row has
                // to clear it as explicitly as it sets it. An uninstalled system app is greyed
                // whatever the setting says: it has no icon to speak of and nothing to run.
                boolean grey = item.isRestorableSystemApp()
                        || (frozen && mGreyFrozenIcons);
                mIcon.setColorFilter(grey ? GREYSCALE : null);
                mIcon.setImageDrawable(null);
                cancelIconRequest();
                mIconFuture = AppIconLoader.load(mContext, item.getIconCacheKey(), icon -> {
                    if (token == mBindingToken && mIcon != null) {
                        mIcon.setImageDrawable(icon);
                    }
                });
            }
        }

        void recycle() {
            ++mBindingToken;
            cancelIconRequest();
            if (mIcon != null) {
                mIcon.setImageDrawable(null);
                mIcon.setColorFilter(null);
            }
        }

        private void cancelIconRequest() {
            if (mIconFuture != null) {
                mIconFuture.cancel(true);
                mIconFuture = null;
            }
        }
    }
}

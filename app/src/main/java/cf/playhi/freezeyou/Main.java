package cf.playhi.freezeyou;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.Adapter;
import android.widget.EditText;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.util.TypedValue;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;

import cf.playhi.freezeyou.adapter.MainAppListSimpleAdapter;
import cf.playhi.freezeyou.app.FreezeYouBaseActivity;
import cf.playhi.freezeyou.receiver.InstallPackagesFinishedReceiver;
import cf.playhi.freezeyou.service.ForceStopService;
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage;
import cf.playhi.freezeyou.ui.AboutActivity;
import cf.playhi.freezeyou.ui.BackupMainActivity;
import cf.playhi.freezeyou.ui.FirstTimeSetupActivity;
import cf.playhi.freezeyou.ui.OneKeyScreenLockImmediatelyActivity;
import cf.playhi.freezeyou.ui.ScheduledTasksManageActivity;
import cf.playhi.freezeyou.ui.SettingsActivity;
import cf.playhi.freezeyou.ui.ShortcutLauncherFolderActivity;
import cf.playhi.freezeyou.ui.fragment.MainActivityAppListFragment;
import cf.playhi.freezeyou.utils.AccessibilityUtils;
import cf.playhi.freezeyou.utils.AndroidUserUtils;
import cf.playhi.freezeyou.utils.LauncherShortcutUtils;
import cf.playhi.freezeyou.utils.CriticalPackagesUtils;
import cf.playhi.freezeyou.utils.DevicePolicyManagerUtils;
import cf.playhi.freezeyou.utils.PrivilegedShellUtils;
import cf.playhi.freezeyou.utils.RunningAppsUtils;
import cf.playhi.freezeyou.utils.FUFUtils;
import cf.playhi.freezeyou.utils.LogSharingUtils;
import cf.playhi.freezeyou.utils.OneKeyListUtils;
import cf.playhi.freezeyou.utils.ServiceUtils;
import cf.playhi.freezeyou.utils.ShortcutTokenUtils;
import cf.playhi.freezeyou.utils.TasksUtils;
import cf.playhi.freezeyou.utils.RestoreUtils;
import cf.playhi.freezeyou.utils.ShizukuPackageInstaller;
import cf.playhi.freezeyou.utils.UninstallPolicyUtils;

import static cf.playhi.freezeyou.app.FreezeYouAlertDialogBuilderKt.FreezeYouAlertDialogBuilder;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.hideUninstallSuccessDialog;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.lesserToast;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.showInRecents;
import static cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys.cacheApplicationsIcons;
import static cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys.greyFrozenApplicationsIcons;
import static cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys.noCaution;
import static cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys.saveOnClickFunctionStatus;
import static cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys.saveSortMethodStatus;
import static cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageStringKeys.launchMode;
import static cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageStringKeys.mainActivityPattern;
import static cf.playhi.freezeyou.utils.AlertDialogUtils.buildAlertDialog;
import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon;
import static cf.playhi.freezeyou.utils.ApplicationInfoUtils.getApplicationInfoFromPkgName;
import static cf.playhi.freezeyou.utils.ApplicationLabelUtils.getApplicationLabel;
import static cf.playhi.freezeyou.utils.ClipboardUtils.copyToClipboard;
import static cf.playhi.freezeyou.utils.FUFUtils.askRun;
import static cf.playhi.freezeyou.utils.FUFUtils.processSingleActionInProcess;
import static cf.playhi.freezeyou.utils.FUFUtils.realGetFrozenStatus;
import static cf.playhi.freezeyou.utils.LauncherShortcutUtils.checkSettingsAndRequestCreateShortcut;
import static cf.playhi.freezeyou.utils.LauncherShortcutUtils.createShortCut;
import static cf.playhi.freezeyou.utils.MoreUtils.processListFilter;
import static cf.playhi.freezeyou.utils.MoreUtils.requestOpenWebSite;
import static cf.playhi.freezeyou.utils.OneKeyListUtils.addToOneKeyList;
import static cf.playhi.freezeyou.utils.OneKeyListUtils.removeFromOneKeyList;
import static cf.playhi.freezeyou.utils.Support.showChooseActionPopupMenu;
import static cf.playhi.freezeyou.utils.ThemeUtils.getThemeDot;
import static cf.playhi.freezeyou.utils.ThemeUtils.getThemeFabDotBackground;
import static cf.playhi.freezeyou.utils.ThemeUtils.getThemeSecondDot;
import static cf.playhi.freezeyou.utils.ThemeUtils.getLightPopupContext;
import static cf.playhi.freezeyou.utils.ThemeUtils.getUiTheme;
import static cf.playhi.freezeyou.utils.ThemeUtils.isLightUiTheme;
import static cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme;
import static cf.playhi.freezeyou.utils.ToastUtils.showToast;
import static cf.playhi.freezeyou.utils.VersionUtils.checkUpdate;
import static cf.playhi.freezeyou.utils.VersionUtils.getVersionCode;
import static cf.playhi.freezeyou.utils.VersionUtils.isOutdated;

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
public class Main extends FreezeYouBaseActivity {

    private final static int APPListViewOnClickMode_chooseAction = 0;
    private final static int APPListViewOnClickMode_autoUFOrFreeze = 1;
    private final static int APPListViewOnClickMode_freezeImmediately = 2;
    private final static int APPListViewOnClickMode_UFImmediately = 3;
    private final static int APPListViewOnClickMode_addToOFList = 4;
    private final static int APPListViewOnClickMode_removeFromOFList = 5;
    private final static int APPListViewOnClickMode_addToOUFList = 6;
    private final static int APPListViewOnClickMode_removeFromOUFList = 7;
    private final static int APPListViewOnClickMode_addToFOQList = 8;
    private final static int APPListViewOnClickMode_removeFromFOQList = 9;
    private final static int APPListViewOnClickMode_UFAndRun = 10;
    private final static int APPListViewOnClickMode_autoUFOrFreezeAndRun = 11;
    private final static int APPListViewOnClickMode_createFUFShortcut = 12;

    private final static int SORT_BY_DEFAULT = 0;
    private final static int SORT_BY_NO = 1;
    private final static int SORT_BY_UF_ASCENDING = 2;
    private final static int SORT_BY_UF_DESCENDING = 3;
    private final static int SORT_BY_FF_ASCENDING = 4;
    private final static int SORT_BY_FF_DESCENDING = 5;
    private final static int SORT_BY_US_ASCENDING = 6;
    private final static int SORT_BY_US_DESCENDING = 7;
    private final static int SORT_BY_ALPHABETICAL = 8;
    private final static int SORT_BY_LAST_INSTALLED = 9;
    private final static int SORT_BY_LAST_UPDATED = 10;

    private final ArrayList<String> selectedPackages = new ArrayList<>();
    private ActionMode currentSelectionActionMode;
    private AbsListView.MultiChoiceModeListener currentMultiChoiceModeListener;
    private int appListViewOnClickMode = APPListViewOnClickMode_chooseAction;
    // Both are replaced in onCreate by the theme's own dots; these are only what the row uses if
    // that call fails, so they no longer name a coloured palette that the themes no longer have.
    private int customThemeDisabledDot = R.drawable.shapedotblack;
    private int customThemeEnabledDot = R.drawable.shapedotblack;
    private BroadcastReceiver updateFrozenStatusBroadcastReceiver;
    private BroadcastReceiver mPackageChangeReceiver;
    // The adapter only holds the rows the search shows; menu eligibility and force-stop targets
    // must follow the whole selection, including packages the query hides.
    private volatile List<Map<String, Object>> mCurrentUnfilteredAppList;
    private volatile String currentFilter = "all";
    // The base64 label of the custom category the list currently shows, or null for any other
    // filter. A selection-menu edit has no other way to tell whether its category is still on
    // screen when it finishes.
    private volatile String currentCategoryLabel = null;
    private volatile int currentSortRule = SORT_BY_DEFAULT;
    private boolean needProcessOnItemCheckedStateChanged = true;

    // The launcher's pin dialog pauses this activity between two shortcuts, so a batch has to
    // survive resumes. It used to walk selectedPackages itself, which crashed the screen as soon
    // as the user left the selection mode while a dialog was still open; this is a snapshot taken
    // when the batch starts and read forward from.
    private List<String> mPendingShortcutPackages = null;
    private int mPendingShortcutIndex = 0;

    private boolean isGridMode = false;
    private boolean restoreFilterFromSavedState = false;
    private volatile boolean filterEstablished = false;
    private boolean mRestoreSelectionAfterFragmentRebuild = false;

    private MainActivityAppListFragment mMainActivityAppListFragment;
    private final Queue<String> mUnprivilegedUninstallQueue = new LinkedList<>();
    private String mCurrentUnprivilegedUninstallPkg = null;
    private int mUnprivilegedUninstallSuccessCount = 0;

    private final AtomicInteger mListGeneration = new AtomicInteger(0);
    // A full status snapshot must not apply after a newer one, and must not overwrite the rows
    // a single-package broadcast patched while it was still being computed.
    private final AtomicInteger mStatusRefreshGeneration = new AtomicInteger(0);
    private final Set<String> mStatusPatchedDuringFullRefresh =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private TextWatcher mSearchTextWatcher = null;
    private ImageButton mMoreSettingsImageButton;
    private ImageButton mSelectionActionsImageButton;
    // A popup left open when the activity pauses is restored with the window and then torn down
    // on focus loss, which flashes the dimming layer on the way back into the app.
    private PopupMenu mActivePopupMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        processSetTheme(this);
        if (isLightUiTheme(this)) {
            setTheme(R.style.AppTheme_Default_Main);
        }
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            // recreate() keeps the intent that started the activity, so a shortcut's filter would
            // otherwise win over the filter the user had switched to before the grid/list toggle.
            currentFilter = savedInstanceState.getString("currentFilter", currentFilter);
            // The category the filter came from travels with it: without the label the recreated
            // list is still the category's packages, but no edit can tell that it is on screen.
            currentCategoryLabel = savedInstanceState.getString("currentCategoryLabel");
            restoreFilterFromSavedState = savedInstanceState.containsKey("currentFilter");
            filterEstablished = restoreFilterFromSavedState;
        }
        setContentView(R.layout.main);
        if (savedInstanceState != null) {
            EditText searchEditText = findViewById(R.id.search_editText);
            String searchQuery = savedInstanceState.getString("searchQuery");
            if (searchEditText != null && !TextUtils.isEmpty(searchQuery)) {
                searchEditText.setText(searchQuery);
                searchEditText.setSelection(searchQuery.length());
            }
        }
        applyOriginalLightMainBackground();
        setupFloatingButtons();
        setupBackNavigation();
        try {
            manageCrashLog();
        } catch (Exception e) {
            e.printStackTrace();
            checkIfNeedAskFirstTimeSetupAndShowDialog();
        }
    }

    /**
     * The manifest routes orientation and screen-size changes here instead of recreating the
     * activity, so {@link #go()} runs only once per instance and would keep the layout it picked on
     * launch. With the automatic pattern the list/grid choice has to follow the new configuration;
     * an explicit grid/list choice is the user's decision and stays put.
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // go() has not run yet while the first-run dialog is still up; it resolves the mode from
        // the configuration it finds, so there is nothing to rebuild here.
        if (mMainActivityAppListFragment == null) {
            return;
        }

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String mainPattern =
                sharedPref.getString(mainActivityPattern.name(), mainActivityPattern.defaultValue());
        if (!mainActivityPattern.defaultValue().equals(mainPattern)) {
            return;
        }

        boolean newGridMode = resolveGridMode(
                mainPattern,
                newConfig.orientation,
                newConfig.screenWidthDp,
                newConfig.screenHeightDp
        );
        if (newGridMode == isGridMode) {
            return;
        }

        isGridMode = newGridMode;
        // Swapping the widget destroys the old list's modal action mode, whose teardown normally
        // clears the selection. A rotation is not the user cancelling it, so the selection has to
        // survive the swap and be checked again on the new list.
        mRestoreSelectionAfterFragmentRebuild =
                (currentSelectionActionMode != null || mRestoreSelectionAfterFragmentRebuild)
                        && !selectedPackages.isEmpty();
        mMainActivityAppListFragment = new MainActivityAppListFragment();
        mMainActivityAppListFragment.setUseGridMode(isGridMode);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_appList_fragmentContainer_frameLayout, mMainActivityAppListFragment)
                .commit();
        new Thread(() -> generateList(currentFilter)).start();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Before the launch worker has run there is no filter choice to save yet; writing the
        // initial "all" would then beat the shortcut's filter on the recreated instance.
        if (filterEstablished) {
            outState.putString("currentFilter", currentFilter);
            if (currentCategoryLabel != null) {
                outState.putString("currentCategoryLabel", currentCategoryLabel);
            }
        }
        EditText searchEditText = findViewById(R.id.search_editText);
        if (searchEditText != null && !TextUtils.isEmpty(searchEditText.getText())) {
            outState.putString("searchQuery", searchEditText.getText().toString());
        }
    }

    /**
     * Configures the modern, multi-tiered back button dispatcher on Main.
     *
     * <p><b>Back Navigation Hierarchy:</b>
     * <ol>
     *   <li><b>Tier 1 (Multi-Choice Selection):</b> If multi-choice action mode is currently active
     *       ([currentSelectionActionMode] != null), pressing back dismisses the selection mode and clears
     *       checked items, keeping the current scroll position and category intact.</li>
     *   <li><b>Tier 2 (Search Query &amp; Focus):</b> If the search field contains query text or has focus,
     *       clears the search text, releases focus, and dismisses the soft input keyboard via
     *       {@link InputMethodManager#hideSoftInputFromWindow}.</li>
     *   <li><b>Tier 3 (Category Filter Reset):</b> If a specific category filter is active (e.g. "frozen",
     *       "user", "system", "OUS"), pressing back returns the user to the root "all" application list
     *       by asynchronously triggering {@code generateList("all")}.</li>
     *   <li><b>Tier 4 (Exit Application):</b> If already situated at the top-level, unfiltered "all" list,
     *       temporarily disables this callback and delegates to {@link androidx.activity.OnBackPressedDispatcher#onBackPressed}
     *       to finish the activity / exit the application naturally.</li>
     * </ol>
     */
    private void setupFloatingButtons() {
        mMoreSettingsImageButton = findViewById(R.id.main_moreSettings_button);
        mSelectionActionsImageButton = findViewById(R.id.main_selectionActions_button);

        if (mMoreSettingsImageButton != null) {
            mMoreSettingsImageButton.setOnLongClickListener(v -> {
                if (mMoreSettingsImageButton.getAlpha() == 0.2f) {
                    mMoreSettingsImageButton.setAlpha(1f);
                } else {
                    mMoreSettingsImageButton.setAlpha(0.2f);
                }
                return true;
            });

            mMoreSettingsImageButton.setOnClickListener(v -> {
                mMoreSettingsImageButton.setAlpha(1f);
                PopupMenu popupMenu = new PopupMenu(getLightPopupContext(Main.this), v);
                popupMenu.inflate(R.menu.menu);
                onPrepareMainOptionsMenu(popupMenu.getMenu(), true);
                popupMenu.setOnMenuItemClickListener(this::onMainOptionsItemSelected);
                popupMenu.setOnDismissListener(menu -> {
                    if (mActivePopupMenu == popupMenu) {
                        mActivePopupMenu = null;
                    }
                    RotateAnimation animation =
                            new RotateAnimation(45, 0, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                    animation.setDuration(300);
                    animation.setRepeatMode(RotateAnimation.REVERSE);
                    animation.setFillAfter(true);
                    mMoreSettingsImageButton.startAnimation(animation);
                });
                RotateAnimation animation =
                        new RotateAnimation(0, 45, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                animation.setDuration(300);
                animation.setRepeatMode(RotateAnimation.REVERSE);
                animation.setFillAfter(true);
                mMoreSettingsImageButton.startAnimation(animation);
                mActivePopupMenu = popupMenu;
                popupMenu.show();
            });
        }
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentSelectionActionMode != null) {
                    currentSelectionActionMode.finish();
                    return;
                }
                EditText searchEditText = findViewById(R.id.search_editText);
                if (searchEditText != null) {
                    boolean hasText = !TextUtils.isEmpty(searchEditText.getText());
                    boolean hasFocus = searchEditText.hasFocus();
                    if (hasText || hasFocus) {
                        if (hasText) {
                            searchEditText.setText("");
                        }
                        searchEditText.clearFocus();
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
                        }
                        return;
                    }
                }
                if (currentFilter != null && !"all".equals(currentFilter)) {
                    new Thread(() -> generateList("all")).start();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }

    /**
     * The stable-fork light theme left its home page on AppCompat's near-white background.
     * Restore that colour on Main only; settings keep their separate grey palette, and the two
     * dark themes continue to use the window background supplied by their theme.
     */
    private void applyOriginalLightMainBackground() {
        if (!isLightUiTheme(this)) {
            return;
        }

        View content = findViewById(android.R.id.content);
        if (content != null) {
            content.setBackgroundColor(ContextCompat.getColor(this, R.color.appMainScreenLight));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasPendingShortcuts(mPendingShortcutPackages, mPendingShortcutIndex)) {
            requestNextPinShortcut();
        }
        updateFrozenStatus(null);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPref.getBoolean(saveOnClickFunctionStatus.name(), saveOnClickFunctionStatus.defaultValue())) {
            appListViewOnClickMode = sharedPref.getInt("onClickFunctionStatus", APPListViewOnClickMode_chooseAction);
        }
        if (sharedPref.getBoolean(saveSortMethodStatus.name(), saveSortMethodStatus.defaultValue())) {
            currentSortRule = sharedPref.getInt("sortMethodStatus", SORT_BY_DEFAULT);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mActivePopupMenu != null) {
            try {
                mActivePopupMenu.dismiss();
            } catch (Exception ignored) {
            }
            mActivePopupMenu = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUninstallCompletedDialog != null && mUninstallCompletedDialog.isShowing()) {
            try {
                mUninstallCompletedDialog.dismiss();
            } catch (Exception ignored) {}
            mUninstallCompletedDialog = null;
        }
        if (updateFrozenStatusBroadcastReceiver != null)
            unregisterReceiver(updateFrozenStatusBroadcastReceiver);
        if (mPackageChangeReceiver != null) {
            try {
                unregisterReceiver(mPackageChangeReceiver);
            } catch (Exception ignored) {
            }
            mPackageChangeReceiver = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 80001:
                if (resultCode == RESULT_OK && data != null) {
                    String c_80001_title = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
                    LauncherShortcutUtils.requestCreateShortCut(
                            c_80001_title,
                            ((Intent) data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT))
                                    .setAction(Intent.ACTION_MAIN),
                            null,
                            "FreezeYou! FolderShortcut" + c_80001_title + new Date().getTime(),
                            this,
                            (Bitmap) data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON)
                    );
                }
                break;
            case 80002:
                if (abortUninstallBatchOnCancel(
                        resultCode, isPackageStillInstalled(mCurrentUnprivilegedUninstallPkg))) {
                    // The user backed out of a system uninstall prompt: the rest of the batch
                    // must not keep throwing prompts at them one after another.
                    mUnprivilegedUninstallQueue.clear();
                    mCurrentUnprivilegedUninstallPkg = null;
                    mIsBatchUninstallRunning = false;
                    if (mUnprivilegedUninstallSuccessCount > 0) {
                        showUninstallCompletedDialog(null, false, mUnprivilegedUninstallSuccessCount);
                        sendBroadcast(new Intent("cf.playhi.freezeyou.action.packageStatusChanged")
                                .setPackage(getPackageName())
                                .putExtra("refreshAppList", true));
                        new Thread(() -> generateList(currentFilter)).start();
                    }
                } else {
                    processNextUnprivilegedUninstall();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            String cTheme = getUiTheme(this);
            if ("white".equals(cTheme) || "default".equals(cTheme)) {
                menu.findItem(R.id.menu_timedTasks).setIcon(R.drawable.ic_action_alarm_light);
                menu.findItem(R.id.menu_viewMode).setIcon(R.drawable.ic_action_filter_light);
                menu.findItem(R.id.menu_sortBy).setIcon(R.drawable.ic_action_sort_light);
            }
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        onPrepareMainOptionsMenu(menu, false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        return onMainOptionsItemSelected(item);
    }

    private void generateList(String filter) {
        generateList(filter, currentSortRule);
    }

    /**
     * Shows which filter produced the current list — with 9+ items in the filter menu it's easy
     * to lose track after switching a few times. Swaps the toolbar title itself (filter name
     * instead of "FreezeYou") rather than adding a subtitle line: a subtitle sat right under the
     * app name and read as two headlines competing for attention. Reverts to the app name for
     * the unfiltered state and for custom categories/shortcut package lists, since there's no
     * short label to show for those.
     */
    private void updateFilterTitle(String filter) {
        final int titleResId;
        switch (filter) {
            case "all":
                // Keep the app name for the unfiltered state: "Show all" reads oddly as a
                // status label, and it's the common case anyway.
                titleResId = 0;
                break;
            case "OF":
                titleResId = R.string.onlyFrozen;
                break;
            case "UF":
                titleResId = R.string.onlyUF;
                break;
            case "OO":
                titleResId = R.string.onlyOnekey;
                break;
            case "OOU":
                titleResId = R.string.oneKeyUF;
                break;
            case "FOQ":
                titleResId = R.string.freezeOnceQuit;
                break;
            case "OS":
                titleResId = R.string.onlySA;
                break;
            case "OUS":
                titleResId = R.string.onlyUninstalledSys;
                break;
            case "OU":
                titleResId = R.string.onlyUA;
                break;
            case "UFU":
                titleResId = R.string.unfrozenUA;
                break;
            case "RUN":
                titleResId = R.string.onlyRunning;
                break;
            case "RUN_SYS":
                titleResId = R.string.onlyRunningSys;
                break;
            case "RUN_USER":
                titleResId = R.string.onlyRunningUser;
                break;
            default:
                titleResId = 0;
                break;
        }
        runOnUiThread(() -> {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(titleResId == 0 ? getString(R.string.app_name) : getString(titleResId));
            }
        });
    }

    /**
     * @param filter   Filter rule or comma-separated package names
     * @param sortRule Sort rule ID
     */
    private void generateList(String filter, int sortRule) {
        // A refresh (and a sort change) re-renders the filter already on screen; keep tracking
        // the category it came from so an edit started from the selection menu can still refresh
        // this view. Any other filter starts a different list, with no category behind it.
        generateList(filter, sortRule, filter.equals(currentFilter) ? currentCategoryLabel : null);
    }

    private void generateList(String filter, int sortRule, String categoryLabel) {
        currentFilter = filter;
        currentCategoryLabel = categoryLabel;
        currentSortRule = sortRule;
        filterEstablished = true;
        final int generation = mListGeneration.incrementAndGet();
        final ArrayList<Map<String, Object>> AppList = new ArrayList<>();
        final Context applicationContext = getApplicationContext();

        if (isFinishing()) return;

        runOnUiThread(() -> {
            if (generation != mListGeneration.get() || isFinishing()) return;

            final FrameLayout appListFragmentContainer = findViewById(R.id.main_appList_fragmentContainer_frameLayout);
            final ProgressBar progressBar = findViewById(R.id.progressBar);
            final TextView mainCautionTextView = findViewById(R.id.main_caution_textView);
            final TextView main_loading_progress_textView = findViewById(R.id.main_loading_progress_textView);
            final FrameLayout linearLayout = findViewById(R.id.layout2);

            int floatingButtonBackground = Build.VERSION.SDK_INT >= 21 ?
                    (isGridMode ? R.drawable.oval_ripple_grid : R.drawable.oval_ripple) :
                    getThemeFabDotBackground(Main.this);
            if (mMoreSettingsImageButton != null) {
                mMoreSettingsImageButton.setBackgroundResource(floatingButtonBackground);
            }
            if (mSelectionActionsImageButton != null) {
                mSelectionActionsImageButton.setBackgroundResource(floatingButtonBackground);
            }

            if (linearLayout != null) linearLayout.setVisibility(View.VISIBLE);
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            if (main_loading_progress_textView != null) {
                main_loading_progress_textView.setVisibility(View.VISIBLE);
                main_loading_progress_textView.setText(R.string.loadingPkgList);
            }
            if (appListFragmentContainer != null) appListFragmentContainer.setVisibility(View.GONE);
            if (mainCautionTextView != null) {
                if (noCaution.getValue(applicationContext)) {
                    mainCautionTextView.setVisibility(View.GONE);
                } else {
                    mainCautionTextView.setVisibility(View.VISIBLE);
                }
            }
        });

        try {
            customThemeDisabledDot = getThemeDot(Main.this);
            customThemeEnabledDot = getThemeSecondDot(Main.this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        PackageInfo packageInfo1;
        PackageManager packageManager = applicationContext.getPackageManager();
        List<PackageInfo> packageInfo = packageManager.getInstalledPackages(PackageManager.GET_UNINSTALLED_PACKAGES);
        int size = packageInfo == null ? 0 : packageInfo.size();
        boolean saveIconCache = cacheApplicationsIcons.getValue(applicationContext);
        switch (filter) {
            case "OUS":
                for (int i = 0; i < size; i++) {
                    packageInfo1 = packageInfo.get(i);
                    boolean isUninstalled = RestoreUtils.isAppUninstalled(packageInfo1.applicationInfo);
                    boolean isSystemApp = (packageInfo1.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM;
                    if (isUninstalled && isSystemApp) {
                        Map<String, Object> keyValuePair = processAppStatus(
                                getApplicationLabel(applicationContext, packageManager, packageInfo1.applicationInfo, packageInfo1.packageName),
                                packageInfo1.packageName,
                                packageInfo1,
                                packageManager,
                                saveIconCache
                        );
                        if (keyValuePair != null) {
                            AppList.add(keyValuePair);
                        }
                    }
                }
                checkAndAddNotAvailablePair(AppList);
                break;
            case "all":
                for (int i = 0; i < size; i++) {
                    packageInfo1 = packageInfo.get(i);
                    if (RestoreUtils.isAppUninstalled(packageInfo1.applicationInfo)) continue;
                    Map<String, Object> keyValuePair = processAppStatus(
                            getApplicationLabel(applicationContext, packageManager, packageInfo1.applicationInfo, packageInfo1.packageName),
                            packageInfo1.packageName,
                            packageInfo1,
                            packageManager,
                            saveIconCache
                    );
                    if (keyValuePair != null) {
                        AppList.add(keyValuePair);
                    }
                }
                checkAndAddNotAvailablePair(AppList);
                break;
            case "OF":
                for (int i = 0; i < size; i++) {
                    packageInfo1 = packageInfo.get(i);
                    if (RestoreUtils.isAppUninstalled(packageInfo1.applicationInfo)) continue;
                    Map<String, Object> keyValuePair = processAppStatus(
                            getApplicationLabel(applicationContext, packageManager, packageInfo1.applicationInfo, packageInfo1.packageName),
                            packageInfo1.packageName,
                            packageInfo1,
                            packageManager,
                            saveIconCache
                    );
                    if ((keyValuePair != null) && (customThemeDisabledDot == (int) keyValuePair.get("isFrozen"))) {
                        AppList.add(keyValuePair);
                    }
                }
                checkAndAddNotAvailablePair(AppList);
                break;
            case "UF":
                for (int i = 0; i < size; i++) {
                    packageInfo1 = packageInfo.get(i);
                    if (RestoreUtils.isAppUninstalled(packageInfo1.applicationInfo)) continue;
                    Map<String, Object> keyValuePair = processAppStatus(
                            getApplicationLabel(applicationContext, packageManager, packageInfo1.applicationInfo, packageInfo1.packageName),
                            packageInfo1.packageName,
                            packageInfo1,
                            packageManager,
                            saveIconCache
                    );
                    if (keyValuePair != null && customThemeEnabledDot == (int) keyValuePair.get("isFrozen")) {
                        AppList.add(keyValuePair);
                    }
                }
                checkAndAddNotAvailablePair(AppList);
                break;
            case "OO":
                oneKeyListCheckAndGenerate(
                        new DefaultMultiProcessMMKVStorage().getString(getString(R.string.sAutoFreezeApplicationList), ""),
                        AppList);
                checkAndAddNotAvailablePair(AppList);
                break;
            case "OOU":
                oneKeyListCheckAndGenerate(
                        new DefaultMultiProcessMMKVStorage().getString(getString(R.string.sOneKeyUFApplicationList), ""),
                        AppList);
                checkAndAddNotAvailablePair(AppList);
                break;
            case "FOQ":
                oneKeyListCheckAndGenerate(
                        new DefaultMultiProcessMMKVStorage().getString(getString(R.string.sFreezeOnceQuit), ""),
                        AppList);
                checkAndAddNotAvailablePair(AppList);
                break;
            case "OS":
                for (int i = 0; i < size; i++) {
                    packageInfo1 = packageInfo.get(i);
                    if (RestoreUtils.isAppUninstalled(packageInfo1.applicationInfo)) continue;
                    if ((packageInfo1.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM) {
                        Map<String, Object> keyValuePair = processAppStatus(
                                getApplicationLabel(applicationContext, packageManager, packageInfo1.applicationInfo, packageInfo1.packageName),
                                packageInfo1.packageName,
                                packageInfo1,
                                packageManager,
                                saveIconCache
                        );
                        if (keyValuePair != null) {
                            AppList.add(keyValuePair);
                        }
                    }
                }
                checkAndAddNotAvailablePair(AppList);
                break;
            case "OU":
                for (int i = 0; i < size; i++) {
                    packageInfo1 = packageInfo.get(i);
                    if (RestoreUtils.isAppUninstalled(packageInfo1.applicationInfo)) continue;
                    if ((packageInfo1.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != ApplicationInfo.FLAG_SYSTEM) {
                        Map<String, Object> keyValuePair = processAppStatus(
                                getApplicationLabel(applicationContext, packageManager, packageInfo1.applicationInfo, packageInfo1.packageName),
                                packageInfo1.packageName,
                                packageInfo1,
                                packageManager,
                                saveIconCache
                        );
                        if (keyValuePair != null) {
                            AppList.add(keyValuePair);
                        }
                    }
                }
                checkAndAddNotAvailablePair(AppList);
                break;
            case "UFU":
                for (int i = 0; i < size; i++) {
                    packageInfo1 = packageInfo.get(i);
                    if (RestoreUtils.isAppUninstalled(packageInfo1.applicationInfo)) continue;
                    if ((packageInfo1.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != ApplicationInfo.FLAG_SYSTEM) {
                        Map<String, Object> keyValuePair = processAppStatus(
                                getApplicationLabel(applicationContext, packageManager, packageInfo1.applicationInfo, packageInfo1.packageName),
                                packageInfo1.packageName,
                                packageInfo1,
                                packageManager,
                                saveIconCache
                        );
                        if (keyValuePair != null && customThemeEnabledDot == (int) keyValuePair.get("isFrozen")) {
                            AppList.add(keyValuePair);
                        }
                    }
                }
                checkAndAddNotAvailablePair(AppList);
                break;
            case "RUN":
            case "RUN_SYS":
            case "RUN_USER":
                Set<String> runningPackages = RunningAppsUtils.getRunningPackages(applicationContext);
                for (int i = 0; i < size; i++) {
                    packageInfo1 = packageInfo.get(i);
                    if (RestoreUtils.isAppUninstalled(packageInfo1.applicationInfo)) continue;
                    boolean isSystemApp = (packageInfo1.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM;
                    if (("RUN_SYS".equals(filter) && !isSystemApp) || ("RUN_USER".equals(filter) && isSystemApp)) {
                        continue;
                    }
                    if (runningPackages.contains(packageInfo1.packageName)) {
                        Map<String, Object> keyValuePair = processAppStatus(
                                getApplicationLabel(applicationContext, packageManager, packageInfo1.applicationInfo, packageInfo1.packageName),
                                packageInfo1.packageName,
                                packageInfo1,
                                packageManager,
                                saveIconCache
                        );
                        if (keyValuePair != null) {
                            AppList.add(keyValuePair);
                        }
                    }
                }
                checkAndAddNotAvailablePair(AppList);
                break;
            default:
                oneKeyListCheckAndGenerate(filter, AppList);
                checkAndAddNotAvailablePair(AppList);
                break;
        }

        if (isFinishing()) return;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView main_loading_progress_textView = findViewById(R.id.main_loading_progress_textView);
                if (main_loading_progress_textView != null) {
                    main_loading_progress_textView.setText(R.string.sorting);
                }
            }
        });

        if (!AppList.isEmpty()) {
            switch (sortRule) {
                case SORT_BY_DEFAULT:
                    setSortByDefault(AppList);
                    break;
                case SORT_BY_UF_ASCENDING:
                    setSortByDefault(AppList);
                    final HashMap<String, Integer> ufTimesMap = getUFreezeTimesMap();
                    Collections.sort(AppList, new Comparator<Map<String, Object>>() {
                        @Override
                        public int compare(Map<String, Object> m0, Map<String, Object> m1) {
                            String s0 =
                                    Base64.encodeToString(
                                            ((String) m0.get("PackageName")).getBytes(),
                                            Base64.DEFAULT);
                            String s1 =
                                    Base64.encodeToString(
                                            ((String) m1.get("PackageName")).getBytes(),
                                            Base64.DEFAULT);

                            if (ufTimesMap.containsKey(s0) || ufTimesMap.containsKey(s1)) {
                                if (ufTimesMap.containsKey(s0) && ufTimesMap.containsKey(s1)) {
                                    return ufTimesMap.get(s0).compareTo(ufTimesMap.get(s1));
                                } else if (ufTimesMap.containsKey(s0) && ufTimesMap.get(s0) > 0) {
                                    return 1;
                                } else if (ufTimesMap.containsKey(s1) && ufTimesMap.get(s1) > 0) {
                                    return -1;
                                } else {
                                    return 0;
                                }
                            } else {
                                return 0;
                            }
                        }
                    });
                    break;
                case SORT_BY_UF_DESCENDING:
                    setSortByDefault(AppList);
                    final HashMap<String, Integer> uFreezeTimesMapTimesMap = getUFreezeTimesMap();
                    Collections.sort(AppList, new Comparator<Map<String, Object>>() {
                        @Override
                        public int compare(Map<String, Object> m0, Map<String, Object> m1) {
                            String s0 =
                                    Base64.encodeToString(
                                            ((String) m0.get("PackageName")).getBytes(),
                                            Base64.DEFAULT);
                            String s1 =
                                    Base64.encodeToString(
                                            ((String) m1.get("PackageName")).getBytes(),
                                            Base64.DEFAULT);

                            if (uFreezeTimesMapTimesMap.containsKey(s0) || uFreezeTimesMapTimesMap.containsKey(s1)) {
                                if (uFreezeTimesMapTimesMap.containsKey(s0) && uFreezeTimesMapTimesMap.containsKey(s1)) {
                                    return uFreezeTimesMapTimesMap.get(s1).compareTo(uFreezeTimesMapTimesMap.get(s0));
                                } else if (uFreezeTimesMapTimesMap.containsKey(s0) && uFreezeTimesMapTimesMap.get(s0) > 0) {
                                    return -1;
                                } else if (uFreezeTimesMapTimesMap.containsKey(s1) && uFreezeTimesMapTimesMap.get(s1) > 0) {
                                    return 1;
                                } else {
                                    return 0;
                                }
                            } else {
                                return 0;
                            }
                        }
                    });
                    break;
                case SORT_BY_FF_ASCENDING:
                    setSortByDefault(AppList);
                    final HashMap<String, Integer> freezeTimesMap = getFreezeTimesMap();
                    Collections.sort(AppList, new Comparator<Map<String, Object>>() {
                        @Override
                        public int compare(Map<String, Object> m0, Map<String, Object> m1) {
                            String s0 =
                                    Base64.encodeToString(
                                            ((String) m0.get("PackageName")).getBytes(),
                                            Base64.DEFAULT);
                            String s1 =
                                    Base64.encodeToString(
                                            ((String) m1.get("PackageName")).getBytes(),
                                            Base64.DEFAULT);

                            if (freezeTimesMap.containsKey(s0) || freezeTimesMap.containsKey(s1)) {
                                if (freezeTimesMap.containsKey(s0) && freezeTimesMap.containsKey(s1)) {
                                    return freezeTimesMap.get(s0).compareTo(freezeTimesMap.get(s1));
                                } else if (freezeTimesMap.containsKey(s0) && freezeTimesMap.get(s0) > 0) {
                                    return 1;
                                } else if (freezeTimesMap.containsKey(s1) && freezeTimesMap.get(s1) > 0) {
                                    return -1;
                                } else {
                                    return 0;
                                }
                            } else {
                                return 0;
                            }
                        }
                    });
                    break;
                case SORT_BY_FF_DESCENDING:
                    setSortByDefault(AppList);
                    final HashMap<String, Integer> freezeTimesMap1 = getFreezeTimesMap();
                    Collections.sort(AppList, new Comparator<Map<String, Object>>() {
                        @Override
                        public int compare(Map<String, Object> m0, Map<String, Object> m1) {
                            String s0 =
                                    Base64.encodeToString(
                                            ((String) m0.get("PackageName")).getBytes(),
                                            Base64.DEFAULT);
                            String s1 =
                                    Base64.encodeToString(
                                            ((String) m1.get("PackageName")).getBytes(),
                                            Base64.DEFAULT);

                            if (freezeTimesMap1.containsKey(s0) || freezeTimesMap1.containsKey(s1)) {
                                if (freezeTimesMap1.containsKey(s0) && freezeTimesMap1.containsKey(s1)) {
                                    return freezeTimesMap1.get(s1).compareTo(freezeTimesMap1.get(s0));
                                } else if (freezeTimesMap1.containsKey(s0) && freezeTimesMap1.get(s0) > 0) {
                                    return -1;
                                } else if (freezeTimesMap1.containsKey(s1) && freezeTimesMap1.get(s1) > 0) {
                                    return 1;
                                } else {
                                    return 0;
                                }
                            } else {
                                return 0;
                            }
                        }
                    });
                    break;
                case SORT_BY_US_ASCENDING:
                    setSortByDefault(AppList);
                    final HashMap<String, Integer> useTimesMap1 = getUseTimesMap();
                    Collections.sort(AppList, new Comparator<Map<String, Object>>() {
                        @Override
                        public int compare(Map<String, Object> m0, Map<String, Object> m1) {
                            String s0 =
                                    Base64.encodeToString(
                                            ((String) m0.get("PackageName")).getBytes(),
                                            Base64.DEFAULT);
                            String s1 =
                                    Base64.encodeToString(
                                            ((String) m1.get("PackageName")).getBytes(),
                                            Base64.DEFAULT);

                            if (useTimesMap1.containsKey(s0) || useTimesMap1.containsKey(s1)) {
                                if (useTimesMap1.containsKey(s0) && useTimesMap1.containsKey(s1)) {
                                    return useTimesMap1.get(s0).compareTo(useTimesMap1.get(s1));
                                } else if (useTimesMap1.containsKey(s0) && useTimesMap1.get(s0) > 0) {
                                    return 1;
                                } else if (useTimesMap1.containsKey(s1) && useTimesMap1.get(s1) > 0) {
                                    return -1;
                                } else {
                                    return 0;
                                }
                            } else {
                                return 0;
                            }
                        }
                    });
                    break;
                case SORT_BY_US_DESCENDING:
                    setSortByDefault(AppList);
                    final HashMap<String, Integer> useTimesMap = getUseTimesMap();
                    Collections.sort(AppList, new Comparator<Map<String, Object>>() {
                        @Override
                        public int compare(Map<String, Object> m0, Map<String, Object> m1) {
                            String s0 =
                                    Base64.encodeToString(
                                            ((String) m0.get("PackageName")).getBytes(),
                                            Base64.DEFAULT);
                            String s1 =
                                    Base64.encodeToString(
                                            ((String) m1.get("PackageName")).getBytes(),
                                            Base64.DEFAULT);

                            if (useTimesMap.containsKey(s0) || useTimesMap.containsKey(s1)) {
                                if (useTimesMap.containsKey(s0) && useTimesMap.containsKey(s1)) {
                                    return useTimesMap.get(s1).compareTo(useTimesMap.get(s0));
                                } else if (useTimesMap.containsKey(s0) && useTimesMap.get(s0) > 0) {
                                    return -1;
                                } else if (useTimesMap.containsKey(s1) && useTimesMap.get(s1) > 0) {
                                    return 1;
                                } else {
                                    return 0;
                                }
                            } else {
                                return 0;
                            }
                        }
                    });
                    break;
                case SORT_BY_ALPHABETICAL:
                    setSortByDefault(AppList);
                    Collator collator = Collator.getInstance();
                    Collections.sort(AppList, new Comparator<Map<String, Object>>() {
                        @Override
                        public int compare(Map<String, Object> m0, Map<String, Object> m1) {
                            return collator.compare((String) m0.get("Name"), (String) m1.get("Name"));
                        }
                    });
                    break;
                case SORT_BY_LAST_INSTALLED:
                    setSortByDefault(AppList);
                    Collections.sort(AppList, new Comparator<Map<String, Object>>() {
                        @Override
                        public int compare(Map<String, Object> m0, Map<String, Object> m1) {
                            return -Long.compare((Long) m0.get("InstallTime"), (Long) m1.get("InstallTime"));
                        }
                    });
                    break;
                case SORT_BY_LAST_UPDATED:
                    setSortByDefault(AppList);
                    Collections.sort(AppList, new Comparator<Map<String, Object>>() {
                        @Override
                        public int compare(Map<String, Object> m0, Map<String, Object> m1) {
                            return -Long.compare((Long) m0.get("UpdateTime"), (Long) m1.get("UpdateTime"));
                        }
                    });
                    break;
                case SORT_BY_NO:
                default:
                    break;

            }
        }
        if (generation != mListGeneration.get() || isFinishing()) return;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (generation != mListGeneration.get() || isFinishing()) return;

                currentFilter = filter;
                currentCategoryLabel = categoryLabel;
                currentSortRule = sortRule;
                updateFilterTitle(filter);
                mCurrentUnfilteredAppList = AppList;

                final EditText search_editText = findViewById(R.id.search_editText);
                final FrameLayout appListFragmentContainer = findViewById(R.id.main_appList_fragmentContainer_frameLayout);
                final ProgressBar progressBar = findViewById(R.id.progressBar);
                final TextView mainCautionTextView = findViewById(R.id.main_caution_textView);
                final TextView main_loading_progress_textView = findViewById(R.id.main_loading_progress_textView);
                final FrameLayout linearLayout = findViewById(R.id.layout2);

                final MainAppListSimpleAdapter adapter =
                        mMainActivityAppListFragment.setAppListAdapter(
                                Main.this,
                                (ArrayList<Map<String, Object>>) AppList.clone(),
                                selectedPackages
                        );


                if (search_editText != null) {
                    if (mSearchTextWatcher != null) {
                        search_editText.removeTextChangedListener(mSearchTextWatcher);
                    }
                    mSearchTextWatcher = new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                        }

                        @Override
                        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                            if (TextUtils.isEmpty(charSequence)) {
                                adapter.replaceAllInFormerArrayList(AppList);
                            } else {
                                adapter.replaceAllInFormerArrayList(processListFilter(charSequence, AppList));
                            }
                            if (currentSelectionActionMode != null) {
                                updateRestoreMenuVisibility(currentSelectionActionMode.getMenu());
                            }
                        }

                        @Override
                        public void afterTextChanged(Editable editable) {
                        }
                    };
                    search_editText.addTextChangedListener(mSearchTextWatcher);

                    CharSequence currentQuery = search_editText.getText();
                    if (!TextUtils.isEmpty(currentQuery)) {
                        adapter.replaceAllInFormerArrayList(processListFilter(currentQuery, AppList));
                    }
                }

                main_loading_progress_textView.setText(R.string.finish);
                progressBar.setVisibility(View.GONE);
                mainCautionTextView.setVisibility(View.GONE);
                main_loading_progress_textView.setVisibility(View.GONE);
                linearLayout.setVisibility(View.GONE);
                appListFragmentContainer.setVisibility(View.VISIBLE);

                currentMultiChoiceModeListener = new AbsListView.MultiChoiceModeListener() {
                    @Override
                    public void onItemCheckedStateChanged(ActionMode actionMode, int i, long l, boolean b) {
                        final String pkgName =
                                (String) ((MainAppListSimpleAdapter) mMainActivityAppListFragment.getAppListAdapter())
                                        .getStoredArrayList().get(i).get("PackageName");

                        if (needProcessOnItemCheckedStateChanged) {
                            if (selectedPackages.contains(pkgName)) {
                                selectedPackages.remove(pkgName);
                            } else {
                                selectedPackages.add(pkgName);
                            }
                            needProcessOnItemCheckedStateChanged = false;
                            mMainActivityAppListFragment.setItemChecked(i, true);
                            actionMode.setTitle(Integer.toString(selectedPackages.size()));
                            adapter.notifyDataSetChanged();
                            updateRestoreMenuVisibility(actionMode.getMenu());
                        } else {
                            needProcessOnItemCheckedStateChanged = true;
                        }
                    }

                    @Override
                    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                        currentSelectionActionMode = actionMode;
                        if (mSelectionActionsImageButton != null) mSelectionActionsImageButton.setVisibility(View.VISIBLE);
                        Main.this.getMenuInflater().inflate(R.menu.multichoicemenu, menu);
                        MenuItem restoreItem = menu.findItem(R.id.list_menu_restoreImmediately);
                        if (restoreItem != null) restoreItem.setVisible(false);
                        return true;
                    }

                    @Override
                    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                        updateRestoreMenuVisibility(menu);
                        try {
                            SubMenu addToUserDefinedSubMenu = menu.findItem(R.id.list_menu_groupItem_addToUserDefined).getSubMenu();
                            SubMenu removeFromUserDefinedSubMenu = menu.findItem(R.id.list_menu_groupItem_removeFromUserDefined).getSubMenu();
                            addToUserDefinedSubMenu.clear();
                            removeFromUserDefinedSubMenu.clear();

                            addToUserDefinedSubMenu.add(
                                    R.id.list_menu_groupItem_addToUserDefined_menuGroup,
                                    R.id.list_menu_groupItem_addToUserDefined_newClassification,
                                    0,
                                    R.string.newClassification
                            );
                            removeFromUserDefinedSubMenu.add(
                                    R.id.list_menu_groupItem_removeFromUserDefined_menuGroup,
                                    R.id.list_menu_groupItem_removeFromUserDefined_newClassification,
                                    0,
                                    R.string.newClassification
                            );

                            // Add user-defined categories
                            SQLiteDatabase userDefinedDb = openOrCreateDatabase("userDefinedCategories", MODE_PRIVATE, null);
                            userDefinedDb.execSQL(
                                    "create table if not exists categories(_id integer primary key autoincrement,label varchar,packages varchar)"
                            );
                            Cursor cursor = userDefinedDb.query("categories", new String[]{"label", "_id"}, null, null, null, null, null);
                            if (cursor.moveToFirst()) {
                                for (int i = 0; i < cursor.getCount(); i++) {
                                    int id = cursor.getInt(cursor.getColumnIndex("_id"));
                                    String title = cursor.getString(cursor.getColumnIndex("label"));
                                    addToUserDefinedSubMenu.add(R.id.list_menu_groupItem_addToUserDefined_menuGroup, id, id, new String(Base64.decode(title, Base64.DEFAULT)));
                                    removeFromUserDefinedSubMenu.add(R.id.list_menu_groupItem_removeFromUserDefined_menuGroup, id, id, new String(Base64.decode(title, Base64.DEFAULT)));
                                    cursor.moveToNext();
                                }
                            }
                            cursor.close();
                            userDefinedDb.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return true;
                    }

                    @Override
                    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                        switch (menuItem.getGroupId()) {
                            case R.id.list_menu_groupItem_addToUserDefined_menuGroup:
                            case R.id.list_menu_groupItem_removeFromUserDefined_menuGroup:
                                switch (menuItem.getItemId()) {
                                    case R.id.list_menu_groupItem_addToUserDefined_newClassification:
                                    case R.id.list_menu_groupItem_removeFromUserDefined_newClassification:
                                        showAddNewUserDefinedClassificationDialog();
                                        return true;
                                    default:
                                        final boolean isInRemoveMode =
                                                menuItem.getGroupId()
                                                        == R.id.list_menu_groupItem_removeFromUserDefined_menuGroup;
                                        final String title = menuItem.getTitle().toString();
                                        final ArrayList<String> packages = new ArrayList<>(selectedPackages);
                                        final Context appContext = getApplicationContext();
                                        new Thread(() -> updateUserDefinedCategoryFromSelection(
                                                appContext, title, packages, isInRemoveMode),
                                                "UserDefinedCategoryMutation").start();
                                        return true;
                                }
                            default:
                                switch (menuItem.getItemId()) {
                                    case R.id.list_menu_selectAll:
                                        Adapter adpt = mMainActivityAppListFragment.getAppListAdapter();
                                        if (adpt instanceof MainAppListSimpleAdapter) {
                                            for (int i = 0; i < adpt.getCount(); i++) {
                                                String pkg = (String) ((MainAppListSimpleAdapter) adpt).getStoredArrayList().get(i).get("PackageName");
                                                if (!selectedPackages.contains(pkg)) {
                                                    selectedPackages.add(pkg);
                                                }
                                            }
                                            actionMode.setTitle(Integer.toString(selectedPackages.size()));
                                            ((MainAppListSimpleAdapter) adpt).notifyDataSetChanged();
                                            updateRestoreMenuVisibility(actionMode.getMenu());
                                        }
                                        return true;
                                    case R.id.list_menu_selectUnselected:
                                        Adapter adapt = mMainActivityAppListFragment.getAppListAdapter();
                                        if (adapt instanceof MainAppListSimpleAdapter) {
                                            for (int i = 0; i < adapt.getCount(); i++) {
                                                String pkg = (String) ((MainAppListSimpleAdapter) adapt).getStoredArrayList().get(i).get("PackageName");
                                                if (selectedPackages.contains(pkg)) {
                                                    selectedPackages.remove(pkg);
                                                } else {
                                                    selectedPackages.add(pkg);
                                                }
                                            }
                                            actionMode.setTitle(Integer.toString(selectedPackages.size()));
                                            ((MainAppListSimpleAdapter) adapt).notifyDataSetChanged();
                                            updateRestoreMenuVisibility(actionMode.getMenu());
                                        }
                                        return true;
                                    case R.id.list_menu_addToOneKeyFreezeList:
                                        processAddToOneKeyList(getString(R.string.sAutoFreezeApplicationList));
                                        return true;
                                    case R.id.list_menu_addToOneKeyUFList:
                                        processAddToOneKeyList(getString(R.string.sOneKeyUFApplicationList));
                                        return true;
                                    case R.id.list_menu_addToFreezeOnceQuit:
                                        processAddToOneKeyList(getString(R.string.sFreezeOnceQuit));
                                        return true;
                                    case R.id.list_menu_removeFromOneKeyFreezeList:
                                        processRemoveFromOneKeyList(getString(R.string.sAutoFreezeApplicationList));
                                        return true;
                                    case R.id.list_menu_removeFromOneKeyUFList:
                                        processRemoveFromOneKeyList(getString(R.string.sOneKeyUFApplicationList));
                                        return true;
                                    case R.id.list_menu_removeFromFreezeOnceQuit:
                                        processRemoveFromOneKeyList(getString(R.string.sFreezeOnceQuit));
                                        return true;
                                    case R.id.list_menu_freezeImmediately:
                                        confirmIfCriticalSelectedThenRun(false, () -> {
                                            processDisableAndEnableImmediately(true);
                                            actionMode.finish();
                                        });
                                        return true;
                                    case R.id.list_menu_UFImmediately:
                                        processDisableAndEnableImmediately(false);
                                        actionMode.finish();
                                        return true;
                                    case R.id.list_menu_ForceStopImmediately:
                                        processForceStopImmediately();
                                        actionMode.finish();
                                        return true;
                                    case R.id.list_menu_uninstallImmediately:
                                        processBatchUninstall();
                                        return true;
                                    case R.id.list_menu_restoreImmediately: {
                                        List<String> packagesToRestore = new ArrayList<>(selectedPackages);
                                        if (!RestoreUtils.isRestoreFilter(currentFilter)
                                                || !RestoreUtils.canRestorePackages(Main.this, packagesToRestore)) {
                                            showToast(Main.this, R.string.restore_failed);
                                            actionMode.invalidate();
                                            return true;
                                        }
                                        RestoreUtils.restorePackages(Main.this, packagesToRestore, () -> {
                                            new Thread(() -> generateList(currentFilter)).start();
                                        });
                                        actionMode.finish();
                                        return true;
                                    }
                                    case R.id.list_menu_createDisEnableShortCut:
                                        if (selectedPackages.isEmpty()) {
                                            return true;
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            ShortcutManager mShortcutManager =
                                                    Main.this.getSystemService(ShortcutManager.class);
                                            if (mShortcutManager != null && mShortcutManager.isRequestPinShortcutSupported()) {
                                                // One system dialog per package, and each one takes
                                                // this activity off screen, so the rest of the batch
                                                // is continued from onResume against a snapshot the
                                                // selection mode cannot clear.
                                                mPendingShortcutPackages = new ArrayList<>(selectedPackages);
                                                mPendingShortcutIndex = 0;
                                                requestNextPinShortcut();
                                            } else {
                                                createFUFShortcutsBatch(new ArrayList<>(selectedPackages));
                                            }
                                        } else {
                                            createFUFShortcutsBatch(new ArrayList<>(selectedPackages));
                                        }
                                        return true;
                                    case R.id.list_menu_copyAfterBeingFormatted:
                                        StringBuilder formattedPackages = new StringBuilder();
                                        int size = selectedPackages.size();
                                        for (int i = 0; i < size; i++) {
                                            formattedPackages.append(selectedPackages.get(i)).append(",");
                                        }
                                        if (copyToClipboard(Main.this, formattedPackages.toString())) {
                                            showToast(Main.this, R.string.success);
                                        } else {
                                            showToast(Main.this, R.string.failed);
                                        }
                                        return true;
                                    default:
                                        return false;
                                }
                        }
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode actionMode) {
                        // A configuration swap tears the action mode down for its own reasons; the
                        // selection was not cancelled by the user and is restored below.
                        if (!mRestoreSelectionAfterFragmentRebuild) {
                            selectedPackages.clear();
                            // Leaving the selection mode also cancels an unfinished shortcut batch:
                            // the snapshot is dropped here, so the next resume finds nothing left
                            // to pin.
                            mPendingShortcutPackages = null;
                            mPendingShortcutIndex = 0;
                            mIsBatchUninstallChecking = false;
                        }
                        currentSelectionActionMode = null;
                        if (mSelectionActionsImageButton != null) mSelectionActionsImageButton.setVisibility(View.GONE);
                    }
                };
                mMainActivityAppListFragment.setMultiChoiceModeListener(currentMultiChoiceModeListener);

                if (mRestoreSelectionAfterFragmentRebuild) {
                    mRestoreSelectionAfterFragmentRebuild = false;
                    restoreCheckedSelection(adapter);
                }

                if (mSelectionActionsImageButton != null) {
                    mSelectionActionsImageButton.setOnClickListener(v -> {
                    if (currentSelectionActionMode == null || currentMultiChoiceModeListener == null) {
                        return;
                    }
                    PopupMenu popupMenu = new PopupMenu(getLightPopupContext(Main.this), v);
                    popupMenu.inflate(R.menu.multichoicemenu);
                    currentMultiChoiceModeListener.onPrepareActionMode(currentSelectionActionMode, popupMenu.getMenu());
                    popupMenu.setOnMenuItemClickListener(item ->
                            currentMultiChoiceModeListener.onActionItemClicked(currentSelectionActionMode, item));
                    popupMenu.setOnDismissListener(menu -> {
                        if (mActivePopupMenu == popupMenu) {
                            mActivePopupMenu = null;
                        }
                    });
                    mActivePopupMenu = popupMenu;
                    popupMenu.show();
                });
                }


        mMainActivityAppListFragment.setOnAppListItemClickListener((adapterView, view, i, l) -> {
            Map<String, Object> map =
                    ((MainAppListSimpleAdapter) mMainActivityAppListFragment.getAppListAdapter())
                            .getStoredArrayList().get(i);
            final String name = (String) map.get("Name");
            final String pkgName = (String) map.get("PackageName");
            if (!getString(R.string.notAvailable).equals(name)) {
                boolean isRestorableSystemApp = RestoreUtils.isRestorableSystemPackage(Main.this, pkgName);
                if (isRestorableSystemApp && RestoreUtils.isRestoreFilter(currentFilter)) {
                    RestoreUtils.showRestoreConfirmDialog(Main.this, java.util.Collections.singletonList(pkgName), name, () -> {
                        new Thread(() -> generateList(currentFilter)).start();
                    });
                    return;
                }
                if (isRestorableSystemApp && appListViewOnClickMode != APPListViewOnClickMode_chooseAction) {
                    showToast(Main.this, R.string.restore_only_in_uninstalled_system_filter);
                    return;
                }
                switch (appListViewOnClickMode) {
                    case APPListViewOnClickMode_chooseAction:
                        showChooseActionPopupMenu(
                                getLightPopupContext(Main.this), Main.this, view, pkgName, name,
                                RestoreUtils.isRestoreFilter(currentFilter));
                        break;
                    case APPListViewOnClickMode_autoUFOrFreeze:
                        if (realGetFrozenStatus(Main.this, pkgName, null)) {
                            // Unfreezing is the way back to a missing status bar or keyboard, so it
                            // stays a single tap.
                            processSingleActionInProcess(
                                    Main.this, pkgName, null, null,
                                    false, false, false);
                        } else {
                            confirmIfCriticalPackagesThenRun(
                                    Collections.singletonList(pkgName), false,
                                    () -> processSingleActionInProcess(
                                            Main.this, pkgName, null, null,
                                            true, false, false));
                        }
                        break;
                    case APPListViewOnClickMode_freezeImmediately:
                        if (!realGetFrozenStatus(Main.this, pkgName, null)) {
                            confirmIfCriticalPackagesThenRun(
                                    Collections.singletonList(pkgName), false,
                                    () -> processSingleActionInProcess(
                                            Main.this, pkgName, null, null,
                                            true, false, false));
                        } else {
                            if (!lesserToast.getValue(null)) {
                                showToast(Main.this, R.string.freezeCompleted);
                            }
                        }
                        break;
                    case APPListViewOnClickMode_UFImmediately:
                        if (realGetFrozenStatus(Main.this, pkgName, null)) {
                            processSingleActionInProcess(
                                    Main.this, pkgName, null, null,
                                    false, false, false);
                        } else {
                            if (!lesserToast.getValue(null)) {
                                showToast(Main.this, R.string.UFCompleted);
                            }
                        }
                        break;
                    case APPListViewOnClickMode_UFAndRun:
                        if (realGetFrozenStatus(Main.this, pkgName, null)) {
                            processSingleActionInProcess(
                                    Main.this, pkgName, null, null,
                                    false, true, false);
                        } else {
                            if (!lesserToast.getValue(null)) {
                                showToast(Main.this, R.string.UFCompleted);
                            }
                            askRun(Main.this, pkgName, null,
                                    null, false, null, false);
                        }
                        break;
                    case APPListViewOnClickMode_autoUFOrFreezeAndRun:
                        if (realGetFrozenStatus(Main.this, pkgName, null)) {
                            processSingleActionInProcess(
                                    Main.this, pkgName, null, null,
                                    false, true, false);
                        } else {
                            confirmIfCriticalPackagesThenRun(
                                    Collections.singletonList(pkgName), false,
                                    () -> processSingleActionInProcess(
                                            Main.this, pkgName, null, null,
                                            true, false, false));
                        }
                        break;
                    case APPListViewOnClickMode_addToOFList:
                        new Thread(() -> showToast(Main.this,
                                addToOneKeyList(applicationContext, getString(R.string.sAutoFreezeApplicationList), pkgName) ? R.string.added : R.string.failed),
                                "OneKeyListMutation").start();
                        break;
                    case APPListViewOnClickMode_removeFromOFList:
                        new Thread(() -> showToast(Main.this,
                                removeFromOneKeyList(applicationContext, getString(R.string.sAutoFreezeApplicationList), pkgName) ? R.string.removed : R.string.failed),
                                "OneKeyListMutation").start();
                        break;
                    case APPListViewOnClickMode_addToOUFList:
                        new Thread(() -> showToast(Main.this,
                                addToOneKeyList(applicationContext, getString(R.string.sOneKeyUFApplicationList), pkgName) ? R.string.added : R.string.failed),
                                "OneKeyListMutation").start();
                        break;
                    case APPListViewOnClickMode_removeFromOUFList:
                        new Thread(() -> showToast(Main.this,
                                removeFromOneKeyList(applicationContext, getString(R.string.sOneKeyUFApplicationList), pkgName) ? R.string.removed : R.string.failed),
                                "OneKeyListMutation").start();
                        break;
                    case APPListViewOnClickMode_addToFOQList:
                        new Thread(() -> showToast(Main.this,
                                addToOneKeyList(applicationContext, getString(R.string.sFreezeOnceQuit), pkgName) ? R.string.added : R.string.failed),
                                "OneKeyListMutation").start();
                        break;
                    case APPListViewOnClickMode_removeFromFOQList:
                        new Thread(() -> showToast(Main.this,
                                removeFromOneKeyList(applicationContext, getString(R.string.sFreezeOnceQuit), pkgName) ? R.string.removed : R.string.failed),
                                "OneKeyListMutation").start();
                        break;
                    case APPListViewOnClickMode_createFUFShortcut:
                        checkSettingsAndRequestCreateShortcut(
                                name,
                                pkgName,
                                getApplicationIcon(
                                        Main.this,
                                        pkgName,
                                        getApplicationInfoFromPkgName(pkgName, Main.this),
                                        false),
                                Freeze.class,
                                "FreezeYou! " + pkgName,
                                Main.this);
                        break;
                    default:
                        break;
                }
            }
        });
            }
        });
    }

    private static void addNotAvailablePair(Context context, List<Map<String, Object>> AppList) {
        Map<String, Object> keyValuePair = new HashMap<>();
        keyValuePair.put("Img", android.R.drawable.sym_def_app_icon);
        keyValuePair.put("Name", context.getString(R.string.notAvailable));
        keyValuePair.put("PackageName", context.getString(R.string.notAvailable));
        keyValuePair.put("InstallTime", 0L);
        keyValuePair.put("UpdateTime", 0L);
        AppList.add(keyValuePair);
    }

    /**
     * The log used to go one place only: a crash report page belonging to the upstream author,
     * which is of no use to a fork. It is handed to the system share sheet instead, so it can go
     * wherever it is actually wanted.
     */
    private void manageCrashLog() throws Exception {
        File crashCheck = new File(getCacheDir() + File.separator + "log" + File.separator + "NeedUpload.log");
        if (!crashCheck.exists()) {
            checkIfNeedAskFirstTimeSetupAndShowDialog();
            return;
        }
        BufferedReader bufferedReader = new BufferedReader(new FileReader(crashCheck));
        String filePath = bufferedReader.readLine();
        bufferedReader.close();
        // Dropped first: whatever happens with the dialog, the same log must not be offered again.
        crashCheck.delete();
        final File logFile = filePath == null ? null : new File(filePath);
        if (logFile == null || !logFile.isFile()) {
            checkIfNeedAskFirstTimeSetupAndShowDialog();
            return;
        }
        final String logText = readTextFile(logFile);
        buildAlertDialog(Main.this, R.mipmap.ic_launcher_new_round, R.string.ifUploadCrashLog, R.string.notice)
                .setPositiveButton(R.string.share, (dialogInterface, i) -> {
                    shareCrashLog(logFile, logText);
                    checkIfNeedAskFirstTimeSetupAndShowDialog();
                })
                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                    logFile.delete();
                    checkIfNeedAskFirstTimeSetupAndShowDialog();
                })
                .setOnCancelListener(dialogInterface ->
                        checkIfNeedAskFirstTimeSetupAndShowDialog())
                .create()
                .show();
    }

    private static String readTextFile(File file) throws Exception {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fileInputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, read);
            }
            return byteArrayOutputStream.toString();
        }
    }

    /**
     * The crash log already sits in a file, so that file is handed over as it is. Everything about
     * how a log is shared lives in one place, which the log viewer uses as well.
     */
    private void shareCrashLog(File logFile, String logText) {
        LogSharingUtils.shareLog(
                this,
                getString(R.string.app_name) + " \u2014 " + getString(R.string.crashLog),
                logText,
                logFile
        );
    }

    /**
     * Resolves the list/grid mode for the given pattern. The automatic pattern follows the
     * orientation, and only falls back to the aspect ratio when the configuration has none
     * (for example in freeform windows).
     */
    public static boolean resolveGridMode(String pattern, int orientation, int width, int height) {
        if ("grid".equals(pattern)) {
            return true;
        }
        if ("list".equals(pattern)) {
            return false;
        }
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return true;
        }
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return false;
        }
        return height > 0 && width > height * 1.2;
    }

    public static boolean isPackageChangeAction(String action) {
        return Intent.ACTION_PACKAGE_ADDED.equals(action)
                || Intent.ACTION_PACKAGE_REMOVED.equals(action)
                || Intent.ACTION_PACKAGE_REPLACED.equals(action);
    }

    public static boolean shouldDropPackageFromSelectionOnRemoval(String action, boolean isReplacing) {
        return Intent.ACTION_PACKAGE_REMOVED.equals(action) && !isReplacing;
    }

    private void go() {
        if (updateFrozenStatusBroadcastReceiver == null) {
            updateFrozenStatusBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null) {
                        return;
                    }
                    // An intent aimed at somebody else is not ours to answer. A missing target is
                    // allowed on purpose: the senders outside Main do not name a package, and on
                    // every Android version the registration above already keeps them to this app.
                    if (intent.getPackage() != null && !Main.this.getPackageName().equals(intent.getPackage())) {
                        return;
                    }
                    if (intent.getBooleanExtra("uninstallSuccess", false)) {
                        showUninstallCompletedDialog(
                                intent.getStringExtra("appName"),
                                intent.getBooleanExtra("uninstallUpdatesOnly", false),
                                intent.getIntExtra("batchCount", -1)
                        );
                    }
                    if (intent.getBooleanExtra("refreshAppList", false)
                            || isStatusDependentFilter(currentFilter)) {
                        // A status change can add or remove the row from a status-dependent list
                        // (frozen, unfrozen, running), so the filter has to be re-run; patching
                        // the dot in place would leave the app in a list it no longer matches.
                        new Thread(() -> generateList(currentFilter)).start();
                    } else {
                        // A single action names its package; re-checking every row costs two
                        // binder calls per app on the UI thread, which is most of the visible lag.
                        updateFrozenStatus(intent.getStringExtra("pkgName"));
                    }
                }
            };
            IntentFilter filter = new IntentFilter("cf.playhi.freezeyou.action.packageStatusChanged");
            filter.addAction("cf.playhi.freezeyou.action.packageStatusChanged");
            // Everything this receiver reacts to is sent by this app. Exporting it let any local
            // application rebuild the whole list or pop the uninstall-completed dialog. The flag
            // is not version-gated: below Android 13 ContextCompat registers the receiver behind
            // the app's own signature permission, so the sender restriction holds there too, while
            // the broadcasts this app sends itself still arrive.
            ContextCompat.registerReceiver(
                    Main.this,
                    updateFrozenStatusBroadcastReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );
        }

        if (mPackageChangeReceiver == null) {
            mPackageChangeReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null) {
                        return;
                    }
                    String action = intent.getAction();
                    if (!isPackageChangeAction(action)) {
                        return;
                    }
                    if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                        boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
                        Uri data = intent.getData();
                        String pkgName = data == null ? null : data.getSchemeSpecificPart();
                        if (shouldDropPackageFromSelectionOnRemoval(action, replacing)
                                && pkgName != null
                                && selectedPackages.contains(pkgName)) {
                            selectedPackages.remove(pkgName);
                            if (currentSelectionActionMode != null) {
                                if (selectedPackages.isEmpty()) {
                                    // Nothing is left to act on, so an empty action bar would
                                    // only offer actions against no packages.
                                    currentSelectionActionMode.finish();
                                } else {
                                    currentSelectionActionMode.setTitle(Integer.toString(selectedPackages.size()));
                                    Menu menu = currentSelectionActionMode.getMenu();
                                    if (menu != null) {
                                        updateRestoreMenuVisibility(menu);
                                    }
                                }
                            }
                        }
                    }
                    // Installed, updated or removed outside this app: nothing has refreshed the
                    // rows' membership, labels or icons yet.
                    new Thread(() -> generateList(currentFilter)).start();
                }
            };
            IntentFilter packageChangeFilter = new IntentFilter();
            packageChangeFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
            packageChangeFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            packageChangeFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            packageChangeFilter.addDataScheme("package");
            ContextCompat.registerReceiver(
                    Main.this,
                    mPackageChangeReceiver,
                    packageChangeFilter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );
        }

        TasksUtils.checkTimeTasks(this);
        TasksUtils.checkTriggerTasks(this);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(Main.this);
        if (sharedPref.getBoolean(saveOnClickFunctionStatus.name(), saveOnClickFunctionStatus.defaultValue())) {
            appListViewOnClickMode = sharedPref.getInt("onClickFunctionStatus", APPListViewOnClickMode_chooseAction);
        }
        if (sharedPref.getBoolean(saveSortMethodStatus.name(), saveSortMethodStatus.defaultValue())) {
            currentSortRule = sharedPref.getInt("sortMethodStatus", SORT_BY_DEFAULT);
        }
        if (!sharedPref.getBoolean(noCaution.name(), noCaution.defaultValue())) {
            buildAlertDialog(Main.this, R.mipmap.ic_launcher_new_round, R.string.cautionContent, R.string.caution)
                    .setPositiveButton(android.R.string.ok, (dialogInterface, ii) -> {
                    })
                    .setNeutralButton(R.string.hToUse, (dialog, which) ->
                            requestOpenWebSite(Main.this, "https://www.zidon.net/"))
                    .setNegativeButton(R.string.nCaution, (dialog, which) ->
                            sharedPref.edit().putBoolean(noCaution.name(), true).apply())
                    .create().show();
        }

        String mainPattern =
                sharedPref.getString(mainActivityPattern.name(), mainActivityPattern.defaultValue());
        isGridMode = resolveGridMode(
                mainPattern,
                getResources().getConfiguration().orientation,
                getWindowManager().getDefaultDisplay().getWidth(),
                getWindowManager().getDefaultDisplay().getHeight()
        );
        mMainActivityAppListFragment = new MainActivityAppListFragment();
        mMainActivityAppListFragment.setUseGridMode(isGridMode);

        // replace(), not add(): after recreate() the FragmentManager has already restored the
        // previous list fragment into this container, and add() stacked a second one on top of it.
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.main_appList_fragmentContainer_frameLayout, mMainActivityAppListFragment);
        fragmentTransaction.commit();

        Thread initThread;
        initThread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (restoreFilterFromSavedState) {
                    generateList(currentFilter, currentSortRule, currentCategoryLabel);
                    return;
                }
                String mode = getIntent().getStringExtra("pkgName");
                if (mode == null) {
                    mode = launchMode.getValue(Main.this);
                }
                if (mode == null) {
                    mode = "";
                }
                if (mode.startsWith("CATEGORY")) {
                    String categoryLabel = mode.substring("CATEGORY".length());
                    generateListForCategory(categoryLabel);
                    return;
                }
                switch (mode) {
                    case "OF":
                        generateList("OF");
                        break;
                    case "UF":
                        generateList("UF");
                        break;
                    case "OO":
                        generateList("OO");
                        break;
                    case "OOU":
                        generateList("OOU");
                        break;
                    case "OS":
                        generateList("OS");
                        break;
                    case "OU":
                        generateList("OU");
                        break;
                    case "FOQ":
                        generateList("FOQ");
                        break;
                    case "UFU":
                        generateList("UFU");
                        break;
                    default:
                        generateList("all");
                        break;
                }
            }
        });
        initThread.start();
        checkLongTimeNotUpdated();
    }

    private void processFrozenStatus(Map<String, Object> keyValuePair, String packageName, PackageManager packageManager) {
        boolean frozen = realGetFrozenStatus(Main.this, packageName, packageManager);
        // The dot at the end of the row, and the plain flag the adapter greys the icon by.
        keyValuePair.put("isFrozen", frozen ? customThemeDisabledDot : customThemeEnabledDot);
        keyValuePair.put("Frozen", frozen);
    }

    private Map<String, Object> processAppStatus(String name, String packageName, PackageInfo packageInfo, PackageManager packageManager, boolean saveIconCache) {
        if (!("android".equals(packageName) || getPackageName().equals(packageName))) {
            Map<String, Object> keyValuePair = new HashMap<>();
            keyValuePair.put(
                    "Img",
                    getApplicationIcon(
                            Main.this,
                            packageName,
                            packageInfo.applicationInfo,
                            false,
                            saveIconCache
                    )
            );
            keyValuePair.put("Name", name);
            processFrozenStatus(keyValuePair, packageName, packageManager);
            keyValuePair.put("PackageName", packageName);
            boolean isRestorable = RestoreUtils.isRestorableSystemApp(packageInfo.applicationInfo);
            keyValuePair.put("RestorableSystemApp", isRestorable);
            if (isRestorable) {
                keyValuePair.put("isFrozen", customThemeDisabledDot);
                keyValuePair.put("Frozen", true);
            }
            keyValuePair.put("InstallTime", packageInfo.firstInstallTime);
            keyValuePair.put("UpdateTime", packageInfo.lastUpdateTime);
            return keyValuePair;
        }
        return null;
    }

    private void oneKeyListGenerate(String[] source, List<Map<String, Object>> AppList) {
        String name;
        Drawable icon;
        for (String aPkg : source) {
            name = getApplicationLabel(getApplicationContext(), null, null, aPkg);
            long installTime = 0L;
            long updateTime = 0L;
            boolean restorableSystemApp = false;
            try {
                PackageInfo pi = getPackageManager().getPackageInfo(aPkg, PackageManager.GET_UNINSTALLED_PACKAGES);
                installTime = pi.firstInstallTime;
                updateTime = pi.lastUpdateTime;
                restorableSystemApp = RestoreUtils.isRestorableSystemApp(pi.applicationInfo);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            if (!("android".equals(aPkg) || getPackageName().equals(aPkg) || "".equals(aPkg))) {
                Map<String, Object> keyValuePair = new HashMap<>();
                icon = getApplicationIcon(
                        Main.this,
                        aPkg,
                        getApplicationInfoFromPkgName(aPkg, Main.this),
                        true
                );
                keyValuePair.put("Img", icon);
                keyValuePair.put("Name", name);
                processFrozenStatus(keyValuePair, aPkg, null);
                keyValuePair.put("PackageName", aPkg);
                keyValuePair.put("RestorableSystemApp", restorableSystemApp);
                if (restorableSystemApp) {
                    keyValuePair.put("isFrozen", customThemeDisabledDot);
                    keyValuePair.put("Frozen", true);
                }
                keyValuePair.put("InstallTime", installTime);
                keyValuePair.put("UpdateTime", updateTime);
                AppList.add(keyValuePair);
            }
        }
    }

    private void oneKeyListCheckAndGenerate(String pkgNames, List<Map<String, Object>> AppList) {
        if (pkgNames != null) {
            oneKeyListGenerate(pkgNames.split(","), AppList);
        }
    }

    private void checkAndAddNotAvailablePair(List<Map<String, Object>> AppList) {
        if (AppList.size() == 0) {
            addNotAvailablePair(getApplicationContext(), AppList);
        }
    }

    /**
     * Re-checks the rows that were selected before a configuration swap rebuilt the list. Checking
     * an item in a modal-choice list starts the selection action mode by itself, so the checked
     * state and its controls come back together. The listener reports every check it receives;
     * the membership is left alone because the selection is exactly what is being restored.
     */
    private void restoreCheckedSelection(MainAppListSimpleAdapter adapter) {
        if (selectedPackages.isEmpty() || mMainActivityAppListFragment == null) {
            return;
        }
        ArrayList<Map<String, Object>> rows = adapter.getStoredArrayList();
        // The selection is carried by checked rows. An application the active search hides has no
        // row to restore, and leaving it in the list would keep it in batch actions without
        // anything on screen showing it is still selected.
        Set<String> visiblePackages = new HashSet<>();
        for (Map<String, Object> row : rows) {
            visiblePackages.add((String) row.get("PackageName"));
        }
        selectedPackages.retainAll(visiblePackages);
        if (selectedPackages.isEmpty()) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (selectedPackages.contains((String) rows.get(i).get("PackageName"))) {
                needProcessOnItemCheckedStateChanged = false;
                mMainActivityAppListFragment.setItemChecked(i, true);
            }
        }
        if (currentSelectionActionMode != null) {
            currentSelectionActionMode.setTitle(Integer.toString(selectedPackages.size()));
            updateRestoreMenuVisibility(currentSelectionActionMode.getMenu());
        }
    }

    public static boolean canForceStop(boolean installedAppActionsVisible, boolean hasUnfrozenSelection) {
        return installedAppActionsVisible && hasUnfrozenSelection;
    }

    public static List<String> filterForceStopTargets(List<String> selectedPackages, Set<String> frozenPackageSet) {
        List<String> targetPackages = new ArrayList<>();
        if (selectedPackages == null) {
            return targetPackages;
        }
        for (String pkg : selectedPackages) {
            if (frozenPackageSet == null || !frozenPackageSet.contains(pkg)) {
                targetPackages.add(pkg);
            }
        }
        return targetPackages;
    }

    public static boolean hasUnfrozenPackage(Set<String> selectedPackages, List<Map<String, Object>> packageRows) {
        if (selectedPackages == null || packageRows == null) {
            return false;
        }
        for (Map<String, Object> row : packageRows) {
            String packageName = (String) row.get("PackageName");
            if (selectedPackages.contains(packageName) && !Boolean.TRUE.equals(row.get("Frozen"))) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasUninstalledSystemPackage(Set<String> selectedPackages, List<Map<String, Object>> packageRows) {
        if (selectedPackages == null || packageRows == null) {
            return false;
        }
        for (Map<String, Object> row : packageRows) {
            String packageName = (String) row.get("PackageName");
            if (selectedPackages.contains(packageName) && Boolean.TRUE.equals(row.get("RestorableSystemApp"))) {
                return true;
            }
        }
        return false;
    }

    private void updateRestoreMenuVisibility(Menu menu) {
        MenuItem restoreItem = menu.findItem(R.id.list_menu_restoreImmediately);
        if (restoreItem == null) return;
        boolean uninstalledSystemFilter = RestoreUtils.isRestoreFilter(currentFilter);
        boolean eligible = uninstalledSystemFilter && Build.VERSION.SDK_INT >= 26
                && !selectedPackages.isEmpty()
                && !selectedPackages.contains(getString(R.string.notAvailable));
        restoreItem.setVisible(eligible);

        // Saved one-key/custom lists can retain an uninstalled system package, so inspect the
        // selected rows' cached flags rather than relying on the current filter alone. The
        // adapter carries only the rows the search shows, so a selected package the query hides
        // would otherwise drop out of the calculation and hide or expose the wrong actions.
        Set<String> selectedPackageSet = new HashSet<>(selectedPackages);
        List<Map<String, Object>> rows = mCurrentUnfilteredAppList;
        if (rows == null
                && mMainActivityAppListFragment != null
                && mMainActivityAppListFragment.getAppListAdapter() instanceof MainAppListSimpleAdapter) {
            rows = ((MainAppListSimpleAdapter)
                    mMainActivityAppListFragment.getAppListAdapter()).getStoredArrayList();
        }
        boolean hasUninstalledSystemSelection = hasUninstalledSystemPackage(selectedPackageSet, rows);
        boolean hasUnfrozenSelection = hasUnfrozenPackage(selectedPackageSet, rows);
        boolean installedAppActionsVisible = !uninstalledSystemFilter
                && !hasUninstalledSystemSelection;

        boolean forceStopVisible = canForceStop(installedAppActionsVisible, hasUnfrozenSelection);

        menu.findItem(R.id.list_menu_freezeImmediately).setVisible(installedAppActionsVisible);
        menu.findItem(R.id.list_menu_UFImmediately).setVisible(installedAppActionsVisible);
        menu.findItem(R.id.list_menu_ForceStopImmediately).setVisible(forceStopVisible);
        menu.findItem(R.id.list_menu_createDisEnableShortCut).setVisible(installedAppActionsVisible);
        MenuItem uninstallItem = menu.findItem(R.id.list_menu_uninstallImmediately);
        if (uninstallItem != null) {
            uninstallItem.setVisible(installedAppActionsVisible);
        }
    }

    private void processAddToOneKeyList(final String string) {
        final ArrayList<String> packages = new ArrayList<>(selectedPackages);
        final Context appContext = getApplicationContext();
        new Thread(() -> showToast(Main.this,
                addToOneKeyList(appContext, string, packages) ? R.string.success : R.string.failed),
                "OneKeyListMutation").start();
    }

    private void processRemoveFromOneKeyList(final String string) {
        final ArrayList<String> packages = new ArrayList<>(selectedPackages);
        final Context appContext = getApplicationContext();
        new Thread(() -> showToast(Main.this,
                removeFromOneKeyList(appContext, string, packages) ? R.string.success : R.string.failed),
                "OneKeyListMutation").start();
    }

    private void updateUserDefinedCategoryFromSelection(
            Context context, String title, ArrayList<String> packages, boolean remove) {
        final String encodedTitle = Base64.encodeToString(title.getBytes(), Base64.DEFAULT);
        // The helper reads the category under the same lock the per-app popup takes, so the two
        // writers cannot overwrite each other's membership change with a stale copy.
        final OneKeyListUtils.CategoryUpdate update = OneKeyListUtils.updateUserDefinedCategory(
                context, encodedTitle, packages, remove);
        if (update == null) {
            return;
        }
        showToast(Main.this, remove ? R.string.removed : R.string.added);
        runOnUiThread(() -> {
            // The list may have been opened before this worker committed: refresh it with the
            // membership just written, but only while it still shows that category - the user
            // may have opened another filter in the meantime. A later edit of the same category
            // supersedes this one even if this callback happens to land after it.
            if (isFinishing()
                    || !encodedTitle.equals(currentCategoryLabel)
                    || !OneKeyListUtils.isLatestCategoryUpdate(encodedTitle, update.revision)) {
                return;
            }
            final int sortRule = currentSortRule;
            new Thread(() -> {
                if (isFinishing()
                        || !encodedTitle.equals(currentCategoryLabel)
                        || !OneKeyListUtils.isLatestCategoryUpdate(encodedTitle, update.revision)) {
                    return;
                }
                generateList(update.packages, sortRule, encodedTitle);
            }, "UserDefinedCategoryRefresh").start();
        });
    }

    private void processDisableAndEnableImmediately(boolean freeze) {
        Set<String> selectedSet = new HashSet<>(selectedPackages);
        applyFrozenStateToRows(mCurrentUnfilteredAppList, selectedSet, freeze);
        if (mMainActivityAppListFragment != null
                && mMainActivityAppListFragment.getAppListAdapter() instanceof MainAppListSimpleAdapter) {
            MainAppListSimpleAdapter appListAdapter = (MainAppListSimpleAdapter)
                    mMainActivityAppListFragment.getAppListAdapter();
            applyFrozenStateToRows(appListAdapter.getStoredArrayList(), selectedSet, freeze);
            appListAdapter.notifyDataSetChanged();
        }
        int size = selectedPackages.size();
        String[] pkgNameList = selectedPackages.toArray(new String[size]);
        FUFUtils.processBatchAction(Main.this, pkgNameList, freeze);
    }

    /**
     * The adapter holds only the rows the current search shows, so the unfiltered list is patched
     * as well: clearing the query would otherwise bring the old status dots back.
     */
    private void applyFrozenStateToRows(List<Map<String, Object>> rows, Set<String> selectedSet, boolean freeze) {
        if (rows == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            if (selectedSet.contains((String) row.get("PackageName"))) {
                row.put("Frozen", freeze);
                row.put("isFrozen", freeze ? customThemeDisabledDot : customThemeEnabledDot);
            }
        }
    }

    private void processForceStopImmediately() {
        Set<String> frozenPackageSet = new HashSet<>();
        List<Map<String, Object>> rows = mCurrentUnfilteredAppList;
        if (rows == null
                && mMainActivityAppListFragment != null
                && mMainActivityAppListFragment.getAppListAdapter() instanceof MainAppListSimpleAdapter) {
            rows = ((MainAppListSimpleAdapter)
                    mMainActivityAppListFragment.getAppListAdapter()).getStoredArrayList();
        }
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                if (Boolean.TRUE.equals(row.get("Frozen"))) {
                    frozenPackageSet.add((String) row.get("PackageName"));
                }
            }
        }
        List<String> targetPackages = filterForceStopTargets(selectedPackages, frozenPackageSet);
        if (targetPackages.isEmpty()) {
            return;
        }
        String[] pkgNameList = targetPackages.toArray(new String[0]);
        // No toast here on purpose: ForceStopUtils.forceStop is the one that learns whether the
        // elevated shell was reachable, and it already reports "executed" or the failure. Saying
        // "executed" up front would announce a success that may never happen, and would ignore
        // the lesserToast preference that the real report respects.
        ServiceUtils.startService(
                Main.this,
                new Intent(Main.this, ForceStopService.class)
                        .putExtra("packages", pkgNameList)
        );
    }

    private void processBatchUninstall() {
        if (selectedPackages.isEmpty() || mIsBatchUninstallRunning || mIsBatchUninstallChecking) {
            return;
        }

        final List<String> userApps = new ArrayList<>();
        final List<String> systemAppsWithUpdates = new ArrayList<>();
        final List<String> systemApps = new ArrayList<>();

        PackageManager pm = getPackageManager();
        String notAvailableStr = getString(R.string.notAvailable);
        for (String pkg : selectedPackages) {
            if (notAvailableStr.equals(pkg)) continue;
            try {
                PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
                if (pi.applicationInfo == null || RestoreUtils.isAppUninstalled(pi.applicationInfo)) {
                    continue;
                }
                boolean isSystem = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean hasUpdate = (pi.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                if (!isSystem) {
                    userApps.add(pkg);
                } else if (hasUpdate) {
                    systemAppsWithUpdates.add(pkg);
                } else {
                    systemApps.add(pkg);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        int totalFound = userApps.size() + systemAppsWithUpdates.size() + systemApps.size();
        if (totalFound == 0) {
            return;
        }

        final List<String> selectedSnapshot = new ArrayList<>(selectedPackages);

        final boolean shizukuAvailable = PrivilegedShellUtils.isShizukuAvailable();
        if (shizukuAvailable) {
            showBatchUninstallDialog(userApps, systemAppsWithUpdates, systemApps, totalFound, true, false);
        } else {
            mIsBatchUninstallChecking = true;
            new Thread(() -> {
                boolean root = false;
                try {
                    root = FUFUtils.checkRootPermission();
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    final boolean rootAvailable = root;
                    runOnUiThread(() -> {
                        mIsBatchUninstallChecking = false;
                        if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                            return;
                        }
                        // The action mode is a new instance after a configuration swap restores
                        // the selection, so identity cannot be compared here: the unchanged
                        // package snapshot is the thing that says this result still applies.
                        if (currentSelectionActionMode == null
                                || !selectedPackages.equals(selectedSnapshot)) {
                            return;
                        }
                        showBatchUninstallDialog(userApps, systemAppsWithUpdates, systemApps, totalFound, false, rootAvailable);
                    });
                }
            }, "BatchUninstallRootCheck").start();
        }
    }

    private void showBatchUninstallDialog(
            List<String> userApps,
            List<String> systemAppsWithUpdates,
            List<String> systemApps,
            int totalFound,
            boolean shizukuAvailable,
            boolean rootAvailable) {

        boolean hasPrivilegedShell = shizukuAvailable || rootAvailable;
        int skippedSystemCount = systemAppsWithUpdates.size() + systemApps.size();

        if (!hasPrivilegedShell) {
            if (userApps.isEmpty()) {
                FreezeYouAlertDialogBuilder(this)
                        .setTitle(R.string.batch_uninstall)
                        .setMessage(R.string.full_system_uninstall_requires_shell)
                        .setPositiveButton(R.string.okay, null)
                        .show();
                return;
            }

            StringBuilder msg = new StringBuilder();
            msg.append("• ").append(getString(R.string.user_applications)).append(": ").append(userApps.size());
            if (skippedSystemCount > 0) {
                msg.append("\n\n").append(getString(R.string.batch_uninstall_system_apps_require_shell, skippedSystemCount));
            }

            FreezeYouAlertDialogBuilder(this)
                    .setTitle(R.string.batch_uninstall)
                    .setMessage(msg.toString())
                    .setPositiveButton(getString(R.string.uninstall) + " (" + userApps.size() + ")", (dialog, which) -> {
                        confirmIfCriticalSelectedThenRun(true, () -> {
                            if (currentSelectionActionMode != null) {
                                currentSelectionActionMode.finish();
                            }
                            executeBatchUninstall(userApps, Collections.emptyList(), Collections.emptyList(), false, false, false);
                        });
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        StringBuilder summary = new StringBuilder();
        if (!userApps.isEmpty()) {
            summary.append("• ").append(getString(R.string.user_applications)).append(": ").append(userApps.size()).append("\n");
        }
        if (!systemAppsWithUpdates.isEmpty()) {
            summary.append("• ").append(getString(R.string.system_applications_with_updates)).append(": ").append(systemAppsWithUpdates.size()).append("\n");
        }
        if (!systemApps.isEmpty()) {
            summary.append("• ").append(getString(R.string.system_applications)).append(": ").append(systemApps.size()).append("\n");
        }

        AlertDialog.Builder builder = FreezeYouAlertDialogBuilder(this)
                .setTitle(R.string.batch_uninstall)
                .setNegativeButton(R.string.cancel, null);

        if (!systemAppsWithUpdates.isEmpty()) {
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (18 * getResources().getDisplayMetrics().density);
            layout.setPadding(padding, padding / 2, padding, 0);

            TextView messageView = new TextView(this);
            messageView.setText(summary.toString().trim());
            messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            layout.addView(messageView);

            TextView promptView = new TextView(this);
            promptView.setText(R.string.batch_uninstall_action_for_updated_system);
            promptView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            promptView.setPadding(0, padding / 2, 0, padding / 4);
            layout.addView(promptView);

            RadioGroup radioGroup = new RadioGroup(this);
            RadioButton rbCompletely = new RadioButton(this);
            rbCompletely.setText(R.string.uninstall_completely);
            rbCompletely.setChecked(true);
            radioGroup.addView(rbCompletely);

            RadioButton rbUpdatesOnly = new RadioButton(this);
            rbUpdatesOnly.setText(R.string.uninstall_updates_only);
            radioGroup.addView(rbUpdatesOnly);

            layout.addView(radioGroup);
            builder.setView(layout);

            builder.setPositiveButton(getString(R.string.uninstall) + " (" + totalFound + ")", (dialog, which) -> {
                boolean uninstallUpdatesOnly = rbUpdatesOnly.isChecked();
                confirmIfCriticalSelectedThenRun(true, () -> {
                    if (currentSelectionActionMode != null) {
                        currentSelectionActionMode.finish();
                    }
                    executeBatchUninstall(userApps, systemAppsWithUpdates, systemApps, uninstallUpdatesOnly, true, shizukuAvailable);
                });
            });
        } else {
            builder.setMessage(summary.toString().trim());
            builder.setPositiveButton(getString(R.string.uninstall) + " (" + totalFound + ")", (dialog, which) -> {
                confirmIfCriticalSelectedThenRun(true, () -> {
                    if (currentSelectionActionMode != null) {
                        currentSelectionActionMode.finish();
                    }
                    executeBatchUninstall(userApps, systemAppsWithUpdates, systemApps, false, true, shizukuAvailable);
                });
            });
        }

        builder.show();
    }

    private void executeBatchUninstall(
            List<String> userApps,
            List<String> systemAppsWithUpdates,
            List<String> systemApps,
            boolean uninstallUpdatesOnly,
            boolean hasPrivilegedShell,
            boolean shizukuAvailable) {

        if (hasPrivilegedShell) {
            mIsBatchUninstallRunning = true;
            ProgressDialog progressDialog = new ProgressDialog(Main.this);
            progressDialog.setMessage(getString(R.string.batch_uninstalling));
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.show();

            new Thread(() -> {
                int successCount = 0;
                try {
                    int userId = AndroidUserUtils.currentUserId();
                List<BatchUninstallItem> items = new ArrayList<>();
                for (String pkg : userApps) {
                    items.add(new BatchUninstallItem(pkg, false, false, false));
                }
                for (String pkg : systemAppsWithUpdates) {
                    items.add(new BatchUninstallItem(pkg, true, true, uninstallUpdatesOnly));
                }
                for (String pkg : systemApps) {
                    items.add(new BatchUninstallItem(pkg, true, false, false));
                }

                for (BatchUninstallItem item : items) {
                    try {
                        boolean isSystem = item.isSystemApp;
                        boolean hasUpdate = item.hasSystemUpdate;
                        try {
                            PackageInfo freshPi = getPackageManager().getPackageInfo(item.packageName, PackageManager.GET_UNINSTALLED_PACKAGES);
                            if (freshPi != null && freshPi.applicationInfo != null) {
                                isSystem = (freshPi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                                hasUpdate = (freshPi.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                            }
                        } catch (Exception ignored) {}

                        boolean ok = false;
                        if (shizukuAvailable) {
                            try {
                                ok = ShizukuPackageInstaller.uninstall(
                                        Main.this,
                                        item.packageName,
                                        isSystem,
                                        hasUpdate,
                                        item.uninstallUpdatesOnly
                                );
                            } catch (Throwable t) {
                                t.printStackTrace();
                                ok = false;
                            }
                        }

                        // Fallback or pure Root execution via shell commands
                        if (!ok) {
                            List<String> commands = UninstallPolicyUtils.buildUninstallCommands(
                                    isSystem,
                                    hasUpdate,
                                    item.uninstallUpdatesOnly,
                                    userId,
                                    item.packageName
                            );
                            boolean cmdSuccess = true;
                            for (String cmd : commands) {
                                PrivilegedShellUtils.CommandResult result = shizukuAvailable
                                        ? PrivilegedShellUtils.runShizukuCommand(cmd)
                                        : PrivilegedShellUtils.runRootCommand(cmd);
                                if (!result.isSuccessful()) {
                                    cmdSuccess = false;
                                    break;
                                }
                            }
                            ok = cmdSuccess;
                        }

                        if (ok) {
                            successCount++;
                            sendBroadcast(new Intent("cf.playhi.freezeyou.action.packageStatusChanged")
                                    .setPackage(getPackageName())
                                    .putExtra("pkgName", item.packageName)
                                    .putExtra("refreshAppList", false));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                mIsBatchUninstallRunning = false;
            }

                final int finalSuccessCount = successCount;
                runOnUiThread(() -> {
                    try {
                        if (progressDialog.isShowing() && !isFinishing()) {
                            progressDialog.dismiss();
                        }
                    } catch (Exception ignored) {}
                    showUninstallCompletedDialog(null, false, finalSuccessCount);
                    new Thread(() -> generateList(currentFilter)).start();
                });
            }).start();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && DevicePolicyManagerUtils.isDeviceOwner(Main.this)) {
            mIsBatchUninstallRunning = true;
            ProgressDialog progressDialog = new ProgressDialog(Main.this);
            progressDialog.setMessage(getString(R.string.batch_uninstalling));
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.show();

            new Thread(() -> {
                PackageManager pm = getPackageManager();
                PackageInstaller packageInstaller = pm.getPackageInstaller();
                final int total = userApps.size();
                final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(total);
                final java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
                final String uniqueAction = "cf.playhi.freezeyou.action.DO_UNINSTALL_RESULT." + java.util.UUID.randomUUID().toString();

                BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (intent != null) {
                            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
                            if (status == PackageInstaller.STATUS_SUCCESS) {
                                successCount.incrementAndGet();
                            }
                            latch.countDown();
                        }
                    }
                };

                androidx.core.content.ContextCompat.registerReceiver(
                        Main.this,
                        receiver,
                        new IntentFilter(uniqueAction),
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? androidx.core.content.ContextCompat.RECEIVER_EXPORTED : 0
                );

                try {
                    for (String pkg : userApps) {
                        try {
                            Intent statusIntent = new Intent(uniqueAction).setPackage(getPackageName());
                            int pendingFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                                    ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                                    : PendingIntent.FLAG_UPDATE_CURRENT;
                            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                                    Main.this,
                                    pkg.hashCode(),
                                    statusIntent,
                                    pendingFlags
                            );
                            packageInstaller.uninstall(pkg, pendingIntent.getIntentSender());
                        } catch (Exception e) {
                            e.printStackTrace();
                            latch.countDown();
                        }
                    }
                    latch.await(Math.min(total * 4L, 30L), java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    mIsBatchUninstallRunning = false;
                    try {
                        unregisterReceiver(receiver);
                    } catch (Exception ignored) {}
                }

                runOnUiThread(() -> {
                    try {
                        if (progressDialog.isShowing() && !isFinishing()) {
                            progressDialog.dismiss();
                        }
                    } catch (Exception ignored) {}
                    showUninstallCompletedDialog(null, false, successCount.get());
                    new Thread(() -> generateList(currentFilter)).start();
                });
            }).start();
            return;
        }

        startUnprivilegedUninstallQueue(userApps);
    }

    /**
     * A cancelled system uninstall prompt ends the whole batch: the user pressed back, and the
     * prompts left in the queue would only trap them into confirming one package after another.
     * The uninstaller reports RESULT_CANCELED by default though, so a removal that went through
     * arrives looking exactly like a back press - the package has to still be installed before
     * this may throw the rest of the queue away.
     */
    static boolean abortUninstallBatchOnCancel(int resultCode, boolean currentPkgStillInstalled) {
        return resultCode == RESULT_CANCELED && currentPkgStillInstalled;
    }

    private boolean isPackageStillInstalled(String pkg) {
        if (pkg == null) {
            return false;
        }
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void startUnprivilegedUninstallQueue(List<String> userApps) {
        mUnprivilegedUninstallQueue.clear();
        mUnprivilegedUninstallQueue.addAll(userApps);
        mUnprivilegedUninstallSuccessCount = 0;
        mCurrentUnprivilegedUninstallPkg = null;
        // The queue spans many activity results, so a second batch must not start over it.
        mIsBatchUninstallRunning = true;
        processNextUnprivilegedUninstall();
    }

    private void processNextUnprivilegedUninstall() {
        if (mCurrentUnprivilegedUninstallPkg != null) {
            if (!isPackageStillInstalled(mCurrentUnprivilegedUninstallPkg)) {
                mUnprivilegedUninstallSuccessCount++;
            }
            mCurrentUnprivilegedUninstallPkg = null;
        }

        if (mUnprivilegedUninstallQueue.isEmpty()) {
            mIsBatchUninstallRunning = false;
            showUninstallCompletedDialog(null, false, mUnprivilegedUninstallSuccessCount);
            sendBroadcast(new Intent("cf.playhi.freezeyou.action.packageStatusChanged")
                    .setPackage(getPackageName())
                    .putExtra("refreshAppList", true));
            new Thread(() -> generateList(currentFilter)).start();
            return;
        }

        mCurrentUnprivilegedUninstallPkg = mUnprivilegedUninstallQueue.poll();
        try {
            startActivityForResult(
                    new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + mCurrentUnprivilegedUninstallPkg)),
                    80002
            );
        } catch (Exception e) {
            e.printStackTrace();
            processNextUnprivilegedUninstall();
        }
    }

    private AlertDialog mUninstallCompletedDialog = null;
    private volatile boolean mIsBatchUninstallRunning = false;
    private volatile boolean mIsBatchUninstallChecking = false;

    private void showUninstallCompletedDialog(String appName, boolean updatesOnly, int batchCount) {
        if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
            return;
        }
        if (hideUninstallSuccessDialog.getValue(this)) {
            return;
        }
        String message;
        if (batchCount > 0) {
            message = String.format(getString(R.string.batch_uninstall_result), batchCount);
        } else if (appName != null) {
            message = String.format(getString(updatesOnly
                    ? R.string.updates_uninstallFinished
                    : R.string.app_uninstallFinished), appName);
        } else {
            message = getString(R.string.uninstall_completed);
        }

        try {
            if (mUninstallCompletedDialog != null && mUninstallCompletedDialog.isShowing()) {
                mUninstallCompletedDialog.dismiss();
            }
        } catch (Exception ignored) {}

        AlertDialog.Builder builder = FreezeYouAlertDialogBuilder(this);
        builder.setIcon(R.mipmap.ic_launcher_new_round);
        builder.setTitle(R.string.uninstall_completed);
        builder.setMessage(message);
        builder.setPositiveButton(R.string.okay, null);
        mUninstallCompletedDialog = builder.create();
        mUninstallCompletedDialog.setCanceledOnTouchOutside(true);
        if (isLightUiTheme(this)) {
            mUninstallCompletedDialog.setOnShowListener(dialog -> {
                android.widget.Button positiveButton = mUninstallCompletedDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (positiveButton != null) {
                    positiveButton.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.appAccent));
                }
            });
        }
        mUninstallCompletedDialog.show();
    }

    private static final class BatchUninstallItem {
        final String packageName;
        final boolean isSystemApp;
        final boolean hasSystemUpdate;
        final boolean uninstallUpdatesOnly;

        BatchUninstallItem(String packageName, boolean isSystemApp, boolean hasSystemUpdate, boolean uninstallUpdatesOnly) {
            this.packageName = packageName;
            this.isSystemApp = isSystemApp;
            this.hasSystemUpdate = hasSystemUpdate;
            this.uninstallUpdatesOnly = uninstallUpdatesOnly;
        }
    }

    /**
     * Filters whose membership is decided by the package's current freeze state. A status
     * broadcast can move an app in or out of them, so the list has to be rebuilt instead of
     * patching the row's status dot.
     */
    public static boolean isStatusDependentFilter(String filter) {
        if (filter == null) {
            return false;
        }
        return filter.startsWith("RUN")
                || "OF".equals(filter)
                || "UF".equals(filter)
                || "UFU".equals(filter);
    }

    private static final class FrozenStatusCheck {
        final String packageName;
        final Map<String, Object> row;
        final boolean packageMissing;
        final boolean restorableSystemApp;
        final boolean frozen;

        FrozenStatusCheck(String packageName, Map<String, Object> row, boolean packageMissing, boolean restorableSystemApp, boolean frozen) {
            this.packageName = packageName;
            this.row = row;
            this.packageMissing = packageMissing;
            this.restorableSystemApp = restorableSystemApp;
            this.frozen = frozen;
        }
    }

    /**
     * Updates application freeze status indicators in-place across the visible list adapter.
     *
     * <p><b>Scroll Position Preservation Rationale:</b><br>
     * Rebuilding the entire application list via {@link #generateList} causes the ListView to reset
     * its scroll position to the top, causing annoying visual jumps whenever an app is frozen or
     * unfrozen. Instead, this method mutates the existing data maps within
     * {@link MainAppListSimpleAdapter#getStoredArrayList} and calls {@link android.widget.BaseAdapter#notifyDataSetChanged},
     * preserving scroll offsets perfectly.
     *
     * <p><b>Granular Row Targeting:</b><br>
     * When invoked with a specific {@code onlyPkgName} (such as from {@link #updateFrozenStatusBroadcastReceiver}
     * upon receiving {@code cf.playhi.freezeyou.action.packageStatusChanged}), only the rows of that
     * package are looked up and updated in memory, minimizing CPU overhead and preventing UI stutter.
     *
     * <p><b>Off-UI Evaluation:</b><br>
     * A full re-check costs two or three synchronous binder calls per installed package. On a
     * device with hundreds of apps it used to hold the UI thread for the whole resume, so only the
     * single-package path stays synchronous; the full list is evaluated on a worker and applied
     * back on the UI thread.
     *
     * @param onlyPkgName target package name to refresh individually, or null to re-evaluate all rows (e.g. onResume)
     */
    private void updateFrozenStatus(String onlyPkgName) {
        if (mMainActivityAppListFragment == null) {
            return;
        }

        Adapter adapter = mMainActivityAppListFragment.getAppListAdapter();
        if (!(adapter instanceof MainAppListSimpleAdapter)) {
            return;
        }
        final MainAppListSimpleAdapter appListAdapter = (MainAppListSimpleAdapter) adapter;
        // Runs on every onResume, so coming back from the settings applies the switch at once.
        appListAdapter.setGreyFrozenIcons(greyFrozenApplicationsIcons.getValue(this));

        if (onlyPkgName != null) {
            mStatusPatchedDuringFullRefresh.add(onlyPkgName);
            updateSingleRowFrozenStatus(appListAdapter, onlyPkgName);
            return;
        }

        final int generation = mListGeneration.get();
        mStatusPatchedDuringFullRefresh.clear();
        final int statusGeneration = mStatusRefreshGeneration.incrementAndGet();
        final String notAvailable = getString(R.string.notAvailable);
        // The adapter holds only the rows the search matches, while the selection menu and the
        // force-stop batch read the flags from the unfiltered list behind it. Evaluating the
        // hidden rows here too keeps both sources in step without waiting for a rebuild.
        final Set<Map<String, Object>> seenRows =
                Collections.newSetFromMap(new IdentityHashMap<>());
        final ArrayList<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : appListAdapter.getStoredArrayList()) {
            if (seenRows.add(row)) {
                rows.add(row);
            }
        }
        if (mCurrentUnfilteredAppList != null) {
            for (Map<String, Object> row : mCurrentUnfilteredAppList) {
                if (seenRows.add(row)) {
                    rows.add(row);
                }
            }
        }
        new Thread(() -> {
            final PackageManager pm = getPackageManager();
            final ArrayList<FrozenStatusCheck> checks = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                String pkgName = (String) row.get("PackageName");
                if (notAvailable.equals(pkgName)) {
                    continue;
                }
                ApplicationInfo applicationInfo = getApplicationInfoFromPkgName(pkgName, Main.this);
                if (applicationInfo == null) {
                    checks.add(new FrozenStatusCheck(pkgName, row, true, false, false));
                    continue;
                }
                checks.add(new FrozenStatusCheck(
                        pkgName,
                        row,
                        false,
                        RestoreUtils.isRestorableSystemApp(applicationInfo),
                        realGetFrozenStatus(Main.this, pkgName, pm)));
            }
            runOnUiThread(() -> {
                if (isFinishing()
                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())
                        || generation != mListGeneration.get()
                        || statusGeneration != mStatusRefreshGeneration.get()
                        || mMainActivityAppListFragment == null
                        || mMainActivityAppListFragment.getAppListAdapter() != appListAdapter) {
                    return;
                }
                for (FrozenStatusCheck check : checks) {
                    if (check.packageName != null
                            && mStatusPatchedDuringFullRefresh.contains(check.packageName)) {
                        continue;
                    }
                    if (check.packageMissing) {
                        check.row.put("Name", getString(R.string.uninstalled));
                        continue;
                    }
                    check.row.put("RestorableSystemApp", check.restorableSystemApp);
                    // A restorable system app can only be restored, so it keeps the frozen look
                    // even when the frozen flag itself is off.
                    boolean frozen = check.frozen || check.restorableSystemApp;
                    check.row.put("isFrozen", frozen ? customThemeDisabledDot : customThemeEnabledDot);
                    check.row.put("Frozen", frozen);
                }
                appListAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    /**
     * The single-package path is cheap by construction: it evaluates the named package once and
     * applies the result to every row that shows it, so it may stay on the calling thread. All
     * duplicate rows are updated because the full snapshot skips the whole package by name.
     */
    private void updateSingleRowFrozenStatus(MainAppListSimpleAdapter appListAdapter, String onlyPkgName) {
        final Set<Map<String, Object>> seenRows =
                Collections.newSetFromMap(new IdentityHashMap<>());
        final ArrayList<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> hm : appListAdapter.getStoredArrayList()) {
            if (onlyPkgName.equals(hm.get("PackageName")) && seenRows.add(hm)) {
                rows.add(hm);
            }
        }
        // A package the search hides has no adapter row, but its flags still drive the selection
        // menu and the force-stop batch, so the unfiltered rows are patched as well.
        if (mCurrentUnfilteredAppList != null) {
            for (Map<String, Object> hm : mCurrentUnfilteredAppList) {
                if (onlyPkgName.equals(hm.get("PackageName")) && seenRows.add(hm)) {
                    rows.add(hm);
                }
            }
        }
        if (rows.isEmpty()) {
            return;
        }

        ApplicationInfo applicationInfo = getApplicationInfoFromPkgName(onlyPkgName, this);
        // Check if uninstalled
        if (applicationInfo == null) {
            for (Map<String, Object> hm : rows) {
                hm.put("Name", getString(R.string.uninstalled));
            }
            appListAdapter.notifyDataSetChanged();
            return;
        }

        boolean isUninstalled = RestoreUtils.isAppUninstalled(applicationInfo);
        boolean isRestoreFilter = RestoreUtils.isRestoreFilter(currentFilter);
        if (!mIsBatchUninstallRunning
                && ((isUninstalled && !isRestoreFilter) || (isRestoreFilter && !isUninstalled))) {
            new Thread(() -> generateList(currentFilter)).start();
            return;
        }

        PackageManager pm = getPackageManager();
        String name = getApplicationLabel(Main.this, pm, applicationInfo, onlyPkgName);
        Drawable icon = getApplicationIcon(Main.this, onlyPkgName, applicationInfo, false, cacheApplicationsIcons.getValue(Main.this));
        boolean restorable = RestoreUtils.isRestorableSystemApp(applicationInfo);
        // A restorable system app can only be restored, so it keeps the frozen look. The icon is
        // not regenerated; the greyscale filter is applied by the adapter.
        boolean frozen = realGetFrozenStatus(Main.this, onlyPkgName, pm) || restorable;
        int dot = frozen ? customThemeDisabledDot : customThemeEnabledDot;

        for (Map<String, Object> hm : rows) {
            // Each row needs its own instance: binding one animated drawable to two ImageViews
            // replaces its callback and stops the animation on the first one.
            hm.put("Img", copyDrawable(icon));
            hm.put("Name", name);
            hm.put("RestorableSystemApp", restorable);
            hm.put("isFrozen", dot);
            hm.put("Frozen", frozen);
        }
        appListAdapter.notifyDataSetChanged();
    }

    private static Drawable copyDrawable(Drawable drawable) {
        if (drawable == null) {
            return null;
        }
        Drawable.ConstantState state = drawable.getConstantState();
        return state == null ? drawable : state.newDrawable().mutate();
    }

    private void saveOnClickFunctionStatus(int status) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (sharedPreferences.getBoolean(
                saveOnClickFunctionStatus.name(), saveOnClickFunctionStatus.defaultValue())) {
            sharedPreferences.edit().putInt("onClickFunctionStatus", status).apply();
        }
    }

    private void saveSortMethodStatus(int status) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (sharedPreferences.getBoolean(saveSortMethodStatus.name(), saveSortMethodStatus.defaultValue())) {
            sharedPreferences.edit().putInt("sortMethodStatus", status).apply();
        }
    }

    private void checkLongTimeNotUpdated() {
        new Thread(() -> {
            try {
                final SharedPreferences sharedPreferences = getSharedPreferences("Ver", MODE_PRIVATE);
                if (sharedPreferences.getInt("Ver", 0) < getVersionCode(getApplicationContext())) {
                    sharedPreferences.edit()
                            .putInt("Ver", getVersionCode(getApplicationContext()))
                            .putLong("Time", new Date().getTime())
                            .apply();
                }
                if (isOutdated(sharedPreferences)) {

                    if (isFinishing()) return;

                    runOnUiThread(() -> buildAlertDialog(
                            Main.this,
                            R.mipmap.ic_launcher_new_round,
                            R.string.notUpdatedForALongTimeMessage,
                            R.string.notice)
                            .setPositiveButton(R.string.okay, (dialogInterface, i) ->
                                    checkUpdate(Main.this))
                            .setNegativeButton(R.string.cancel, null)
                            .setNeutralButton(R.string.later, (dialog, which) -> {
                                sharedPreferences.edit()
                                        .putLong("Time", new Date().getTime())
                                        .apply();
                            })
                            .create().show());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void createFUFShortcutsBatch(List<String> targetPackages) { // Android < 8.0 and no pin support
        if (targetPackages == null) {
            return;
        }
        for (String pkgName : targetPackages) {
            if (pkgName == null) {
                continue;
            }
            createShortCut(
                    getApplicationLabel(Main.this, null, null, pkgName),
                    pkgName,
                    getApplicationIcon(
                            Main.this,
                            pkgName,
                            getApplicationInfoFromPkgName(pkgName, Main.this),
                            false),
                    Freeze.class,
                    "FreezeYou! " + pkgName,
                    Main.this
            );
        }
    }

    /**
     * Pins the next package of the batch and steps the cursor forward. The system dialog this
     * raises takes the activity off screen, so the call that matters is the one {@code onResume}
     * makes when the user comes back, not the one running now.
     */
    private void requestNextPinShortcut() {
        if (!hasPendingShortcuts(mPendingShortcutPackages, mPendingShortcutIndex)) {
            mPendingShortcutPackages = null;
            mPendingShortcutIndex = 0;
            return;
        }
        String pkgName = getNextPendingShortcutPackage(mPendingShortcutPackages, mPendingShortcutIndex);
        mPendingShortcutIndex++;
        if (pkgName != null) {
            createShortCut(
                    getApplicationLabel(Main.this, null, null, pkgName),
                    pkgName,
                    getApplicationIcon(
                            Main.this,
                            pkgName,
                            getApplicationInfoFromPkgName(pkgName, Main.this),
                            false),
                    Freeze.class,
                    "FreezeYou! " + pkgName,
                    Main.this
            );
        }
        if (!hasPendingShortcuts(mPendingShortcutPackages, mPendingShortcutIndex)) {
            mPendingShortcutPackages = null;
            mPendingShortcutIndex = 0;
        }
    }

    static boolean hasPendingShortcuts(List<String> snapshot, int currentIndex) {
        return snapshot != null && currentIndex >= 0 && currentIndex < snapshot.size();
    }

    static String getNextPendingShortcutPackage(List<String> snapshot, int currentIndex) {
        if (snapshot == null || currentIndex < 0 || currentIndex >= snapshot.size()) {
            return null;
        }
        return snapshot.get(currentIndex);
    }

    private void setSortByDefault(ArrayList<Map<String, Object>> AppList) {
        Collections.sort(AppList, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> stringObjectMap, Map<String, Object> t1) {
                return ((String) stringObjectMap.get("PackageName")).compareTo((String) t1.get("PackageName"));
            }
        });
    }

    private void checkIfNeedAskFirstTimeSetupAndShowDialog() {

        if (getSharedPreferences("Ver", MODE_PRIVATE).getInt("Ver", 0) != 0) {
            go();
            return;
        }

        AlertDialog.Builder builder = FreezeYouAlertDialogBuilder(this);
        builder.setIcon(R.mipmap.ic_launcher_new_round);
        builder.setTitle(String.format(getString(R.string.welcomeToUseAppName), getString(R.string.app_name)));
        builder.setMessage(String.format(getString(R.string.welcomeToUseAppName), getString(R.string.app_name)));
        builder.setPositiveButton(R.string.quickSetup, (dialogInterface, i) -> {
            startActivity(
                    new Intent(getApplicationContext(), FirstTimeSetupActivity.class)
            );
            go();
        });
        builder.setNegativeButton(R.string.importConfig, (dialogInterface, i) -> {
            startActivity(
                    new Intent(getApplicationContext(), BackupMainActivity.class)
            );
            go();
        });
        builder.setNeutralButton(R.string.okay, (dialogInterface, i) -> go());
        builder.setOnCancelListener(dialogInterface -> go());
        builder.show();
    }

    private HashMap<String, Integer> getUFreezeTimesMap() {
        SQLiteDatabase db = openOrCreateDatabase("ApplicationsUFreezeTimes", Context.MODE_PRIVATE, null);
        HashMap<String, Integer> hashMap = getTimesMap(db);
        db.close();
        return hashMap;
    }

    private HashMap<String, Integer> getFreezeTimesMap() {
        SQLiteDatabase db = openOrCreateDatabase("ApplicationsFreezeTimes", Context.MODE_PRIVATE, null);
        HashMap<String, Integer> hashMap = getTimesMap(db);
        db.close();
        return hashMap;
    }

    private HashMap<String, Integer> getUseTimesMap() {
        SQLiteDatabase db = openOrCreateDatabase("ApplicationsUseTimes", Context.MODE_PRIVATE, null);
        HashMap<String, Integer> hashMap = getTimesMap(db);
        db.close();
        return hashMap;
    }

    private HashMap<String, Integer> getTimesMap(SQLiteDatabase db) {
        HashMap<String, Integer> hashMap = new HashMap<>();

        if (db == null) {
            return hashMap;
        }

        db.execSQL(
                "create table if not exists TimesList(_id integer primary key autoincrement,pkg varchar,times int)"
        );
        Cursor cursor = db.query("TimesList", new String[]{"pkg", "times"}, null, null, null, null, null);

        if (cursor == null) {
            return hashMap;
        }

        if (cursor.moveToFirst()) {
            for (int i = 0; i < cursor.getCount(); i++) {
                hashMap.put(
                        cursor.getString(cursor.getColumnIndex("pkg")),
                        Integer.parseInt(cursor.getString(cursor.getColumnIndex("times")))
                );
                cursor.moveToNext();
            }
        }
        cursor.close();
        return hashMap;
    }

    private void addUserDefinedCategoriesTo(SubMenu subMenu, int groupId, int newClassificationId, int idBase) {
        subMenu.clear();

        subMenu.add(
                groupId,
                newClassificationId,
                0,
                R.string.newClassification
        );

        // Add user-defined categories
        SQLiteDatabase userDefinedDb = openOrCreateDatabase("userDefinedCategories", MODE_PRIVATE, null);
        userDefinedDb.execSQL(
                "create table if not exists categories(_id integer primary key autoincrement,label varchar,packages varchar)"
        );
        Cursor cursor = userDefinedDb.query("categories", new String[]{"label", "_id"}, null, null, null, null, null);
        if (cursor.moveToFirst()) {
            for (int i = 0; i < cursor.getCount(); i++) {
                int id = cursor.getInt(cursor.getColumnIndex("_id"));
                String title = cursor.getString(cursor.getColumnIndex("label"));
                String decodedTitle;
                try {
                    decodedTitle = new String(Base64.decode(title, Base64.DEFAULT));
                } catch (Exception e) {
                    decodedTitle = title != null ? title : "";
                }
                subMenu.add(groupId, id + idBase, id, decodedTitle);
                cursor.moveToNext();
            }
        }
        cursor.close();
        userDefinedDb.close();
    }

    /**
     * One menu resource feeds both the action bar overflow and the floating button's popup, so
     * whatever is here applies to both and the two cannot drift apart.
     *
     * @param fromFloatingButton The popup under the thumb is for what gets used often. The FAQ and
     *                           the about screen are opened once in a while and stay in the action
     *                           bar overflow only, which is the menu that holds everything.
     *                           Settings started out hidden here too and came back: they are
     *                           opened constantly while testing, and reaching for the top of the
     *                           screen every time is the thing this button exists to avoid. Their
     *                           row sorts last, so it sits closest to the thumb.
     */
    private void onPrepareMainOptionsMenu(Menu menu, boolean fromFloatingButton) {
        if (fromFloatingButton) {
            hideMenuItem(menu, R.id.menu_faq);
            hideMenuItem(menu, R.id.menu_about);
        }
        try {
            // Dynamic theme-attribute tinting turned out unreliable in practice (rendered fully
            // transparent on the test device). Fall back to a static light/dark drawable swap,
            // but read it from the resolved theme's isLightTheme attribute rather than the
            // getUiTheme() name string: "default" is returned both for the actual light theme
            // and for Material3 dynamic color following the system into dark mode (Android 12+),
            // so a name-based check picked the light-background icon variant on a dark background.
            android.util.TypedValue isLightThemeValue = new android.util.TypedValue();
            boolean isLightTheme =
                    getTheme().resolveAttribute(android.R.attr.isLightTheme, isLightThemeValue, true)
                            && isLightThemeValue.data != 0;
            // Icon shows the mode a tap will switch TO, not the current one.
            menu.findItem(R.id.menu_toggleGridListMode).setIcon(
                    isGridMode
                            ? (isLightTheme ? R.drawable.ic_action_view_list_light : R.drawable.ic_action_view_list)
                            : (isLightTheme ? R.drawable.ic_action_view_grid_light : R.drawable.ic_action_view_grid)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            SubMenu vmUserDefinedSubMenu = menu.findItem(R.id.menu_vM_userDefined).getSubMenu();
            SubMenu createUserDefinedShortcutSubMenu = menu.findItem(R.id.menu_createUserDefinedShortcut).getSubMenu();
            SubMenu forceStopUserDefinedShortcutSubMenu = menu.findItem(R.id.menu_forceStopUserDefinedShortcut).getSubMenu();

            addUserDefinedCategoriesTo(vmUserDefinedSubMenu,
                    R.id.menu_vM_userDefined_menuGroup,
                    R.id.menu_vM_userDefined_newClassification,
                    0
            );

            addUserDefinedCategoriesTo(createUserDefinedShortcutSubMenu,
                    R.id.menu_createUserDefinedShortcut_menuGroup,
                    R.id.menu_createUserDefinedShortcut_newClassification,
                    0x2AAAAAAA
            );

            addUserDefinedCategoriesTo(forceStopUserDefinedShortcutSubMenu,
                    R.id.menu_forceStopUserDefinedShortcut_menuGroup,
                    R.id.menu_forceStopUserDefinedShortcut_newClassification,
                    0x55555555
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void hideMenuItem(Menu menu, int id) {
        MenuItem item = menu.findItem(id);
        if (item != null) {
            item.setVisible(false);
        }
    }

    private void generateListForCategory(String base64Label) {
        SQLiteDatabase userDefinedDb = openOrCreateDatabase("userDefinedCategories", Context.MODE_PRIVATE, null);
        userDefinedDb.execSQL(
                "create table if not exists categories(_id integer primary key autoincrement,label varchar,packages varchar)"
        );
        Cursor cursor =
                userDefinedDb.query(
                        "categories",
                        new String[]{"packages"},
                        "label = '" + base64Label + "'",
                        null, null, null, null
                );
        if (cursor.moveToFirst()) {
            generateList(cursor.getString(cursor.getColumnIndex("packages")),
                    currentSortRule, base64Label);
        } else {
            showToast(this, R.string.failed);
        }
        cursor.close();
        userDefinedDb.close();
    }

    private boolean onMainOptionsItemSelected(MenuItem item) {

        switch (item.getGroupId()) {
            case R.id.menu_vM_userDefined_menuGroup:
                switch (item.getItemId()) {
                    case R.id.menu_vM_userDefined_newClassification:
                        showAddNewUserDefinedClassificationDialog();
                        return true;
                    default:
                        String title = item.getTitle().toString();
                        generateListForCategory(Base64.encodeToString(title.getBytes(), Base64.DEFAULT));
                        return true;
                }
            case R.id.menu_createUserDefinedShortcut_menuGroup:
                switch (item.getItemId()) {
                    case R.id.menu_createUserDefinedShortcut_newClassification:
                        showAddNewUserDefinedClassificationDialog();
                        return true;
                    default:
                        String title = item.getTitle().toString();

                        checkSettingsAndRequestCreateShortcut(
                                title,
                                "CATEGORY" + Base64.encodeToString(title.getBytes(), Base64.DEFAULT),
                                getResources().getDrawable(R.mipmap.ic_launcher_round),
                                Main.class,
                                "Category " + Base64.encodeToString(title.getBytes(), Base64.DEFAULT),
                                this);

                        return true;
                }
            case R.id.menu_forceStopUserDefinedShortcut_menuGroup:
                switch (item.getItemId()) {
                    case R.id.menu_forceStopUserDefinedShortcut_newClassification:
                        showAddNewUserDefinedClassificationDialog();
                        return true;
                    default:
                        String title = item.getTitle().toString();

                        checkSettingsAndRequestCreateShortcut(
                                getString(R.string.forceStop) + " " + title,
                                "FORCESTOPCATEGORY" + Base64.encodeToString(title.getBytes(), Base64.DEFAULT),
                                getResources().getDrawable(R.mipmap.ic_launcher_round),
                                ForceStop.class,
                                "ForceStopCategory " + Base64.encodeToString(title.getBytes(), Base64.DEFAULT),
                                this);

                        return true;
                }

            default:
                switch (item.getItemId()) {
                    case R.id.menu_createOneKeyFreezeShortCut:
                        checkSettingsAndRequestCreateShortcut(
                                getString(R.string.oneKeyFreeze),
                                "cf.playhi.freezeyou.extra.fuf",
                                getResources().getDrawable(R.mipmap.ic_launcher_round),
                                OneKeyFreeze.class,
                                "OneKeyFreeze",
                                this);
                        return true;
                    case R.id.menu_createOneKeyUFShortCut:
                        checkSettingsAndRequestCreateShortcut(
                                getString(R.string.oneKeyUF),
                                "cf.playhi.freezeyou.extra.fuf",
                                getResources().getDrawable(R.mipmap.ic_launcher_round),
                                OneKeyUF.class,
                                "OneKeyUF",
                                this);
                        return true;
                    case R.id.menu_createOneKeyLockScreenShortCut:
                        checkSettingsAndRequestCreateShortcut(
                                getString(R.string.oneKeyLockScreen),
                                "cf.playhi.freezeyou.extra.oklock",
                                getResources().getDrawable(R.drawable.screenlock),
                                OneKeyScreenLockImmediatelyActivity.class,
                                "OneKeyLockScreen",
                                this);
                        return true;
                    case R.id.menu_createOnlyFrozenShortCut:
                        checkSettingsAndRequestCreateShortcut(
                                getString(R.string.onlyFrozen),
                                "OF",
                                getResources().getDrawable(R.mipmap.ic_launcher_round),
                                Main.class,
                                "OF",
                                this);
                        return true;
                    case R.id.menu_createOnlyUFShortCut:
                        checkSettingsAndRequestCreateShortcut(
                                getString(R.string.onlyUF),
                                "UF",
                                getResources().getDrawable(R.mipmap.ic_launcher_round),
                                Main.class,
                                "UF",
                                this);
                        return true;
                    case R.id.menu_createOnlyOnekeyShortCut:
                        checkSettingsAndRequestCreateShortcut(
                                getString(R.string.onlyOnekey),
                                "OO",
                                getResources().getDrawable(R.mipmap.ic_launcher_round),
                                Main.class,
                                "OO",
                                this);
                        return true;
                    case R.id.menu_createOnlyOnekeyUFShortCut:
                        checkSettingsAndRequestCreateShortcut(
                                getString(R.string.oneKeyUF),
                                "OOU",
                                getResources().getDrawable(R.mipmap.ic_launcher_round),
                                Main.class,
                                "OOU",
                                this);
                        return true;
                    case R.id.menu_createFreezeOnceQuitShortCut:
                        checkSettingsAndRequestCreateShortcut(
                                getString(R.string.freezeOnceQuit),
                                "FOQ",
                                getResources().getDrawable(R.mipmap.ic_launcher_round),
                                Main.class,
                                "FOQ",
                                this);
                        return true;
                    case R.id.menu_createOnlySAShortcut:
                        checkSettingsAndRequestCreateShortcut(
                                getString(R.string.onlySA),
                                "OS",
                                getResources().getDrawable(R.mipmap.ic_launcher_round),
                                Main.class,
                                "OS",
                                this);
                        return true;
                    case R.id.menu_createOnlyUAShortcut:
                        checkSettingsAndRequestCreateShortcut(
                                getString(R.string.onlyUA),
                                "OU",
                                getResources().getDrawable(R.mipmap.ic_launcher_round),
                                Main.class,
                                "OU",
                                this);
                        return true;
                    case R.id.menu_createUnfrozenUAShortcut:
                        checkSettingsAndRequestCreateShortcut(
                                getString(R.string.unfrozenUA),
                                "UFU",
                                getResources().getDrawable(R.mipmap.ic_launcher_round),
                                Main.class,
                                "UFU",
                                this);
                        return true;
                    case R.id.menu_createNewFolderShortCut:
                        startActivityForResult(
                                new Intent(this, ShortcutLauncherFolderActivity.class)
                                        .setAction(Intent.ACTION_CREATE_SHORTCUT),
                                80001
                        );
                        return true;
                    case R.id.menu_timedTasks:
                        startActivity(new Intent(this, ScheduledTasksManageActivity.class));
                        return true;
                    case R.id.menu_toggleGridListMode:
                        // The List/Grid widget choice is only decided once, in the app-list
                        // fragment's onCreateView — setUseGridMode() alone doesn't swap it on an
                        // already-created fragment. Persist the choice, then recreate() so the
                        // fragment (and everything else) is rebuilt fresh with the new mode,
                        // same pattern already used for theme switching in SettingsActivity.
                        mainActivityPattern.setValue(this, isGridMode ? "list" : "grid");
                        recreate();
                        return true;
                    case R.id.menu_about:
                        startActivity(new Intent(this, AboutActivity.class));
                        return true;
                    case R.id.menu_oneKeyFreezeImmediately:
                        Intent oneKeyFreezeIntent = new Intent(this, OneKeyFreeze.class).putExtra("autoCheckAndLockScreen", false);
                        ShortcutTokenUtils.attachToken(oneKeyFreezeIntent, ShortcutTokenUtils.ACTION_ONE_KEY_FREEZE);
                        startActivity(oneKeyFreezeIntent);
                        return true;
                    case R.id.menu_oneKeyUFImmediately:
                        Intent oneKeyUFIntent = new Intent(this, OneKeyUF.class);
                        ShortcutTokenUtils.attachToken(oneKeyUFIntent, ShortcutTokenUtils.ACTION_ONE_KEY_UF);
                        startActivity(oneKeyUFIntent);
                        return true;
                    case R.id.menu_vM_onlyFrozen:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList("OF");
                            }
                        }).start();
                        return true;
                    case R.id.menu_vM_onlyUF:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList("UF");
                            }
                        }).start();
                        return true;
                    case R.id.menu_vM_all:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList("all");
                            }
                        }).start();
                        return true;
                    case R.id.menu_vM_onlyOnekey:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList("OO");
                            }
                        }).start();
                        return true;
                    case R.id.menu_vM_onlyOnekeyUF:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList("OOU");
                            }
                        }).start();
                        return true;
                    case R.id.menu_vM_onlySA:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList("OS");
                            }
                        }).start();
                        return true;
                    case R.id.menu_vM_onlyUninstalledSys:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList("OUS");
                            }
                        }).start();
                        return true;
                    case R.id.menu_vM_onlyUA:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList("OU");
                            }
                        }).start();
                        return true;
                    case R.id.menu_vM_freezeOnceQuit:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList("FOQ");
                            }
                        }).start();
                        return true;
                    case R.id.menu_vM_unfrozenUA:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList("UFU");
                            }
                        }).start();
                        return true;
                    case R.id.menu_vM_onlyRunning:
                        if (checkRunningFilterAvailableAndWarn()) {
                            new Thread(() -> generateList("RUN")).start();
                        }
                        return true;
                    case R.id.menu_vM_onlyRunningSys:
                        if (checkRunningFilterAvailableAndWarn()) {
                            new Thread(() -> generateList("RUN_SYS")).start();
                        }
                        return true;
                    case R.id.menu_vM_onlyRunningUser:
                        if (checkRunningFilterAvailableAndWarn()) {
                            new Thread(() -> generateList("RUN_USER")).start();
                        }
                        return true;
                    case R.id.menu_vM_userDefined:
                        addUserDefinedCategoriesTo(item.getSubMenu(),
                                R.id.menu_vM_userDefined_menuGroup,
                                R.id.menu_vM_userDefined_newClassification,
                                0
                        );
                        return true;
                    case R.id.menu_update:
                        checkUpdate(Main.this);
                        return true;
                    case R.id.menu_moreSettings:
                        startActivity(new Intent(this, SettingsActivity.class));
                        return true;
                    case R.id.menu_faq:
                        requestOpenWebSite(this,
                                String.format("https://www.zidon.net/%1$s/faq/",
                                        getString(R.string.correspondingAndAvailableWebsiteUrlLanguageCode)));
                        return true;
                    case R.id.menu_onClickFunc_autoUFOrFreeze:
                        appListViewOnClickMode = APPListViewOnClickMode_autoUFOrFreeze;
                        saveOnClickFunctionStatus(appListViewOnClickMode);
                        return true;
                    case R.id.menu_onClickFunc_freezeImmediately:
                        appListViewOnClickMode = APPListViewOnClickMode_freezeImmediately;
                        saveOnClickFunctionStatus(appListViewOnClickMode);
                        return true;
                    case R.id.menu_onClickFunc_UFImmediately:
                        appListViewOnClickMode = APPListViewOnClickMode_UFImmediately;
                        saveOnClickFunctionStatus(appListViewOnClickMode);
                        return true;
                    case R.id.menu_onClickFunc_chooseAction:
                        appListViewOnClickMode = APPListViewOnClickMode_chooseAction;
                        saveOnClickFunctionStatus(appListViewOnClickMode);
                        return true;
                    case R.id.menu_onClickFunc_addToFOQList:
                        appListViewOnClickMode = APPListViewOnClickMode_addToFOQList;
                        saveOnClickFunctionStatus(appListViewOnClickMode);
                        return true;
                    case R.id.menu_onClickFunc_addToOFList:
                        appListViewOnClickMode = APPListViewOnClickMode_addToOFList;
                        saveOnClickFunctionStatus(appListViewOnClickMode);
                        return true;
                    case R.id.menu_onClickFunc_addToOUFList:
                        appListViewOnClickMode = APPListViewOnClickMode_addToOUFList;
                        saveOnClickFunctionStatus(appListViewOnClickMode);
                        return true;
                    case R.id.menu_onClickFunc_removeFromFOQList:
                        appListViewOnClickMode = APPListViewOnClickMode_removeFromFOQList;
                        saveOnClickFunctionStatus(appListViewOnClickMode);
                        return true;
                    case R.id.menu_onClickFunc_removeFromOFList:
                        appListViewOnClickMode = APPListViewOnClickMode_removeFromOFList;
                        saveOnClickFunctionStatus(appListViewOnClickMode);
                        return true;
                    case R.id.menu_onClickFunc_removeFromOUFList:
                        appListViewOnClickMode = APPListViewOnClickMode_removeFromOUFList;
                        saveOnClickFunctionStatus(appListViewOnClickMode);
                        return true;
                    case R.id.menu_onClickFunc_UFAndRun:
                        appListViewOnClickMode = APPListViewOnClickMode_UFAndRun;
                        saveOnClickFunctionStatus(appListViewOnClickMode);
                        return true;
                    case R.id.menu_onClickFunc_autoUFOrFreezeAndRun:
                        appListViewOnClickMode = APPListViewOnClickMode_autoUFOrFreezeAndRun;
                        saveOnClickFunctionStatus(appListViewOnClickMode);
                        return true;
                    case R.id.menu_onClickFunc_createFUFShortcut:
                        appListViewOnClickMode = APPListViewOnClickMode_createFUFShortcut;
                        saveOnClickFunctionStatus(appListViewOnClickMode);
                        return true;
                    case R.id.menu_sB_default:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList(currentFilter, SORT_BY_DEFAULT);
                            }
                        }).start();
                        saveSortMethodStatus(SORT_BY_DEFAULT);
                        return true;
                    case R.id.menu_sB_no:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList(currentFilter, SORT_BY_NO);
                            }
                        }).start();
                        saveSortMethodStatus(SORT_BY_NO);
                        return true;
                    case R.id.menu_sB_uf_ascending:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList(currentFilter, SORT_BY_UF_ASCENDING);
                            }
                        }).start();
                        saveSortMethodStatus(SORT_BY_UF_ASCENDING);
                        return true;
                    case R.id.menu_sB_uf_descending:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList(currentFilter, SORT_BY_UF_DESCENDING);
                            }
                        }).start();
                        saveSortMethodStatus(SORT_BY_UF_DESCENDING);
                        return true;
                    case R.id.menu_sB_ff_ascending:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList(currentFilter, SORT_BY_FF_ASCENDING);
                            }
                        }).start();
                        saveSortMethodStatus(SORT_BY_FF_ASCENDING);
                        return true;
                    case R.id.menu_sB_ff_descending:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList(currentFilter, SORT_BY_FF_DESCENDING);
                            }
                        }).start();
                        saveSortMethodStatus(SORT_BY_FF_DESCENDING);
                        return true;
                    case R.id.menu_sB_us_ascending:
                        AccessibilityUtils.checkAndRequestIfAccessibilitySettingsOff(this);
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList(currentFilter, SORT_BY_US_ASCENDING);
                            }
                        }).start();
                        saveSortMethodStatus(SORT_BY_US_ASCENDING);
                        return true;
                    case R.id.menu_sB_us_descending:
                        AccessibilityUtils.checkAndRequestIfAccessibilitySettingsOff(this);
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList(currentFilter, SORT_BY_US_DESCENDING);
                            }
                        }).start();
                        saveSortMethodStatus(SORT_BY_US_DESCENDING);
                        return true;
                    case R.id.menu_sB_alphabetical:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList(currentFilter, SORT_BY_ALPHABETICAL);
                            }
                        }).start();
                        saveSortMethodStatus(SORT_BY_ALPHABETICAL);
                        return true;
                    case R.id.menu_sB_last_install:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList(currentFilter, SORT_BY_LAST_INSTALLED);
                            }
                        }).start();
                        saveSortMethodStatus(SORT_BY_LAST_INSTALLED);
                        return true;
                    case R.id.menu_sB_last_update:
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                generateList(currentFilter, SORT_BY_LAST_UPDATED);
                            }
                        }).start();
                        saveSortMethodStatus(SORT_BY_LAST_UPDATED);
                        return true;
                    default:
                        return super.onOptionsItemSelected(item);
                }
        }
    }

    /**
     * Bulk-freezing straight from a "select all" is the one action that can leave the device
     * without a status bar, Settings or a keyboard, with no easy way back. Freezing them stays
     * allowed — this only asks first, and only when such a package is actually in the selection.
     */
    private void confirmIfCriticalSelectedThenRun(boolean isUninstall, Runnable action) {
        confirmIfCriticalPackagesThenRun(selectedPackages, isUninstall, action);
    }

    /**
     * The same question for any set of packages, so that a single row tapped into one of the
     * direct-freeze modes cannot slip past a safeguard the bulk path has: one application freezes
     * just as hard as a selection of them.
     */
    private void confirmIfCriticalPackagesThenRun(List<String> packagesToCheck, boolean isUninstall, Runnable action) {
        final List<String> critical =
                CriticalPackagesUtils.findCriticalPackages(this, packagesToCheck);
        if (critical.isEmpty()) {
            action.run();
            return;
        }

        StringBuilder names = new StringBuilder();
        for (String pkgName : critical) {
            if (names.length() > 0) {
                names.append(System.getProperty("line.separator"));
            }
            names.append("• ")
                    .append(getApplicationLabel(Main.this, null, null, pkgName))
                    .append(" (").append(pkgName).append(")");
        }

        final int warningResId;
        if (critical.size() == 1) {
            warningResId = isUninstall
                    ? R.string.critical_package_uninstall_warning_single_confirm
                    : R.string.critical_package_freeze_warning_single;
        } else {
            warningResId = isUninstall
                    ? R.string.critical_packages_uninstall_warning_batch
                    : R.string.criticalPackagesWarning;
        }

        FreezeYouAlertDialogBuilder(this)
                .setTitle(R.string.caution)
                .setMessage(getString(warningResId, names.toString()))
                .setPositiveButton(R.string.okay, (dialog, which) -> action.run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * The Running filter used to be hidden outright when the freeze mode was neither ROOT nor
     * Shizuku, which left no way to tell whether the feature was missing, broken, or just
     * unavailable. It stays visible now and says what is wrong instead.
     */
    private boolean checkRunningFilterAvailableAndWarn() {
        if (RunningAppsUtils.isRunningFilterAvailable()) {
            return true;
        }
        showToast(this, R.string.runningFilterUnavailable);
        return false;
    }

    private void showAddNewUserDefinedClassificationDialog() {
        final EditText vmUserDefinedNameAlertDialogEditText = new EditText(this);
        AlertDialog.Builder vmUserDefinedNameAlertDialog = FreezeYouAlertDialogBuilder(this);
        vmUserDefinedNameAlertDialog.setTitle(R.string.label);
        vmUserDefinedNameAlertDialog.setView(vmUserDefinedNameAlertDialogEditText);
        vmUserDefinedNameAlertDialog.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String label = Base64.encodeToString(vmUserDefinedNameAlertDialogEditText.getText().toString().getBytes(), Base64.DEFAULT);
                if ("".equals(label)) {
                    showToast(Main.this, R.string.emptyNotAllowed);
                } else {
                    boolean alreadyExists = false;
                    SQLiteDatabase vmUserDefinedDb = openOrCreateDatabase("userDefinedCategories", MODE_PRIVATE, null);
                    vmUserDefinedDb.execSQL(
                            "create table if not exists categories(_id integer primary key autoincrement,label varchar,packages varchar)"
                    );
                    Cursor cursor = vmUserDefinedDb.query("categories", new String[]{"label"}, null, null, null, null, null);
                    if (cursor.moveToFirst()) {
                        for (int i = 0; i < cursor.getCount(); i++) {
                            if (label.equals(cursor.getString(cursor.getColumnIndex("label")))) {
                                alreadyExists = true;
                                break;
                            }
                            cursor.moveToNext();
                        }
                    }
                    cursor.close();
                    if (alreadyExists) {
                        showToast(Main.this, R.string.alreadyExist);
                    } else {
                        // Data, not SQL text: concatenating the value into a quoted literal let
                        // anything that looked like a quote end the literal and throw.
                        ContentValues values = new ContentValues();
                        values.put("label", label);
                        values.put("packages", "");
                        vmUserDefinedDb.insertWithOnConflict(
                                "categories", null, values, SQLiteDatabase.CONFLICT_REPLACE
                        );
                    }
                    vmUserDefinedDb.close();
                }
            }
        });
        vmUserDefinedNameAlertDialog.setNegativeButton(R.string.cancel, null);
        vmUserDefinedNameAlertDialog.show();
    }

    @Override
    public void finish() {
        if (Build.VERSION.SDK_INT >= 21 && !showInRecents.getValue(null)) {
            finishAndRemoveTask();
        }
        super.finish();
    }

}

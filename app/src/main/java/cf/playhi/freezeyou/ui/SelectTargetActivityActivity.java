package cf.playhi.freezeyou.ui;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.HashMap;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.app.FreezeYouBaseActivity;
import cf.playhi.freezeyou.fuf.FUFSinglePackage;
import cf.playhi.freezeyou.utils.ApplicationIconUtils;

import rikka.shizuku.Shizuku;

import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.selectFUFMode;
import static cf.playhi.freezeyou.utils.ThemeUtils.processActionBar;
import static cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme;
import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon;
import static cf.playhi.freezeyou.utils.ApplicationInfoUtils.getApplicationInfoFromPkgName;
import static cf.playhi.freezeyou.utils.ApplicationLabelUtils.getApplicationLabel;
import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

public class SelectTargetActivityActivity extends FreezeYouBaseActivity {

    /**
     * Whether this device can start a component another app keeps to itself. Read from the
     * configured freeze mode rather than probed: asking for root here would pop a permission
     * prompt just for opening a list.
     */
    private boolean elevatedLaunchAvailable() {
        int mode;
        try {
            mode = Integer.parseInt(selectFUFMode.getValue(this));
        } catch (Exception e) {
            return false;
        }
        switch (mode) {
            case FUFSinglePackage.API_FREEZEYOU_ROOT_DISABLE_ENABLE:
            case FUFSinglePackage.API_FREEZEYOU_ROOT_UNHIDE_HIDE:
                return true;
            case FUFSinglePackage.API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE:
            case FUFSinglePackage.API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_USER:
            case FUFSinglePackage.API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED:
                // Cheap and silent, unlike spawning su, and it catches "Shizuku is selected but
                // not running" before the user builds a shortcut that could never fire.
                return Shizuku.pingBinder();
            default:
                return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        processSetTheme(this);
        super.onCreate(savedInstanceState);
        processActionBar(getSupportActionBar());
        setContentView(R.layout.staa_main);
        init();
    }

    /**
     * Off the main thread on purpose. Asking the system for a package's activities can fail for a
     * large package, and the fallback then parses the whole APK in this process and loads an icon
     * and a label per row. That is exactly the case for Google Play services or Settings — the
     * packages with hundreds of activities — so doing it in onCreate was an ANR waiting to happen.
     */
    private void init() {
        final Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }
        final String pkgName = intent.getStringExtra("pkgName");
        if (pkgName == null) {
            finish();
            return;
        }
        // Anything thrown while reading a foreign package would otherwise leave the spinner
        // turning for ever, with no way to tell that from a slow package.
        new Thread(() -> {
            try {
                buildListAndShow(pkgName);
            } catch (Throwable t) {
                t.printStackTrace();
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    hideLoadingIndicator();
                    showToast(SelectTargetActivityActivity.this, R.string.failed);
                });
            }
        }).start();
    }

    private void hideLoadingIndicator() {
        View progressBar = findViewById(R.id.staa_main_loading_progressBar);
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void buildListAndShow(final String pkgName) {
        final ArrayList<HashMap<String, Object>> arrayList = new ArrayList<>();
        HashMap<String, Object> hm = new HashMap<>();
        hm.put("Img",
                getApplicationIcon(
                        this,
                        pkgName,
                        getApplicationInfoFromPkgName(pkgName, this),
                        false));
        hm.put("Name", getString(R.string.launch));
        hm.put("Label",
                getApplicationLabel(
                        this, getPackageManager(),
                        getApplicationInfoFromPkgName(pkgName, this), pkgName)
        );
        arrayList.add(hm);

        HashMap<String, Object> hm2 = new HashMap<>();
        hm2.put("Img",
                getApplicationIcon(
                        this,
                        pkgName,
                        getApplicationInfoFromPkgName(pkgName, this),
                        false));
        hm2.put("Name", getString(R.string.onlyUnfreeze));
        hm2.put("Label",
                getApplicationLabel(
                        this, getPackageManager(),
                        getApplicationInfoFromPkgName(pkgName, this), pkgName)
        );
        arrayList.add(hm2);

        PackageManager pm = getPackageManager();
        ActivityInfo[] activityInfos = getActivitiesFromSystem(pm, pkgName);
        // The system answer travels over a binder transaction with a ~1MB ceiling. Packages
        // like Google Play services or Settings declare far more activities than fit, and
        // the call throws instead of returning a shorter list — which is why those used to
        // show nothing at all. Parsing the APK happens in this process, with no such limit.
        boolean fromArchive = false;
        if (activityInfos == null || activityInfos.length == 0) {
            activityInfos = getActivitiesFromApk(pm, pkgName);
            fromArchive = activityInfos != null;
        }

        // Starting a non-exported activity needs root or Shizuku. Without one of those
        // configured, listing them would only offer the user targets that cannot work,
        // so they are left out entirely rather than explained away in every row.
        final boolean canLaunchNonExported = elevatedLaunchAvailable();

        if (activityInfos != null) {
            for (ActivityInfo activityInfo : activityInfos) {
                String ais = activityInfo.name;
                if (ais == null) {
                    continue;
                }
                if (!activityInfo.exported && !canLaunchNonExported) {
                    continue;
                }
                // Non-exported activities used to be filtered out entirely. Since
                // Android 12 forces every component to declare android:exported
                // and almost everything is declared false, that hid nearly the
                // whole list. They are listed and labelled instead: starting one
                // falls back to a root/Shizuku launch, which is allowed to.
                HashMap<String, Object> hashMap = new HashMap<>();
                String label;
                if (fromArchive) {
                    // An ActivityInfo parsed out of an APK carries no resource paths, so
                    // asking it for its icon and label would reopen the APK per row. The
                    // class name is what the user picks by anyway.
                    hashMap.put("Img", getApplicationIcon(
                            this, pkgName, getApplicationInfoFromPkgName(pkgName, this), false));
                    int dot = ais.lastIndexOf('.');
                    label = dot >= 0 && dot < ais.length() - 1 ? ais.substring(dot + 1) : ais;
                } else {
                    hashMap.put("Img", activityInfo.loadIcon(pm));
                    label = activityInfo.loadLabel(pm).toString();
                }
                hashMap.put("Name", ais);
                hashMap.put("Label", label);
                arrayList.add(hashMap);
            }
        }

        final SimpleAdapter adapter =
                new SimpleAdapter(
                        SelectTargetActivityActivity.this,
                        arrayList,
                        R.layout.staa_main_item,
                        new String[]{"Img", "Label", "Name"},
                        new int[]{
                                R.id.staa_main_item_imageView,
                                R.id.staa_main_item_textView,
                                R.id.staa_main_item_subtitle_textView
                        });

        adapter.setViewBinder((view, data, textRepresentation) -> {
            if (view instanceof ImageView && data instanceof Drawable) {
                ((ImageView) view).setImageDrawable((Drawable) data);
                return true;
            } else {
                return false;
            }
        });

        runOnUiThread(() -> {
            if (isFinishing()) {
                return;
            }
            hideLoadingIndicator();
            ListView staaMainListView = findViewById(R.id.staa_main_listView);
            staaMainListView.setAdapter(adapter);
            staaMainListView.setOnItemClickListener((parent, view, position, id) ->
                    selectAndFinish(arrayList, position, pkgName));
        });

    }

    private void selectAndFinish(
            ArrayList<HashMap<String, Object>> arrayList, int position, String pkgName) {
        String name = (String) arrayList.get(position).get("Name");
        String label = (String) arrayList.get(position).get("Label");
        Drawable drawable = (Drawable) arrayList.get(position).get("Img");
        Bitmap icon = drawable == null ?
                null : ApplicationIconUtils.getBitmapFromDrawable(drawable);
        setResult(
                RESULT_OK,
                new Intent()
                        .putExtra("name", name)
                        .putExtra("icon", icon)
                        .putExtra("label", label)
                        .putExtra("id", "FreezeYou!" + pkgName + " " + name));
        finish();
    }

    /**
     * Disabled components are left out unless asked for, and a component disabled by FreezeYou
     * itself is exactly the kind the user wants to point a shortcut at.
     */
    private static ActivityInfo[] getActivitiesFromSystem(PackageManager pm, String pkgName) {
        int flags = PackageManager.GET_ACTIVITIES;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            flags |= PackageManager.MATCH_DISABLED_COMPONENTS
                    | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS;
        } else {
            //noinspection deprecation
            flags |= PackageManager.GET_DISABLED_COMPONENTS;
        }
        try {
            return pm.getPackageInfo(pkgName, flags).activities;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Reads the package's own APK instead of asking the system, which is the only way to see the
     * activity list of a package too large to cross a binder transaction.
     */
    private static ActivityInfo[] getActivitiesFromApk(PackageManager pm, String pkgName) {
        try {
            ApplicationInfo applicationInfo = pm.getApplicationInfo(pkgName, 0);
            String apkPath = applicationInfo.publicSourceDir != null
                    ? applicationInfo.publicSourceDir
                    : applicationInfo.sourceDir;
            if (apkPath == null) {
                return null;
            }
            PackageInfo packageInfo =
                    pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES);
            return packageInfo == null ? null : packageInfo.activities;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

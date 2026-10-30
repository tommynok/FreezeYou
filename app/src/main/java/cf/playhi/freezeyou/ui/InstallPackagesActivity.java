package cf.playhi.freezeyou.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.UUID;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.app.FreezeYouBaseActivity;
import cf.playhi.freezeyou.app.ObsdAlertDialog;
import cf.playhi.freezeyou.service.InstallPackagesService;
import cf.playhi.freezeyou.utils.AccessibilityUtils;
import cf.playhi.freezeyou.utils.AlertDialogUtils;
import cf.playhi.freezeyou.utils.AutoAllowListUtils;
import cf.playhi.freezeyou.utils.CriticalPackagesUtils;
import cf.playhi.freezeyou.utils.DevicePolicyManagerUtils;
import cf.playhi.freezeyou.utils.FUFUtils;
import cf.playhi.freezeyou.utils.FileUtils;
import cf.playhi.freezeyou.utils.InstallPackagesUtils;
import cf.playhi.freezeyou.utils.ServiceUtils;
import cf.playhi.freezeyou.utils.UninstallPolicyUtils;

import static cf.playhi.freezeyou.app.FreezeYouAlertDialogBuilderKt.FreezeYouAlertDialogBuilder;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.notAllowInstallWhenIsObsd;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.tryToAvoidUpdateWhenUsing;
import static cf.playhi.freezeyou.utils.ApplicationLabelUtils.getApplicationLabel;
import static cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme;
import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

/**
 * Install and uninstall
 */
public class InstallPackagesActivity extends FreezeYouBaseActivity {
    private static final String ILLEGALPKGNAME = "Fy^&IllegalPN*@!128`+=：:,.[";

    /** Set while an install started from here is running and this window is waiting for its result. */
    private BroadcastReceiver installStatusReceiver;

    /** The window an install started from here reports through, from its spinner to its result. */
    private AlertDialog installProgressDialog;
    private View installProgressView;
    private AlertDialog prepareProgressDialog;
    private AlertDialog currentConfirmationDialog;
    private int currentInitGeneration = 0;
    private Handler installTimeoutHandler;
    private Runnable installTimeoutRunnable;
    private String trackedRequestId;
    private String trackedPackageName;
    private String pendingStagedApkPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        processSetTheme(this, true);
        super.onCreate(savedInstanceState);

        init();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        currentInitGeneration++;
        if (prepareProgressDialog != null) {
            try {
                if (prepareProgressDialog.isShowing()) {
                    prepareProgressDialog.dismiss();
                }
            } catch (Exception ignored) {
            }
            prepareProgressDialog = null;
        }
        if (currentConfirmationDialog != null) {
            try {
                if (currentConfirmationDialog.isShowing()) {
                    currentConfirmationDialog.dismiss();
                }
            } catch (Exception ignored) {
            }
            currentConfirmationDialog = null;
        }
        if (pendingStagedApkPath != null) {
            clearTempFile(pendingStagedApkPath);
            pendingStagedApkPath = null;
        }
        unregisterInstallStatusReceiver();
        if (installProgressDialog != null) {
            try {
                if (installProgressDialog.isShowing()) {
                    installProgressDialog.dismiss();
                }
            } catch (Exception ignored) {
            }
            installProgressDialog = null;
        }
        installProgressView = null;
        init();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        currentInitGeneration++;
        if (prepareProgressDialog != null) {
            try {
                if (prepareProgressDialog.isShowing()) {
                    prepareProgressDialog.dismiss();
                }
            } catch (Exception ignored) {
            }
            prepareProgressDialog = null;
        }
        if (currentConfirmationDialog != null) {
            try {
                if (currentConfirmationDialog.isShowing()) {
                    currentConfirmationDialog.dismiss();
                }
            } catch (Exception ignored) {
            }
            currentConfirmationDialog = null;
        }
        if (pendingStagedApkPath != null) {
            clearTempFile(pendingStagedApkPath);
            pendingStagedApkPath = null;
        }
        unregisterInstallStatusReceiver();
        if (installProgressDialog != null) {
            try {
                if (installProgressDialog.isShowing()) {
                    installProgressDialog.dismiss();
                }
            } catch (Exception ignored) {
                // The window is going with the activity anyway.
            }
            installProgressDialog = null;
        }
        installProgressView = null;
    }

    private void clearTempFile(String filePath) {
        InstallPackagesUtils.deleteTempFile(this, filePath, false);
    }

    private void init() {

        final Intent intent = getIntent();
        final Uri packageUri = intent.getData();

        if (packageUri == null) {
            showToast(this, String.format(getString(R.string.invalidUriToast), "null"));
            finish();
            return;
        }

        String scheme = packageUri.getScheme();

        if ((!ContentResolver.SCHEME_FILE.equals(scheme)
                && !ContentResolver.SCHEME_CONTENT.equals(scheme)) && !"package".equals(scheme)) {
            showToast(this, String.format(getString(R.string.invalidUriToast), packageUri));
            finish();
            return;
        }

        final boolean install =
                !(Intent.ACTION_DELETE.equals(intent.getAction()) ||
                        Intent.ACTION_UNINSTALL_PACKAGE.equals(intent.getAction()));

        final String apkFilePath;

        if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            // Check Storage Permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    AlertDialog.Builder b = AlertDialogUtils
                            .buildAlertDialog(
                                    this,
                                    R.drawable.ic_warning,
                                    R.string.needStoragePermission,
                                    R.string.notice)
                            .setOnCancelListener(dialog -> finish())
                            .setPositiveButton(R.string.okay, (dialog, which) ->
                                    requestPermissions(
                                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                            301
                                    )
                            )
                            .setNegativeButton(R.string.cancel, (dialog, which) -> finish());
                    if (!isFinishing()) {
                        b.show();
                    }
                } else {
                    apkFilePath = packageUri.getPath();
                    checkAutoAndPrepareInstallDialog(install, packageUri, apkFilePath);
                }
            } else {
                apkFilePath = packageUri.getEncodedPath();
                checkAutoAndPrepareInstallDialog(install, packageUri, apkFilePath);
            }
        } else {
            String apkFileName = "package" + new Date().getTime() + "F.apk";
            apkFilePath = getExternalCacheDir() + File.separator + "ZDF-" + apkFileName;

            checkAutoAndPrepareInstallDialog(install, packageUri, apkFilePath);
        }

    }

    private void checkAutoAndPrepareInstallDialog(boolean install, Uri packageUri, String apkFilePath) {

        final String fromPkgLabel;
        final String fromPkgName;
        Intent intent = getIntent();
        if (Build.VERSION.SDK_INT >= 22
                && intent != null
                && intent.getParcelableExtra(Intent.EXTRA_REFERRER) == null
                && intent.getStringExtra(Intent.EXTRA_REFERRER_NAME) == null) {
            Uri referrerUri = getReferrer();
            if (referrerUri != null && "android-app".equals(referrerUri.getScheme())) {
                String ssp = referrerUri.getEncodedSchemeSpecificPart();
                if (ssp != null && ssp.startsWith("//") && ssp.length() > 2) {
                    fromPkgName = ssp.substring(2);
                    String refererPackageLabel =
                            getApplicationLabel(
                                    InstallPackagesActivity.this,
                                    null, null,
                                    fromPkgName
                            );
                    if (refererPackageLabel.equals(getString(R.string.uninstalled))) {
                        fromPkgLabel = ILLEGALPKGNAME;
                    } else {
                        fromPkgLabel = refererPackageLabel;
                    }
                } else {
                    fromPkgLabel = ILLEGALPKGNAME;
                    fromPkgName = ILLEGALPKGNAME;
                }
            } else {
                fromPkgLabel = ILLEGALPKGNAME;
                fromPkgName = ILLEGALPKGNAME;
            }
        } else {
            fromPkgLabel = ILLEGALPKGNAME;
            fromPkgName = ILLEGALPKGNAME;
        }

        prepareInstallDialog(install, packageUri, apkFilePath, fromPkgLabel, fromPkgName);
    }

    private View createProgressSpinnerView() {
        FrameLayout container = new FrameLayout(this);
        ProgressBar progressBar = new ProgressBar(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        int padVertical = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(0, padVertical, 0, padVertical);
        container.addView(progressBar, lp);
        return container;
    }

    private void prepareInstallDialog(final boolean install, final Uri packageUri, final String apkFilePath, final String fromPkgLabel, final String fromPkgName) {
        final StringBuilder alertDialogMessage = new StringBuilder();

        if (isFinishing()) return;

        final int gen = currentInitGeneration;
        final String nl = System.getProperty("line.separator");
        if (install) {
            final ObsdAlertDialog progressDialog = new ObsdAlertDialog(this);
            progressDialog.setTitle(R.string.plsWait);
            progressDialog.setMessage(getString(R.string.loading___));
            final View spinnerView = createProgressSpinnerView();
            progressDialog.setView(spinnerView);
            progressDialog.setCancelable(true);
            progressDialog.setCanceledOnTouchOutside(false);
            setFinishOnTouchOutside(false);
            progressDialog.setOnCancelListener(d -> {
                clearTempFile(apkFilePath);
                finish();
            });
            progressDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.okay), (d, w) -> {});
            progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.cancel), (d, w) -> {});
            progressDialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.installWhenNotUsing), (d, w) -> {});
            prepareProgressDialog = progressDialog;
            currentConfirmationDialog = progressDialog;
            installProgressDialog = progressDialog;
            installProgressView = spinnerView;
            progressDialog.show();
            clearCustomPanelMinimumHeight(spinnerView);
            setButtonVisible(progressDialog, DialogInterface.BUTTON_POSITIVE, false);
            setButtonVisible(progressDialog, DialogInterface.BUTTON_NEGATIVE, false);
            setButtonVisible(progressDialog, DialogInterface.BUTTON_NEUTRAL, false);

            if (apkFilePath != null && apkFilePath.startsWith(getExternalCacheDir() + File.separator + "ZDF-")) {
                if (pendingStagedApkPath != null && !pendingStagedApkPath.equals(apkFilePath)) {
                    clearTempFile(pendingStagedApkPath);
                }
                pendingStagedApkPath = apkFilePath;
            }
            new Thread(() -> {
                try {
                    if (apkFilePath != null && apkFilePath.startsWith(getExternalCacheDir() + File.separator + "ZDF-")) {
                        InputStream in = getContentResolver().openInputStream(packageUri);
                        if (in == null) {
                            clearTempFile(apkFilePath);
                            if (gen == currentInitGeneration && !isFinishing()) {
                                finish();
                            }
                            return;
                        }
                        FileUtils.copyFile(in, apkFilePath);
                        if (gen != currentInitGeneration || isFinishing()
                                || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                            clearTempFile(apkFilePath);
                            return;
                        }
                    }

                    PackageManager pm = getPackageManager();
                    final PackageInfo packageInfo = pm.getPackageArchiveInfo(apkFilePath, 0);
                    packageInfo.applicationInfo.sourceDir = apkFilePath;
                    packageInfo.applicationInfo.publicSourceDir = apkFilePath;

                    //Check AutoAllow
                    if (!ILLEGALPKGNAME.equals(fromPkgLabel)
                            && AutoAllowListUtils.allows(
                            AutoAllowListUtils.INSTALL_ALLOW, fromPkgName)) {
                        //Allow
                        if (pendingStagedApkPath != null && pendingStagedApkPath.equals(apkFilePath)) {
                            pendingStagedApkPath = null;
                        }
                        ServiceUtils.startService(
                                InstallPackagesActivity.this,
                                new Intent(InstallPackagesActivity.this, InstallPackagesService.class)
                                        .putExtra("install", true)
                                        .putExtra("packageUri", packageUri)
                                        .putExtra("apkFilePath", apkFilePath)
                                        .putExtra("packageInfo", packageInfo)
                                        .putExtra("waitForLeaving",
                                                tryToAvoidUpdateWhenUsing.getValue(null)
                                        )
                        );

                        if (isFinishing()) return;

                        runOnUiThread(() -> {
                            if (gen != currentInitGeneration || isFinishing()
                                    || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                                return;
                            }
                            if (progressDialog.isShowing())
                                progressDialog.dismiss();
                            finish();
                        });
                        return;
                    }

                    alertDialogMessage.append(
                            String.format(
                                    getString(R.string.pkgName_colon_pkgName),
                                    packageInfo.packageName
                            )
                    );
                    try {
                        PackageInfo pi =
                                getPackageManager().getPackageInfo(
                                        packageInfo.packageName,
                                        PackageManager.GET_UNINSTALLED_PACKAGES
                                );
                        alertDialogMessage.append(nl);
                        alertDialogMessage.append(
                                String.format(
                                    getString(R.string.existed_colon_vN_longVC),
                                    pi.versionName,
                                    Build.VERSION.SDK_INT < 28 ?
                                            Integer.toString(pi.versionCode) :
                                            Long.toString(pi.getLongVersionCode())
                                )
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    alertDialogMessage.append(nl);
                    alertDialogMessage.append(
                            String.format(
                                    getString(R.string.version_colon_vN_longVC),
                                    packageInfo.versionName,
                                    Build.VERSION.SDK_INT < 28 ?
                                            Integer.toString(packageInfo.versionCode) :
                                            Long.toString(packageInfo.getLongVersionCode())
                            )
                    );


                    if (gen != currentInitGeneration || isFinishing()) return;

                    runOnUiThread(() -> {
                        if (gen != currentInitGeneration || isFinishing()
                                || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                            return;
                        }
                        showInstallDialog(
                                progressDialog, 1,
                                alertDialogMessage, apkFilePath,
                                packageUri, fromPkgLabel, fromPkgName, packageInfo
                        );
                    });
                } catch (Exception e) {
                    alertDialogMessage.append(
                            String.format(
                                    getString(R.string.cannotInstall_colon_msg),
                                    e.getLocalizedMessage()
                            )
                    );

                    if (gen != currentInitGeneration || isFinishing()) return;

                    runOnUiThread(() -> {
                        if (gen != currentInitGeneration || isFinishing()
                                || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                            return;
                        }
                        showInstallDialog(
                                progressDialog, 2,
                                alertDialogMessage, apkFilePath,
                                packageUri, fromPkgLabel, fromPkgName, null
                        );
                    });
                }
            }).start();
        } else {
            String packageName = packageUri.getEncodedSchemeSpecificPart();
            if (packageName == null) {
                showToast(this, String.format(getString(R.string.invalidUriToast), packageUri));
                finish();
                return;
            }
            PackageInfo uninstalledPackageInfo = null;
            try {
                uninstalledPackageInfo = getPackageManager().getPackageInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            if (uninstalledPackageInfo != null && (uninstalledPackageInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
                alertDialogMessage.append(getString(R.string.system_app_warning));
                alertDialogMessage.append(nl);
                alertDialogMessage.append(nl);
            }
            java.util.List<String> critical = CriticalPackagesUtils.findCriticalPackages(this, java.util.Collections.singletonList(packageName));
            boolean isCritical = !critical.isEmpty();
            if (isCritical) {
                alertDialogMessage.append(getString(R.string.caution)).append(": ");
                alertDialogMessage.append(getString(R.string.critical_package_uninstall_warning_single));
                alertDialogMessage.append(nl);
                alertDialogMessage.append(nl);
            }
            alertDialogMessage.append(
                    String.format(
                            getString(R.string.pkgName_colon_pkgName),
                            packageName
                    )
            );
            alertDialogMessage.append(nl);
            showInstallDialog(
                    null, 0,
                    alertDialogMessage, apkFilePath,
                    packageUri, fromPkgLabel, fromPkgName, uninstalledPackageInfo, isCritical
            );
        }
    }

    //install: 0-uninstall, 1-install, 2-failed.
    private void showInstallDialog(final AlertDialog progressDialog, final int install, final CharSequence alertDialogMessage, final String apkFilePath, final Uri packageUri, final String fromPkgLabel, final String fromPkgName, final PackageInfo processedPackageInfo) {
        showInstallDialog(progressDialog, install, alertDialogMessage, apkFilePath, packageUri, fromPkgLabel, fromPkgName, processedPackageInfo, false);
    }

    private void showInstallDialog(final AlertDialog progressDialog, final int install, final CharSequence alertDialogMessage, final String apkFilePath, final Uri packageUri, final String fromPkgLabel, final String fromPkgName, final PackageInfo processedPackageInfo, final boolean isCriticalApp) {
        if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
            return;
        }

        final boolean isExistingDialog = (progressDialog instanceof ObsdAlertDialog && progressDialog.isShowing());
        final ObsdAlertDialog installPackagesAlertDialog = isExistingDialog
                ? (ObsdAlertDialog) progressDialog
                : new ObsdAlertDialog(this);

        final boolean isSystemApp = install == 0 && processedPackageInfo != null
                && processedPackageInfo.applicationInfo != null
                && (processedPackageInfo.applicationInfo.flags
                & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
        final boolean hasSystemUpdate = install == 0 && processedPackageInfo != null
                && processedPackageInfo.applicationInfo != null
                && (processedPackageInfo.applicationInfo.flags
                & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

        switch (install) {
            case 0:
                installPackagesAlertDialog.setTitle(
                        getApplicationLabel(
                                InstallPackagesActivity.this, null, null,
                                packageUri == null ? "" : packageUri.getEncodedSchemeSpecificPart()
                        )
                );
                if (isSystemApp || isCriticalApp) {
                    installPackagesAlertDialog.setIcon(R.drawable.ic_warning);
                }
                break;
            case 1:
                installPackagesAlertDialog.setTitle(installAppLabel(processedPackageInfo));
                break;
            case 2:
                installPackagesAlertDialog.setTitle(R.string.failed);
                break;
            default:
                break;
        }

        final boolean preDefinedTryToAvoidUpdateWhenUsing =
                tryToAvoidUpdateWhenUsing.getValue(null);

        installPackagesAlertDialog.setMessage(alertDialogMessage);

        if (isExistingDialog && installProgressView != null) {
            installProgressView.setVisibility(View.GONE);
        }

        String positiveButtonText = getString(R.string.okay);
        if (install == 0) {
            if (isSystemApp && hasSystemUpdate) {
                positiveButtonText = getString(R.string.uninstall_completely);
            } else {
                positiveButtonText = getString(R.string.uninstall);
            }
        }

        if (isExistingDialog) {
            Button positiveButton = installPackagesAlertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setText(positiveButtonText);
                positiveButton.setVisibility(View.VISIBLE);
                positiveButton.setOnClickListener(v -> handlePositiveAction(
                        installPackagesAlertDialog, install, alertDialogMessage, apkFilePath,
                        packageUri, fromPkgLabel, fromPkgName, processedPackageInfo,
                        isCriticalApp, isSystemApp, preDefinedTryToAvoidUpdateWhenUsing
                ));
            }

            Button negativeButton = installPackagesAlertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            if (negativeButton != null) {
                if (install == 2) {
                    negativeButton.setVisibility(View.GONE);
                } else {
                    negativeButton.setText(R.string.cancel);
                    negativeButton.setVisibility(View.VISIBLE);
                    negativeButton.setOnClickListener(v -> {
                        if (install != 0) {
                            clearTempFile(apkFilePath);
                            if (pendingStagedApkPath != null && pendingStagedApkPath.equals(apkFilePath)) {
                                pendingStagedApkPath = null;
                            }
                        }
                        installPackagesAlertDialog.dismiss();
                        finish();
                    });
                }
            }

            Button neutralButton = installPackagesAlertDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            if (neutralButton != null) {
                if (install == 0 && hasSystemUpdate) {
                    neutralButton.setText(R.string.uninstall_updates_only);
                    neutralButton.setVisibility(View.VISIBLE);
                    neutralButton.setOnClickListener(v -> handleUninstallUpdatesOnly(
                            installPackagesAlertDialog, packageUri, processedPackageInfo, preDefinedTryToAvoidUpdateWhenUsing
                    ));
                } else if (install == 1
                        && !preDefinedTryToAvoidUpdateWhenUsing
                        && processedPackageInfo != null
                        && AccessibilityUtils.isAccessibilitySettingsOn(this)) {
                    neutralButton.setText(R.string.installWhenNotUsing);
                    neutralButton.setVisibility(View.VISIBLE);
                    neutralButton.setOnClickListener(v -> handleInstallWhenNotUsing(
                            installPackagesAlertDialog, alertDialogMessage, apkFilePath,
                            packageUri, fromPkgLabel, fromPkgName, processedPackageInfo, isCriticalApp
                    ));
                } else {
                    neutralButton.setVisibility(View.GONE);
                }
            }

            installPackagesAlertDialog.setOnCancelListener(dialog -> {
                if (install != 0) clearTempFile(apkFilePath);
                finish();
            });

            currentConfirmationDialog = installPackagesAlertDialog;
            applyButtonAccentColor(installPackagesAlertDialog);
        } else {
            installPackagesAlertDialog.setButton(
                    DialogInterface.BUTTON_POSITIVE,
                    positiveButtonText,
                    (dialog, which) -> handlePositiveAction(
                            installPackagesAlertDialog, install, alertDialogMessage, apkFilePath,
                            packageUri, fromPkgLabel, fromPkgName, processedPackageInfo,
                            isCriticalApp, isSystemApp, preDefinedTryToAvoidUpdateWhenUsing
                    )
            );
            if (install != 2) {
                installPackagesAlertDialog.setButton(
                        DialogInterface.BUTTON_NEGATIVE,
                        getString(R.string.cancel),
                        (dialog, which) -> {
                            if (install != 0) {
                                clearTempFile(apkFilePath);
                                if (pendingStagedApkPath != null && pendingStagedApkPath.equals(apkFilePath)) {
                                    pendingStagedApkPath = null;
                                }
                            }
                            finish();
                        }
                );
            }
            if (install == 0 && hasSystemUpdate) {
                installPackagesAlertDialog.setButton(
                        DialogInterface.BUTTON_NEUTRAL,
                        getString(R.string.uninstall_updates_only),
                        (dialog, which) -> handleUninstallUpdatesOnly(
                                installPackagesAlertDialog, packageUri, processedPackageInfo, preDefinedTryToAvoidUpdateWhenUsing
                        )
                );
            } else if (install == 1
                    && !preDefinedTryToAvoidUpdateWhenUsing
                    && processedPackageInfo != null
                    && AccessibilityUtils.isAccessibilitySettingsOn(this)) {
                installPackagesAlertDialog.setButton(
                        DialogInterface.BUTTON_NEUTRAL,
                        getString(R.string.installWhenNotUsing),
                        (dialog, which) -> handleInstallWhenNotUsing(
                                installPackagesAlertDialog, alertDialogMessage, apkFilePath,
                                packageUri, fromPkgLabel, fromPkgName, processedPackageInfo, isCriticalApp
                        )
                );
            }
            installPackagesAlertDialog.setOnCancelListener(dialog -> {
                if (install != 0) clearTempFile(apkFilePath);
                finish();
            });

            currentConfirmationDialog = installPackagesAlertDialog;
            installPackagesAlertDialog.show();
            applyButtonAccentColor(installPackagesAlertDialog);

            Window w = installPackagesAlertDialog.getWindow();
            if (w != null) {
                View v = (View) w.findViewById(android.R.id.custom);
                if (v != null) {
                    View p = (View) v.getParent();
                    if (p != null)
                        p.setMinimumHeight(0);
                }
            }
        }
    }

    private void handlePositiveAction(final ObsdAlertDialog dialog, final int install,
                                      final CharSequence alertDialogMessage, final String apkFilePath,
                                      final Uri packageUri, final String fromPkgLabel,
                                      final String fromPkgName, final PackageInfo processedPackageInfo,
                                      final boolean isCriticalApp, final boolean isSystemApp,
                                      final boolean preDefinedTryToAvoidUpdateWhenUsing) {
        if (notAllowInstallWhenIsObsd.getValue(null) && dialog.isObsd()) {
            AlertDialogUtils.buildAlertDialog(
                            InstallPackagesActivity.this,
                            R.drawable.ic_warning,
                            R.string.alert_isObsd,
                            R.string.dangerous)
                    .setPositiveButton(R.string.retry, (dialog1, which1) ->
                            showInstallDialog(
                                    dialog, install,
                                    alertDialogMessage, apkFilePath, packageUri,
                                    fromPkgLabel, fromPkgName, processedPackageInfo, isCriticalApp)
                    )
                    .setNegativeButton(R.string.cancel, (dialog12, which12) -> {
                        if (install != 0) clearTempFile(apkFilePath);
                        dialog.dismiss();
                        finish();
                    })
                    .setOnCancelListener(dialog13 ->
                            showInstallDialog(dialog, install,
                                    alertDialogMessage, apkFilePath, packageUri,
                                    fromPkgLabel, fromPkgName, processedPackageInfo, isCriticalApp)
                    )
                    .create().show();
            return;
        }

        if (install == 2) {
            clearTempFile(apkFilePath);
            dialog.dismiss();
            finish();
            return;
        }

        boolean shizukuAvailable = false;
        try {
            shizukuAvailable = rikka.shizuku.Shizuku.pingBinder()
                    && rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {}
        boolean deviceOwnerAvailable = DevicePolicyManagerUtils.isDeviceOwner(InstallPackagesActivity.this);
        boolean fullUninstallNeedsShell = install == 0
                && UninstallPolicyUtils.requiresPrivilegedShellForFullUninstall(isSystemApp, false);
        boolean rootCheckRequired = UninstallPolicyUtils.shouldCheckRootPermission(
                deviceOwnerAvailable, fullUninstallNeedsShell);
        boolean rootAvailable = !shizukuAvailable && rootCheckRequired && FUFUtils.checkRootPermission();
        boolean privilegedShellAvailable = rootAvailable || shizukuAvailable;

        if (deviceOwnerAvailable && fullUninstallNeedsShell && !privilegedShellAvailable) {
            showToast(InstallPackagesActivity.this, R.string.full_system_uninstall_requires_shell);
            return;
        }

        if (deviceOwnerAvailable || rootAvailable || shizukuAvailable) {
            final boolean trackInDialog = canTrackInstallInDialog(
                    install, processedPackageInfo, preDefinedTryToAvoidUpdateWhenUsing);
            final String installRequestId = UUID.randomUUID().toString();
            if (pendingStagedApkPath != null && pendingStagedApkPath.equals(apkFilePath)) {
                pendingStagedApkPath = null;
            }
            if (trackInDialog) {
                beginInstallTracking(dialog, processedPackageInfo, installRequestId);
            }
            ServiceUtils.startService(
                    InstallPackagesActivity.this,
                    new Intent(InstallPackagesActivity.this, InstallPackagesService.class)
                            .putExtra("install", install == 1)
                            .putExtra("packageUri", packageUri)
                            .putExtra("apkFilePath", apkFilePath)
                            .putExtra("packageInfo", processedPackageInfo)
                            .putExtra("installRequestId", installRequestId)
                            .putExtra("waitForLeaving", preDefinedTryToAvoidUpdateWhenUsing));
            if (!trackInDialog) {
                dialog.dismiss();
                finish();
            }
        } else if (install == 0) {
            if (isSystemApp) {
                showToast(InstallPackagesActivity.this, R.string.full_system_uninstall_requires_shell);
            } else {
                startActivity(
                        new Intent(
                                Intent.ACTION_DELETE,
                                Uri.parse("package:" + packageUri.getEncodedSchemeSpecificPart())
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                );
                dialog.dismiss();
                finish();
            }
        } else {
            dialog.dismiss();
            showInstallPermissionCheckFailedDialog(
                    install, apkFilePath, packageUri,
                    processedPackageInfo, preDefinedTryToAvoidUpdateWhenUsing);
        }
    }

    private void handleUninstallUpdatesOnly(final AlertDialog dialog, final Uri packageUri,
                                           final PackageInfo processedPackageInfo,
                                           final boolean preDefinedTryToAvoidUpdateWhenUsing) {
        boolean shizukuAvailable = false;
        try {
            shizukuAvailable = rikka.shizuku.Shizuku.pingBinder()
                    && rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {}
        boolean deviceOwnerAvailable = DevicePolicyManagerUtils.isDeviceOwner(InstallPackagesActivity.this);
        boolean rootAvailable = !shizukuAvailable
                && UninstallPolicyUtils.shouldCheckRootPermission(deviceOwnerAvailable, false)
                && FUFUtils.checkRootPermission();
        if (deviceOwnerAvailable || rootAvailable || shizukuAvailable) {
            ServiceUtils.startService(
                    InstallPackagesActivity.this,
                    new Intent(InstallPackagesActivity.this, InstallPackagesService.class)
                            .putExtra("install", false)
                            .putExtra("packageUri", packageUri)
                            .putExtra("uninstall_updates_only", true)
                            .putExtra("packageInfo", processedPackageInfo)
                            .putExtra("waitForLeaving", preDefinedTryToAvoidUpdateWhenUsing));
        } else {
            startActivity(
                    new Intent(
                            Intent.ACTION_DELETE,
                            Uri.parse("package:" + packageUri.getEncodedSchemeSpecificPart())
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            );
        }
        if (dialog != null) dialog.dismiss();
        finish();
    }

    private void handleInstallWhenNotUsing(final ObsdAlertDialog dialog,
                                          final CharSequence alertDialogMessage, final String apkFilePath,
                                          final Uri packageUri, final String fromPkgLabel,
                                          final String fromPkgName, final PackageInfo processedPackageInfo,
                                          final boolean isCriticalApp) {
        if (notAllowInstallWhenIsObsd.getValue(null) && dialog.isObsd()) {
            AlertDialogUtils.buildAlertDialog(
                            InstallPackagesActivity.this,
                            R.drawable.ic_warning,
                            R.string.alert_isObsd,
                            R.string.dangerous)
                    .setPositiveButton(R.string.retry, (dialog14, which13) ->
                            showInstallDialog(
                                    dialog, 1,
                                    alertDialogMessage, apkFilePath,
                                    packageUri, fromPkgLabel,
                                    fromPkgName, processedPackageInfo, isCriticalApp)
                    )
                    .setNegativeButton(R.string.cancel, (dialog15, which14) -> {
                        clearTempFile(apkFilePath);
                        if (pendingStagedApkPath != null && pendingStagedApkPath.equals(apkFilePath)) {
                            pendingStagedApkPath = null;
                        }
                        if (dialog != null) dialog.dismiss();
                        finish();
                    })
                    .create().show();
            return;
        }

        if (pendingStagedApkPath != null && pendingStagedApkPath.equals(apkFilePath)) {
            pendingStagedApkPath = null;
        }
        ServiceUtils.startService(
                InstallPackagesActivity.this,
                new Intent(InstallPackagesActivity.this,
                        InstallPackagesService.class)
                        .putExtra("install", true)
                        .putExtra("packageUri", packageUri)
                        .putExtra("apkFilePath", apkFilePath)
                        .putExtra("packageInfo", processedPackageInfo)
                        .putExtra("waitForLeaving", true));
        if (dialog != null) dialog.dismiss();
        finish();
    }

    private void applyButtonAccentColor(AlertDialog dialog) {
        if (dialog == null) return;
        if (cf.playhi.freezeyou.utils.ThemeUtils.isLightUiTheme(this)) {
            int accentColor = androidx.core.content.ContextCompat.getColor(this, R.color.appAccent);
            Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (positiveButton != null) positiveButton.setTextColor(accentColor);
            Button negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            if (negativeButton != null) negativeButton.setTextColor(accentColor);
            Button neutralButton = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            if (neutralButton != null) neutralButton.setTextColor(accentColor);
        }
    }

    /**
     * The name the install dialogs and the result message show, resolved from the APK's own
     * application info: the package is not installed yet, so the package manager cannot be asked
     * for it the way an already installed one can.
     */
    private CharSequence installAppLabel(PackageInfo packageInfo) {
        if (packageInfo == null || packageInfo.applicationInfo == null) {
            return getString(R.string.install);
        }
        try {
            CharSequence label = getPackageManager().getApplicationLabel(packageInfo.applicationInfo);
            if (label != null) return label;
            label = packageInfo.applicationInfo.loadLabel(getPackageManager());
            if (label != null) return label;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return getString(R.string.install);
    }

    /**
     * Whether this window can stay up and report the install itself. Only an install has a result
     * to wait for, and one the service defers until FreezeYou has left — a self-update with
     * "avoid updating while in use" on — is excluded: the user would watch a spinner that cannot
     * finish while the window that has to go is still up.
     */
    private boolean canTrackInstallInDialog(int install, PackageInfo packageInfo,
                                            boolean waitForLeaving) {
        if (install != 1 || packageInfo == null || packageInfo.packageName == null) {
            return false;
        }
        if (waitForLeaving) {
            final String pkg = packageInfo.packageName;
            if (pkg.equals(getPackageName())
                    || pkg.equals(cf.playhi.freezeyou.MainApplication.getCurrentPackage())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Updates the existing dialog to carry the install through to its result in place.
     */
    private void beginInstallTracking(final AlertDialog dialog, final PackageInfo packageInfo, final String requestId) {
        this.trackedRequestId = requestId;
        this.trackedPackageName = packageInfo.packageName;
        final String pkgName = packageInfo.packageName;
        final CharSequence appLabel = installAppLabel(packageInfo);

        installProgressDialog = dialog;

        dialog.setTitle(R.string.plsWait);
        dialog.setMessage(getString(R.string.loading___));
        if (installProgressView != null) {
            installProgressView.setVisibility(View.VISIBLE);
        }
        setButtonVisible(dialog, DialogInterface.BUTTON_POSITIVE, false);
        setButtonVisible(dialog, DialogInterface.BUTTON_NEGATIVE, false);
        setButtonVisible(dialog, DialogInterface.BUTTON_NEUTRAL, false);

        dialog.setOnCancelListener(d -> finish());
        dialog.setCanceledOnTouchOutside(false);
        setFinishOnTouchOutside(false);

        installTimeoutHandler = new Handler(Looper.getMainLooper());
        installTimeoutRunnable = () -> {
            showInstallResult(appLabel, false, getString(R.string.failed));
        };
        // Initial queue watchdog: generous timeout while waiting in service queue
        installTimeoutHandler.postDelayed(installTimeoutRunnable, 10 * 60_000L);

        installStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) {
                    return;
                }
                final String incomingRequestId = intent.getStringExtra("installRequestId");
                if (incomingRequestId != null) {
                    if (!requestId.equals(incomingRequestId)) {
                        return;
                    }
                } else if (!pkgName.equals(intent.getStringExtra("pkgName"))) {
                    return;
                }
                // When the service starts executing this request, reset watchdog to execution timeout
                if (intent.getBooleanExtra("started", false)) {
                    if (installTimeoutHandler != null && installTimeoutRunnable != null) {
                        installTimeoutHandler.removeCallbacks(installTimeoutRunnable);
                        installTimeoutHandler.postDelayed(installTimeoutRunnable,
                                InstallPackagesService.INSTALL_TIMEOUT_MS + 15_000L);
                    }
                    return;
                }
                showInstallResult(appLabel,
                        intent.getBooleanExtra("success", false),
                        intent.getStringExtra("statusMessage"));
            }
        };
        ContextCompat.registerReceiver(
                this,
                installStatusReceiver,
                new IntentFilter(InstallPackagesService.ACTION_INSTALL_STATUS_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    private void showInstallResult(final CharSequence appLabel, final boolean success,
                                   final String statusMessage) {
        // One install, one result: the listener has nothing left to wait for either way.
        unregisterInstallStatusReceiver();
        final AlertDialog dialog = installProgressDialog;
        if (dialog == null
                || isFinishing()
                || (Build.VERSION.SDK_INT >= 17 && isDestroyed())
                || !dialog.isShowing()) {
            return;
        }

        if (installProgressView != null) {
            installProgressView.setVisibility(View.GONE);
        }
        if (success) {
            dialog.setTitle(R.string.success);
            dialog.setMessage(String.format(getString(R.string.app_installFinished), appLabel));

            final Button positiveBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (positiveBtn != null) {
                positiveBtn.setText(R.string.launch);
                positiveBtn.setVisibility(View.VISIBLE);
                positiveBtn.setOnClickListener(v -> {
                    if (trackedPackageName != null) {
                        launchInstalledApp(trackedPackageName);
                    }
                    dialog.dismiss();
                    finish();
                });
            }
            final Button negativeBtn = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            if (negativeBtn != null) {
                negativeBtn.setText(R.string.finish);
                negativeBtn.setVisibility(View.VISIBLE);
                negativeBtn.setOnClickListener(v -> {
                    dialog.dismiss();
                    finish();
                });
            }
            setButtonVisible(dialog, DialogInterface.BUTTON_NEUTRAL, false);
            applyButtonAccentColor(dialog);
        } else {
            final StringBuilder failed = new StringBuilder(
                    String.format(getString(R.string.app_installFailed), appLabel));
            if (statusMessage != null && !statusMessage.isEmpty()) {
                failed.append(System.getProperty("line.separator")).append(statusMessage);
            }
            dialog.setTitle(R.string.failed);
            dialog.setMessage(failed);
            setButtonVisible(dialog, DialogInterface.BUTTON_NEGATIVE, false);
            setButtonVisible(dialog, DialogInterface.BUTTON_NEUTRAL, false);
            final Button positiveBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (positiveBtn != null) {
                positiveBtn.setText(R.string.okay);
                positiveBtn.setVisibility(View.VISIBLE);
                positiveBtn.setOnClickListener(v -> {
                    dialog.dismiss();
                    finish();
                });
            }
            applyButtonAccentColor(dialog);
        }
    }

    private static void clearCustomPanelMinimumHeight(View customView) {
        for (ViewParent panel = customView.getParent();
             panel instanceof FrameLayout;
             panel = panel.getParent()) {
            ((FrameLayout) panel).setMinimumHeight(0);
        }
    }

    /** A dialog button keeps the visibility it was given once the dialog is shown. */
    private static void setButtonVisible(AlertDialog dialog, int whichButton, boolean visible) {
        Button button = dialog.getButton(whichButton);
        if (button != null) {
            button.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void launchInstalledApp(String pkgName) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkgName);
            if (launch == null) {
                showToast(this, R.string.failed);
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        } catch (Exception e) {
            e.printStackTrace();
            showToast(this, R.string.failed);
        }
    }

    private void unregisterInstallStatusReceiver() {
        if (installTimeoutHandler != null && installTimeoutRunnable != null) {
            installTimeoutHandler.removeCallbacks(installTimeoutRunnable);
            installTimeoutRunnable = null;
        }
        installTimeoutHandler = null;
        if (installStatusReceiver == null) return;
        try {
            unregisterReceiver(installStatusReceiver);
        } catch (Exception ignored) {
            // Already gone: the window was torn down while the install was running.
        }
        installStatusReceiver = null;
    }

    private void showInstallPermissionCheckFailedDialog(final int install, final String apkFilePath,
                                                        final Uri packageUri,
                                                        final PackageInfo processedPackageInfo,
                                                        final boolean preDefinedTryToAvoidUpdateWhenUsing) {
        AlertDialog.Builder adbd = FreezeYouAlertDialogBuilder(InstallPackagesActivity.this);
        adbd.setMessage(R.string.installPerimisionCheckFailed_ifContinue);
        adbd.setTitle(R.string.notice);
        adbd.setPositiveButton(R.string.okay, (dialog, which) -> {
            if (pendingStagedApkPath != null && pendingStagedApkPath.equals(apkFilePath)) {
                pendingStagedApkPath = null;
            }
            ServiceUtils.startService(
                    InstallPackagesActivity.this,
                    new Intent(InstallPackagesActivity.this,
                            InstallPackagesService.class)
                            .putExtra("install", install == 1)
                            .putExtra("packageUri", packageUri)
                            .putExtra("apkFilePath", apkFilePath)
                            .putExtra("packageInfo", processedPackageInfo)
                            .putExtra("waitForLeaving", preDefinedTryToAvoidUpdateWhenUsing));
            finish();
        });
        adbd.setNegativeButton(R.string.cancel, (dialog, which) -> {
            if (install != 0) {
                clearTempFile(apkFilePath);
                if (pendingStagedApkPath != null && pendingStagedApkPath.equals(apkFilePath)) {
                    pendingStagedApkPath = null;
                }
            }
            finish();
        });
        adbd.setNeutralButton(R.string.jumpToSysInstaller, (dialog, which) -> {
            if (install == 0) {
                InstallPackagesActivity.this.startActivity(
                        new Intent(
                                Intent.ACTION_DELETE,
                                Uri.parse("package:" + packageUri.getEncodedSchemeSpecificPart())
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                );
                finish();
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (getPackageManager().canRequestPackageInstalls()) {
                        requestSysInstallPkg(apkFilePath);
                    } else {
                        showInstallPermissionCheckFailedDialog(
                                install, apkFilePath, packageUri,
                                processedPackageInfo, preDefinedTryToAvoidUpdateWhenUsing);
                        Uri packageUri1 = Uri.parse("package:" + getPackageName());
                        Intent intent =
                                new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri1);
                        startActivity(intent);
                    }
                } else {
                    requestSysInstallPkg(apkFilePath);
                }
            }
        });
        adbd.setOnCancelListener(dialog -> finish());
        adbd.show();
    }

    private void requestSysInstallPkg(String filePath) {
        File file = new File(filePath);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Uri uri = FileProvider.getUriForFile(this, "cf.playhi.freezeyou.fileprovider", file);
                launchSysInstall(uri);
            } catch (IllegalArgumentException e) {
                new Thread(() -> {
                    File targetDir = getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir();
                    File tempFile = new File(targetDir, "ZDF-fallback-" + System.currentTimeMillis() + "_" + file.getName());
                    try (InputStream in = new FileInputStream(file)) {
                        FileUtils.copyFile(in, tempFile.getAbsolutePath());
                        Uri uri = FileProvider.getUriForFile(this, "cf.playhi.freezeyou.fileprovider", tempFile);
                        runOnUiThread(() -> launchSysInstall(uri));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                        runOnUiThread(() -> {
                            showToast(this, R.string.failed);
                            finish();
                        });
                    }
                }).start();
            }
        } else {
            launchSysInstall(Uri.fromFile(file));
        }
    }

    private void launchSysInstall(Uri uri) {
        if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            Intent chooser = Intent.createChooser(intent, getString(R.string.plsSelect));
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(chooser);
            }
        } finally {
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 301) {
            if (grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                init();
            } else {
                finish();
            }
        }
    }

}

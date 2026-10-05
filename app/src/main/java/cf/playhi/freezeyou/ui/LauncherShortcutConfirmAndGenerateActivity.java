package cf.playhi.freezeyou.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Date;

import cf.playhi.freezeyou.Freeze;
import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.utils.FUFUtils;
import cf.playhi.freezeyou.utils.ThemeUtils;
import cf.playhi.freezeyou.app.FreezeYouBaseActivity;

import static cf.playhi.freezeyou.utils.LauncherShortcutUtils.createShortCut;
import static cf.playhi.freezeyou.utils.ThemeUtils.processActionBar;
import static cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme;
import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon;
import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getBitmapFromDrawable;
import static cf.playhi.freezeyou.utils.ApplicationInfoUtils.getApplicationInfoFromPkgName;
import static cf.playhi.freezeyou.utils.MoreUtils.requestOpenWebSite;
import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

public class LauncherShortcutConfirmAndGenerateActivity extends FreezeYouBaseActivity {

    private boolean requestFromLauncher;
    private Class<?> targetSelfCls;
    private Drawable finalDrawable;
    /**
     * A shortcut that only starts one activity. The same screen serves it, minus the fields that
     * belong to freezing — the attached task and the shortcut id — and with the activity picker
     * opening by itself, since picking an activity is the whole point of coming here.
     * <p>
     * Held in a field rather than read from the intent each time: re-picking the package replaces
     * the intent with the picker's answer, which carries no mode of its own.
     */
    private boolean activityShortcutMode;
    private boolean openTargetPickerOnNextInit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        processSetTheme(this);
        super.onCreate(savedInstanceState);
        processActionBar(getSupportActionBar());

        setContentView(R.layout.lscaga_main);

        Intent intent = getIntent();

        requestFromLauncher = Intent.ACTION_CREATE_SHORTCUT.equals(intent.getAction());

        activityShortcutMode = intent.getBooleanExtra("activityShortcutMode", false);
        // Not after a rotation: the picked activity is already in the fields, and reopening the
        // list would throw it away.
        openTargetPickerOnNextInit = activityShortcutMode && savedInstanceState == null;

        targetSelfCls =
                requestFromLauncher
                        ?
                        Freeze.class
                        :
                        ((Class<?>) intent.getSerializableExtra("class"));

        init();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.lscaga_menu, menu);
        String cTheme = ThemeUtils.getUiTheme(this);
        if ("white".equals(cTheme) || "default".equals(cTheme))
            menu.findItem(R.id.lscaga_menu_help).setIcon(R.drawable.ic_action_help_outline_light);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.lscaga_menu_help:
                requestOpenWebSite(this,
                        String.format("https://www.zidon.net/%1$s/guide/schedules.html",
                                getString(R.string.correspondingAndAvailableWebsiteUrlLanguageCode)));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 8:
                if (resultCode == RESULT_OK) {
                    EditText lscaga_target_editText = findViewById(R.id.lscaga_target_editText);
                    EditText lscaga_id_editText = findViewById(R.id.lscaga_id_editText);
                    ImageButton lscaga_icon_imageButton = findViewById(R.id.lscaga_icon_imageButton);
                    EditText lscaga_displayName_editText = findViewById(R.id.lscaga_displayName_editText);
                    lscaga_target_editText.setText(data.getStringExtra("name"));
                    lscaga_id_editText.setText(data.getStringExtra("id"));
                    lscaga_displayName_editText.setText(data.getStringExtra("label"));
                    Bitmap bm = data.getParcelableExtra("icon");
                    if (bm != null) {
                        finalDrawable = new BitmapDrawable(bm);
                        lscaga_icon_imageButton.setImageDrawable(finalDrawable);
                    }
                    updateActionButtons(
                            findViewById(R.id.lscaga_generate_button),
                            findViewById(R.id.lscaga_simulate_button),
                            lscaga_target_editText);
                } else if (activityShortcutMode
                        && ((EditText) findViewById(R.id.lscaga_target_editText)).length() == 0) {
                    // The pick is the whole point of this screen; a cancelled picker leaves
                    // nothing to generate.
                    finish();
                }
                break;
            case 11:
                if (resultCode == RESULT_OK) {
                    setIntent(data);
                    // A different package means a different activity list, so ask again.
                    openTargetPickerOnNextInit = activityShortcutMode;
                    init();
                }
                break;
            case 21:
                if (resultCode == RESULT_OK) {
                    finalDrawable = new BitmapDrawable((Bitmap) data.getParcelableExtra("Icon"));
                    ImageButton lscaga_icon_imageButton = findViewById(R.id.lscaga_icon_imageButton);
                    lscaga_icon_imageButton.setImageDrawable(finalDrawable);
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The picked target text comes back with the view state after init() has run, so the
        // buttons are re-evaluated here rather than trusted from onCreate.
        if (activityShortcutMode) {
            updateActionButtons(
                    findViewById(R.id.lscaga_generate_button),
                    findViewById(R.id.lscaga_simulate_button),
                    findViewById(R.id.lscaga_target_editText));
        }
    }

    private String checkAndAvoidNull(String s, String alternate) {
        return s == null ? (alternate == null ? "" : alternate) : s;
    }

    private void init() {
        Intent intent = getIntent();

        String name = checkAndAvoidNull(intent.getStringExtra("name"), getString(R.string.name));
        String id = checkAndAvoidNull(intent.getStringExtra("id"), Long.toString(new Date().getTime()));//若桌面（类小部件快捷方式）发起，id 值无需考虑，无需使用。
        String pkgName = checkAndAvoidNull(intent.getStringExtra("pkgName"), getString(R.string.plsSelect));

        Button lscaga_package_button = findViewById(R.id.lscaga_package_button);
        Button lscaga_target_button = findViewById(R.id.lscaga_target_button);
        Button lscaga_generate_button = findViewById(R.id.lscaga_generate_button);
        Button lscaga_cancel_button = findViewById(R.id.lscaga_cancel_button);
        Button lscaga_simulate_button = findViewById(R.id.lscaga_simulate_button);
        EditText lscaga_package_editText = findViewById(R.id.lscaga_package_editText);
        EditText lscaga_displayName_editText = findViewById(R.id.lscaga_displayName_editText);
        EditText lscaga_target_editText = findViewById(R.id.lscaga_target_editText);
        EditText lscaga_task_editText = findViewById(R.id.lscaga_task_editText);
        EditText lscaga_id_editText = findViewById(R.id.lscaga_id_editText);
        ImageButton lscaga_icon_imageButton = findViewById(R.id.lscaga_icon_imageButton);

        processSelectedPackageEditText(pkgName, lscaga_package_editText);

        processSelectPackageButton(lscaga_package_button);

        processDisplayNameEditText(name, lscaga_displayName_editText);

        // In the activity-shortcut mode the target is the picked activity itself: no "launch
        // the app" default and no re-picking. Both pickers below belong to the freeze flow.
        if (!activityShortcutMode) {
            processSelectedTargetEditText(pkgName, lscaga_target_editText);
        }

        processChangeIconImageButton(pkgName, lscaga_icon_imageButton);

        if (!activityShortcutMode) {
            processSelectTargetButton(pkgName, lscaga_target_button);
        }

        processTaskEditText(lscaga_task_editText);

        processIDEditText(id, lscaga_id_editText);

        processCancelButton(lscaga_cancel_button);

        processSimulateButton(lscaga_package_editText, lscaga_target_editText, lscaga_task_editText, lscaga_simulate_button);

        processGenerateButton(lscaga_generate_button, lscaga_package_editText, lscaga_displayName_editText, lscaga_target_editText, lscaga_id_editText, lscaga_task_editText);

        if (activityShortcutMode) {
            hideFieldsThatBelongToFreezing();
            hidePackagePicker(findViewById(R.id.lscaga_package_textView),
                    findViewById(R.id.lscaga_package_linearLayout),
                    findViewById(R.id.lscaga_package_hint_textView),
                    findViewById(R.id.lscaga_displayName_textView));
            prepareTargetAsReadOnly(lscaga_target_editText,
                    findViewById(R.id.lscaga_target_button),
                    findViewById(R.id.lscaga_target_hint_textView));
            if (openTargetPickerOnNextInit) {
                openTargetPickerOnNextInit = false;
                startSelectTargetActivityForResult(pkgName);
            }
        }

        updateActionButtons(lscaga_generate_button, lscaga_simulate_button, lscaga_target_editText);

    }

    /**
     * The attached task and the shortcut id are part of a freeze shortcut, not of one that starts
     * an activity. The id is still needed to pin the shortcut: the picker supplies one per
     * activity, and until then {@link #init()} has generated a timestamp, so it is never the
     * freeze shortcut's id for this package. Every hidden view sits at the end of the layout's
     * chain of rules, so nothing is positioned relative to them.
     */
    private void hideFieldsThatBelongToFreezing() {
        int[] ids = {
                R.id.lscaga_task_textView,
                R.id.lscaga_task_editText,
                R.id.lscaga_id_textView,
                R.id.lscaga_id_editText
        };
        for (int id : ids) {
            View view = findViewById(id);
            if (view != null) {
                view.setVisibility(View.GONE);
            }
        }
    }

    /**
     * The package is the context the owner came from: the menu item lives inside one
     * application, and repointing the shortcut at another one would cascade into re-picking
     * the activity anyway. The label above the name is re-anchored to the top of the screen,
     * because the hidden rows sit at the head of the RelativeLayout's below-chain.
     */
    private void hidePackagePicker(View packageLabel, View packageRow, View packageHint,
                                   View displayNameLabel) {
        for (View view : new View[]{packageLabel, packageRow, packageHint}) {
            view.setVisibility(View.GONE);
        }
        RelativeLayout.LayoutParams layoutParams =
                (RelativeLayout.LayoutParams) displayNameLabel.getLayoutParams();
        layoutParams.addRule(RelativeLayout.BELOW, 0);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        displayNameLabel.setLayoutParams(layoutParams);
    }

    /**
     * The picked activity IS the target: the freeze flow's "launch the app" default and the
     * re-picking button do not apply here, the row only confirms what was picked. It stays
     * empty until the auto-opened picker returns.
     */
    private void prepareTargetAsReadOnly(EditText targetText, View targetButton, View targetHint) {
        targetText.setText("");
        targetText.setKeyListener(null);
        targetText.setCursorVisible(false);
        targetButton.setVisibility(View.GONE);
        targetHint.setVisibility(View.GONE);
    }

    /**
     * Generating before an activity is picked would silently produce a launch-the-app
     * shortcut, and the cancel case closes the screen instead.
     */
    private void updateActionButtons(Button generateButton, Button simulateButton, EditText targetText) {
        if (!activityShortcutMode) {
            return;
        }
        boolean picked = targetText.length() > 0;
        generateButton.setEnabled(picked);
        simulateButton.setEnabled(picked);
    }

    private void processDisplayNameEditText(String name, EditText lscaga_displayName_editText) {
        lscaga_displayName_editText.setText(name);
    }

    private void processSelectedTargetEditText(final String pkgName, EditText lscaga_target_editText) {
        lscaga_target_editText.setText(R.string.launch);
        lscaga_target_editText.setOnClickListener(v -> startSelectTargetActivityForResult(pkgName));
    }

    private void processSelectedPackageEditText(String pkgName, EditText lscaga_package_editText) {
        lscaga_package_editText.setText(pkgName);
        lscaga_package_editText.setOnClickListener(v -> startSelectPackageActivityForResult());
    }

    private void processTaskEditText(EditText lscaga_task_editText) {
        lscaga_task_editText.setText("");
    }

    private void processIDEditText(String id, EditText lscaga_id_editText) {
        lscaga_id_editText.setText(id);
    }

    private void processChangeIconImageButton(String pkgName, ImageButton lscaga_icon_imageButton) {
        int widthAndHeight = (int) (getResources().getDisplayMetrics().widthPixels * 0.35);
        if (widthAndHeight <= 0)
            widthAndHeight = 1;
        ViewGroup.LayoutParams layoutParams = lscaga_icon_imageButton.getLayoutParams();
        layoutParams.height = widthAndHeight;
        layoutParams.width = widthAndHeight;
        lscaga_icon_imageButton.setLayoutParams(layoutParams);
        if (pkgName != null) {
            switch (pkgName) {
                case "cf.playhi.freezeyou.extra.fuf":
                case "OF":
                case "UF":
                case "OO":
                case "OOU":
                case "FOQ":
                case "OS":
                case "OU":
                case "UFU":
                    finalDrawable = getResources().getDrawable(R.mipmap.ic_launcher_round);
                    break;
                case "cf.playhi.freezeyou.extra.oklock":
                    finalDrawable = getResources().getDrawable(R.drawable.screenlock);
                    break;
                default:
                    if (pkgName.startsWith("CATEGORY") || pkgName.startsWith("FORCESTOPCATEGORY")) {
                        finalDrawable = getResources().getDrawable(R.mipmap.ic_launcher_round);
                        break;
                    }
                    finalDrawable = getString(R.string.plsSelect).equals(pkgName)
                            ?
                            getResources().getDrawable(R.drawable.grid_add)
                            :
                            getApplicationIcon(
                                    this,
                                    pkgName,
                                    getApplicationInfoFromPkgName(pkgName, this),
                                    false
                            );
                    break;
            }

            lscaga_icon_imageButton.setImageDrawable(finalDrawable);
            lscaga_icon_imageButton.setOnClickListener(v ->
                    startActivityForResult(
                            new Intent(
                                    LauncherShortcutConfirmAndGenerateActivity.this,
                                    SelectShortcutIconActivity.class
                            ),
                            21
                    )
            );
        }
    }

    private void processSelectPackageButton(Button lscaga_package_button) {
        lscaga_package_button.setOnClickListener(v -> startSelectPackageActivityForResult());
    }

    private void processSelectTargetButton(final String pkgName, Button lscaga_target_button) {
        lscaga_target_button.setOnClickListener(v -> startSelectTargetActivityForResult(pkgName));
    }

    private void processGenerateButton(Button lscaga_generate_button, final EditText lscaga_package_editText, final EditText lscaga_displayName_editText, final EditText lscaga_target_editText, final EditText lscaga_id_editText, final EditText lscaga_task_editText) {

        lscaga_generate_button.setOnClickListener(v -> {
            Context context = getApplicationContext();
            String pkgName = lscaga_package_editText.getText().toString();
            String title = lscaga_displayName_editText.getText().toString();
            String target = lscaga_target_editText.getText().toString();
            String tasks = lscaga_task_editText.getText().toString();
            target = FUFUtils.normalizeSelectedTarget(this, target);
            if (requestFromLauncher) {
                Intent shortcutIntent = new Intent(LauncherShortcutConfirmAndGenerateActivity.this, Freeze.class);
                shortcutIntent.putExtra("pkgName", pkgName);
                shortcutIntent.putExtra("target", target);
                shortcutIntent.putExtra("tasks", tasks);
                shortcutIntent.putExtra("justLaunch", activityShortcutMode);
                Intent intent = new Intent();
                intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
                intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
                intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, getBitmapFromDrawable(finalDrawable));
                setResult(RESULT_OK, intent);
                finish();
            } else {
                createShortCut(
                        title,
                        pkgName,
                        finalDrawable,
                        targetSelfCls,
                        lscaga_id_editText.getText().toString(),
                        context,
                        target,
                        tasks,
                        activityShortcutMode
                );
            }
        });

    }

    private void processCancelButton(Button lscaga_cancel_button) {
        lscaga_cancel_button.setOnClickListener(v -> finish());
    }

    private void processSimulateButton(final EditText lscaga_package_editText, final EditText lscaga_target_editText, final EditText lscaga_task_editText, final Button lscaga_simulate_button) {
        lscaga_simulate_button.setOnClickListener(v -> {
            String pkgName = lscaga_package_editText.getText().toString();
            String target = lscaga_target_editText.getText().toString();
            String tasks = lscaga_task_editText.getText().toString();
            target = FUFUtils.normalizeSelectedTarget(this, target);
            startActivity(
                    new Intent(LauncherShortcutConfirmAndGenerateActivity.this, Freeze.class)
                            .putExtra("pkgName", pkgName)
                            .putExtra("target", target)
                            .putExtra("tasks", tasks)
                            .putExtra("justLaunch", activityShortcutMode)
            );
        });
    }

    private void startSelectPackageActivityForResult() {
        startActivityForResult(
                new Intent(LauncherShortcutConfirmAndGenerateActivity.this, FUFLauncherShortcutCreator.class)
                        .putExtra("returnPkgName", true),
                11);
    }

    private void startSelectTargetActivityForResult(final String pkgName) {
        try {
            // Only that the package exists. Asking for its activities here as well used to throw
            // for a package too large to cross a binder transaction — Google Play services,
            // Settings — and the picker then refused to open at all, although it knows how to
            // read those from the APK instead.
            //
            // The flag is what the rest of the application uses (see ApplicationInfoUtils): a
            // frozen package is disabled or hidden, and without it the lookup throws for exactly
            // the applications this screen is most wanted for.
            //noinspection deprecation
            getPackageManager().getApplicationInfo(
                    pkgName, PackageManager.GET_UNINSTALLED_PACKAGES);
            startActivityForResult(
                    new Intent(
                            LauncherShortcutConfirmAndGenerateActivity.this,
                            SelectTargetActivityActivity.class)
                            .putExtra("pkgName", pkgName)
                            .putExtra("activityShortcutMode", activityShortcutMode),
                    8);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            showToast(LauncherShortcutConfirmAndGenerateActivity.this, R.string.packageNotFound);
        } catch (Exception e) {
            e.printStackTrace();
            showToast(LauncherShortcutConfirmAndGenerateActivity.this,
                    R.string.failed + File.separator + e.getLocalizedMessage());
        }
    }

}

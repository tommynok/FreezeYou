package cf.playhi.freezeyou;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Intent;
import cf.playhi.freezeyou.utils.ShortcutTokenUtils;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import cf.playhi.freezeyou.app.FreezeYouBaseActivity;
import cf.playhi.freezeyou.fuf.FUFSinglePackage;
import cf.playhi.freezeyou.utils.CriticalPackagesUtils;
import cf.playhi.freezeyou.viewmodel.DialogData;
import cf.playhi.freezeyou.viewmodel.FreezeActivityViewModel;
import cf.playhi.freezeyou.viewmodel.PlayAnimatorData;

import static cf.playhi.freezeyou.app.FreezeYouAlertDialogBuilderKt.FreezeYouAlertDialogBuilder;
import static cf.playhi.freezeyou.fuf.FUFSinglePackage.ACTION_MODE_UNFREEZE;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.includeFUFActivityInRecents;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.playFUFAnimations;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.showInRecents;
import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon;
import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getBitmapFromDrawable;
import static cf.playhi.freezeyou.utils.ApplicationInfoUtils.getApplicationInfoFromPkgName;
import static cf.playhi.freezeyou.utils.ApplicationLabelUtils.getApplicationLabel;
import static cf.playhi.freezeyou.utils.FUFUtils.getFUFRelatedToastString;
import static cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme;
import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
public class Freeze extends FreezeYouBaseActivity {
    private FreezeActivityViewModel viewModel;
    private ImageView applicationIconImageView;
    private boolean mPendingGo = false;
    private boolean mIsExecuting = false;
    private final Queue<Intent> mPendingIntents = new ArrayDeque<>();
    private AlertDialog mCurrentDialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        processSetTheme(this, true);
        super.onCreate(savedInstanceState);
        if (playFUFAnimations.getValue(null)) initApplicationIconImageView();
        viewModel = new ViewModelProvider(this).get(FreezeActivityViewModel.class);
        viewModel.getPkgName().observe(this, pkgName -> {
            setUnlockLogoPkgName(pkgName);
            updateTaskDescription(pkgName);
        });
        viewModel.getToastStringId().observe(this, id -> showToast(this, id));
        viewModel.getExecuteResult().observe(this, result ->
                showToast(
                        this,
                        getFUFRelatedToastString(
                                this,
                                result.getFreezeYouFUFSinglePackage().getActionMode() == ACTION_MODE_UNFREEZE
                                        ?
                                        result.getFreezeYouFUFSinglePackage()
                                                .checkAndStartTaskAndTargetAndActivity(result.getResult())
                                        :
                                        result.getResult()
                        )
                )
        );
        viewModel.getFinishMe().observe(this, finishMe -> {
            if (finishMe) {
                finishOrProcessNext();
            }
        });
        viewModel.getPlayAnimator().observe(this, this::onPlayAnimator);
        viewModel.getShowDialog().observe(this, this::buildAndShowFUFDialog);
        viewModel.getShowUnfreezeToLaunchDialog().observe(
                this, this::buildAndShowUnfreezeToLaunchDialog);
        viewModel.loadStartedIntentAndPkgName(getIntent());
        mPendingGo = true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (mIsExecuting || mPendingGo) {
            mPendingIntents.add(intent);
        } else if (viewModel != null) {
            viewModel.loadStartedIntentAndPkgName(intent);
            mPendingGo = true;
            checkAndExecuteGo();
        }
    }

    private void dismissCurrentDialog() {
        if (mCurrentDialog != null && mCurrentDialog.isShowing()) {
            mCurrentDialog.dismiss();
            mCurrentDialog = null;
        }
    }

    private void finishOrProcessNext() {
        if (!mPendingIntents.isEmpty()) {
            Intent next = mPendingIntents.poll();
            viewModel.loadStartedIntentAndPkgName(next);
            mPendingGo = true;
            checkAndExecuteGo();
        } else {
            mIsExecuting = false;
            dismissCurrentDialog();
            finish();
        }
    }

    private void onPlayAnimator(PlayAnimatorData playAnimatorData) {
        if (applicationIconImageView == null) return;
        applicationIconImageView.setImageDrawable(
                getApplicationIcon(
                        this,
                        playAnimatorData.getPkgName(),
                        getApplicationInfoFromPkgName(playAnimatorData.getPkgName(), this),
                        false
                )
        );
        if (playAnimatorData.getFreezing()) {
            onFreezeStart();
        } else {
            onUnfreezeStart();
        }
    }

    private void updateTaskDescription(String pkgName) {
        if (Build.VERSION.SDK_INT >= 21) {
            setTaskDescription(
                    new ActivityManager.TaskDescription(
                            getApplicationLabel(
                                    this,
                                    null,
                                    null,
                                    pkgName
                            )
                                    + " - "
                                    + getString(R.string.app_name),
                            getBitmapFromDrawable(
                                    getApplicationIcon(
                                            this,
                                            pkgName,
                                            getApplicationInfoFromPkgName(
                                                    pkgName,
                                                    this
                                             ),
                                            false
                                    )
                            )
                    )
            );
        }
    }

    private void initApplicationIconImageView() {
        getWindow().setBackgroundDrawable(new ColorDrawable(0));
        ImageView imageView = new ImageView(this);
        imageView.setBackgroundColor(getResources().getColor(android.R.color.transparent));
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(480, 480);
        imageView.setLayoutParams(layoutParams);
        ((FrameLayout) findViewById(android.R.id.content)).addView(imageView);
        applicationIconImageView = imageView;
    }

    private void onUnfreezeStart() {
        long animDuration = viewModel.getAnimationDurationMillis();
        ObjectAnimator fadeAnim = ObjectAnimator.ofFloat(
                applicationIconImageView, "alpha", 0.2f, 1f);
        fadeAnim.setDuration(animDuration);
        fadeAnim.setInterpolator(new DecelerateInterpolator());
        ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(
                applicationIconImageView, View.SCALE_X, 0.6f, 1f);
        scaleXAnim.setDuration(animDuration);
        scaleXAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(
                applicationIconImageView, View.SCALE_Y, 0.6f, 1f);
        scaleYAnim.setDuration(animDuration);
        scaleYAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        AnimatorSet animatorSet = new AnimatorSet();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            ValueAnimator greyAnim = ValueAnimator.ofFloat(0f, 1f);
            greyAnim.addUpdateListener(animation -> {
                float animatedValue = (float) animation.getAnimatedValue();
                ColorMatrix matrix = new ColorMatrix();
                matrix.setSaturation(animatedValue);
                applicationIconImageView.setColorFilter(new ColorMatrixColorFilter(matrix));
            });
            greyAnim.setDuration(animDuration);
            greyAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            animatorSet.play(greyAnim).with(fadeAnim).with(scaleXAnim).with(scaleYAnim);
        } else {
            animatorSet.play(fadeAnim).with(scaleXAnim).with(scaleYAnim);
        }
        animatorSet.start();
    }

    private void onFreezeStart() {
        long animDuration = viewModel.getAnimationDurationMillis();
        ObjectAnimator fadeAnim = ObjectAnimator.ofFloat(
                applicationIconImageView, "alpha", 1f, 0f);
        fadeAnim.setDuration(animDuration);
        fadeAnim.setInterpolator(new DecelerateInterpolator());
        ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(
                applicationIconImageView, View.SCALE_X, 1f, 0.6f);
        scaleXAnim.setDuration(animDuration);
        scaleXAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(
                applicationIconImageView, View.SCALE_Y, 1f, 0.6f);
        scaleYAnim.setDuration(animDuration);
        scaleYAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        AnimatorSet animatorSet = new AnimatorSet();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            ValueAnimator greyAnim = ValueAnimator.ofFloat(1f, 0f);
            greyAnim.addUpdateListener(animation -> {
                float animatedValue = (float) animation.getAnimatedValue();
                ColorMatrix matrix = new ColorMatrix();
                matrix.setSaturation(animatedValue);
                applicationIconImageView.setColorFilter(new ColorMatrixColorFilter(matrix));
            });
            greyAnim.setDuration(animDuration);
            greyAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            animatorSet.play(greyAnim).with(fadeAnim).with(scaleXAnim).with(scaleYAnim);
        } else {
            animatorSet.play(fadeAnim).with(scaleXAnim).with(scaleYAnim);
        }
        animatorSet.start();
    }

    private void checkAndExecuteGo() {
        if (mPendingGo && viewModel != null) {
            if (isLocked()) {
                checkAppLock();
                return;
            }
            mPendingGo = false;
            mIsExecuting = true;
            viewModel.go();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAndExecuteGo();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isLocked()) {
            dismissCurrentDialog();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissCurrentDialog();
    }

    @Override
    public void finish() {
        if (Build.VERSION.SDK_INT >= 21
                &&
                (!showInRecents.getValue(null)
                        || !includeFUFActivityInRecents.getValue(null))) {
            finishAndRemoveTask();
        } else {
            super.finish();
        }
    }

    /**
     * An activity shortcut aimed at a frozen application. Two buttons and three lines: the offer,
     * what it leaves behind, and where to go for a shortcut that can freeze back. The name of that
     * other shortcut is taken from its own menu entry, so the two cannot end up calling it
     * different things.
     */
    private void buildAndShowUnfreezeToLaunchDialog(DialogData data) {
        if (data == null) return;
        dismissCurrentDialog();
        mCurrentDialog = FreezeYouAlertDialogBuilder(this)
                .setIcon(
                        getApplicationIcon(
                                this,
                                data.getPkgName(),
                                getApplicationInfoFromPkgName(data.getPkgName(), this),
                                true
                        )
                )
                .setTitle(getApplicationLabel(this, null, null, data.getPkgName()))
                .setMessage(
                        getString(
                                R.string.unfreezeAndLaunchActivity,
                                getString(R.string.createDisEnableShortCut)
                        )
                )
                .setPositiveButton(R.string.unfreeze, (dialogInterface, i) ->
                        viewModel.fufAction(
                                data.getPkgName(), data.getTarget(), data.getTasks(),
                                true, true
                        )
                )
                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> finishOrProcessNext())
                .setOnCancelListener(dialogInterface -> finishOrProcessNext())
                .show();
    }

    private void buildAndShowFUFDialog(DialogData data) {
        dismissCurrentDialog();
        AlertDialog.Builder builder = FreezeYouAlertDialogBuilder(this)
                .setIcon(
                        getApplicationIcon(
                                this,
                                data.getPkgName(),
                                getApplicationInfoFromPkgName(data.getPkgName(), this),
                                true
                        )
                )
                .setMessage(getString(R.string.chooseDetailAction))
                .setTitle(
                        getApplicationLabel(
                                this,
                                null,
                                null,
                                data.getPkgName()
                        )
                )
                .setNeutralButton(R.string.cancel, (dialogInterface, i) -> finishOrProcessNext())
                .setOnCancelListener(dialogInterface -> finishOrProcessNext());
        if (data.getFrozen()) {
            builder.setPositiveButton(R.string.unfreeze, (dialogInterface, i) ->
                    viewModel.fufAction(
                            data.getPkgName(), data.getTarget(), data.getTasks(),
                            false, true
                    )
            );
        } else {
            builder.setPositiveButton(R.string.launch, (dialogInterface, i) -> {
                int result = viewModel.checkAndStartTaskAndTargetAndActivityOfUnfrozenApp(data);
                if (result != FUFSinglePackage.ERROR_NO_ERROR_SUCCESS
                        && result != FUFSinglePackage.ERROR_NO_ERROR_CAUGHT_UNKNOWN_RESULT) {
                    showToast(this, getFUFRelatedToastString(this, result));
                }
                finishOrProcessNext();
            });
            builder.setNegativeButton(R.string.freeze, (dialogInterface, i) ->
                    confirmIfCriticalThenFreeze(data)
            );
        }
        mCurrentDialog = builder.show();
    }

    /**
     * Freezing one application is as hard to undo as freezing it inside a bulk selection: the
     * status bar, Settings or the keyboard go away the same way. The bulk path asks first, so
     * this asks too, with the same warning and with "Cancel" leaving the application untouched.
     */
    private void confirmIfCriticalThenFreeze(DialogData data) {
        final List<String> critical = CriticalPackagesUtils.findCriticalPackages(
                this, Collections.singletonList(data.getPkgName()));
        if (!needsCriticalFreezeConfirmation(data.getFrozen(), !critical.isEmpty())) {
            runFreeze(data);
            return;
        }
        dismissCurrentDialog();
        mCurrentDialog = FreezeYouAlertDialogBuilder(this)
                .setTitle(R.string.caution)
                .setMessage(getString(
                        R.string.critical_package_freeze_warning_single,
                        criticalPackageWarningLine(data.getPkgName())))
                .setPositiveButton(R.string.okay, (dialogInterface, i) -> runFreeze(data))
                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> finishOrProcessNext())
                .setOnCancelListener(dialogInterface -> finishOrProcessNext())
                .show();
    }

    private void runFreeze(DialogData data) {
        viewModel.fufAction(
                data.getPkgName(), data.getTarget(), data.getTasks(),
                false, false
        );
    }

    /** The same "• Label (package)" line the bulk warning builds, so the two read identically. */
    private String criticalPackageWarningLine(String pkgName) {
        return "• " + getApplicationLabel(this, null, null, pkgName) + " (" + pkgName + ")";
    }

    /**
     * Only a freeze is asked about. Unfreezing a critical package is the way back out of a device
     * with no status bar or keyboard, so putting a confirmation in front of it — or, worse, in
     * front of the launch shortcut that unfreezes first — could be the thing that keeps a user
     * locked out.
     */
    static boolean needsCriticalFreezeConfirmation(boolean frozen, boolean hasCriticalPackage) {
        return !frozen && hasCriticalPackage;
    }
}

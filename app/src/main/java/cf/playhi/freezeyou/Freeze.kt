package cf.playhi.freezeyou

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.lifecycle.ViewModelProvider
import cf.playhi.freezeyou.app.FreezeYouAlertDialogBuilder
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.fuf.FUFSinglePackage
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.ACTION_MODE_UNFREEZE
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.*
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getBitmapFromDrawable
import cf.playhi.freezeyou.utils.ApplicationInfoUtils.getApplicationInfoFromPkgName
import cf.playhi.freezeyou.utils.ApplicationLabelUtils.getApplicationLabel
import cf.playhi.freezeyou.utils.FUFUtils.getFUFRelatedToastString
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import cf.playhi.freezeyou.viewmodel.DialogData
import cf.playhi.freezeyou.viewmodel.FreezeActivityViewModel
import cf.playhi.freezeyou.viewmodel.PlayAnimatorData

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
class Freeze : FreezeYouBaseActivity() {
    private var viewModel: FreezeActivityViewModel? = null
    private var applicationIconImageView: ImageView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this, true)
        super.onCreate(savedInstanceState)
        if (playFUFAnimations.getValue(null)) initApplicationIconImageView()
        viewModel = ViewModelProvider(this).get(FreezeActivityViewModel::class.java)
        viewModel!!.getPkgName().observe(this) { pkgName: String? ->
            setUnlockLogoPkgName(pkgName)
            updateTaskDescription(pkgName)
        }
        viewModel!!.getToastStringId().observe(this) { id: Int ->
            showToast(
                this,
                id
            )
        }
        viewModel!!.getExecuteResult().observe(this) { result ->
            showToast(
                this,
                getFUFRelatedToastString(
                    this,
                    if (result.freezeYouFUFSinglePackage.actionMode == ACTION_MODE_UNFREEZE) result.freezeYouFUFSinglePackage
                        .checkAndStartTaskAndTargetAndActivity(result.result) else result.result
                )
            )
        }
        viewModel!!.getFinishMe().observe(this) { finishMe: Boolean ->
            if (finishMe) finish()
        }
        viewModel!!.getPlayAnimator().observe(
            this
        ) { playAnimatorData: PlayAnimatorData? -> onPlayAnimator(playAnimatorData!!) }
        viewModel!!.getShowDialog().observe(
            this
        ) { data: DialogData? -> buildAndShowFUFDialog(data!!) }
        viewModel!!.loadStartedIntentAndPkgName(intent)
    }

    private fun onPlayAnimator(playAnimatorData: PlayAnimatorData) {
        if (applicationIconImageView == null) return
        applicationIconImageView!!.setImageDrawable(
            getApplicationIcon(
                this,
                playAnimatorData.pkgName,
                getApplicationInfoFromPkgName(playAnimatorData.pkgName, this),
                false
            )
        )
        if (playAnimatorData.freezing) {
            onFreezeStart()
        } else {
            onUnfreezeStart()
        }
    }

    private fun updateTaskDescription(pkgName: String?) {
        @Suppress("DEPRECATION")
        setTaskDescription(
            ActivityManager.TaskDescription(
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
                        pkgName!!,
                        getApplicationInfoFromPkgName(
                            pkgName,
                            this
                        ),
                        false
                    )
                )
            )
        )
    }

    private fun initApplicationIconImageView() {
        window.setBackgroundDrawable(ColorDrawable(0))
        val imageView = ImageView(this)
        @Suppress("DEPRECATION")
        imageView.setBackgroundColor(resources.getColor(android.R.color.transparent))
        val layoutParams = ViewGroup.LayoutParams(480, 480)
        imageView.layoutParams = layoutParams
        (findViewById<View>(android.R.id.content) as FrameLayout).addView(imageView)
        applicationIconImageView = imageView
    }

    private fun onUnfreezeStart() {
        val animDuration = viewModel!!.getAverageTimeCosts()
        val fadeAnim = ObjectAnimator.ofFloat(
            applicationIconImageView, "alpha", 0.2f, 1f
        )
        fadeAnim.duration = animDuration
        fadeAnim.interpolator = DecelerateInterpolator()
        val scaleXAnim = ObjectAnimator.ofFloat(
            applicationIconImageView, View.SCALE_X, 0.6f, 1f
        )
        scaleXAnim.duration = animDuration
        scaleXAnim.interpolator = AccelerateDecelerateInterpolator()
        val scaleYAnim = ObjectAnimator.ofFloat(
            applicationIconImageView, View.SCALE_Y, 0.6f, 1f
        )
        scaleYAnim.duration = animDuration
        scaleYAnim.interpolator = AccelerateDecelerateInterpolator()
        val animatorSet = AnimatorSet()
        val greyAnim = ValueAnimator.ofFloat(0f, 1f)
        greyAnim.addUpdateListener { animation: ValueAnimator ->
            val animatedValue = animation.animatedValue as Float
            val matrix = ColorMatrix()
            matrix.setSaturation(animatedValue)
            applicationIconImageView!!.colorFilter = ColorMatrixColorFilter(matrix)
        }
        greyAnim.duration = animDuration
        greyAnim.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.play(greyAnim).with(fadeAnim).with(scaleXAnim).with(scaleYAnim)
        animatorSet.start()
    }

    private fun onFreezeStart() {
        val animDuration = viewModel!!.getAverageTimeCosts()
        val fadeAnim = ObjectAnimator.ofFloat(
            applicationIconImageView, "alpha", 1f, 0f
        )
        fadeAnim.duration = animDuration
        fadeAnim.interpolator = DecelerateInterpolator()
        val scaleXAnim = ObjectAnimator.ofFloat(
            applicationIconImageView, View.SCALE_X, 1f, 0.6f
        )
        scaleXAnim.duration = animDuration
        scaleXAnim.interpolator = AccelerateDecelerateInterpolator()
        val scaleYAnim = ObjectAnimator.ofFloat(
            applicationIconImageView, View.SCALE_Y, 1f, 0.6f
        )
        scaleYAnim.duration = animDuration
        scaleYAnim.interpolator = AccelerateDecelerateInterpolator()
        val animatorSet = AnimatorSet()
        val greyAnim = ValueAnimator.ofFloat(1f, 0f)
        greyAnim.addUpdateListener { animation: ValueAnimator ->
            val animatedValue = animation.animatedValue as Float
            val matrix = ColorMatrix()
            matrix.setSaturation(animatedValue)
            applicationIconImageView!!.colorFilter = ColorMatrixColorFilter(matrix)
        }
        greyAnim.duration = animDuration
        greyAnim.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.play(greyAnim).with(fadeAnim).with(scaleXAnim).with(scaleYAnim)
        animatorSet.start()
    }

    override fun onResume() {
        super.onResume()
        if (!isLocked) viewModel!!.go()
    }

    override fun onPause() {
        super.onPause()
        if (!isLocked) finish()
    }

    override fun finish() {
        if (!showInRecents.getValue(null)
            || !includeFUFActivityInRecents.getValue(null)
        ) {
            finishAndRemoveTask()
        } else {
            super.finish()
        }
    }

    private fun buildAndShowFUFDialog(data: DialogData) {
        val builder = FreezeYouAlertDialogBuilder(this)
            .setIcon(
                getApplicationIcon(
                    this,
                    data.pkgName,
                    getApplicationInfoFromPkgName(data.pkgName, this),
                    true
                )
            )
            .setMessage(getString(R.string.chooseDetailAction))
            .setTitle(
                getApplicationLabel(
                    this,
                    null,
                    null,
                    data.pkgName
                )
            )
            .setNeutralButton(R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
        if (data.frozen) {
            builder.setPositiveButton(
                R.string.unfreeze
            ) { _, _ ->
                viewModel!!.fufAction(
                    data.pkgName, data.target, data.tasks,
                    false, true
                )
            }
        } else {
            builder.setPositiveButton(
                R.string.launch
            ) { _, _ ->
                val result = viewModel!!.checkAndStartTaskAndTargetAndActivityOfUnfrozenApp(data)
                if (result != FUFSinglePackage.ERROR_NO_ERROR_SUCCESS
                    && result != FUFSinglePackage.ERROR_NO_ERROR_CAUGHT_UNKNOWN_RESULT
                ) {
                    showToast(this, getFUFRelatedToastString(this, result))
                }
                finish()
            }
            builder.setNegativeButton(
                R.string.freeze
            ) { _, _ ->
                viewModel!!.fufAction(
                    data.pkgName, data.target, data.tasks,
                    false, false
                )
            }
        }
        builder.show()
    }
}

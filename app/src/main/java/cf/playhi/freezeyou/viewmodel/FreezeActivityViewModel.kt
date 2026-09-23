package cf.playhi.freezeyou.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.ACTION_MODE_FREEZE
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.ACTION_MODE_UNFREEZE
import cf.playhi.freezeyou.fuf.FreezeYouFUFSinglePackage
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.*
import cf.playhi.freezeyou.utils.FUFUtils.realGetFrozenStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FreezeActivityViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val ANIMATION_DURATION_MILLIS = 450L
    }

    private lateinit var mStartedIntent: Intent
    private var mIsFromShortcut = false
    private var mTarget: String? = null
    private var mTasks: String? = null

    /**
     * An activity shortcut: start the target component and nothing else. It cannot be encoded in
     * [mTarget] the way `@onlyUnfreeze` is, because for this kind of shortcut the target holds the
     * class name to start.
     */
    private var mJustLaunch = false
    private var mPkgName: MutableLiveData<String> =
        MutableLiveData(getApplication<Application>().packageName)
    private var mToastStringId: MutableLiveData<Int> = MutableLiveData()
    private var mFinishMe: MutableLiveData<Boolean> = MutableLiveData(false)
    private var mShowDialog: MutableLiveData<DialogData?> = MutableLiveData()
    private var mPlayAnimator: MutableLiveData<PlayAnimatorData?> = MutableLiveData()
    private var mExecuteResult: MutableLiveData<ExecuteResult> = MutableLiveData()

    fun getPkgName(): LiveData<String> {
        return mPkgName
    }

    fun getToastStringId(): LiveData<Int> {
        return mToastStringId
    }

    fun getFinishMe(): LiveData<Boolean> {
        return mFinishMe
    }

    fun getShowDialog(): LiveData<DialogData?> {
        return mShowDialog
    }

    fun getPlayAnimator(): LiveData<PlayAnimatorData?> {
        return mPlayAnimator
    }

    fun getExecuteResult(): LiveData<ExecuteResult> {
        return mExecuteResult
    }

    /**
     * How long the freeze and unfreeze animation runs.
     *
     * It used to be the measured average of the last five operations, back when an operation took
     * about half a second and the animation could simply last as long as the work did. Now that a
     * freeze is one binder call, that average collapsed to its 200 ms floor and the animation
     * became a blink — so the animation has its own duration, and the activity waits for it.
     */
    fun getAnimationDurationMillis(): Long = ANIMATION_DURATION_MILLIS

    fun loadStartedIntentAndPkgName(startedIntent: Intent) {
        mStartedIntent = startedIntent
        if ("freezeyou" == mStartedIntent.scheme) {
            val dataUri = mStartedIntent.data
            dataUri?.run { getQueryParameter("pkgName")?.let { mPkgName.value = it } }
            mIsFromShortcut = false
        } else {
            mStartedIntent.getStringExtra("pkgName")?.let { mPkgName.value = it }
            mIsFromShortcut = mStartedIntent.getBooleanExtra("fromShortcut", true)
        }
        mTarget = mStartedIntent.getStringExtra("target")
        mTasks = mStartedIntent.getStringExtra("tasks")
        mJustLaunch = mStartedIntent.getBooleanExtra("justLaunch", false)
    }

    fun go() {
        mPkgName.value.let {
            val target = mTarget
            val tasks = mTasks
            if (it.isNullOrEmpty() || it == getApplication<Application>().packageName) {
                mToastStringId.value = R.string.invalidArguments
                mFinishMe.value = true
                return
            }
            val frozen = realGetFrozenStatus(getApplication(), it, null)
            if (mJustLaunch) {
                // A frozen package is disabled or hidden in the package manager, and none of its
                // components can be started — not even with root. Saying so beats a launch that
                // appears to do nothing.
                if (frozen) {
                    mToastStringId.value = R.string.cannotLaunchFrozenApplication
                } else {
                    checkAndStartTaskAndTargetAndActivityOfUnfrozenApp(it, target, tasks)
                }
                mFinishMe.value = true
                return
            }
            if (mIsFromShortcut && shortcutAutoFUF.getValue()) {
                if (frozen) {
                    fufAction(
                        it,
                        target,
                        tasks,
                        openImmediatelyAfterUnfreezeUseShortcutAutoFUF.getValue(),
                        true
                    )
                } else {
                    if (needConfirmWhenFreezeUseShortcutAutoFUF.getValue()) {
                        checkOpenAndUFImmediately(
                            it,
                            target,
                            tasks,
                            frozen = false,
                            ignoreAutoRun = true
                        )
                    } else {
                        fufAction(it, target, tasks, runImmediately = false, frozen = false)
                    }
                }
            } else {
                checkOpenAndUFImmediately(
                    it,
                    target,
                    tasks,
                    frozen,
                    !mIsFromShortcut
                )
            }
        }
    }

    private fun checkOpenAndUFImmediately(
        pkgName: String,
        target: String?,
        tasks: String?,
        frozen: Boolean,
        ignoreAutoRun: Boolean
    ) {
        if (!ignoreAutoRun && openAndUFImmediately.getValue()) {
            if (frozen) {
                fufAction(pkgName, target, tasks, runImmediately = true, frozen = true)
            } else {
                checkAndStartTaskAndTargetAndActivityOfUnfrozenApp(pkgName, target, tasks)
                mFinishMe.postValue(true)
            }
        } else {
            mShowDialog.value = DialogData(pkgName, target, tasks, frozen, true)
        }
    }

    fun fufAction(
        pkgName: String,
        target: String?,
        tasks: String?,
        runImmediately: Boolean,
        frozen: Boolean
    ) {
        mPlayAnimator.value = PlayAnimatorData(pkgName, !frozen)
        viewModelScope.launch(Dispatchers.IO) {
            val freezeYouFUFSinglePackage = FreezeYouFUFSinglePackage(
                getApplication(),
                pkgName,
                if (frozen) ACTION_MODE_UNFREEZE else ACTION_MODE_FREEZE,
                needAskRun = frozen,
                runImmediately = runImmediately,
                tasks = tasks,
                target = target
            )
            val result = freezeYouFUFSinglePackage.commit()
            mExecuteResult.postValue(ExecuteResult(result, freezeYouFUFSinglePackage))
            mFinishMe.postValue(true)
        }
    }

    fun checkAndStartTaskAndTargetAndActivityOfUnfrozenApp(data: DialogData): Int {
        return checkAndStartTaskAndTargetAndActivityOfUnfrozenApp(
            data.pkgName,
            data.target,
            data.tasks
        )
    }

    private fun checkAndStartTaskAndTargetAndActivityOfUnfrozenApp(
        pkgName: String,
        target: String?,
        tasks: String?
    ): Int {
        return FreezeYouFUFSinglePackage.checkAndStartTaskAndTargetAndActivityOfUnfrozenApp(
            getApplication(), pkgName, target, tasks
        )
    }

}

data class DialogData(
    val pkgName: String,
    val target: String?,
    val tasks: String?,
    val frozen: Boolean,
    var show: Boolean
)

data class PlayAnimatorData(
    val pkgName: String,
    val freezing: Boolean
)

data class ExecuteResult(
    val result: Int,
    val freezeYouFUFSinglePackage: FreezeYouFUFSinglePackage
)

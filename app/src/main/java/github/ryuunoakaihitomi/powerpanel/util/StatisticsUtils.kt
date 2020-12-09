package github.ryuunoakaihitomi.powerpanel.util

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.baidu.mobstat.StatService
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.oasisfeng.condom.CondomContext
import github.ryuunoakaihitomi.powerpanel.BuildConfig
import github.ryuunoakaihitomi.powerpanel.MyApplication
import timber.log.Timber
import java.util.*

/**
 * 统计工具类，**为方便迁移请务必在此处创建接口并且不在此类外直接调用统计SDK**
 *
 * 目前使用的是`Firebase`和`百度移动统计`
 */
object StatisticsUtils {

    /* 记录电源操作，评估万一某个功能有bug所带来的影响范围 */
    private const val EVENT_PWR_OP = "power_operation"
    private const val KEY_SRC = "source"
    private const val KEY_TIME_HOUR = "time_hour"
    private const val KEY_TYPE = "type"
    private const val KEY_FORCE_MODE = "force_mode"
    private const val KEY_DONE = "done"

    /* 记录特权模式二次操作取消，这是之前添加广告的地方，现在评估一下此处受众占比 */
    private const val EVENT_DIALOG_CANCEL = "dialog_cancel"
    private const val KEY_CANCELLED = "cancelled"

    fun logPowerOperation(
        activity: AppCompatActivity,
        @StringRes labelResId: Int,
        forceMode: Boolean,
        done: Boolean
    ) {
        val bundle = Bundle().apply {
            /* 包名不一致需要自行缩短本地类名 */
            val split = activity.localClassName.split('.')
            putString(KEY_SRC, split[split.size - 1])
            // 作为数字，也就是用putInt()，在Events面板只能看到平均值和总数
            putString(KEY_TIME_HOUR, Calendar.getInstance()[Calendar.HOUR_OF_DAY].toString())
            putString(KEY_TYPE, labelResId.toLabel())
            /* FirebaseAnalytics不支持Boolean (String, long and double param types are supported.) */
            putString(KEY_FORCE_MODE, forceMode.toString())
            putString(KEY_DONE, done.toString())
        }
        Timber.i("$bundle")
        logEvent(EVENT_PWR_OP, bundle)
    }

    fun logDialogCancel(@StringRes labelResId: Int, cancelled: Boolean) {
        val bundle = Bundle().apply {
            putString(KEY_TYPE, labelResId.toLabel())
            putString(KEY_CANCELLED, cancelled.toString())
        }
        Timber.i(bundle.toString())
        logEvent(EVENT_DIALOG_CANCEL, bundle)
    }

    fun recordEnvInfo() {
        arrayOf(Build::class, Build.VERSION::class).forEach { clz ->
            clz.java.fields.forEach {
                @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                setCustomKey("${clz.simpleName} ${it.name}", it[null])
            }
        }
    }

    fun log(level: String, tag: String, msg: String) {
        val logLine = listOf(level, tag, msg).toString()
        Firebase.crashlytics.log(logLine)
    }

    /**
     * 手动初始化
     */
    fun initialize(context: Context) {
        val lang = Locale.getDefault().toString()
        Timber.d("language = $lang")

        /* 百度移动统计，由于找不到隐私声明，仅在简体中文环境下加载 */
        if (lang.contains("zh_CN")) {
            Timber.i("Load baidu mob stat sdk for Chinese lang env.")
            loadBaiduMobileStatisticSdk(context)
        }
    }

    private fun loadBaiduMobileStatisticSdk(baseContext: Context) {
        val context = CondomContext.wrap(baseContext, "BaiduMobStat")

        StatService.setAppKey(BuildConfig.BaiduMobAd_STAT_ID)
        StatService.setDebugOn(BuildConfig.DEBUG)
        StatService.setSessionTimeOut(3)    // 大多数情况下3秒即可完成一次操作
        StatService.enableDeviceMac(context, false)
        StatService.setAuthorizedState(context, true)

        StatService.start(context)
    }

    private fun setCustomKey(key: String, value: Any) {
        Firebase.crashlytics.apply {
            when (value) {
                is String -> setCustomKey(key, value)
                is Boolean -> setCustomKey(key, value)
                is Int -> setCustomKey(key, value)
                is Long -> setCustomKey(key, value)
                is Float -> setCustomKey(key, value)
                is Double -> setCustomKey(key, value)
                is Array<*> -> setCustomKey(key, value.contentToString())
                else -> Timber.w("Undefined type: $key, $value")
            }
        }
    }

    private fun logEvent(string: String, bundle: Bundle) {
        Firebase.analytics.logEvent(string, bundle)
        Firebase.crashlytics.setCustomKey(string, bundle.toString())
    }

    private fun Int.toLabel() = MyApplication.getInstance().resources.getResourceEntryName(this)
}
package github.ryuunoakaihitomi.powerpanel.ui.main

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.AdapterView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import es.dmoral.toasty.Toasty
import github.ryuunoakaihitomi.powerpanel.BuildConfig
import github.ryuunoakaihitomi.powerpanel.R
import github.ryuunoakaihitomi.powerpanel.desc.PowerExecution
import github.ryuunoakaihitomi.powerpanel.desc.getIconResIdArray
import github.ryuunoakaihitomi.powerpanel.desc.getLabelArray
import github.ryuunoakaihitomi.powerpanel.stat.Statistics
import github.ryuunoakaihitomi.powerpanel.ui.DonateActivity
import github.ryuunoakaihitomi.powerpanel.ui.OpenSourceLibDependencyActivity
import github.ryuunoakaihitomi.powerpanel.ui.ShortcutActivity
import github.ryuunoakaihitomi.powerpanel.ui.tile.CmCustomTileService
import github.ryuunoakaihitomi.powerpanel.util.*
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import org.apache.commons.io.IOUtils
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.nio.charset.Charset
import java.util.*

// 窗口透明度
private const val DIALOG_ALPHA = 0.85f

// 项目链接
private const val SOURCE_LINK = "https://github.com/ryuunoakaihitomi/rebootmenu"

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeTransparent()
        Timber.v("start!")
        var mainDialog = AlertDialog.Builder(this).create()
        val powerViewModel = ViewModelProvider(this)[PowerViewModel::class.java]
        powerViewModel.labelResId.observe(this) {
            PowerExecution.execute(it, this, powerViewModel.getForceMode())
        }
        powerViewModel.shortcutInfoArray.observe(this) { it ->
            ShortcutManagerCompat.removeAllDynamicShortcuts(this)
            val maxCount = ShortcutManagerCompat.getMaxShortcutCountPerActivity(this)
            if (maxCount >= it.size) {
                Timber.d("Allow app shortcut. ${it.size} in $maxCount")
                it.forEach {
                    val unspannedLabel = it.label.toString()
                    val shortcut = ShortcutInfoCompat.Builder(this, unspannedLabel).run {
                        setShortLabel(unspannedLabel)
                        setIcon(IconCompat.createWithResource(applicationContext, it.iconResId))
                        setIntent(ShortcutActivity.getActionIntent(it.labelResId))
                        build()
                    }
                    ShortcutManagerCompat.addDynamicShortcuts(this, listOf(shortcut))
                }
            }
        }
        powerViewModel.infoArray.observe(this) {
            val rootMode = powerViewModel.rootMode.value ?: false
            /* 特权模式下主动请求Shizuku授权，在受限模式下PowerAct已经处理好这个步骤 */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                rootMode && Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
            ) {
                Timber.i("Request shizuku permission.")
                Shizuku.requestPermission(0)
            }
            PowerActHelper.toggleExposedComponents(application, !rootMode)   // 为CMTile提前更新外置组件可见性
            CmCustomTileService.start()
            mainDialog = AlertDialog.Builder(this).apply {
                setTitle(powerViewModel.title.value)
                setAdapter(
                    PowerItemAdapter(this@MainActivity, it.getLabelArray(), it.getIconResIdArray())
                ) { dialog, which ->
                    val item = it[which]
                    if (powerViewModel.shouldConfirmAgain(item)) {
                        setTitle(
                            String.format(getString(R.string.title_dialog_confirm_op), item.label)
                        )
                        setAdapter(null, null)
                        setItems(
                            arrayOf(
                                resources.getText(android.R.string.ok).emphasize(),
                                resources.getText(android.R.string.cancel).emphasize()
                            )
                        ) { _, confirmWhich ->
                            val ok = confirmWhich == 0
                            Statistics.logDialogCancel(item.labelResId, ok.not())
                            if (ok) {
                                powerViewModel.call(item.labelResId)
                                // dismiss防止窗口泄露
                                dialog.dismiss()
                            } else {
                                powerViewModel.prepare()
                            }
                        }
                        setOnCancelListener {
                            Statistics.logDialogCancel(item.labelResId, true)
                            powerViewModel.prepare()
                        }
                        setNeutralButton(null, null)
                        setPositiveButton(null, null)
                        show().listView.rootView.emptyAccessibilityDelegate()
                    } else {
                        powerViewModel.call(item.labelResId)
                        dialog.dismiss()
                    }
                }
                if (rootMode) {
                    setNeutralButton(R.string.btn_dialog_switch_mode) { _, _ -> powerViewModel.reverseForceMode() }
                }
                setPositiveButton(R.string.btn_dialog_help) { _, _ ->
                    /* --- 帮助界面逻辑 ---*/
                    setTitle(R.string.title_dialog_help_info)
                    // 准备使用下面的MarkDown组件
                    setMessage("placeholder")
                    setPositiveButton(R.string.btn_dialog_source_code) { _, _ ->
                        openUrlInBrowser(SOURCE_LINK)
                        finish()
                    }
                    setNegativeButton(R.string.donate) { _, _ -> teleport<DonateActivity>() }
                    setNeutralButton(R.string.open_source_lib_dependency) { _, _ ->
                        teleport<OpenSourceLibDependencyActivity>()
                    }
                    setOnCancelListener { powerViewModel.prepare() }
                    /* 主要信息 */
                    val alertDialogMessageView = BlackMagic.getAlertDialogMessageView(show())
                    Markwon.builder(this@MainActivity)
                        .usePlugin(StrikethroughPlugin.create())
                        .build()
                        .setMarkdown(
                            alertDialogMessageView,
                            IOUtils.toString(
                                resources.openRawResource(R.raw.help),
                                Charset.defaultCharset()
                            )
                        )
                    // 不能设置为可选择，否则无法点击打开链接
                    //alertDialogMessageView.setTextIsSelectable(true)
                    Toasty.normal(
                        this@MainActivity,
                        """
                            ${BuildConfig.VERSION_NAME}
                            ${BuildConfig.VERSION_CODE}
                        """.trimIndent(),
                        Toasty.LENGTH_LONG
                    ).show()
                }
                setOnCancelListener { finish() }
            }.show()
            mainDialog.listView.onItemLongClickListener =
                AdapterView.OnItemLongClickListener { _, _, position, _ ->
                    val item = it[position]
                    RC.getDrawable(resources, item.iconResId, null)?.run {
                        // 注意：必须使用mutate()保持Drawable独立性以修复无法变色的Bug
                        mutate().run {
                            // 如果是强制模式，为了便于区分，改变shortcut图标颜色
                            if (powerViewModel.isOnForceMode(item)) {
                                setTint(
                                    RC.getColor(resources, R.color.colorIconForceModeShortcut, null)
                                )
                            }
                            val unspannedLabel = item.label.toString()
                            val shortcut = ShortcutInfoCompat.Builder(
                                applicationContext,
                                // 使用UUID有两种后果：可以重复添加Icon，修复图标无法变色的bug
                                UUID.randomUUID().toString()
                            ).run {
                                setShortLabel(unspannedLabel)
                                setLongLabel(unspannedLabel)
                                setIcon(IconCompat.createWithBitmap(toBitmap()))
                                setIntent(
                                    ShortcutActivity.getActionIntent(
                                        item.labelResId,
                                        powerViewModel.getForceMode()
                                    )
                                )
                                build()
                            }
                            ShortcutManagerCompat.requestPinShortcut(application, shortcut, null)
                        }
                    }
                    // 在Android8.0以下和一些自定义系统（自动拒绝）可能没有反馈
                    Toasty.info(this, R.string.toast_shortcut_added).show()
                    return@OnItemLongClickListener true
                }
            mainDialog.window?.decorView?.run {
                // 半透明度
                alpha = DIALOG_ALPHA
                emptyAccessibilityDelegate()
            }
        }
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                mainDialog.dismiss()
            }
        })
        powerViewModel.prepare()
    }
}
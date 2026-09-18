package com.tingbili.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * 后台保活的系统侧开关。
 *
 * 为什么需要它：Android 的 Doze + 各厂商省电策略会在息屏/退后台一段时间后冻结甚至清杀
 * 应用进程，前台播放服务也保不住。唯一由 App 自己能做到的官方手段是申请把自身
 * 加入"电池优化白名单"（Doze 豁免）；国产 ROM（MIUI / EMUI / ColorOS / OriginOS）
 * 还额外需要用户在应用详情里手动允许"自启动 / 后台运行"。
 */
object BatteryOptimization {

    private const val TAG = "BatteryOptimization"

    /** 是否已在电池优化白名单里（Doze 豁免） */
    fun isIgnoring(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)
    }

    /**
     * 弹系统"是否允许忽略电池优化"确认框。
     * 少数 ROM 阉割了这个页面，失败时退回应用详情页，至少让用户能手动开自启动。
     */
    fun requestIgnore(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "打开电池优化白名单失败：${it.message}") }
            .isSuccess
        if (!ok) openAppDetails(context)
    }

    /** 应用详情页：国产 ROM 的"自启动 / 后台运行 / 省电策略"都在这后面 */
    fun openAppDetails(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "打开应用详情页失败：${it.message}") }
    }
}

package app.pickup.notification

import android.app.PendingIntent
import android.content.Context
import android.database.SQLException
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import app.pickup.R
import app.pickup.data.ParcelRepository
import app.pickup.model.Parcel
import app.pickup.permission.canPostNotifications
import javax.inject.Inject

/**
 * 新包裹通知（spec message_source.md §6）：每批新包裹（一条短信）发一条，用同一个 ID 覆盖上一条，
 * 通知栏里只留一条最新的待取概览，不会挂着几条件数不同的旧通知。
 */
class PickupNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parcelRepository: ParcelRepository,
) {
    private val manager = NotificationManagerCompat.from(context)

    suspend fun notifyNewParcels(parcels: List<Parcel>) {
        if (parcels.isEmpty() || !canNotify()) return
        // 读不到全部待取包裹时只统计这一批：通知照发，也不让异常打断接收器随后推进扫描位置
        val pending = try {
            parcelRepository.getPendingParcels()
        } catch (_: SQLException) {
            parcels
        }
        val summary = summarizePending(pending)

        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_NEW_PARCEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.new_parcel_channel))
                .build(),
        )
        // 同点桌面图标：应用已在后台就切回原任务，不另开一个页面
        val openApp = PendingIntent.getActivity(
            context,
            0,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val title = batchLocation(parcels)
            ?.let { context.getString(R.string.notification_title_with_location, it) }
            ?: context.getString(R.string.notification_title_no_location)
        val notification = NotificationCompat.Builder(context, CHANNEL_NEW_PARCEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_content, summary.count, summary.locationCount))
            .setWhen(parcels.maxOf { it.arrivedAt }.toEpochMilli())
            .setShowWhen(true)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    /** 打开取件清单时清掉：通知里的包裹清单上都有 */
    fun cancelAll() = manager.cancelAll()

    /** 通知权限与系统里本应用的通知开关都开着 */
    fun canNotify(): Boolean = context.canPostNotifications()

    private companion object {
        const val CHANNEL_NEW_PARCEL = "new_parcel"
        const val NOTIFICATION_ID = 1
    }
}

/** 通知正文的待取概览 */
internal data class PendingSummary(val count: Int, val locationCount: Int)

/** 件数与地点数；未识别地点的包裹合起来也算一个地点（spec message_source.md §6） */
internal fun summarizePending(pending: List<Parcel>): PendingSummary =
    PendingSummary(count = pending.size, locationCount = pending.map { it.location }.distinct().size)

/** 标题里的地点：这一批都在同一个已识别地点时才有，否则标题不带地点 */
internal fun batchLocation(parcels: List<Parcel>): String? =
    parcels.map { it.location }.distinct().singleOrNull()?.takeIf { it.isNotBlank() }

package app.pickup.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import app.pickup.R
import app.pickup.data.ParcelRepository
import app.pickup.model.Parcel
import javax.inject.Inject

/** 新包裹通知，一件包裹一条（spec message_source.md §6） */
class PickupNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parcelRepository: ParcelRepository,
) {
    private val manager = NotificationManagerCompat.from(context)

    suspend fun notifyNewParcels(parcels: List<Parcel>) {
        if (parcels.isEmpty() || !canNotify()) return
        val pending = parcelRepository.getPendingParcels()
        val pendingCount = if (pending.isNotEmpty()) pending.size else parcels.size
        val locationCount = if (pending.isNotEmpty()) {
            pending.map { it.location }.distinct().size
        } else {
            parcels.map { it.location }.distinct().size
        }
        val contentText = formatContent(context, pendingCount, locationCount)

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
        for (parcel in parcels) {
            val title = formatTitle(context, parcel.location)
            val notification = NotificationCompat.Builder(context, CHANNEL_NEW_PARCEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(contentText)
                .setWhen(parcel.arrivedAt.toEpochMilli())
                .setShowWhen(true)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
            manager.notify(parcel.id.toInt(), notification)
        }
    }

    /** 打开取件清单时清掉：通知里的包裹清单上都有 */
    fun cancelAll() = manager.cancelAll()

    /** 通知权限与系统里本应用的通知开关都开着 */
    fun canNotify(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled()

    companion object {
        const val CHANNEL_NEW_PARCEL = "new_parcel"

        fun formatTitleText(location: String?): String =
            location?.takeIf { it.isNotBlank() }?.let {
                "新包裹已到达$it"
            } ?: "新包裹已到达"

        fun formatContentText(pendingCount: Int, locationCount: Int): String =
            "剩${pendingCount}件未取包裹，在${locationCount}个地点。"

        /** 标题显示「新包裹已到达xxx」，未识别地点时显示「新包裹已到达」 */
        fun formatTitle(context: Context, location: String?): String =
            location?.takeIf { it.isNotBlank() }?.let {
                context.getString(R.string.notification_title_with_location, it)
            } ?: context.getString(R.string.notification_title_no_location)

        /** 内容：剩x件未取包裹，在x个地点。 */
        fun formatContent(context: Context, pendingCount: Int, locationCount: Int): String =
            context.getString(R.string.notification_content, pendingCount, locationCount)
    }
}

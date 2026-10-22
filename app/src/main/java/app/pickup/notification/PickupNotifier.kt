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
import app.pickup.model.Parcel
import app.pickup.model.PickupMethod
import javax.inject.Inject

/** 新包裹的取件码通知，一件包裹一条（spec message_source.md §6） */
class PickupNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun notifyNewParcels(parcels: List<Parcel>) {
        if (parcels.isEmpty() || !canNotify()) return
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
            val notification = NotificationCompat.Builder(context, CHANNEL_NEW_PARCEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(parcel.headline())
                .setContentText(parcel.detail())
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

    /** 有码显示码，没码显示取件说明 */
    private fun Parcel.headline(): String = when (val method = pickupMethod) {
        is PickupMethod.ShelfCode -> method.code
        is PickupMethod.LockerCode -> method.code
        is PickupMethod.IdentityCode -> method.instruction
        is PickupMethod.Other -> method.instruction
    }

    /** 地点，快递柜再加柜号、格口，如「华强北路12号 · 7号柜 · 23格口」 */
    private fun Parcel.detail(): String {
        val locker = pickupMethod as? PickupMethod.LockerCode
        return listOfNotNull(
            location ?: context.getString(R.string.unknown_location),
            locker?.lockerNumber?.let { context.getString(R.string.locker_number, it) },
            locker?.compartment?.let { context.getString(R.string.compartment, it) },
        ).joinToString(" · ")
    }

    private companion object {
        const val CHANNEL_NEW_PARCEL = "new_parcel"
    }
}

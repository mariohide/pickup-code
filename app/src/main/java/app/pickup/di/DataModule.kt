package app.pickup.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import app.pickup.data.MessageScanRepository
import app.pickup.data.ParcelRepository
import app.pickup.data.RoomParcelRepository
import app.pickup.data.SmsScanRepository
import app.pickup.data.db.ParcelDao
import app.pickup.data.db.PickupDatabase
import app.pickup.data.sms.ContentResolverSmsInbox
import app.pickup.data.sms.SmsInbox
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun parcelRepository(impl: RoomParcelRepository): ParcelRepository

    @Binds
    @Singleton
    abstract fun messageScanRepository(impl: SmsScanRepository): MessageScanRepository

    @Binds
    abstract fun smsInbox(impl: ContentResolverSmsInbox): SmsInbox

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): PickupDatabase =
            Room.databaseBuilder(context, PickupDatabase::class.java, "pickup.db").build()

        @Provides
        fun parcelDao(database: PickupDatabase): ParcelDao = database.parcelDao()

        @Provides
        fun clock(): Clock = Clock.systemDefaultZone()
    }
}

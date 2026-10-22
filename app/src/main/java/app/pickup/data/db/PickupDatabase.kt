package app.pickup.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ParcelEntity::class, ScanCursorEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class PickupDatabase : RoomDatabase() {
    abstract fun parcelDao(): ParcelDao

    abstract fun scanCursorDao(): ScanCursorDao
}

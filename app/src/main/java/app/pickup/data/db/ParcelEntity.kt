package app.pickup.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.pickup.data.sms.ParsedParcel
import app.pickup.model.Parcel
import app.pickup.model.ParcelStatus
import app.pickup.model.PickupMethod
import java.time.Instant

@Entity(tableName = "parcel")
data class ParcelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val carrier: String?,
    @ColumnInfo(name = "arrived_at") val arrivedAt: Long,
    val location: String?,
    @ColumnInfo(name = "phone_tail") val phoneTail: String?,
    @ColumnInfo(name = "pickup_method") val pickupMethod: StoredPickupMethod,
    @ColumnInfo(name = "pickup_code") val pickupCode: String?,
    @ColumnInfo(name = "locker_number") val lockerNumber: String?,
    val compartment: String?,
    @ColumnInfo(name = "pickup_instruction") val pickupInstruction: String?,
    /** 记为已取的时间；为空即待取，状态只由这一列决定 */
    @ColumnInfo(name = "picked_at") val pickedAt: Long?,
)

enum class StoredPickupMethod { SHELF_CODE, LOCKER_CODE, IDENTITY_CODE, OTHER }

fun ParcelEntity.toDomain(): Parcel = Parcel(
    id = id,
    carrier = carrier,
    arrivedAt = Instant.ofEpochMilli(arrivedAt),
    location = location,
    phoneTail = phoneTail,
    pickupMethod = when (pickupMethod) {
        StoredPickupMethod.SHELF_CODE -> PickupMethod.ShelfCode(checkNotNull(pickupCode))
        StoredPickupMethod.LOCKER_CODE -> PickupMethod.LockerCode(checkNotNull(pickupCode), lockerNumber, compartment)
        StoredPickupMethod.IDENTITY_CODE -> PickupMethod.IdentityCode(checkNotNull(pickupInstruction))
        StoredPickupMethod.OTHER -> PickupMethod.Other(checkNotNull(pickupInstruction))
    },
    status = pickedAt?.let { ParcelStatus.PickedUp(Instant.ofEpochMilli(it)) } ?: ParcelStatus.Pending,
)

/** 新解析出的包裹入库时为待取 */
fun ParsedParcel.toEntity(): ParcelEntity {
    val method = pickupMethod
    return ParcelEntity(
        carrier = carrier,
        arrivedAt = arrivedAt.toEpochMilli(),
        location = location,
        phoneTail = phoneTail,
        pickupMethod = when (method) {
            is PickupMethod.ShelfCode -> StoredPickupMethod.SHELF_CODE
            is PickupMethod.LockerCode -> StoredPickupMethod.LOCKER_CODE
            is PickupMethod.IdentityCode -> StoredPickupMethod.IDENTITY_CODE
            is PickupMethod.Other -> StoredPickupMethod.OTHER
        },
        pickupCode = when (method) {
            is PickupMethod.ShelfCode -> method.code
            is PickupMethod.LockerCode -> method.code
            else -> null
        },
        lockerNumber = (method as? PickupMethod.LockerCode)?.lockerNumber,
        compartment = (method as? PickupMethod.LockerCode)?.compartment,
        pickupInstruction = when (method) {
            is PickupMethod.IdentityCode -> method.instruction
            is PickupMethod.Other -> method.instruction
            else -> null
        },
        pickedAt = null,
    )
}

/** 去重时只比对解析出的字段 */
fun ParcelEntity.toParsed(): ParsedParcel = toDomain().let {
    ParsedParcel(it.carrier, it.arrivedAt, it.location, it.phoneTail, it.pickupMethod)
}

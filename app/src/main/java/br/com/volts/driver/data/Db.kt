package br.com.volts.driver.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Turno de trabalho: do "Iniciar" ao "Encerrar", com a distância medida pelo GPS. */
@Entity
data class Shift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val distanceMeters: Double = 0.0,
)

/** Oferta de corrida exibida por um app de transporte. */
@Entity
data class Offer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val pkg: String,
    val valueCents: Long,
    val km: Double,
    val minutes: Int,
)

@Entity
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val amountCents: Long,
    val method: String,
) {
    companion object {
        const val PIX = "Pix"
        const val NFC = "Aproximação"
        const val CASH = "Dinheiro"
    }
}

@Dao
interface VoltsDao {
    @Insert suspend fun insertShift(shift: Shift): Long
    @Query("SELECT * FROM Shift WHERE endTime IS NULL ORDER BY id DESC LIMIT 1") suspend fun openShift(): Shift?
    @Query("SELECT * FROM Shift WHERE endTime IS NULL ORDER BY id DESC LIMIT 1") fun activeShift(): Flow<Shift?>
    @Query("UPDATE Shift SET distanceMeters = distanceMeters + :meters WHERE id = :id") suspend fun addDistance(id: Long, meters: Double)
    @Query("UPDATE Shift SET endTime = :time WHERE endTime IS NULL") suspend fun closeOpenShifts(time: Long)
    @Query("SELECT * FROM Shift WHERE startTime >= :since OR endTime IS NULL") fun shiftsSince(since: Long): Flow<List<Shift>>

    @Insert suspend fun insertOffer(offer: Offer)
    @Query("SELECT * FROM Offer WHERE time >= :since ORDER BY time DESC") fun offersSince(since: Long): Flow<List<Offer>>

    @Insert suspend fun insertPayment(payment: Payment)
    @Query("SELECT * FROM Payment WHERE time >= :since ORDER BY time DESC") fun paymentsSince(since: Long): Flow<List<Payment>>
}

@Database(entities = [Shift::class, Offer::class, Payment::class], version = 1, exportSchema = false)
abstract class VoltsDb : RoomDatabase() {
    abstract fun dao(): VoltsDao
}

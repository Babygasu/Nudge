package com.babygasu.nudge.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun observe(id: Long): Flow<Reminder?>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE status = 'OPEN'")
    suspend fun open(): List<Reminder>

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM reminders WHERE status != 'OPEN'")
    suspend fun deleteClosed(): Int
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: ReminderEvent)

    @Query("DELETE FROM reminder_events WHERE reminderId = :reminderId")
    suspend fun deleteFor(reminderId: Long)

    @Query("DELETE FROM reminder_events WHERE reminderId NOT IN (SELECT id FROM reminders)")
    suspend fun deleteOrphans()
}

@Dao
interface MonitoredAppDao {
    @Query("SELECT * FROM monitored_apps ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<MonitoredApp>>

    @Query("SELECT * FROM monitored_apps WHERE packageName = :packageName")
    suspend fun get(packageName: String): MonitoredApp?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(app: MonitoredApp)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: MonitoredApp)
}

/** Encrypted at rest with SQLCipher; the passphrase is protected by the Android Keystore. */
@Database(
    entities = [Reminder::class, ReminderEvent::class, MonitoredApp::class],
    version = 1,
    exportSchema = false,
)
abstract class NudgeDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun eventDao(): EventDao
    abstract fun monitoredAppDao(): MonitoredAppDao

    companion object {
        const val NAME = "nudge.db"

        fun build(context: Context, passphrase: ByteArray): NudgeDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, NudgeDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}

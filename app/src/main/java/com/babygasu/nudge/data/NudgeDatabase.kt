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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    /** Re-points (or clears, with null) "possible duplicate of" links when a reminder goes away. */
    @Query("UPDATE reminders SET possibleDuplicateOf = :newId WHERE possibleDuplicateOf = :oldId")
    suspend fun repointDuplicateLinks(oldId: Long, newId: Long?)
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
    version = 2,
    exportSchema = false,
)
abstract class NudgeDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun eventDao(): EventDao
    abstract fun monitoredAppDao(): MonitoredAppDao

    companion object {
        const val NAME = "nudge.db"

        /** v2: duplicate-confirmation fields. Existing reminders are kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN possibleDuplicateOf INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE reminders ADD COLUMN duplicateReviewed INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context, passphrase: ByteArray): NudgeDatabase {
            System.loadLibrary("sqlcipher")
            // No destructive fallback: a missing migration must fail loudly, never silently erase reminders.
            return Room.databaseBuilder(context, NudgeDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}

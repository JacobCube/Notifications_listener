package cz.cubeit.notificationslistener

import android.app.PendingIntent
import android.content.Context
import androidx.room.*
import java.text.DateFormat

/***
 * model proměnné notifikace
 */
@Entity(tableName = "tbl_notification")
data class ReceivedNotification(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "tableId") var tableId: Long?,
    @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "packageName") val packageName: String,
    @ColumnInfo(name = "millis") val millis: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "read") val read: Boolean,
    @ColumnInfo(name = "icon") val icon: ByteArray?
) {
    val time: String get() = DateFormat.getTimeInstance().format(millis).toString()
    val date: String get() = DateFormat.getDateInstance().format(millis).toString()
}

/***
 * Kontrolní model (interface) databáze
 */
@Dao interface NotificationDao {
    @Query("SELECT * FROM tbl_notification")
    fun getAll(): List<ReceivedNotification>

    @Query("SELECT * FROM tbl_notification WHERE packageName LIKE :packageName")
    fun findByPackageName(packageName: String): List<ReceivedNotification>

    @Query("SELECT * FROM tbl_notification WHERE id IN (:notificationIds)")
    fun loadAllByIds(notificationIds: IntArray): List<ReceivedNotification>

    @Query("SELECT * FROM tbl_notification WHERE id LIKE :id LIMIT 1")
    fun findByUid(id: Int): ReceivedNotification

    @Query("UPDATE tbl_notification SET read = :read WHERE id == :id")
    fun readNotification(id: Int, read: Boolean)

    @Insert
    fun insertAll(vararg notifications: ReceivedNotification)

    @Delete
    fun delete(notification: ReceivedNotification)
}

@Database(entities = [ReceivedNotification::class], version = 1)
abstract class NotificationDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    /***
     * Singleton instance databáze
     */
    companion object{
        private var instance: NotificationDatabase? = null

        fun getInstance(context: Context): NotificationDatabase{
            if (instance == null){
                instance = Room.databaseBuilder(
                    context,
                    NotificationDatabase::class.java,
                    "notificationDB")
                    .enableMultiInstanceInvalidation()
                    .build()
            }
            return instance as NotificationDatabase
        }
    }
}


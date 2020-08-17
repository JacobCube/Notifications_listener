package cz.cubeit.notificationslistener

import android.R.attr.bitmap
import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import org.jetbrains.anko.doAsync
import java.io.ByteArrayOutputStream
import kotlin.random.Random


/***
 * zahájení služby (service) při každém novém BOOT(u) aplikace
 * viz. registrace služby v AndroidManifest.xml (android.intent.action.BOOT_COMPLETED)
 */
class BootBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val startServiceIntent = Intent(context, NotificationService::class.java)
        context.startService(startServiceIntent)
    }
}

class NotificationService : NotificationListenerService() {

    /***
     * singleton obsahující proměnné pro všechny služby (services)
     */
    companion object {
        var receiver: NotificationReceiver? = null
        var dbInstance: NotificationDatabase? = null
    }

    /***
     * UI (front-end) callback prostředí
     */
    fun setListener(receiver: NotificationReceiver?) {
        Companion.receiver = receiver
    }

    /***
     * při započnutí služby (service)
     */
    override fun onCreate() {
        super.onCreate()
        ///inicializace databáze
        dbInstance = NotificationDatabase.getInstance(this)
        Log.d("NotificationService", "successfully started")
        startService(Intent(applicationContext, NotificationService::class.java))
    }

    /***
     * voláno při nové notifikaci
     */
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        val bitmapdata: ByteArray? = if(Build.VERSION.SDK_INT >= 23) {
            val bitmap = sbn.notification.smallIcon.loadDrawable(this).toBitmap()
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }else null

        val newNotification = ReceivedNotification(
            null,
            (if(Build.VERSION.SDK_INT >= 29){
                sbn.uid
            } else sbn.id + Random.nextInt(0, 999)),
            sbn.packageName,
            sbn.postTime,
            (sbn.notification.extras.getString(Notification.EXTRA_TITLE) ?: "Unknown title"),
            (sbn.notification.extras.getString(Notification.EXTRA_TEXT) ?: "Unknown text"),
            false,
            bitmapdata
        )
        receiver?.addNotification(newNotification)
        doAsync {
            dbInstance?.notificationDao()?.insertAll(newNotification)
        }
        Log.d("onNotificationPosted", "triggered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return Service.START_STICKY
    }

    /***
     * odstranění notifikace ze status bar(u)
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        val readId = (if(Build.VERSION.SDK_INT >= 29){
            sbn.uid
        } else sbn.id)
        receiver?.readNotification(readId)
        doAsync {
            dbInstance?.notificationDao()?.readNotification(readId, true)
        }
    }
}
package cz.cubeit.notificationslistener

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.row_notification.view.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import kotlin.random.Random.Default.nextInt


/***
 * prostředník mezi naslouchačem a instancí aplikace
 */
interface NotificationReceiver {
    fun addNotification(notification: ReceivedNotification)
    fun readNotification(id: Int)
}

internal const val CHANNEL_ID = "0242ac130003"
class MainActivity : AppCompatActivity(), NotificationReceiver {
    //private lateinit var recyclerView: RecyclerView
    //private lateinit var spinner: Spinner
    private lateinit var instanceDB: NotificationDatabase
    private var cacheNotifications = mutableListOf<ReceivedNotification>()
    private var cacheNotificationsBackUp = mutableListOf<ReceivedNotification>()
    private var currentFilter = "All"

    /***
     * zpracování ikon z uložených notifikací
     * v případě vnoření funkcionality v RecyclerView by došlo ke zpomalení UI
     */
    private fun List<ReceivedNotification>.getBitmaps(resources: Resources): List<Bitmap> {
        val res = mutableListOf<Bitmap>()
        for(i in this) {
            res.add(if(i.icon == null){
                BitmapFactory.decodeResource(resources, android.R.drawable.sym_def_app_icon)
            }else BitmapFactory.decodeByteArray(i.icon, 0, i.icon.size))
        }
        return res
    }

    /***
     * změna v aplikaci -> kvůli automaticky spouštící se službě v pozadí musíme nahradit existující službu za takovou,
     * která je schopna komunikovat s UI (front-end)
     */
    override fun onResume() {
        super.onResume()
        applicationContext.stopService(Intent(applicationContext, NotificationService::class.java))
        val newService = NotificationService()
        newService.setListener(this)
    }

    /***
     * v případě tázání se o permise
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        applicationContext.stopService(Intent(applicationContext, NotificationService::class.java))
        val newService = NotificationService()
        newService.setListener(this)
    }

    /***
     * nové notifikace -> šance nového packageName
     */
    private fun invalidateSpinnerAdapter() {
        val list = cacheNotifications.map { it.packageName }.toMutableList()
        list.add("All")
        val listPackageName = list.sorted().distinct()
        spinnerPackageName.apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                R.layout.spinner_item_yellow,
                listPackageName.toTypedArray()
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val packageName = listPackageName[position]
                    Log.d("onItemSelected", packageName)
                    if(currentFilter != packageName) {
                        cacheNotifications.clear()
                        cacheNotifications.addAll(cacheNotificationsBackUp)

                        Log.d("onItemSelected inner", position.toString())
                        if(position != 0) {
                            cacheNotifications = cacheNotifications.filter { it.packageName == list.sorted().distinct()[position]}.toMutableList()
                        }
                        (recyclerViewNotifications.adapter as NotificationAdapter).refresh(cacheNotifications, cacheNotifications.getBitmaps(resources))
                        currentFilter = packageName
                    }
                }
            }
        }
        spinnerPackageName.setSelection(listPackageName.indexOf(currentFilter))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /***
         * poptávka po permisích
         */
        if (!NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            startActivity(intent)
        }

        /***
         * RecyclerView inicializace
         */
        val notificationAdapter = NotificationAdapter(cacheNotifications, resources).also {
            it.setHasStableIds(true)
        }
        recyclerViewNotifications.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.VERTICAL, false)
            adapter = notificationAdapter
        }

        /***
         * Spinner (ComboBox) inicializace
         */
        //spinner = spinnerPackageName

        /***
         * vytvoření instance databáze a náčet všech lokálně uložených dat
         */
        doAsync {
            instanceDB = NotificationDatabase.getInstance(this@MainActivity)
            cacheNotifications = instanceDB.notificationDao().getAll().sortedByDescending { it.millis }.toMutableList()
            cacheNotificationsBackUp.addAll(cacheNotifications)
            uiThread {
                Log.d("cacheNotifications", cacheNotifications.toString())
                invalidateSpinnerAdapter()
                (recyclerViewNotifications.adapter as NotificationAdapter).refresh(cacheNotifications, cacheNotifications.getBitmaps(resources))
            }
        }

        /***
         * testovací notifikace
         */
        var mNotificationManager: NotificationManager
        buttonTest.setOnClickListener{
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setContentTitle("Notification listener test")
                .setContentText("This is user generated notification for testing purposes.")
                .setPriority(NotificationCompat.PRIORITY_MAX)

            mNotificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelId = CHANNEL_ID
                val channel = NotificationChannel(
                    channelId,
                    "Notification listener test",
                    NotificationManager.IMPORTANCE_HIGH
                )
                mNotificationManager.createNotificationChannel(channel)
                builder.setChannelId(channelId)
            }

            mNotificationManager.notify(nextInt(0, 999), builder.build())
        }
    }

    /***
     * nově příchozí notifikace
     */
    override fun addNotification(notification: ReceivedNotification) {
        cacheNotifications.add(0, notification)
        cacheNotificationsBackUp.add(0, notification)
        Log.d("addNotification", "triggered")
        (recyclerViewNotifications.adapter as NotificationAdapter).refresh(cacheNotifications, cacheNotifications.getBitmaps(resources))
        invalidateSpinnerAdapter()
    }

    /***
     * notifikace byla odstraněna ze status baru (přečtena)
     */
    override fun readNotification(id: Int) {
        /*(recyclerViewNotifications.adapter as NotificationAdapter).notifyItemChanged(
            cacheNotifications.indexOf(cacheNotifications.find { it.id == id })
        )*/
    }

    /***
     * adapter pro RecyclerView
     */
    private class NotificationAdapter(
        val notifications: MutableList<ReceivedNotification>,
        val resources: Resources
    ) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {
        var inflater: View? = null
        var bitmaps = mutableListOf<Bitmap>()

        class NotificationViewHolder(
            val icon: ImageView,
            val postTime: TextView,
            val title: TextView,
            val text: TextView,
            val packageName: TextView,
            inflater: View
        ): RecyclerView.ViewHolder(inflater)

        override fun getItemCount(): Int = notifications.size

        fun refresh(listNotifications: List<ReceivedNotification>, listBitmap: List<Bitmap>) {
            notifications.clear()
            notifications.addAll(listNotifications)
            bitmaps.clear()
            bitmaps.addAll(listBitmap)
            notifyDataSetChanged()
        }

        /***
         * vnitřní unikátní identifikátor
         */
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getItemViewType(position: Int): Int = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
            inflater = LayoutInflater.from(parent.context).inflate(R.layout.row_notification, parent, false)
            inflater?.setOnClickListener {

            }
            return NotificationViewHolder(
                inflater!!.imageViewIcon,
                inflater!!.textViewPostTime,
                inflater!!.textViewTitle,
                inflater!!.textViewText,
                inflater!!.textViewPackageName,
                inflater ?: LayoutInflater.from(parent.context).inflate(R.layout.row_notification, parent, false)
            )
        }

        /***
         * zobrazení dat na základě aktuální pozice v listu
         */
        override fun onBindViewHolder(viewHolder: NotificationViewHolder, position: Int) {
            with(notifications[position]) {
                viewHolder.packageName.text = packageName
                viewHolder.postTime.text = resources.getString(R.string.notification_postTime, time, date)
                viewHolder.text.text = text
                viewHolder.title.text = title
                viewHolder.icon.setImageBitmap(bitmaps[position])
            }
        }
    }
}
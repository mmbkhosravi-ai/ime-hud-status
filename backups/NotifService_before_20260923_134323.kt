package com.imehud.status

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NotifService : Service() {

    companion object {
        const val TAG = "IMEHUD"
        const val CHANNEL_ID = "imehud-price"
        const val NOTIF_ID = 8888
        const val INTERVAL_MS = 5000L
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotif("----", "در حال اتصال...", Color.GRAY))
        startLoop()
        return START_STICKY
    }

    private fun startLoop() {
        job?.cancel()
        job = scope.launch {
            while (true) {
                try {
                    val data = PriceFetcher.fetch()
                    if (data != null) {
                        val digits = first3(data.price)
                        val color = if ((data.changePct ?: 0.0) >= 0)
                            Color.rgb(34, 197, 94) else Color.rgb(220, 38, 38)

                        val content = buildContent(data)
                        val notif = buildNotif(digits, content, color, data.price)
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(NOTIF_ID, notif)
                        Log.d(TAG, "updated: $digits | $content")
                    } else {
                        Log.w(TAG, "fetch failed")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "loop error", e)
                }
                delay(INTERVAL_MS)
            }
        }
    }

    private fun buildContent(d: PriceData): String {
        val sb = StringBuilder()
        sb.append(formatPrice(d.price))
        d.changePct?.let { sb.append("  ").append(String.format("%+.2f%%", it)) }
        d.bubble?.let { sb.append("  حباب ").append(String.format("%+.2f%%", it)) }
        sb.append("\n")
        sb.append(if (d.marketOpen) "🟢 بازار باز" else "🔴 بازار بسته")
        sb.append("  ·  ").append(d.alias)
        return sb.toString()
    }

    private fun buildNotif(
        digits: String,
        content: String,
        color: Int,
        price: Double = 0.0
    ): Notification {
        val bmp = NotifBuilder.makePriceBitmap(price, color)
        val smallIcon = Icon.createWithBitmap(bmp)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID)
        else
            Notification.Builder(this)

        return builder
            .setSmallIcon(smallIcon)
            .setContentTitle("IME-HUD")
            .setContentText(content)
            .setStyle(Notification.BigTextStyle().bigText(content))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                "IME-HUD قیمت",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نمایش قیمت صندوق عیار در نوار وضعیت"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun first3(v: Double): String {
        if (v.isNaN() || v == 0.0) return "---"
        val s = kotlin.math.abs(v).toLong().toString()
        return if (s.length <= 3) s else s.substring(0, 3)
    }

    private fun formatPrice(v: Double): String {
        return if (v >= 1000) String.format("%,.0f", v) else String.format("%.2f", v)
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

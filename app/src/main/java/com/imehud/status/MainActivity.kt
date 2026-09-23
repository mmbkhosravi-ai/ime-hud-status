package com.imehud.status

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var priceText: TextView
    private lateinit var changeText: TextView
    private lateinit var bubbleText: TextView
    private lateinit var marketText: TextView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button

    private val scope = CoroutineScope(Dispatchers.Main)
    private var uiJob: Job? = null
    private var isRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            setBackgroundColor(Color.parseColor("#0f1419"))
        }

        // ─── عنوان ───
        val title = TextView(this).apply {
            text = "IME-HUD Status"
            textSize = 22f
            setTextColor(Color.parseColor("#d4a017"))
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "نمایش قیمت در استاتوس بار"
            textSize = 12f
            setTextColor(Color.parseColor("#7a8ea5"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 32)
        }

        // ─── کارت قیمت ───
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 40)
            setBackgroundColor(Color.parseColor("#1a222c"))
            gravity = Gravity.CENTER
        }

        priceText = TextView(this).apply {
            text = "---,---"
            textSize = 48f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }

        changeText = TextView(this).apply {
            text = "---"
            textSize = 20f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        bubbleText = TextView(this).apply {
            text = "حباب ---"
            textSize = 14f
            setTextColor(Color.parseColor("#d4a017"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        marketText = TextView(this).apply {
            text = "در حال اتصال..."
            textSize = 13f
            setTextColor(Color.parseColor("#7a8ea5"))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        card.addView(priceText)
        card.addView(changeText)
        card.addView(bubbleText)
        card.addView(marketText)

        // ─── وضعیت اپ ───
        statusText = TextView(this).apply {
            text = "وضعیت: متوقف"
            textSize = 14f
            setTextColor(Color.parseColor("#7a8ea5"))
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 16)
        }

        // ─── دکمه‌ها ───
        startBtn = Button(this).apply {
            text = "▶  شروع سرویس"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#16a34a"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                requestNotifPermission()
                startMyService()
            }
        }

        stopBtn = Button(this).apply {
            text = "■  توقف سرویس"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#dc2626"))
            setTextColor(Color.WHITE)
            setOnClickListener { stopMyService() }
        }

        val space1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 24
            )
        }
        val space2 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 12
            )
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(card)
        root.addView(statusText)
        root.addView(startBtn)
        root.addView(space2)
        root.addView(stopBtn)
        root.addView(space1)

        setContentView(root)

        requestNotifPermission()
        startUiRefresh()
    }

    private fun startUiRefresh() {
        uiJob?.cancel()
        uiJob = scope.launch {
            while (true) {
                try {
                    val data = PriceFetcher.fetch()
                    if (data != null) {
                        priceText.text = formatPrice(data.price)
                        val pct = data.changePct
                        if (pct != null) {
                            changeText.text = String.format("%+.2f%%", pct)
                            changeText.setTextColor(
                                if (pct >= 0) Color.parseColor("#22c55e")
                                else Color.parseColor("#dc2626")
                            )
                        } else {
                            changeText.text = "---"
                            changeText.setTextColor(Color.parseColor("#888888"))
                        }
                        val bub = data.bubble
                        if (bub != null) {
                            bubbleText.text = String.format("حباب %+.2f%%", bub)
                            bubbleText.setTextColor(
                                if (bub >= 0) Color.parseColor("#d4a017")
                                else Color.parseColor("#dc2626")
                            )
                        } else {
                            bubbleText.text = "حباب ---"
                        }
                        marketText.text = if (data.marketOpen) "🟢 بازار باز" else "🔴 بازار بسته"
                        marketText.setTextColor(
                            if (data.marketOpen) Color.parseColor("#22c55e")
                            else Color.parseColor("#dc2626")
                        )
                    } else {
                        marketText.text = "⚠️ اتصال به سرور برقرار نیست"
                        marketText.setTextColor(Color.parseColor("#dc2626"))
                    }
                } catch (e: Exception) {
                    // ignore
                }
                delay(3000)
            }
        }
    }

    private fun formatPrice(v: Double): String {
        return if (v >= 1000) String.format("%,.0f", v) else String.format("%.2f", v)
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun startMyService() {
        val intent = Intent(this, NotifService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        statusText.text = "وضعیت: در حال اجرا ✓"
        statusText.setTextColor(Color.parseColor("#22c55e"))
        isRunning = true
    }

    private fun stopMyService() {
        stopService(Intent(this, NotifService::class.java))
        statusText.text = "وضعیت: متوقف"
        statusText.setTextColor(Color.parseColor("#7a8ea5"))
        isRunning = false
    }

    override fun onDestroy() {
        uiJob?.cancel()
        super.onDestroy()
    }
}

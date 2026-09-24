package com.imehud.status

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.graphics.Color
import android.os.Build
import android.os.Bundle
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
    private lateinit var webBtn: Button
    private lateinit var overlayBtn: Button

    private val scope = CoroutineScope(Dispatchers.Main)
    private var uiJob: Job? = null

    private val webUrl = "http://127.0.0.1:5056/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            setBackgroundColor(Color.parseColor("#0f1419"))
        }

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

        statusText = TextView(this).apply {
            text = "وضعیت: متوقف"
            textSize = 14f
            setTextColor(Color.parseColor("#7a8ea5"))
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 16)
        }

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

        overlayBtn = Button(this).apply {
            text = "🔴  نمایش عدد روی استاتوس بار (Overlay)"
            textSize = 13f
            setBackgroundColor(Color.parseColor("#d4a017"))
            setTextColor(Color.BLACK)
            setOnClickListener { toggleOverlay() }
        }

        webBtn = Button(this).apply {
            text = "🌐  باز کردن رابط وب (کارت‌ها)"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#2563eb"))
            setTextColor(Color.WHITE)
            setOnClickListener { openWeb() }
        }

        val space2 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 12
            )
        }
        val space3 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 24
            )
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(card)
        root.addView(statusText)
        root.addView(startBtn)
        root.addView(space2)
        root.addView(stopBtn)
        root.addView(space2)
        root.addView(overlayBtn)
        root.addView(space3)
        root.addView(webBtn)

        setContentView(root)

        requestNotifPermission()
        startUiRefresh()
    }

    private fun toggleOverlay() {
        if (OverlayService.isRunning) {
            stopService(Intent(this, OverlayService::class.java))
            overlayBtn.text = "🔴  نمایش عدد روی استاتوس بار (Overlay)"
            statusText.text = "Overlay متوقف شد"
            statusText.setTextColor(Color.parseColor("#7a8ea5"))
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            // درخواست مجوز
            try {
                val i = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(i)
                marketText.text = "پس از دادن مجوز، دوباره دکمه را بزنید"
                marketText.setTextColor(Color.parseColor("#d4a017"))
            } catch (e: Exception) {
                marketText.text = "خطا در باز کردن تنظیمات مجوز"
            }
            return
        }

        // شروع سرویس Overlay
        startService(Intent(this, OverlayService::class.java))
        overlayBtn.text = "■  توقف Overlay"
        statusText.text = "Overlay فعال ✓ — عدد روی استاتوس بار"
        statusText.setTextColor(Color.parseColor("#22c55e"))
    }

    private fun openWeb() {
        try {
            val i = Intent(Intent.ACTION_VIEW)
            i.data = android.net.Uri.parse(webUrl)
            startActivity(i)
        } catch (e: Exception) {
            marketText.text = "خطا در باز کردن مرورگر"
        }
    }

    private fun startUiRefresh() {
        uiJob?.cancel()
        uiJob = scope.launch {
            while (true) {
                try {
                    val result = PriceFetcher.fetchRotation()
                    val data = result?.items?.firstOrNull()
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
                        }
                        val bub = data.bubble
                        if (bub != null) {
                            bubbleText.text = String.format("حباب %+.2f%%", bub)
                        } else {
                            bubbleText.text = "حباب ---"
                        }
                        marketText.text = if (data.marketOpen) "🟢 بازار باز" else "🔴 بازار بسته"
                        marketText.setTextColor(
                            if (data.marketOpen) Color.parseColor("#22c55e")
                            else Color.parseColor("#dc2626")
                        )
                    } else {
                        marketText.text = "⚠️ اتصال برقرار نیست"
                        marketText.setTextColor(Color.parseColor("#dc2626"))
                    }
                } catch (e: Exception) {
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
    }

    private fun stopMyService() {
        stopService(Intent(this, NotifService::class.java))
        statusText.text = "وضعیت: متوقف"
        statusText.setTextColor(Color.parseColor("#7a8ea5"))
    }

    override fun onDestroy() {
        uiJob?.cancel()
        super.onDestroy()
    }
}

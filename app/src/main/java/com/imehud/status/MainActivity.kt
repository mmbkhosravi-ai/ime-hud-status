package com.imehud.status

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val title = TextView(this).apply {
            text = "IME-HUD Status"
            textSize = 24f
        }

        statusText = TextView(this).apply {
            text = "وضعیت: متوقف"
            textSize = 16f
            setPadding(0, 32, 0, 32)
        }

        val startBtn = Button(this).apply {
            text = "▶ شروع"
            setOnClickListener {
                requestNotifPermission()
                startService()
            }
        }

        val stopBtn = Button(this).apply {
            text = "■ توقف"
            setOnClickListener {
                stopService(Intent(this@MainActivity, NotifService::class.java))
                statusText.text = "وضعیت: متوقف"
            }
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(startBtn)
        layout.addView(stopBtn)

        setContentView(layout)

        requestNotifPermission()
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

    private fun startService() {
        val intent = Intent(this, NotifService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        statusText.text = "وضعیت: در حال اجرا"
    }
}

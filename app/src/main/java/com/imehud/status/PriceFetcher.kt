package com.imehud.status

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PriceData(
    val price: Double,
    val changePct: Double?,
    val bubble: Double?,
    val marketOpen: Boolean,
    val alias: String
)

object PriceFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private const val URL = "http://127.0.0.1:5056/api/notif/data"

    suspend fun fetch(): PriceData? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(URL).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val j = JSONObject(body)
                return@withContext PriceData(
                    price = j.optDouble("price", 0.0),
                    changePct = if (j.has("change_pct") && !j.isNull("change_pct"))
                        j.getDouble("change_pct") else null,
                    bubble = if (j.has("bubble") && !j.isNull("bubble"))
                        j.getDouble("bubble") else null,
                    marketOpen = j.optBoolean("market_open", false),
                    alias = j.optString("alias", "IME-HUD")
                )
            }
        } catch (e: Exception) {
            return@withContext null
        }
    }
}

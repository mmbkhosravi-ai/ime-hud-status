package com.imehud.status

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PriceData(
    val price: Double,
    val changePct: Double?,
    val bubble: Double?,
    val marketOpen: Boolean,
    val alias: String
)

data class RotationResult(
    val items: List<PriceData>,
    val marketOpen: Boolean
)

data class UsdData(
    val price: Double,
    val prev: Double?,
    val changePct: Double?,
    val alias: String
)

data class MarketItem(
    val key: String,
    val alias: String,
    val price: Double,
    val prev: Double?,
    val changePct: Double?
)

data class NotifSymbol(
    val uid: String,
    val key: String,
    val alias: String,
    val insCode: String,
    val price: Double,
    val changePct: Double?,
    val bubble: Double?,
    val rPct: Double?,
    val alarmEnabled: Boolean,
    val alarmRHigh: Double,
    val alarmRLow: Double,
    val alarmBubbleHigh: Double,
    val alarmBubbleLow: Double
)


object PriceFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private const val URL = "http://127.0.0.1:5056/api/notif/rotation"

    suspend fun fetchRotation(): RotationResult? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(URL).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val j = JSONObject(body)
                val arr: JSONArray = j.optJSONArray("items") ?: JSONArray()
                val items = ArrayList<PriceData>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    items.add(
                        PriceData(
                            price = o.optDouble("price", 0.0),
                            changePct = if (o.has("change_pct") && !o.isNull("change_pct"))
                                o.getDouble("change_pct") else null,
                            bubble = if (o.has("bubble") && !o.isNull("bubble"))
                                o.getDouble("bubble") else null,
                            marketOpen = j.optBoolean("market_open", false),
                            alias = o.optString("alias", "IME-HUD")
                        )
                    )
                }
                return@withContext RotationResult(
                    items = items,
                    marketOpen = j.optBoolean("market_open", false)
                )
            }
        } catch (e: Exception) {
            return@withContext null
        }
    }

    suspend fun fetchUsd(): UsdData? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("http://127.0.0.1:5056/api/notif/usd")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val j = JSONObject(body)
                if (j.has("error") && !j.isNull("error")) return@withContext null
                val price = j.optDouble("price", 0.0)
                if (price <= 0.0) return@withContext null
                return@withContext UsdData(
                    price = price,
                    prev = if (j.has("prev") && !j.isNull("prev"))
                        j.getDouble("prev") else null,
                    changePct = if (j.has("change_pct") && !j.isNull("change_pct"))
                        j.getDouble("change_pct") else null,
                    alias = j.optString("alias", "دلار")
                )
            }
        } catch (e: Exception) {
            return@withContext null
        }
    }

    suspend fun fetchMarket(): List<MarketItem>? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("http://127.0.0.1:5056/api/notif/market")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val j = JSONObject(body)
                val arr = j.optJSONArray("items") ?: JSONArray()
                val list = ArrayList<MarketItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val price = o.optDouble("price", 0.0)
                    if (price <= 0.0) continue
                    list.add(
                        MarketItem(
                            key = o.optString("key", ""),
                            alias = o.optString("alias", "?"),
                            price = price,
                            prev = if (o.has("prev") && !o.isNull("prev"))
                                o.getDouble("prev") else null,
                            changePct = if (o.has("change_pct") && !o.isNull("change_pct"))
                                o.getDouble("change_pct") else null
                        )
                    )
                }
                return@withContext list
            }
        } catch (e: Exception) {
            return@withContext null
        }
    }

    /**
     * ★ وضعیت بازار — endpoint سبک (~5ms).
     */
    suspend fun fetchMarketStatus(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("http://127.0.0.1:5056/api/market/status")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val body = resp.body?.string() ?: return@withContext false
                val j = JSONObject(body)
                return@withContext j.optBoolean("open", false)
            }
        } catch (e: Exception) {
            return@withContext false
        }
    }

    /**
     * ★ منبع اصلی برای Overlay و Notif — لیست نمادهای فعال
     * که کاربر در Web UI تعیین کرده (show=true).
     */
    suspend fun fetchNotifSymbols(): List<NotifSymbol>? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("http://127.0.0.1:5056/api/notif/symbols/cached")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val j = JSONObject(body)
                val arr = j.optJSONArray("items") ?: JSONArray()
                val list = ArrayList<NotifSymbol>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val price = o.optDouble("price", 0.0)
                    if (price <= 0.0) continue
                    list.add(
                        NotifSymbol(
                            uid = o.optString("uid", ""),
                            key = o.optString("key", "?"),
                            alias = o.optString("alias", "?"),
                            insCode = o.optString("ins_code", ""),
                            price = price,
                            changePct = if (o.has("change_pct") && !o.isNull("change_pct"))
                                o.getDouble("change_pct") else null,
                            bubble = if (o.has("bubble") && !o.isNull("bubble"))
                                o.getDouble("bubble") else null,
                            rPct = if (o.has("r_pct") && !o.isNull("r_pct"))
                                o.getDouble("r_pct") else null,
                            alarmEnabled = o.optBoolean("alarm_enabled", true),
                            alarmRHigh = o.optDouble("alarm_r_high", 3.0),
                            alarmRLow = o.optDouble("alarm_r_low", -3.0),
                            alarmBubbleHigh = o.optDouble("alarm_bubble_high", 3.2),
                            alarmBubbleLow = o.optDouble("alarm_bubble_low", 0.5)
                        )
                    )
                }
                return@withContext list
            }
        } catch (e: Exception) {
            return@withContext null
        }
    }
}
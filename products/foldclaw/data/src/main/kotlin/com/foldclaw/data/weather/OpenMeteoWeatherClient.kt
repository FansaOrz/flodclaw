package com.foldclaw.data.weather

import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.tool.WeatherBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Open-Meteo：无需 API Key 的地理编码 + 每日预报。
 */
@Singleton
class OpenMeteoWeatherClient @Inject constructor(
    private val http: OkHttpClient,
) : WeatherBackend {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun forecast(city: String, dayOffset: Int): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val geo = geocode(city)
                    ?: return@withContext Result.Failure(
                        DomainError(ErrorKind.ActionFailed, "找不到城市「$city」"),
                    )
                val daily = dailyForecast(geo.lat, geo.lon, dayOffset)
                    ?: return@withContext Result.Failure(
                        DomainError(ErrorKind.ActionFailed, "无法获取「${geo.name}」的天气数据"),
                    )
                Result.Success(
                    buildString {
                        append("${geo.name}")
                        geo.admin1?.let { append("（$it）") }
                        append(" ${daily.date}：")
                        append(daily.summary)
                        append("，气温 ${daily.tMin.toInt()}~${daily.tMax.toInt()}℃")
                        append("，降水 ${"%.1f".format(daily.precipMm)} mm")
                    },
                )
            } catch (e: Exception) {
                Result.Failure(
                    DomainError(ErrorKind.ProviderUnavailable, "天气查询失败: ${e.message}"),
                )
            }
        }

    private fun geocode(city: String): GeoHit? {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", city)
            .addQueryParameter("count", "1")
            .addQueryParameter("language", "zh")
            .addQueryParameter("format", "json")
            .build()
        val body = httpGet(url.toString()) ?: return null
        val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return null
        val first = results.firstOrNull()?.jsonObject ?: return null
        val lat = first["latitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = first["longitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val name = first["name"]?.jsonPrimitive?.content ?: city
        val admin1 = first["admin1"]?.jsonPrimitive?.content
        return GeoHit(name, admin1, lat, lon)
    }

    private fun dailyForecast(lat: Double, lon: Double, dayOffset: Int): DailyHit? {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", lat.toString())
            .addQueryParameter("longitude", lon.toString())
            .addQueryParameter(
                "daily",
                "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum",
            )
            .addQueryParameter("timezone", "Asia/Shanghai")
            .addQueryParameter("forecast_days", (dayOffset + 1).coerceAtMost(16).toString())
            .build()
        val body = httpGet(url.toString()) ?: return null
        val daily = json.parseToJsonElement(body).jsonObject["daily"]?.jsonObject ?: return null
        val times = daily["time"]?.jsonArray ?: return null
        if (dayOffset !in times.indices) return null
        val code = daily["weather_code"]?.jsonArray?.getOrNull(dayOffset)
            ?.jsonPrimitive?.intOrNull ?: 0
        val tMax = daily["temperature_2m_max"]?.jsonArray?.getOrNull(dayOffset)
            ?.jsonPrimitive?.doubleOrNull ?: return null
        val tMin = daily["temperature_2m_min"]?.jsonArray?.getOrNull(dayOffset)
            ?.jsonPrimitive?.doubleOrNull ?: return null
        val precip = daily["precipitation_sum"]?.jsonArray?.getOrNull(dayOffset)
            ?.jsonPrimitive?.doubleOrNull ?: 0.0
        val date = times[dayOffset].jsonPrimitive.content
        return DailyHit(date, weatherCodeZh(code), tMax, tMin, precip)
    }

    private fun httpGet(url: String): String? {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return null
            return body
        }
    }

    private data class GeoHit(val name: String, val admin1: String?, val lat: Double, val lon: Double)
    private data class DailyHit(
        val date: String,
        val summary: String,
        val tMax: Double,
        val tMin: Double,
        val precipMm: Double,
    )

    companion object {
        fun weatherCodeZh(code: Int): String = when (code) {
            0 -> "晴"
            1, 2 -> "多云"
            3 -> "阴"
            45, 48 -> "雾"
            51, 53, 55, 56, 57 -> "毛毛雨"
            61, 63, 65, 66, 67 -> "雨"
            71, 73, 75, 77 -> "雪"
            80, 81, 82 -> "阵雨"
            85, 86 -> "阵雪"
            95 -> "雷阵雨"
            96, 99 -> "雷暴伴冰雹"
            else -> "天气码$code"
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

package com.foldclaw.domain.tool

import com.foldclaw.domain.model.Result

/** 天气查询后端。 */
interface WeatherBackend {
    suspend fun forecast(city: String, dayOffset: Int): Result<String>
}

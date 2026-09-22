package de.hastnetwork.jarvis.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `weather` field of `GET /api/briefing/today` (architecture.md §12/§13). */
@Serializable
data class WeatherInfo(
    @SerialName("temperature_c") val temperatureC: Double,
    @SerialName("wind_speed_kmh") val windSpeedKmh: Double,
    val condition: String,
    @SerialName("weather_code") val weatherCode: Int,
)

/**
 * `GET /api/briefing/today` response (architecture.md §12). `weather` is
 * `null` whenever `HOME_LATITUDE`/`HOME_LONGITUDE` aren't configured
 * server-side - callers must never show an empty weather state, only omit
 * it entirely when null (same principle as the calendar ticker).
 */
@Serializable
data class BriefingResponse(
    val summary: String,
    val weather: WeatherInfo? = null,
    @SerialName("upcoming_events") val upcomingEvents: List<CalendarEvent> = emptyList(),
    @SerialName("open_reminders") val openReminders: List<String> = emptyList(),
)

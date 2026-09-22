import httpx

from app.config import get_settings

# WMO weather codes -> short German description (subset covering Open-Meteo's
# `current.weather_code`). https://open-meteo.com/en/docs
_WMO_DESCRIPTIONS: dict[int, str] = {
    0: "klarer Himmel",
    1: "überwiegend klar",
    2: "teilweise bewölkt",
    3: "bedeckt",
    45: "Nebel",
    48: "Nebel mit Reifglätte",
    51: "leichter Nieselregen",
    53: "mäßiger Nieselregen",
    55: "starker Nieselregen",
    61: "leichter Regen",
    63: "mäßiger Regen",
    65: "starker Regen",
    71: "leichter Schneefall",
    73: "mäßiger Schneefall",
    75: "starker Schneefall",
    80: "leichte Regenschauer",
    81: "mäßige Regenschauer",
    82: "heftige Regenschauer",
    95: "Gewitter",
    96: "Gewitter mit Hagel",
    99: "starkes Gewitter mit Hagel",
}


class WeatherService:
    """Free, key-less current-weather lookup via Open-Meteo. Inactive unless
    HOME_LATITUDE/HOME_LONGITUDE are configured (docs/architecture.md §13)."""

    def is_configured(self) -> bool:
        return get_settings().weather_configured

    async def get_current(self) -> dict | None:
        settings = get_settings()
        if not settings.weather_configured:
            return None

        params = {
            "latitude": settings.home_latitude,
            "longitude": settings.home_longitude,
            "current": "temperature_2m,weather_code,wind_speed_10m",
            "timezone": settings.timezone,
        }
        async with httpx.AsyncClient(timeout=8.0) as client:
            resp = await client.get(
                "https://api.open-meteo.com/v1/forecast", params=params
            )
            resp.raise_for_status()
            data = resp.json()

        current = data.get("current", {})
        code = current.get("weather_code")
        return {
            "temperature_c": current.get("temperature_2m"),
            "wind_speed_kmh": current.get("wind_speed_10m"),
            "condition": _WMO_DESCRIPTIONS.get(code, "unbekannt"),
            "weather_code": code,
        }


_service = WeatherService()


def get_weather_service() -> WeatherService:
    return _service

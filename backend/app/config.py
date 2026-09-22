from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    host: str = "0.0.0.0"
    port: int = 8000
    device_tokens: str = ""

    ollama_host: str = "http://localhost:11434"
    ollama_default_model: str = "llama3.1:70b"

    database_url: str = "sqlite:///./data/jarvis.db"
    upload_dir: str = "./data/uploads"

    whisper_model: str = "large-v3"
    whisper_device: str = "cuda"
    whisper_compute_type: str = "int8_float32"

    xtts_model_name: str = "tts_models/multilingual/multi-speaker/xtts_v2"
    xtts_device: str = "cuda"
    xtts_default_language: str = "de"
    xtts_speaker_samples_dir: str = "./data/voices"

    home_assistant_enabled: bool = False
    home_assistant_base_url: str = "http://homeassistant.local:8123"
    home_assistant_token: str = ""

    n8n_enabled: bool = False
    n8n_base_url: str = "http://localhost:5678"
    n8n_webhook_token: str = ""

    home_latitude: float | None = None
    home_longitude: float | None = None
    timezone: str = "Europe/Berlin"

    reminder_poll_interval_seconds: float = 15.0

    @property
    def weather_configured(self) -> bool:
        return self.home_latitude is not None and self.home_longitude is not None

    @property
    def device_token_set(self) -> set[str]:
        return {t.strip() for t in self.device_tokens.split(",") if t.strip()}


@lru_cache
def get_settings() -> Settings:
    return Settings()

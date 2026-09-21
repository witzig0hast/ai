import asyncio
from functools import lru_cache

import numpy as np

from app.config import get_settings


class SttService:
    """faster-whisper wrapper. Model is loaded lazily on first use so the API
    can start and answer /api/status without a GPU/model being ready yet."""

    def __init__(self) -> None:
        self._model = None

    def _ensure_model(self):
        if self._model is None:
            from faster_whisper import WhisperModel

            settings = get_settings()
            self._model = WhisperModel(
                settings.whisper_model,
                device=settings.whisper_device,
                compute_type=settings.whisper_compute_type,
            )
        return self._model

    def is_ready(self) -> bool:
        return self._model is not None

    @staticmethod
    def pcm16_to_float32(pcm_bytes: bytes) -> np.ndarray:
        audio = np.frombuffer(pcm_bytes, dtype=np.int16).astype(np.float32)
        return audio / 32768.0

    def transcribe_pcm16(self, pcm_bytes: bytes, sample_rate: int = 16000) -> str:
        """Synchronous, blocking transcription of a full utterance buffer.
        Run via asyncio.to_thread from async call sites (see app/ws/voice.py)."""
        if not pcm_bytes:
            return ""
        model = self._ensure_model()
        audio = self.pcm16_to_float32(pcm_bytes)
        segments, _info = model.transcribe(
            audio, language=None, vad_filter=False, beam_size=5
        )
        return "".join(segment.text for segment in segments).strip()

    async def transcribe_pcm16_async(self, pcm_bytes: bytes, sample_rate: int = 16000) -> str:
        return await asyncio.to_thread(self.transcribe_pcm16, pcm_bytes, sample_rate)


@lru_cache
def get_stt_service() -> SttService:
    return SttService()

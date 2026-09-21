import asyncio
import queue
import threading
from collections.abc import AsyncIterator
from functools import lru_cache
from pathlib import Path

import numpy as np

from app.config import get_settings

_SENTINEL = object()


class TtsService:
    """Coqui XTTS-v2 wrapper with streaming inference (not Piper - explicitly
    ruled out for how unnatural its voices sound, see docs/architecture.md).

    Loaded lazily: the API can start and answer /api/status without the GPU
    model being resident yet. First synthesis call pays the load cost.
    """

    def __init__(self) -> None:
        self._model = None
        self._config = None
        self._voice_cache: dict[str, tuple] = {}
        self._lock = threading.Lock()

    def is_ready(self) -> bool:
        return self._model is not None

    def _ensure_model(self):
        if self._model is not None:
            return self._model, self._config
        with self._lock:
            if self._model is not None:
                return self._model, self._config

            from TTS.tts.configs.xtts_config import XttsConfig
            from TTS.tts.models.xtts import Xtts
            from TTS.utils.manage import ModelManager

            settings = get_settings()
            manager = ModelManager()
            model_path, config_path, _model_item = manager.download_model(
                settings.xtts_model_name
            )
            config = XttsConfig()
            config.load_json(str(Path(config_path)))
            model = Xtts.init_from_config(config)
            model.load_checkpoint(config, checkpoint_dir=str(model_path), eval=True)
            if settings.xtts_device == "cuda":
                model.cuda()

            self._model = model
            self._config = config
        return self._model, self._config

    def _get_voice_latents(self, voice_id: str):
        if voice_id in self._voice_cache:
            return self._voice_cache[voice_id]

        model, _config = self._ensure_model()
        settings = get_settings()
        wav_path = Path(settings.xtts_speaker_samples_dir) / f"{voice_id}.wav"
        if not wav_path.exists():
            wav_path = Path(settings.xtts_speaker_samples_dir) / "default.wav"
        if not wav_path.exists():
            raise FileNotFoundError(
                f"No XTTS reference sample for voice_id={voice_id!r} and no "
                f"default.wav fallback in {settings.xtts_speaker_samples_dir}. "
                "Drop a ~10s clean speech sample there to enable voice cloning "
                "(see backend/README.md)."
            )
        latents = model.get_conditioning_latents(audio_path=[str(wav_path)])
        self._voice_cache[voice_id] = latents
        return latents

    def _synthesize_stream_blocking(
        self, text: str, voice_id: str, language: str | None
    ):
        """Generator yielding PCM16LE mono 24kHz chunks. Runs on a worker
        thread (see synthesize_stream_async) since Xtts inference is
        blocking/CPU+GPU-bound."""
        model, _config = self._ensure_model()
        settings = get_settings()
        lang = language or settings.xtts_default_language
        gpt_cond_latent, speaker_embedding = self._get_voice_latents(voice_id)

        chunks = model.inference_stream(
            text,
            lang,
            gpt_cond_latent,
            speaker_embedding,
        )
        for chunk in chunks:
            samples = chunk.detach().cpu().numpy().squeeze()
            pcm16 = (np.clip(samples, -1.0, 1.0) * 32767).astype(np.int16)
            yield pcm16.tobytes()

    async def synthesize_stream_async(
        self, text: str, voice_id: str, language: str | None = None
    ) -> AsyncIterator[bytes]:
        """Async-friendly wrapper: runs the blocking generator on a thread
        and forwards chunks through a queue so the FastAPI event loop is
        never blocked by GPU inference."""
        q: queue.Queue = queue.Queue(maxsize=8)
        loop = asyncio.get_event_loop()

        def _worker():
            try:
                for pcm_chunk in self._synthesize_stream_blocking(
                    text, voice_id, language
                ):
                    loop.call_soon_threadsafe(q.put_nowait, pcm_chunk)
            except Exception as exc:  # noqa: BLE001 - forwarded to caller below
                loop.call_soon_threadsafe(q.put_nowait, exc)
            finally:
                loop.call_soon_threadsafe(q.put_nowait, _SENTINEL)

        threading.Thread(target=_worker, daemon=True).start()

        while True:
            item = await asyncio.to_thread(q.get)
            if item is _SENTINEL:
                return
            if isinstance(item, Exception):
                raise item
            yield item


@lru_cache
def get_tts_service() -> TtsService:
    return TtsService()

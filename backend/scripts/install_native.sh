#!/usr/bin/env bash
# Native setup helper for the Jarvis backend on a Debian host with an
# NVIDIA Tesla P40. This does NOT install or manage Ollama - Ollama is
# expected to already run natively on this host (see docs/architecture.md §2)
# so it can be shared with e.g. Open WebUI. Run this from backend/.
set -euo pipefail

if ! command -v nvidia-smi >/dev/null 2>&1; then
  echo "nvidia-smi not found. Install the NVIDIA driver for the Tesla P40 first" >&2
  echo "(Pascal architecture: driver branch 470+ or the current production" >&2
  echo "branch both work; CUDA 11.8/12.x runtimes are fine, the P40 just has" >&2
  echo "no fast fp16/tensor cores, hence the int8 compute types used below)." >&2
  exit 1
fi

echo "== GPU =="
nvidia-smi --query-gpu=name,memory.total,driver_version --format=csv,noheader

PYTHON_BIN="${PYTHON_BIN:-python3.11}"
if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  echo "$PYTHON_BIN not found, set PYTHON_BIN to a Python 3.10/3.11 interpreter" >&2
  exit 1
fi

"$PYTHON_BIN" -m venv .venv
# shellcheck disable=SC1091
source .venv/bin/activate
pip install --upgrade pip

# PyTorch build with CUDA support matching your installed driver/toolkit.
# Adjust the index URL if your host uses a different CUDA runtime.
pip install torch --index-url https://download.pytorch.org/whl/cu121

pip install -r requirements.txt

mkdir -p data/uploads data/voices

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example - edit DEVICE_TOKENS and OLLAMA_HOST before starting."
fi

cat <<'EOF'

Next steps:
  1. Edit backend/.env (DEVICE_TOKENS, OLLAMA_HOST, model names).
  2. Drop a ~10s clean speech WAV sample per agent voice into data/voices/
     (e.g. data/voices/default.wav) for XTTS-v2 voice cloning.
  3. Confirm Ollama is already running natively on this host: `curl $OLLAMA_HOST/api/tags`
  4. Start the API: `.venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000`
     (or install scripts/systemd/jarvis-backend.service for a persistent service)
EOF

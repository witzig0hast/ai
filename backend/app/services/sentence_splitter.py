import re

# Sentence-ending punctuation followed by whitespace/end (optionally a
# closing quote/paren in between). Deliberately simple - good enough to get
# TTS started well before the full LLM answer is generated; not meant to
# perfectly handle every edge case (abbreviations like "z.B.", decimals).
_SENTENCE_END_RE = re.compile(r"[.!?][\"')]?(\s|$)")

# If no sentence boundary shows up for a while (long unpunctuated output,
# e.g. a list or code-like text), force a flush at the last whitespace so
# TTS doesn't stall waiting for punctuation that may never come.
_MAX_BUFFER_BEFORE_FORCE_FLUSH = 220


def split_ready_sentence(buffer: str) -> tuple[str | None, str]:
    """Looks for a sentence boundary in `buffer`. Returns (sentence,
    remainder) if the buffer is ready to speak, else (None, buffer) to keep
    accumulating tokens."""
    match = _SENTENCE_END_RE.search(buffer)
    if match and len(buffer[: match.end()].strip()) >= 3:
        split_at = match.end()
        return buffer[:split_at], buffer[split_at:]

    if len(buffer) > _MAX_BUFFER_BEFORE_FORCE_FLUSH:
        idx = buffer.rfind(" ", 0, _MAX_BUFFER_BEFORE_FORCE_FLUSH)
        if idx > 0:
            return buffer[: idx + 1], buffer[idx + 1 :]

    return None, buffer

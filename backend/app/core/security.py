from fastapi import Header, HTTPException, Query, status

from app.config import get_settings


def require_device_token(
    authorization: str | None = Header(default=None),
) -> str:
    """Auth dependency for REST endpoints: Authorization: Bearer <token>."""
    settings = get_settings()
    if not settings.device_token_set:
        # No tokens configured yet (fresh install) -> auth disabled, but this
        # is loudly logged so nobody ships it like this by accident.
        return "unconfigured"

    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Missing bearer token")

    token = authorization.removeprefix("Bearer ").strip()
    if token not in settings.device_token_set:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid device token")
    return token


def require_device_token_ws(token: str | None = Query(default=None)) -> str:
    """Auth check for the /ws/voice handshake (query param, see docs/architecture.md §3)."""
    settings = get_settings()
    if not settings.device_token_set:
        return "unconfigured"
    if not token or token not in settings.device_token_set:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid or missing token")
    return token

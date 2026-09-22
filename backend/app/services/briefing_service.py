from sqlmodel import Session

from app.database import engine
from app.services import reminder_service
from app.services.ollama_client import OllamaClient
from app.services.weather_service import get_weather_service


async def compose_briefing(agent_model: str) -> dict:
    """Builds the daily briefing (docs/architecture.md §12/§13): weather +
    open reminders + upcoming calendar events (still a stub, see
    routes_calendar.py), phrased into a short spoken-friendly summary by the
    LLM. Used both by GET /api/briefing/today and as the spoken voice-mode
    greeting."""
    weather_service = get_weather_service()
    weather = (
        await weather_service.get_current() if weather_service.is_configured() else None
    )

    with Session(engine) as session:
        reminders = reminder_service.list_reminders(session)
    reminder_texts = [r.text for r in reminders[:5]]

    facts = []
    if weather and "error" not in weather:
        facts.append(f"Wetter: {weather['condition']}, {weather['temperature_c']}°C")
    if reminder_texts:
        facts.append("Offene Reminder: " + "; ".join(reminder_texts))

    if not facts:
        summary = "Aktuell gibt es nichts Besonderes zu berichten."
    else:
        prompt = (
            "Formuliere aus diesen Fakten einen kurzen, natürlichen "
            "Briefing-Satz auf Deutsch (maximal 2 Sätze), der jemandem laut "
            "vorgelesen werden kann. Keine Aufzählungszeichen, keine "
            "Einleitung wie 'Hier ist dein Briefing' - nur der Inhalt.\n\n"
            + "\n".join(facts)
        )
        try:
            client = OllamaClient()
            summary = (await client.chat_once(agent_model, [{"role": "user", "content": prompt}])).strip()
        except Exception:  # noqa: BLE001 - fall back to the raw facts, never fail the greeting
            summary = " ".join(facts)

    return {
        "summary": summary,
        "weather": weather,
        "upcoming_events": [],
        "open_reminders": reminder_texts,
    }

from sqlmodel import Session, select

from app.models.agent import Agent

DEFAULT_AGENT = Agent(
    id="jarvis",
    name="Jarvis",
    description="Dein persönlicher Assistent für den Alltag.",
    system_prompt=(
        "Du bist Jarvis, ein hilfsbereiter, präziser persönlicher Assistent. "
        "Antworte klar und knapp, auf Deutsch, außer explizit anders gewünscht. "
        "Wenn du etwas nicht weißt oder keinen Zugriff auf eine Information hast, "
        "sag das direkt, statt zu spekulieren. Dir stehen Tools für Uhrzeit, "
        "Wetter, Reminder/Timer sowie (falls aktiviert) Home Assistant und n8n "
        "zur Verfügung - nutze sie proaktiv statt zu raten, wenn eine Anfrage "
        "danach klingt."
    ),
    ollama_model="llama3.1:70b",
    voice_id="default",
    avatar_color="#4285F4",
    is_default=True,
)


def ensure_default_agent(session: Session) -> Agent:
    existing = session.get(Agent, DEFAULT_AGENT.id)
    if existing:
        return existing
    session.add(DEFAULT_AGENT)
    session.commit()
    session.refresh(DEFAULT_AGENT)
    return DEFAULT_AGENT


def get_active_agent(session: Session) -> Agent:
    agent = session.exec(select(Agent).where(Agent.is_default == True)).first()  # noqa: E712
    return agent or ensure_default_agent(session)

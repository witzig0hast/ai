from sqlmodel import Session, select

from app.models.memory import MemoryFact

# Keeps the injected system-prompt block bounded - if this is ever hit, the
# oldest facts simply drop out of context rather than growing the prompt
# unbounded. Generous enough for a personal assistant's actual fact count.
MAX_FACTS_IN_PROMPT = 50


def remember(session: Session, text: str) -> MemoryFact:
    fact = MemoryFact(text=text)
    session.add(fact)
    session.commit()
    session.refresh(fact)
    return fact


def list_facts(session: Session) -> list[MemoryFact]:
    return session.exec(select(MemoryFact).order_by(MemoryFact.created_at)).all()


def forget(session: Session, fact_id: str) -> bool:
    fact = session.get(MemoryFact, fact_id)
    if not fact:
        return False
    session.delete(fact)
    session.commit()
    return True


def facts_as_system_message(session: Session) -> str | None:
    """Builds an extra system message injected into every chat/voice turn
    (docs/architecture.md §14) so the agent "remembers" facts across
    conversations without the user re-explaining them each time."""
    facts = list_facts(session)[-MAX_FACTS_IN_PROMPT:]
    if not facts:
        return None
    lines = "\n".join(f"- {f.text}" for f in facts)
    return "Bekannte Fakten über den Nutzer (von dir zuvor selbst gemerkt):\n" + lines

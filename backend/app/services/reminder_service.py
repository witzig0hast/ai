from datetime import datetime, timezone

from sqlmodel import Session, select

from app.models.reminder import Reminder


def _as_utc(value: datetime) -> datetime:
    """Normalize to timezone-aware UTC so stored values compare consistently
    (SQLite has no native datetime type - it stores ISO strings, and mixing
    naive/aware values would break the lexicographic comparison used by
    due_unfired_reminders below)."""
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def create_reminder(
    session: Session, text: str, due_at: datetime, conversation_id: str | None = None
) -> Reminder:
    reminder = Reminder(
        text=text, due_at=_as_utc(due_at), conversation_id=conversation_id
    )
    session.add(reminder)
    session.commit()
    session.refresh(reminder)
    return reminder


def list_reminders(session: Session, include_fired: bool = False) -> list[Reminder]:
    query = select(Reminder).order_by(Reminder.due_at)
    if not include_fired:
        query = query.where(Reminder.fired == False)  # noqa: E712
    return session.exec(query).all()


def cancel_reminder(session: Session, reminder_id: str) -> bool:
    reminder = session.get(Reminder, reminder_id)
    if not reminder:
        return False
    session.delete(reminder)
    session.commit()
    return True


def due_unfired_reminders(session: Session) -> list[Reminder]:
    now = datetime.now(timezone.utc)
    return session.exec(
        select(Reminder).where(Reminder.fired == False, Reminder.due_at <= now)  # noqa: E712
    ).all()

import uuid
from datetime import datetime, timezone

from sqlmodel import Field, SQLModel


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _uuid() -> str:
    return str(uuid.uuid4())


class Reminder(SQLModel, table=True):
    id: str = Field(default_factory=_uuid, primary_key=True)
    text: str
    due_at: datetime
    created_at: datetime = Field(default_factory=_now)
    fired: bool = False
    conversation_id: str | None = Field(default=None, foreign_key="conversation.id")


class ReminderCreate(SQLModel):
    text: str
    due_at: datetime
    conversation_id: str | None = None


class ReminderRead(SQLModel):
    id: str
    text: str
    due_at: datetime
    created_at: datetime
    fired: bool
    conversation_id: str | None

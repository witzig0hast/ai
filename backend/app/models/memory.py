import uuid
from datetime import datetime, timezone

from sqlmodel import Field, SQLModel


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _uuid() -> str:
    return str(uuid.uuid4())


class MemoryFact(SQLModel, table=True):
    id: str = Field(default_factory=_uuid, primary_key=True)
    text: str
    created_at: datetime = Field(default_factory=_now)


class MemoryFactCreate(SQLModel):
    text: str


class MemoryFactRead(SQLModel):
    id: str
    text: str
    created_at: datetime

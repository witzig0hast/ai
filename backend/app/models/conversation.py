import json
import uuid
from datetime import datetime, timezone

from sqlmodel import Field, SQLModel


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _uuid() -> str:
    return str(uuid.uuid4())


class Conversation(SQLModel, table=True):
    id: str = Field(default_factory=_uuid, primary_key=True)
    agent_id: str = Field(foreign_key="agent.id")
    title: str = "Neue Unterhaltung"
    created_at: datetime = Field(default_factory=_now)
    updated_at: datetime = Field(default_factory=_now)


class ConversationRead(SQLModel):
    id: str
    agent_id: str
    title: str
    created_at: datetime
    updated_at: datetime


class ConversationSummary(ConversationRead):
    preview: str = ""


class Message(SQLModel, table=True):
    id: str = Field(default_factory=_uuid, primary_key=True)
    conversation_id: str = Field(foreign_key="conversation.id")
    role: str  # "user" | "assistant"
    content: str
    # Stored as JSON text (attachment ids / suggestions) - SQLite has no native array type.
    attachment_ids_json: str = "[]"
    suggestions_json: str = "[]"
    created_at: datetime = Field(default_factory=_now)

    @property
    def attachment_ids(self) -> list[str]:
        return json.loads(self.attachment_ids_json)

    @attachment_ids.setter
    def attachment_ids(self, value: list[str]) -> None:
        self.attachment_ids_json = json.dumps(value)

    @property
    def suggestions(self) -> list[str]:
        return json.loads(self.suggestions_json)

    @suggestions.setter
    def suggestions(self, value: list[str]) -> None:
        self.suggestions_json = json.dumps(value)


class MessageRead(SQLModel):
    id: str
    conversation_id: str
    role: str
    content: str
    attachment_ids: list[str]
    suggestions: list[str]
    created_at: datetime


class ConversationDetail(ConversationRead):
    messages: list[MessageRead]


class Attachment(SQLModel, table=True):
    id: str = Field(default_factory=_uuid, primary_key=True)
    filename: str
    content_type: str
    size_bytes: int
    stored_path: str

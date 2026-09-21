from sqlmodel import Field, SQLModel


class AgentBase(SQLModel):
    name: str
    description: str = ""
    system_prompt: str
    ollama_model: str
    voice_id: str = "default"
    avatar_color: str = "#4285F4"
    is_default: bool = False


class Agent(AgentBase, table=True):
    id: str = Field(primary_key=True)


class AgentCreate(AgentBase):
    id: str


class AgentUpdate(SQLModel):
    name: str | None = None
    description: str | None = None
    system_prompt: str | None = None
    ollama_model: str | None = None
    voice_id: str | None = None
    avatar_color: str | None = None
    is_default: bool | None = None


class AgentRead(AgentBase):
    id: str

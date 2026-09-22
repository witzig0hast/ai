import json

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlmodel import Session, select
from sse_starlette.sse import EventSourceResponse

from app.core.security import require_device_token
from app.database import engine, get_session
from app.models.agent import Agent
from app.models.conversation import Conversation, Message, MessageRead
from app.services.agent_service import ensure_default_agent
from app.services.ollama_client import OllamaClient
from app.services.suggestions import generate_suggestions
from app.services.tool_loop import run_chat_with_tools

router = APIRouter(prefix="/api/chat", tags=["chat"])


class ChatRequest(BaseModel):
    conversation_id: str | None = None
    agent_id: str
    message: str
    attachment_ids: list[str] = []


@router.post("")
async def chat(
    body: ChatRequest,
    session: Session = Depends(get_session),
    _token: str = Depends(require_device_token),
):
    agent = session.get(Agent, body.agent_id) or ensure_default_agent(session)

    conversation = (
        session.get(Conversation, body.conversation_id)
        if body.conversation_id
        else None
    )
    if conversation is None:
        conversation = Conversation(agent_id=agent.id, title=body.message[:60])
        session.add(conversation)
        session.commit()
        session.refresh(conversation)

    user_message = Message(
        conversation_id=conversation.id, role="user", content=body.message
    )
    user_message.attachment_ids = body.attachment_ids
    session.add(user_message)
    session.commit()

    history = session.exec(
        select(Message)
        .where(Message.conversation_id == conversation.id)
        .order_by(Message.created_at)
    ).all()
    ollama_messages = [{"role": "system", "content": agent.system_prompt}]
    ollama_messages += [{"role": m.role, "content": m.content} for m in history]

    agent_id, agent_model, conversation_id = agent.id, agent.ollama_model, conversation.id

    async def event_stream():
        client = OllamaClient()
        full_text = ""
        try:
            async for event in run_chat_with_tools(
                client, agent_model, ollama_messages, conversation_id
            ):
                if event.kind == "token":
                    full_text += event.text or ""
                    yield {"event": "token", "data": json.dumps({"text": event.text})}
                elif event.kind == "tool_call":
                    yield {
                        "event": "tool_call",
                        "data": json.dumps({"name": event.name, "arguments": event.arguments}),
                    }
                elif event.kind == "tool_result":
                    yield {
                        "event": "tool_result",
                        "data": json.dumps({"name": event.name, "result": event.result}),
                    }
        except Exception as exc:  # noqa: BLE001 - surfaced to the client as-is
            yield {"event": "error", "data": json.dumps({"message": str(exc)})}
            return

        suggestions = await generate_suggestions(client, agent_model, full_text)

        # Fresh session: the Depends(get_session) one may already be closed by
        # the time this generator resumes after the initial response bytes,
        # since it streams past the end of the endpoint function's scope.
        with Session(engine) as write_session:
            assistant_message = Message(
                conversation_id=conversation_id, role="assistant", content=full_text
            )
            assistant_message.suggestions = suggestions
            write_session.add(assistant_message)
            convo = write_session.get(Conversation, conversation_id)
            convo.updated_at = assistant_message.created_at
            write_session.add(convo)
            write_session.commit()
            write_session.refresh(assistant_message)

            yield {
                "event": "done",
                "data": json.dumps(
                    {
                        "message": MessageRead.model_validate(
                            assistant_message
                        ).model_dump(mode="json"),
                        "suggestions": suggestions,
                    }
                ),
            }

    return EventSourceResponse(event_stream())

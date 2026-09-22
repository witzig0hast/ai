import asyncio
import json

from fastapi import APIRouter, Query, WebSocket, WebSocketDisconnect
from sqlmodel import Session, select

from app.config import get_settings
from app.database import engine
from app.models.agent import Agent
from app.models.conversation import Conversation, Message
from app.services.agent_service import ensure_default_agent
from app.services import memory_service
from app.services.briefing_service import compose_briefing
from app.services.ollama_client import OllamaClient
from app.services.sentence_splitter import split_ready_sentence
from app.services.stt_service import get_stt_service
from app.services.suggestions import generate_suggestions
from app.services.tool_loop import run_chat_with_tools
from app.services.tts_service import get_tts_service

router = APIRouter()

# Safety net so a client that never sends end_utterance (e.g. a dropped
# network event) doesn't hang the session forever: ~8s of 16kHz mono PCM16.
_MAX_UTTERANCE_BYTES = 16000 * 2 * 8


class VoiceSession:
    def __init__(self, websocket: WebSocket, agent: Agent, conversation_id: str):
        self.ws = websocket
        self.agent = agent
        self.conversation_id = conversation_id
        self.audio_buffer = bytearray()
        self.responding_task: asyncio.Task | None = None

    async def send_json(self, payload: dict) -> None:
        await self.ws.send_text(json.dumps(payload))

    async def cancel_response(self) -> None:
        """Used for barge-in: stop whatever LLM/TTS streaming is in flight."""
        if self.responding_task and not self.responding_task.done():
            self.responding_task.cancel()
            try:
                await self.responding_task
            except asyncio.CancelledError:
                pass
        self.responding_task = None


@router.websocket("/ws/voice")
async def voice_ws(
    websocket: WebSocket,
    agent_id: str = Query(...),
    conversation_id: str | None = Query(default=None),
    token: str | None = Query(default=None),
) -> None:
    settings = get_settings()
    if settings.device_token_set and token not in settings.device_token_set:
        await websocket.close(code=4401)
        return

    await websocket.accept()

    with Session(engine) as session:
        agent = session.get(Agent, agent_id) or ensure_default_agent(session)
        conversation = (
            session.get(Conversation, conversation_id) if conversation_id else None
        )
        if conversation is None:
            conversation = Conversation(agent_id=agent.id, title="Sprachgespräch")
            session.add(conversation)
            session.commit()
            session.refresh(conversation)
        resolved_conversation_id = conversation.id

    voice_session = VoiceSession(websocket, agent, resolved_conversation_id)
    await voice_session.send_json(
        {"type": "session_ready", "conversation_id": resolved_conversation_id}
    )
    voice_session.responding_task = asyncio.create_task(_speak_greeting(voice_session))

    try:
        while True:
            message = await websocket.receive()
            if message["type"] == "websocket.disconnect":
                break

            text = message.get("text")
            data = message.get("bytes")

            if text is not None:
                await _handle_text_frame(voice_session, text)
                if json.loads(text).get("type") == "stop":
                    break
            elif data is not None:
                voice_session.audio_buffer.extend(data)
                if len(voice_session.audio_buffer) > _MAX_UTTERANCE_BYTES:
                    await _start_utterance_response(voice_session)
    except WebSocketDisconnect:
        pass
    finally:
        await voice_session.cancel_response()


async def _handle_text_frame(session: VoiceSession, raw_text: str) -> None:
    try:
        payload = json.loads(raw_text)
    except json.JSONDecodeError:
        return

    msg_type = payload.get("type")
    if msg_type == "start":
        session.audio_buffer = bytearray()
    elif msg_type == "end_utterance":
        await _start_utterance_response(session)
    elif msg_type == "barge_in":
        await session.cancel_response()
        await session.send_json({"type": "tts_end"})
    # "stop" is handled by the caller (closes the connection).


async def _start_utterance_response(session: VoiceSession) -> None:
    await session.cancel_response()
    utterance = bytes(session.audio_buffer)
    session.audio_buffer = bytearray()
    if utterance:
        session.responding_task = asyncio.create_task(
            _handle_utterance(session, utterance)
        )


async def _speak_greeting(session: VoiceSession) -> None:
    briefing = await compose_briefing(session.agent.ollama_model)
    greeting_text = f"Hallo, hier ist {session.agent.name}. {briefing['summary']}".strip()
    await session.send_json({"type": "greeting_text", "text": greeting_text})
    await session.send_json({"type": "tts_start"})
    await _speak_chunk(session, greeting_text)
    await session.send_json({"type": "tts_end"})


async def _handle_utterance(session: VoiceSession, pcm_bytes: bytes) -> None:
    stt = get_stt_service()
    transcript = await stt.transcribe_pcm16_async(pcm_bytes)
    await session.send_json({"type": "transcript_final", "text": transcript})

    if not transcript.strip():
        return

    with Session(engine) as db:
        user_message = Message(
            conversation_id=session.conversation_id, role="user", content=transcript
        )
        db.add(user_message)
        db.commit()

        history = db.exec(
            select(Message)
            .where(Message.conversation_id == session.conversation_id)
            .order_by(Message.created_at)
        ).all()

        memory_block = memory_service.facts_as_system_message(db)

    ollama_messages = [{"role": "system", "content": session.agent.system_prompt}]
    if memory_block:
        ollama_messages.append({"role": "system", "content": memory_block})
    ollama_messages += [{"role": m.role, "content": m.content} for m in history]

    client = OllamaClient()
    full_text = ""
    sentence_buffer = ""
    tts_started = False

    async def flush_sentence(text: str) -> None:
        nonlocal tts_started
        text = text.strip()
        if not text:
            return
        if not tts_started:
            await session.send_json({"type": "tts_start"})
            tts_started = True
        await _speak_chunk(session, text)

    try:
        async for event in run_chat_with_tools(
            client, session.agent.ollama_model, ollama_messages, session.conversation_id
        ):
            if event.kind == "tool_call":
                await session.send_json(
                    {"type": "tool_call", "name": event.name, "arguments": event.arguments}
                )
            elif event.kind == "tool_result":
                await session.send_json(
                    {"type": "tool_result", "name": event.name, "result": event.result}
                )
            elif event.kind == "token":
                token = event.text or ""
                full_text += token
                await session.send_json({"type": "llm_token", "text": token})

                sentence_buffer += token
                # Speak completed sentences as soon as they're ready instead
                # of waiting for the whole answer - cuts perceived latency
                # in the live voice dialog (docs/architecture.md §9/§6).
                sentence, sentence_buffer = split_ready_sentence(sentence_buffer)
                if sentence:
                    await flush_sentence(sentence)
    except asyncio.CancelledError:
        raise
    except Exception as exc:  # noqa: BLE001 - surfaced to the client as-is
        await session.send_json({"type": "error", "message": str(exc)})
        return

    await flush_sentence(sentence_buffer)
    if tts_started:
        await session.send_json({"type": "tts_end"})

    suggestions = await generate_suggestions(client, session.agent.ollama_model, full_text)

    with Session(engine) as db:
        assistant_message = Message(
            conversation_id=session.conversation_id, role="assistant", content=full_text
        )
        assistant_message.suggestions = suggestions
        db.add(assistant_message)
        convo = db.get(Conversation, session.conversation_id)
        convo.updated_at = assistant_message.created_at
        db.add(convo)
        db.commit()

    await session.send_json(
        {"type": "llm_done", "full_text": full_text, "suggestions": suggestions}
    )


async def _speak_chunk(session: VoiceSession, text: str) -> None:
    """Synthesizes and streams one chunk (sentence or greeting) of speech.
    Does NOT send tts_start/tts_end - callers bracket a whole response
    (which may be several chunks) with those, per docs/architecture.md §6."""
    if not text.strip():
        return
    tts = get_tts_service()
    try:
        async for chunk in tts.synthesize_stream_async(text, session.agent.voice_id):
            await session.ws.send_bytes(chunk)
    except asyncio.CancelledError:
        raise
    except Exception as exc:  # noqa: BLE001
        await session.send_json({"type": "error", "message": str(exc)})

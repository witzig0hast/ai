import json
from collections.abc import AsyncIterator
from dataclasses import dataclass

from app.services.ollama_client import OllamaClient
from app.services.tools import TOOL_SCHEMAS, execute_tool

# Bounds runaway tool-call loops (a model that keeps asking for tools without
# ever producing a final answer). After this many rounds we force a plain,
# tool-less final answer instead of looping forever.
MAX_TOOL_ROUNDS = 4


@dataclass
class ToolLoopEvent:
    kind: str  # "token" | "tool_call" | "tool_result"
    text: str | None = None
    name: str | None = None
    arguments: dict | None = None
    result: dict | None = None


async def run_chat_with_tools(
    client: OllamaClient,
    model: str,
    messages: list[dict],
    conversation_id: str | None,
) -> AsyncIterator[ToolLoopEvent]:
    """Agentic loop shared by POST /api/chat and /ws/voice (docs/architecture.md
    §9): stream from Ollama with tools enabled; if the model asks for tool
    calls instead of producing content, execute them, feed the results back
    as `tool` messages, and ask again - up to MAX_TOOL_ROUNDS times. Plain
    conversational turns (the common case) never hit a tool round and stream
    token-by-token exactly as before this feature existed.
    """
    working_messages = list(messages)

    for _round in range(MAX_TOOL_ROUNDS):
        tool_calls: list[dict] = []
        content_buffer = ""
        saw_tool_calls = False

        async for chunk in client.chat_stream_raw(model, working_messages, TOOL_SCHEMAS):
            message = chunk.get("message", {})
            calls = message.get("tool_calls")
            if calls:
                saw_tool_calls = True
                tool_calls.extend(calls)
                continue

            token = message.get("content", "")
            if token and not saw_tool_calls:
                content_buffer += token
                yield ToolLoopEvent(kind="token", text=token)

        if not saw_tool_calls:
            return  # content already streamed above - done

        working_messages.append(
            {"role": "assistant", "content": content_buffer, "tool_calls": tool_calls}
        )

        for call in tool_calls:
            fn = call.get("function", {})
            name = fn.get("name", "")
            arguments = _coerce_arguments(fn.get("arguments"))

            yield ToolLoopEvent(kind="tool_call", name=name, arguments=arguments)
            result = await execute_tool(name, arguments, conversation_id)
            yield ToolLoopEvent(kind="tool_result", name=name, result=result)

            working_messages.append(
                {"role": "tool", "content": json.dumps(result, ensure_ascii=False)}
            )

    # Ran out of rounds while the model kept requesting tools - force one
    # final, tool-less answer so the user isn't left hanging.
    async for token in client.chat_stream(model, working_messages):
        yield ToolLoopEvent(kind="token", text=token)


def _coerce_arguments(raw) -> dict:
    if isinstance(raw, dict):
        return raw
    if isinstance(raw, str):
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return {}
    return {}

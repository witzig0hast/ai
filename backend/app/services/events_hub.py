import asyncio


class EventsHub:
    """In-process pub/sub for the proactive /ws/events channel
    (docs/architecture.md §11). Single-process backend, so a plain in-memory
    set of subscriber queues is enough - no external broker needed."""

    def __init__(self) -> None:
        self._subscribers: set[asyncio.Queue] = set()

    def subscribe(self) -> asyncio.Queue:
        queue: asyncio.Queue = asyncio.Queue(maxsize=50)
        self._subscribers.add(queue)
        return queue

    def unsubscribe(self, queue: asyncio.Queue) -> None:
        self._subscribers.discard(queue)

    async def publish(self, event: dict) -> None:
        for queue in list(self._subscribers):
            try:
                queue.put_nowait(event)
            except asyncio.QueueFull:
                pass  # a slow/stuck client shouldn't block the others


_hub = EventsHub()


def get_events_hub() -> EventsHub:
    return _hub

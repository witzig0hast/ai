from datetime import datetime

from pydantic import BaseModel


class CalendarEvent(BaseModel):
    title: str
    start_time: datetime
    location: str | None = None

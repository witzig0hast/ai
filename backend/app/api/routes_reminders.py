from fastapi import APIRouter, Depends, HTTPException, status
from sqlmodel import Session

from app.core.security import require_device_token
from app.database import get_session
from app.models.reminder import ReminderCreate, ReminderRead
from app.services import reminder_service

router = APIRouter(
    prefix="/api/reminders", tags=["reminders"], dependencies=[Depends(require_device_token)]
)


@router.get("", response_model=list[ReminderRead])
def list_reminders(include_fired: bool = False, session: Session = Depends(get_session)):
    return reminder_service.list_reminders(session, include_fired=include_fired)


@router.post("", response_model=ReminderRead, status_code=status.HTTP_201_CREATED)
def create_reminder(body: ReminderCreate, session: Session = Depends(get_session)):
    return reminder_service.create_reminder(
        session, body.text, body.due_at, body.conversation_id
    )


@router.delete("/{reminder_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_reminder(reminder_id: str, session: Session = Depends(get_session)):
    if not reminder_service.cancel_reminder(session, reminder_id):
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Reminder not found")

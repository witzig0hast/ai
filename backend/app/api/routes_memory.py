from fastapi import APIRouter, Depends, HTTPException, status
from sqlmodel import Session

from app.core.security import require_device_token
from app.database import get_session
from app.models.memory import MemoryFactCreate, MemoryFactRead
from app.services import memory_service

router = APIRouter(
    prefix="/api/memory", tags=["memory"], dependencies=[Depends(require_device_token)]
)


@router.get("", response_model=list[MemoryFactRead])
def list_facts(session: Session = Depends(get_session)):
    return memory_service.list_facts(session)


@router.post("", response_model=MemoryFactRead, status_code=status.HTTP_201_CREATED)
def create_fact(body: MemoryFactCreate, session: Session = Depends(get_session)):
    return memory_service.remember(session, body.text)


@router.delete("/{fact_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_fact(fact_id: str, session: Session = Depends(get_session)):
    if not memory_service.forget(session, fact_id):
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Fact not found")

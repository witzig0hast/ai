from fastapi import APIRouter, Depends, HTTPException, status
from sqlmodel import Session, select

from app.core.security import require_device_token
from app.database import get_session
from app.models.conversation import (
    Conversation,
    ConversationDetail,
    ConversationSummary,
    Message,
    MessageRead,
)

router = APIRouter(
    prefix="/api/conversations",
    tags=["conversations"],
    dependencies=[Depends(require_device_token)],
)


@router.get("", response_model=list[ConversationSummary])
def list_conversations(session: Session = Depends(get_session)):
    conversations = session.exec(
        select(Conversation).order_by(Conversation.updated_at.desc())
    ).all()
    summaries = []
    for convo in conversations:
        last_message = session.exec(
            select(Message)
            .where(Message.conversation_id == convo.id)
            .order_by(Message.created_at.desc())
        ).first()
        summaries.append(
            ConversationSummary(
                **convo.model_dump(),
                preview=(last_message.content[:120] if last_message else ""),
            )
        )
    return summaries


@router.get("/{conversation_id}", response_model=ConversationDetail)
def get_conversation(conversation_id: str, session: Session = Depends(get_session)):
    convo = session.get(Conversation, conversation_id)
    if not convo:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Conversation not found")
    messages = session.exec(
        select(Message)
        .where(Message.conversation_id == conversation_id)
        .order_by(Message.created_at)
    ).all()
    return ConversationDetail(
        **convo.model_dump(),
        messages=[
            MessageRead(
                id=m.id,
                conversation_id=m.conversation_id,
                role=m.role,
                content=m.content,
                attachment_ids=m.attachment_ids,
                suggestions=m.suggestions,
                created_at=m.created_at,
            )
            for m in messages
        ],
    )


@router.delete("/{conversation_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_conversation(conversation_id: str, session: Session = Depends(get_session)):
    convo = session.get(Conversation, conversation_id)
    if not convo:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Conversation not found")
    messages = session.exec(
        select(Message).where(Message.conversation_id == conversation_id)
    ).all()
    for m in messages:
        session.delete(m)
    session.delete(convo)
    session.commit()

from pathlib import Path

from fastapi import APIRouter, Depends, UploadFile
from sqlmodel import Session

from app.config import get_settings
from app.core.security import require_device_token
from app.database import get_session
from app.models.conversation import Attachment

router = APIRouter(prefix="/api/files", tags=["files"], dependencies=[Depends(require_device_token)])


@router.post("")
async def upload_file(file: UploadFile, session: Session = Depends(get_session)):
    settings = get_settings()
    attachment = Attachment(
        filename=file.filename or "upload",
        content_type=file.content_type or "application/octet-stream",
        size_bytes=0,
        stored_path="",
    )
    dest = Path(settings.upload_dir) / attachment.id
    contents = await file.read()
    dest.write_bytes(contents)

    attachment.size_bytes = len(contents)
    attachment.stored_path = str(dest)
    session.add(attachment)
    session.commit()
    session.refresh(attachment)

    return {
        "file_id": attachment.id,
        "filename": attachment.filename,
        "content_type": attachment.content_type,
    }

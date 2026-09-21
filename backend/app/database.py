from pathlib import Path

from sqlmodel import Session, SQLModel, create_engine

from app.config import get_settings

settings = get_settings()

Path(settings.upload_dir).mkdir(parents=True, exist_ok=True)
Path(settings.xtts_speaker_samples_dir).mkdir(parents=True, exist_ok=True)
if settings.database_url.startswith("sqlite:///./"):
    Path(settings.database_url.removeprefix("sqlite:///")).parent.mkdir(
        parents=True, exist_ok=True
    )

connect_args = (
    {"check_same_thread": False} if settings.database_url.startswith("sqlite") else {}
)
engine = create_engine(settings.database_url, connect_args=connect_args)


def init_db() -> None:
    SQLModel.metadata.create_all(engine)


def get_session():
    with Session(engine) as session:
        yield session

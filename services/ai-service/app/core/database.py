"""
SQLAlchemy database engine, session, and Base.

Engine creation is deferred when DATABASE_URL is empty so that tests
(which override engine/SessionLocal in conftest.py) do not crash at import time.
"""
from sqlalchemy import create_engine
from sqlalchemy.orm import declarative_base, sessionmaker

from app.core.config import settings

if settings.DATABASE_URL:
    engine = create_engine(settings.DATABASE_URL, pool_pre_ping=True)
    SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
else:
    # Placeholder — tests will override via conftest.py
    engine = None  # type: ignore[assignment]
    SessionLocal = None  # type: ignore[assignment]

Base = declarative_base()


def get_db():
    """FastAPI dependency — yields a DB session and closes it after request."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

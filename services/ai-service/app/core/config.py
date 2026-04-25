"""
Application settings loaded from environment variables.
"""
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """AI Service configuration."""

    DATABASE_URL: str = "postgresql://callmgmt:callmgmt_secret_2024@localhost:5432/ai_db"
    AUDIO_STORAGE_PATH: str = "./audio_storage"
    MAX_AUDIO_SIZE_MB: int = 50
    WHISPER_MODEL: str = "small"

    # Eureka settings
    EUREKA_SERVER: str = "http://localhost:8761/eureka"
    APP_NAME: str = "ai-service"
    INSTANCE_PORT: int = 8000

    # LLM settings (Gemma 4 via Docker Model Runner / Ollama)
    LLM_BASE_URL: str = "http://host.docker.internal:12434/engines/llama.cpp/v1"
    LLM_API_KEY: str = "docker-model-runner"
    LLM_MODEL: str = "ai/gemma4"
    LLM_TIMEOUT: int = 180  # Gemma 4 on CPU can take 60-120s for RAG prompts

    # Fallback LLM (OpenAI) — used when local Gemma 4 is too slow or unavailable
    OPENAI_API_KEY: str = ""
    OPENAI_MODEL: str = "gpt-4o-mini"
    OPENAI_BASE_URL: str = "https://api.openai.com/v1"
    OPENAI_TIMEOUT: int = 30

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


settings = Settings()

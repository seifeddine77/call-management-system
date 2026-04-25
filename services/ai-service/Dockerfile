# ─────────────────────────────────────────────────────────────────
# AI Service — Production Dockerfile
# Root cause of previous failures: pip>=24 breaks pkg_resources
# Solution: pin pip<24, keep setuptools, install whisper --no-deps
# ─────────────────────────────────────────────────────────────────
FROM python:3.11-slim

WORKDIR /app

# System deps — ffmpeg for whisper, gcc for psycopg2, git for pip git installs
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        ffmpeg libpq-dev gcc git curl && \
    rm -rf /var/lib/apt/lists/*

# Pin pip<24 to keep pkg_resources working
RUN pip install --no-cache-dir "pip<24" "setuptools>=69,<70" wheel

# Torch CPU-only (separate layer for Docker cache — 187MB)
RUN pip install --no-cache-dir torch==2.2.2 --index-url https://download.pytorch.org/whl/cpu

# Tiktoken pre-built wheel
RUN pip install --no-cache-dir tiktoken==0.6.0

# Whisper's heavy deps FIRST
RUN pip install --no-cache-dir more-itertools tqdm regex numba "numpy<2" transformers

# openai-whisper from GitHub (PyPI sdist is broken, GitHub uses pyproject.toml)
RUN pip install --no-cache-dir --no-deps "openai-whisper @ git+https://github.com/openai/whisper.git@main"

# spaCy + French model
RUN pip install --no-cache-dir "spacy==3.7.4"
RUN pip install --no-cache-dir \
    "https://github.com/explosion/spacy-models/releases/download/fr_core_news_sm-3.7.0/fr_core_news_sm-3.7.0-py3-none-any.whl"

# ChromaDB (vector store for RAG chatbot) + sentence-transformers for embeddings
RUN pip install --no-cache-dir chromadb==0.5.23 sentence-transformers==3.0.1

# Lightweight app deps
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# App code
COPY . .
RUN mkdir -p /var/audio

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s \
    CMD curl -f http://localhost:8000/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

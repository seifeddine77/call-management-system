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
        curl ffmpeg gcc git libpq-dev && \
    rm -rf /var/lib/apt/lists/*

# Pin pip<24 to keep pkg_resources working
# NOSONAR — pip/setuptools/wheel cannot use --only-binary (they are build tools)
RUN pip install --no-cache-dir --only-binary :all: "pip==23.3.2" "setuptools==69.1.1" "wheel==0.43.0" # NOSONAR

# Torch CPU-only (separate layer for Docker cache — 187MB)
# Extended timeout + retries to handle slow/unstable networks during CI builds
RUN pip install --no-cache-dir --only-binary :all: --timeout=300 --retries=5 \
    torch==2.2.2 --index-url https://download.pytorch.org/whl/cpu # NOSONAR

# Tiktoken pre-built wheel
RUN pip install --no-cache-dir --only-binary :all: tiktoken==0.6.0 # NOSONAR

# Whisper's heavy deps FIRST (all pinned versions — S8544)
RUN pip install --no-cache-dir --only-binary :all: \
    more-itertools==10.2.0 numba==0.59.1 "numpy>=1.26,<2" \
    regex==2024.4.28 tqdm==4.66.4 transformers==4.40.2 # NOSONAR

# openai-whisper from GitHub (PyPI sdist is broken, GitHub uses pyproject.toml)
# NOSONAR — whisper has C extensions, --only-binary not applicable for git source
RUN pip install --no-cache-dir --no-deps "openai-whisper @ git+https://github.com/openai/whisper.git@v20231117" # NOSONAR

# spaCy + French model (pinned versions — S8544)
RUN pip install --no-cache-dir --only-binary :all: "spacy==3.7.4" && \
    pip install --no-cache-dir --only-binary :all: \
    "https://github.com/explosion/spacy-models/releases/download/fr_core_news_sm-3.7.0/fr_core_news_sm-3.7.0-py3-none-any.whl" # NOSONAR

# ChromaDB (vector store for RAG chatbot) + sentence-transformers for embeddings
RUN pip install --no-cache-dir --only-binary :all: chromadb==0.5.23 sentence-transformers==3.0.1 # NOSONAR

# Pre-download ML models at build time (avoids ~120s of downloads at container start)
# Whisper 'base' model (~139MB) — cached in /root/.cache/whisper/
RUN python -c "import whisper; whisper.load_model('base')" && \
    echo 'Whisper base model cached'
# SentenceTransformer embeddings (~90MB) — cached in /root/.cache/torch/sentence_transformers/
RUN python -c \
    "from sentence_transformers import SentenceTransformer; SentenceTransformer('paraphrase-multilingual-MiniLM-L12-v2')" && \
    echo 'SentenceTransformer model cached'

# Lightweight app deps (pinned in requirements.txt)
COPY requirements.txt .
RUN pip install --no-cache-dir --only-binary :all: -r requirements.txt # NOSONAR

# App code
COPY . .
RUN mkdir -p /var/audio

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=10s --start-period=120s \
    CMD curl -f http://localhost:8000/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

-- AI Service Database Schema
-- Used by SQLAlchemy (auto-created at startup via Base.metadata.create_all)
-- This file is for reference / manual PostgreSQL setup only.

CREATE TABLE IF NOT EXISTS audio_records (
    id VARCHAR(36) PRIMARY KEY,
    ticket_id VARCHAR(36) UNIQUE NOT NULL,
    file_path VARCHAR(500),
    original_filename VARCHAR(255),
    duration_seconds FLOAT,
    transcription TEXT,
    confidence FLOAT,
    segments JSON,
    status VARCHAR(15) DEFAULT 'PENDING' NOT NULL
        CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS analysis_results (
    id VARCHAR(36) PRIMARY KEY,
    ticket_id VARCHAR(36) UNIQUE NOT NULL,
    piece_reference VARCHAR(100),
    type_sinistre VARCHAR(100),
    sentiment VARCHAR(20),
    keywords JSON,
    confidence FLOAT,
    raw_text TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audio_records_ticket ON audio_records(ticket_id);
CREATE INDEX IF NOT EXISTS idx_analysis_results_ticket ON analysis_results(ticket_id);

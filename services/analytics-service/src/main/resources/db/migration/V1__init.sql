CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE daily_stats (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    date DATE NOT NULL UNIQUE,
    total_tickets INT DEFAULT 0,
    tickets_resolus INT DEFAULT 0,
    temps_moyen_minutes DECIMAL(8,2),
    decisions_favorable INT DEFAULT 0,
    decisions_defavorable INT DEFAULT 0,
    decisions_etude INT DEFAULT 0,
    tickets_call INT DEFAULT 0,
    tickets_email INT DEFAULT 0,
    tickets_web INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_daily_stats_date ON daily_stats(date DESC);

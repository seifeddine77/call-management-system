CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE decisions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ticket_id UUID UNIQUE NOT NULL,
    decision VARCHAR(15) NOT NULL CHECK (decision IN ('FAVORABLE', 'DEFAVORABLE', 'ETUDE')),
    score DECIMAL(5,2) CHECK (score BETWEEN 0 AND 100),
    motif TEXT NOT NULL,
    source VARCHAR(10) NOT NULL CHECK (source IN ('RULES', 'AI', 'HYBRID')),
    rules_applied JSONB,
    final_decision VARCHAR(15) CHECK (final_decision IN ('FAVORABLE', 'DEFAVORABLE')),
    override_motif TEXT,
    override_by UUID,
    override_by_name VARCHAR(100),
    overridden_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE letters (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    decision_id UUID NOT NULL REFERENCES decisions(id),
    file_path VARCHAR(500) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('FAVORABLE', 'DEFAVORABLE')),
    sent_by_email BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_decisions_ticket ON decisions(ticket_id);
CREATE INDEX idx_letters_decision ON letters(decision_id);

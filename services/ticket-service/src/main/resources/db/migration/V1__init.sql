CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================
-- Table: tickets
-- ============================================
CREATE TABLE tickets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    numero VARCHAR(30) UNIQUE NOT NULL,
    client_nom VARCHAR(200) NOT NULL,
    client_tel VARCHAR(20),
    client_email VARCHAR(255),
    canal VARCHAR(10) NOT NULL CHECK (canal IN ('CALL', 'EMAIL', 'WEB')),
    statut VARCHAR(15) NOT NULL DEFAULT 'NOUVEAU' CHECK (statut IN ('NOUVEAU', 'EN_COURS', 'RESOLU', 'FERME')),
    priorite SMALLINT DEFAULT 3 CHECK (priorite BETWEEN 1 AND 5),
    operateur_id UUID,
    agence_id UUID NOT NULL,
    description TEXT,
    audio_path VARCHAR(500),
    transcription TEXT,
    transcription_keywords JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_tickets_statut_created ON tickets (statut, created_at DESC);
CREATE INDEX idx_tickets_operateur ON tickets (operateur_id);
CREATE INDEX idx_tickets_agence ON tickets (agence_id);
CREATE INDEX idx_tickets_canal ON tickets (canal);
CREATE INDEX idx_tickets_numero ON tickets (numero);

-- ============================================
-- Table: ticket_actions
-- ============================================
CREATE TABLE ticket_actions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ticket_id UUID NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    user_name VARCHAR(100),
    action VARCHAR(50) NOT NULL,
    old_value VARCHAR(100),
    new_value VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_ticket_actions_ticket ON ticket_actions (ticket_id);

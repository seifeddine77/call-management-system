-- =============================================
-- V2: Table locale des utilisateurs (fallback)
-- Utilisée quand user-service est indisponible
-- Sera syncronisée avec user-service une fois implémenté
-- =============================================
CREATE TABLE IF NOT EXISTS local_users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    telephone VARCHAR(20),
    role VARCHAR(20) NOT NULL CHECK(role IN('ADMIN','SUPERVISOR','OPERATOR')),
    agence_id UUID,
    actif BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_local_users_email ON local_users(email);

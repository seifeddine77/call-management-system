CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE agences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nom VARCHAR(200) NOT NULL,
    adresse TEXT,
    telephone VARCHAR(20),
    email VARCHAR(255),
    responsable_id UUID,
    actif BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    telephone VARCHAR(20),
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'SUPERVISOR', 'OPERATOR')),
    agence_id UUID REFERENCES agences(id),
    actif BOOLEAN DEFAULT true,
    last_login TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_agence_id ON users(agence_id);

-- Insert default admin user (password: admin123 — BCrypt hash)
INSERT INTO users (email, password_hash, full_name, role, actif) VALUES
('admin@callmgmt.com', '$2a$12$LJ3m4Ms3VG4vFC9cKxzIpehLXMwDNMspzQp0Kl7J9oQi5YDdBq.lW', 'Administrateur', 'ADMIN', true);

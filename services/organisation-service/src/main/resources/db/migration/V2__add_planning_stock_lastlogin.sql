-- V2: Ajouts Module 1 - planning, stock_description, last_login
ALTER TABLE users ADD COLUMN IF NOT EXISTS planning TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login TIMESTAMP;

ALTER TABLE agences ADD COLUMN IF NOT EXISTS stock_description TEXT;

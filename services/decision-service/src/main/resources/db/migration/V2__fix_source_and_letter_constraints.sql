-- ============================================
-- V2: Fix source column constraints + letter unique
-- Fixes:
--   1. source VARCHAR(10) → VARCHAR(20) for CIRCUIT_BREAKER (15 chars)
--   2. CHECK constraint: add CIRCUIT_BREAKER to allowed values
--   3. letters.decision_id: add UNIQUE constraint (1 letter per decision)
-- ============================================

-- 1. Widen source column from VARCHAR(10) to VARCHAR(20)
ALTER TABLE decisions ALTER COLUMN source TYPE VARCHAR(20);

-- 2. Drop old CHECK and add new one with CIRCUIT_BREAKER
ALTER TABLE decisions DROP CONSTRAINT IF EXISTS decisions_source_check;
ALTER TABLE decisions ADD CONSTRAINT decisions_source_check
    CHECK (source IN ('RULES', 'AI', 'HYBRID', 'CIRCUIT_BREAKER'));

-- 3. Add UNIQUE constraint on letters.decision_id (1 letter per decision)
ALTER TABLE letters ADD CONSTRAINT uk_letters_decision_id UNIQUE (decision_id);

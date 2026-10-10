-- =====================================================================
-- ClassSync Migration V3
-- Description: Add active column to facilities table
-- =====================================================================

ALTER TABLE facilities ADD COLUMN active BOOLEAN DEFAULT TRUE NOT NULL;

CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- 1. Register the extension
CREATE EXTENSION IF NOT EXISTS temporal_tables;

-- 2. Setup a helper function (Optional but recommended)
-- This makes it one-line simple to enable temporal tracking on any table.
CREATE OR REPLACE FUNCTION enable_temporal_versioning(target_table text) RETURNS void AS $$
BEGIN
    -- Add the system period column
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS sys_period tstzrange NOT NULL DEFAULT tstzrange(current_timestamp, null)', target_table);

    -- Create the history table (matching the main table schema)
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I_history (LIKE %I)', target_table, target_table);

    -- Attach the versioning trigger
    -- The 'true' parameter allows the trigger to adjust timestamps to avoid overlaps
    EXECUTE format('DROP TRIGGER IF EXISTS versioning_trigger ON %I', target_table);
    EXECUTE format('CREATE TRIGGER versioning_trigger BEFORE INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE PROCEDURE versioning(%L, %L, true)',
                   target_table, 'sys_period', target_table || '_history');
END;
$$ LANGUAGE plpgsql;
DO $script$
DECLARE
  app_user text := '${applicationUser}';
  tbl record;
  seq record;
BEGIN
  -- Grant CRUD on all tables in public schema
  FOR tbl IN
    SELECT table_schema, table_name
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_type = 'BASE TABLE'
      AND table_name NOT LIKE 'databasechangelog%'
  LOOP
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE %I.%I TO %I;',
                   tbl.table_schema, tbl.table_name, app_user);
  END LOOP;

  -- Grant USAGE on all sequences in public schema
  FOR seq IN
    SELECT namespace.nspname AS sequence_schema,
           sequence.relname AS sequence_name
    FROM pg_catalog.pg_class sequence
    JOIN pg_catalog.pg_namespace namespace ON namespace.oid = sequence.relnamespace
    WHERE namespace.nspname = 'public'
      AND sequence.relkind = 'S'
  LOOP
    EXECUTE format('GRANT USAGE ON SEQUENCE %I.%I TO %I;',
                   seq.sequence_schema, seq.sequence_name, app_user);
  END LOOP;
END
$script$;

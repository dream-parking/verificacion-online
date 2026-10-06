-- =====================================================================
--  CEIBA · Mueve todos los objetos del esquema ceiba a public y elimina ceiba.
--  V2–V4 no se editan (checksum de Flyway): siguen creando en ceiba y esta
--  migración los traslada. Índices, constraints, defaults, triggers y las
--  secuencias de los serial viajan con su tabla; las vistas siguen apuntando
--  a sus tablas porque PostgreSQL las enlaza por OID.
-- =====================================================================

DO $$
DECLARE
  r record;
BEGIN
  -- Enums (los tipos fila y arreglo de las tablas se mueven con la tabla).
  FOR r IN SELECT t.typname FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
           WHERE n.nspname = 'ceiba' AND t.typtype IN ('e', 'd') LOOP
    EXECUTE format('ALTER TYPE ceiba.%I SET SCHEMA public', r.typname);
  END LOOP;

  FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
           WHERE n.nspname = 'ceiba' AND c.relkind IN ('r', 'p') LOOP
    EXECUTE format('ALTER TABLE ceiba.%I SET SCHEMA public', r.relname);
  END LOOP;

  FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
           WHERE n.nspname = 'ceiba' AND c.relkind = 'v' LOOP
    EXECUTE format('ALTER VIEW ceiba.%I SET SCHEMA public', r.relname);
  END LOOP;

  FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
           WHERE n.nspname = 'ceiba' AND c.relkind = 'm' LOOP
    EXECUTE format('ALTER MATERIALIZED VIEW ceiba.%I SET SCHEMA public', r.relname);
  END LOOP;

  -- Secuencias sueltas (las de serial ya se movieron con su tabla).
  FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
           WHERE n.nspname = 'ceiba' AND c.relkind = 'S' LOOP
    EXECUTE format('ALTER SEQUENCE ceiba.%I SET SCHEMA public', r.relname);
  END LOOP;

  -- Funciones: además del esquema, su search_path fijo apuntaba a ceiba.
  FOR r IN SELECT p.oid::regprocedure AS firma FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
           WHERE n.nspname = 'ceiba' LOOP
    EXECUTE format('ALTER ROUTINE %s SET search_path = public', r.firma);
    EXECUTE format('ALTER ROUTINE %s SET SCHEMA public', r.firma);
  END LOOP;
END $$;

-- RESTRICT: si algo quedó sin mover, la migración falla en vez de borrarlo.
DROP SCHEMA ceiba RESTRICT;

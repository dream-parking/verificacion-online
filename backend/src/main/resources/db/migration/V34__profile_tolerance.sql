-- VDI-137 (story VDI-5): the tolerance of the declared profile tracking (VDI-3) is changed from the console without a
-- deploy. Until now it was the property app.profile-tracking.tolerance-percent (20 by default).
--
-- 1. profile_tolerance: every value the tolerance has had, newest last. The one in force is the last row. A change adds
--    a row with the previous value, who made it (console user) and when; rows are never edited or deleted, so the table
--    is also the log of changes. The first row is the value of the old property; it has no author.
-- 2. profile_deviation keeps the tolerance in force when each deviation was detected. Deviations recorded before this
--    migration were detected with the default of the property, 20.

CREATE TABLE profile_tolerance (
  id                bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  tolerance_percent smallint    NOT NULL,
  previous_percent  smallint,
  changed_by        uuid        REFERENCES console_user(id),
  changed_at        timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_profile_tolerance_range CHECK (tolerance_percent BETWEEN 0 AND 45),
  CONSTRAINT ck_profile_tolerance_previous_range CHECK (previous_percent BETWEEN 0 AND 45)
);

INSERT INTO profile_tolerance (tolerance_percent) VALUES (20);

CREATE FUNCTION prevent_profile_tolerance_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  RAISE EXCEPTION 'The profile tolerance change % cannot be modified or deleted', OLD.id
    USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER trg_profile_tolerance_immutable BEFORE UPDATE OR DELETE ON profile_tolerance
  FOR EACH ROW EXECUTE FUNCTION prevent_profile_tolerance_change();

-- The default fills the existing rows without an UPDATE; new rows must say their tolerance.
ALTER TABLE profile_deviation ADD COLUMN tolerance_percent smallint NOT NULL DEFAULT 20
  CONSTRAINT ck_profile_deviation_tolerance CHECK (tolerance_percent >= 0);
ALTER TABLE profile_deviation ALTER COLUMN tolerance_percent DROP DEFAULT;

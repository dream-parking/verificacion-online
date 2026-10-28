-- VDI-134 (story VDI-35): the binding dossier is retained until a date saved with it: the date of the signature plus the
-- retention period (app.dossier.retention-years, see BindingDossierService). Until that date the database refuses to
-- delete it, and it never lets it be edited.
--
-- V31 allowed the dossier to be deleted together with its request (cascade). That would be a way around the retention
-- period, so the trigger no longer makes that exception: a request with a dossier still within its period cannot be
-- deleted. Once the date has passed the dossier can be deleted, alone or with its request.
--
-- Dossiers written before this migration (none outside development) are retained for 10 years from their signature,
-- the default of the period. They are backfilled with the trigger disabled, since it forbids every update.

ALTER TABLE binding_dossier ADD COLUMN retained_until timestamptz;

ALTER TABLE binding_dossier DISABLE TRIGGER trg_binding_dossier_immutable;
UPDATE binding_dossier SET retained_until = signed_at + interval '10 years';
ALTER TABLE binding_dossier ENABLE TRIGGER trg_binding_dossier_immutable;

ALTER TABLE binding_dossier
  ALTER COLUMN retained_until SET NOT NULL,
  ADD CONSTRAINT ck_binding_dossier_retention CHECK (retained_until > signed_at);

CREATE OR REPLACE FUNCTION prevent_binding_dossier_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF now() >= OLD.retained_until THEN
      RETURN OLD;
    END IF;
    RAISE EXCEPTION 'The binding dossier % of request % cannot be deleted before %', OLD.id, OLD.request_id,
      OLD.retained_until USING ERRCODE = 'integrity_constraint_violation';
  END IF;
  RAISE EXCEPTION 'The binding dossier % of request % cannot be modified', OLD.id, OLD.request_id
    USING ERRCODE = 'integrity_constraint_violation';
END $$;

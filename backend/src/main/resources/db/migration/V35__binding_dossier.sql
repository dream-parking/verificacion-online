-- VDI-131 (story VDI-15): when the customer signs, the evidence that backed the approval is frozen in the binding
-- dossier: the confirmed DUI data and customer number, the privacy consent and notice version, the RNPN result, the
-- liveness confidence, the score with each signal's contribution and the model thresholds, the log of decisions, and the
-- signature (contract version and fingerprint). Each element carries its own date and time. It is a copy, so it can be
-- read years later even if the score model or its thresholds have changed since.
--
-- One dossier per request, append-only: the database refuses to change or delete one. The only deletion allowed is the
-- cascade of deleting the whole request, like the rest of the file (V23, V29).
--   evidence  the frozen elements, as JSON (schemaVersion 1; see BindingDossierResponse.Evidence).
--   signed_at when the customer signed; frozen_at when the dossier was written.

CREATE TABLE binding_dossier (
  id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id           uuid         NOT NULL UNIQUE REFERENCES onboarding_request(id) ON DELETE CASCADE,
  contract_version     varchar(30)  NOT NULL,
  contract_fingerprint varchar(64)   NOT NULL,
  signed_at            timestamptz  NOT NULL,
  evidence             jsonb        NOT NULL,
  frozen_at            timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_binding_dossier_fingerprint CHECK (contract_fingerprint ~ '^[0-9a-f]{64}$'),
  CONSTRAINT ck_binding_dossier_evidence CHECK (jsonb_typeof(evidence) = 'object')
);

CREATE FUNCTION prevent_binding_dossier_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF TG_OP = 'DELETE' AND NOT EXISTS (SELECT 1 FROM onboarding_request WHERE id = OLD.request_id) THEN
    RETURN OLD;
  END IF;
  RAISE EXCEPTION 'The binding dossier % of request % cannot be modified or deleted', OLD.id, OLD.request_id
    USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER trg_binding_dossier_immutable BEFORE UPDATE OR DELETE ON binding_dossier
  FOR EACH ROW EXECUTE FUNCTION prevent_binding_dossier_change();

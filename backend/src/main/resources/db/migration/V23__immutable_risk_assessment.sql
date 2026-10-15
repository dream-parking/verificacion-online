-- VDI-83 (criterion 7 of VDI-2): an assessment is kept in the file with its score, the model version, the date and
-- what each signal contributed, and nobody can change or delete them.
--   risk_assessment         the only change allowed is the one a newer assessment makes: is_current from true to
--                           false (retireCurrent, when the request is scored again on submit).
--   risk_assessment_signal  never changes.
-- Both can only be deleted together with their request (cascade delete), like the rest of the file.

CREATE FUNCTION prevent_assessment_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF EXISTS (SELECT 1 FROM onboarding_request WHERE id = OLD.request_id) THEN
      RAISE EXCEPTION 'The risk assessment % of request % cannot be deleted', OLD.id, OLD.request_id
        USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN OLD;
  END IF;
  IF OLD.is_current AND NOT NEW.is_current
      AND to_jsonb(NEW) - 'is_current' = to_jsonb(OLD) - 'is_current' THEN
    RETURN NEW;
  END IF;
  RAISE EXCEPTION 'The risk assessment % of request % cannot be modified', OLD.id, OLD.request_id
    USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE FUNCTION prevent_assessment_signal_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF TG_OP = 'DELETE' AND NOT EXISTS (SELECT 1 FROM risk_assessment WHERE id = OLD.assessment_id) THEN
    RETURN OLD;
  END IF;
  RAISE EXCEPTION 'What signal % contributed to risk assessment % cannot be modified or deleted', OLD.signal,
    OLD.assessment_id USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER trg_risk_assessment_immutable BEFORE UPDATE OR DELETE ON risk_assessment
  FOR EACH ROW EXECUTE FUNCTION prevent_assessment_change();
CREATE TRIGGER trg_risk_assessment_signal_immutable BEFORE UPDATE OR DELETE ON risk_assessment_signal
  FOR EACH ROW EXECUTE FUNCTION prevent_assessment_signal_change();

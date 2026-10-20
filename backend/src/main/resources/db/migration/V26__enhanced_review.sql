-- VDI-117 (story VDI-18): a request whose score goes above the threshold of the score model is not approved
-- automatically: it is held for enhanced review, and every decision is kept in an audit log with its reason, date and
-- time that nobody can change or delete.
--
-- 1. A submitted request held for enhanced review has its own status. Only COMPLETED requests can open an account, so
--    a held request cannot reach one until a person clears it.
-- 2. The timeline gets an event for the moment a request is held.
-- 3. risk_decision_log: one row per decision taken when a request is submitted, whether it was held or not, so the log
--    also proves that the rule ran. The values that explain the decision (score, threshold, model version) are copied
--    into the row, because the score model keeps evolving.
--
-- The new enum values are not used in this migration: PostgreSQL does not allow it in the transaction that adds them.

ALTER TYPE request_status ADD VALUE 'ENHANCED_REVIEW' AFTER 'COMPLETED';
ALTER TYPE request_event_type ADD VALUE 'ENHANCED_REVIEW_REQUIRED' AFTER 'SCORE_ASSIGNED';

CREATE TABLE risk_decision_log (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id    uuid        NOT NULL REFERENCES onboarding_request(id) ON DELETE CASCADE,
  assessment_id bigint      NOT NULL REFERENCES risk_assessment(id),
  decision      varchar(20) NOT NULL,
  reason        text        NOT NULL,
  score         smallint    NOT NULL,
  threshold     smallint    NOT NULL,
  model_version smallint    NOT NULL REFERENCES score_model(version),
  decided_by    varchar(60) NOT NULL DEFAULT 'SYSTEM',
  decided_at    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_risk_decision_decision CHECK (decision IN ('AUTOMATIC_FLOW', 'ENHANCED_REVIEW')),
  CONSTRAINT ck_risk_decision_score CHECK (score BETWEEN 0 AND 100),
  CONSTRAINT ck_risk_decision_threshold CHECK (threshold BETWEEN 0 AND 99),
  -- The rule: the score goes above the threshold if and only if the request is held.
  CONSTRAINT ck_risk_decision_rule CHECK ((decision = 'ENHANCED_REVIEW') = (score > threshold))
);
CREATE INDEX ix_risk_decision_log_request ON risk_decision_log (request_id, decided_at, id);

-- Append-only, like the assessments (VDI-83): a decision can be neither changed nor deleted. The only deletion allowed
-- is the cascade of deleting the whole request.
CREATE FUNCTION prevent_risk_decision_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF TG_OP = 'DELETE' AND NOT EXISTS (SELECT 1 FROM onboarding_request WHERE id = OLD.request_id) THEN
    RETURN OLD;
  END IF;
  RAISE EXCEPTION 'The risk decision % of request % cannot be modified or deleted', OLD.id, OLD.request_id
    USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER trg_risk_decision_log_immutable BEFORE UPDATE OR DELETE ON risk_decision_log
  FOR EACH ROW EXECUTE FUNCTION prevent_risk_decision_change();

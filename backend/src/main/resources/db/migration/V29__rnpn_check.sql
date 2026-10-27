-- VDI-111 (story VDI-17): the data of the DUI the customer confirmed is checked against the RNPN when the request is
-- submitted. There is no public RNPN API, so the service is simulated for now (provider 'simulated').
--
-- 1. A request whose RNPN did not answer, after the retries, is not approved automatically: it waits in its own
--    status. Like COMPLETED it is a submitted request (ck_request_completed already accepts any status that is not
--    IN_PROGRESS or ABANDONED, see V27).
-- 2. The timeline gets an event for each check.
-- 3. rnpn_check: the result of every check, with the date and time, the number of tries and, when the data did not
--    match, which fields. It is append-only: nothing is edited or deleted, the only deletion allowed is the cascade of
--    deleting the whole request.
--
-- The new enum values are not used in this migration: PostgreSQL does not allow it in the transaction that adds them.

ALTER TYPE request_status ADD VALUE 'AWAITING_RNPN' AFTER 'ENHANCED_REVIEW';
ALTER TYPE request_event_type ADD VALUE 'RNPN_CHECKED' AFTER 'ENHANCED_REVIEW_REQUIRED';

CREATE TABLE rnpn_check (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id        uuid         NOT NULL REFERENCES onboarding_request(id) ON DELETE CASCADE,
  outcome           varchar(12)  NOT NULL,
  provider          varchar(60)  NOT NULL,
  attempts          smallint     NOT NULL,
  mismatched_fields varchar(20)[] NOT NULL DEFAULT '{}',
  failure           varchar(250),
  checked_at        timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_rnpn_check_outcome CHECK (outcome IN ('MATCH', 'NO_MATCH', 'UNAVAILABLE')),
  CONSTRAINT ck_rnpn_check_attempts CHECK (attempts > 0),
  CONSTRAINT ck_rnpn_check_fields CHECK (mismatched_fields <@ ARRAY['DUI', 'FIRST_NAMES', 'LAST_NAMES', 'BIRTH_DATE']::varchar[]),
  -- Only a result that did not match names fields, and only an unavailable one names a failure.
  CONSTRAINT ck_rnpn_check_mismatch CHECK ((outcome = 'NO_MATCH') = (cardinality(mismatched_fields) > 0)),
  CONSTRAINT ck_rnpn_check_failure CHECK (outcome = 'UNAVAILABLE' OR failure IS NULL)
);
CREATE INDEX ix_rnpn_check_request ON rnpn_check (request_id, checked_at, id);

CREATE FUNCTION prevent_rnpn_check_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF TG_OP = 'DELETE' AND NOT EXISTS (SELECT 1 FROM onboarding_request WHERE id = OLD.request_id) THEN
    RETURN OLD;
  END IF;
  RAISE EXCEPTION 'The RNPN check % of request % cannot be modified or deleted', OLD.id, OLD.request_id
    USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER trg_rnpn_check_immutable BEFORE UPDATE OR DELETE ON rnpn_check
  FOR EACH ROW EXECUTE FUNCTION prevent_rnpn_check_change();

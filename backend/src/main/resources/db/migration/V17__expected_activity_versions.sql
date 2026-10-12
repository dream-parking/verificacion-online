-- VDI-90: versions of the declared transactional profile (the type of money and the monthly amount range of the
-- expected activity). Every change the customer makes before submitting is a new version; the highest one is the
-- version in force and the base for monitoring the account. Versions are kept as they were declared and are never
-- edited or deleted. expected_activity holds the version in force, as before.

-- 1. The version in force, in the row that already exists.
ALTER TABLE expected_activity ADD COLUMN version smallint NOT NULL DEFAULT 1;
ALTER TABLE expected_activity ADD CONSTRAINT ck_expected_activity_version CHECK (version >= 1);

-- 2. The history. Version 1 of every request already on file is the declaration it has.
CREATE TABLE expected_activity_version (
  request_id                uuid        NOT NULL REFERENCES onboarding_request(id) ON DELETE CASCADE,
  version                   smallint    NOT NULL,
  transaction_type_code     varchar(30) NOT NULL REFERENCES transaction_type(code),
  monthly_amount_range_code varchar(30) NOT NULL REFERENCES monthly_amount_range(code),
  registered_at             timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (request_id, version),
  CONSTRAINT ck_expected_activity_version_number CHECK (version >= 1)
);

INSERT INTO expected_activity_version (request_id, version, transaction_type_code, monthly_amount_range_code,
                                       registered_at)
SELECT request_id, 1, transaction_type_code, monthly_amount_range_code, registered_at FROM expected_activity;

-- 3. History is append-only: a version cannot be updated, and cannot be deleted unless its request is deleted too
--    (an abandoned request that is purged). When the request is gone the cascade is allowed.
CREATE FUNCTION prevent_history_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF TG_OP = 'DELETE' AND NOT EXISTS (SELECT 1 FROM onboarding_request WHERE id = OLD.request_id) THEN
    RETURN OLD;
  END IF;
  RAISE EXCEPTION 'A version of the declared profile of request % cannot be modified or deleted', OLD.request_id
    USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER trg_expected_activity_version_immutable BEFORE UPDATE OR DELETE ON expected_activity_version
  FOR EACH ROW EXECUTE FUNCTION prevent_history_change();

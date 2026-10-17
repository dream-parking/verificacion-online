-- VDI-3: every declaration of the expected activity is a version of the declared profile. expected_activity keeps
-- the version in force (the one the score and the monitoring read); expected_activity_version keeps all of them, the
-- in-force one included. A change before submitting adds a version and never touches the previous ones: the versions
-- can be neither changed nor deleted, only deleted together with their request (cascade delete). The version in force
-- is the highest one of the request.
-- changed_by says who declared it; for now only the customer, before submitting (CUSTOMER).
CREATE TABLE expected_activity_version (
  id                        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id                uuid          NOT NULL REFERENCES onboarding_request(id) ON DELETE CASCADE,
  version                   smallint      NOT NULL,
  transaction_type_code     varchar(30)   NOT NULL REFERENCES transaction_type(code),
  monthly_amount_range_code varchar(30)   NOT NULL REFERENCES monthly_amount_range(code),
  extra_type_codes          text[]        NOT NULL DEFAULT '{}',
  monthly_amount_usd        numeric(14,2),
  monthly_transaction_count smallint,
  monthly_outflow_usd       numeric(14,2),
  changed_by                varchar(20)   NOT NULL DEFAULT 'CUSTOMER',
  declared_at               timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT uq_expected_activity_version UNIQUE (request_id, version),
  CONSTRAINT ck_expected_activity_version_number CHECK (version > 0),
  CONSTRAINT ck_expected_activity_version_changed_by CHECK (changed_by IN ('CUSTOMER'))
);

CREATE FUNCTION prevent_profile_version_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF TG_OP = 'DELETE' AND NOT EXISTS (SELECT 1 FROM onboarding_request WHERE id = OLD.request_id) THEN
    RETURN OLD;
  END IF;
  RAISE EXCEPTION 'Version % of the declared profile of request % cannot be modified or deleted', OLD.version,
    OLD.request_id USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER trg_expected_activity_version_immutable BEFORE UPDATE OR DELETE ON expected_activity_version
  FOR EACH ROW EXECUTE FUNCTION prevent_profile_version_change();

-- The declarations made before this change become version 1, as they are now and when they were last changed.
INSERT INTO expected_activity_version (request_id, version, transaction_type_code, monthly_amount_range_code,
  extra_type_codes, monthly_amount_usd, monthly_transaction_count, monthly_outflow_usd, declared_at)
SELECT ea.request_id, 1, ea.transaction_type_code, ea.monthly_amount_range_code,
       coalesce((SELECT array_agg(x.transaction_type_code ORDER BY x.transaction_type_code)
                 FROM expected_activity_extra_type x WHERE x.request_id = ea.request_id), '{}'),
       ea.monthly_amount_usd, ea.monthly_transaction_count, ea.monthly_outflow_usd, ea.updated_at
FROM expected_activity ea;

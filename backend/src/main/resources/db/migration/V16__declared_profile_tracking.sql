-- VDI-3: the declared transactional profile is the baseline the bank compares the real account activity against.
-- The declaration (step 5) gets more precise and the account movements sent by the core are compared with it month by
-- month. Every new declaration field is optional, so the current app keeps working with only the type and the range.

-- 1. More precise declaration:
--    monthly_amount_usd         estimated monthly amount; it must fall inside the chosen range (checked by the API),
--                               so the open range («Más de USD 1,000») gets a figure to compare against
--    monthly_transaction_count  how many deposits/incoming payments a month
--    monthly_outflow_usd        how much leaves the account a month (withdrawals, transfers, payments)
ALTER TABLE expected_activity
  ADD COLUMN monthly_amount_usd        numeric(14,2),
  ADD COLUMN monthly_transaction_count smallint,
  ADD COLUMN monthly_outflow_usd       numeric(14,2),
  ADD CONSTRAINT ck_expected_activity_amount CHECK (monthly_amount_usd IS NULL OR monthly_amount_usd > 0),
  ADD CONSTRAINT ck_expected_activity_count CHECK (monthly_transaction_count IS NULL
    OR monthly_transaction_count BETWEEN 1 AND 1000),
  ADD CONSTRAINT ck_expected_activity_outflow CHECK (monthly_outflow_usd IS NULL OR monthly_outflow_usd >= 0);

-- 2. Other types of money besides the main one (e.g. salary and family remittances). Immutable like the declaration.
CREATE TABLE expected_activity_extra_type (
  request_id            uuid        NOT NULL REFERENCES expected_activity(request_id) ON DELETE CASCADE,
  transaction_type_code varchar(30) NOT NULL REFERENCES transaction_type(code),
  PRIMARY KEY (request_id, transaction_type_code)
);
CREATE TRIGGER trg_expected_activity_extra_type_immutable BEFORE UPDATE OR DELETE ON expected_activity_extra_type
  FOR EACH ROW EXECUTE FUNCTION prevent_declaration_change();

-- 3. Real account activity, as the core banking system reports it. external_id is the core's id of the movement, so a
--    retry does not count it twice. transaction_type_code is the core's classification (null = not classified).
--    A movement is a record of what happened: it can be neither changed nor deleted.
CREATE TABLE account_movement (
  id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id            uuid          NOT NULL REFERENCES account(id),
  external_id           varchar(64)   NOT NULL,
  direction             varchar(3)    NOT NULL,
  amount_usd            numeric(14,2) NOT NULL,
  transaction_type_code varchar(30)   REFERENCES transaction_type(code),
  description           varchar(150),
  occurred_at           timestamptz   NOT NULL,
  registered_at         timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT uq_account_movement_external UNIQUE (account_id, external_id),
  CONSTRAINT ck_account_movement_direction CHECK (direction IN ('IN', 'OUT')),
  CONSTRAINT ck_account_movement_amount CHECK (amount_usd > 0)
);
CREATE INDEX ix_account_movement_period ON account_movement (account_id, occurred_at);

CREATE FUNCTION prevent_movement_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  RAISE EXCEPTION 'Account movement % cannot be modified or deleted', OLD.id
    USING ERRCODE = 'integrity_constraint_violation';
END $$;
CREATE TRIGGER trg_account_movement_immutable BEFORE UPDATE OR DELETE ON account_movement
  FOR EACH ROW EXECUTE FUNCTION prevent_movement_change();

-- 4. Tracking: each deviation from the declared profile found in a month (calendar month in El Salvador), with the
--    declared and the real value and the alert it raised. One row per account, month and kind of deviation, so the
--    same deviation does not raise a new alert with every movement of the month.
CREATE TABLE profile_deviation (
  account_id     uuid          NOT NULL REFERENCES account(id),
  period         date          NOT NULL,
  deviation      varchar(40)   NOT NULL,
  declared_value numeric(14,2),
  actual_value   numeric(14,2) NOT NULL,
  detail         varchar(250)  NOT NULL,
  alert_id       uuid          NOT NULL REFERENCES alert(id),
  detected_at    timestamptz   NOT NULL DEFAULT now(),
  PRIMARY KEY (account_id, period, deviation),
  CONSTRAINT ck_profile_deviation_period CHECK (extract(day FROM period) = 1)
);

-- VDI-142 (story VDI-13): internal risk lists. The bank keeps lists of data tied to cases it already knows (confirmed
-- fraud, under investigation, watch) so a request can be crossed against them later (the matching engine is another
-- subtask; nothing here touches the score or the request yet).
--   risk_list               a list, with its type and its weight for the score. The lists are seeded here; the weights
--                           are PROVISIONAL until the matching engine calibrates them against the score model.
--   risk_list_entry         one piece of data of the list: its type (document, phone or device) and its HASH, never the
--                           value itself. The hash is an HMAC-SHA256 with the salt app.risk-lists.salt over the type and
--                           the normalized value (see RiskListHasher), so the same value gives the same hash and can be
--                           looked up, and the database alone cannot be reversed into DUIs or phone numbers.
--   risk_list_entry_change  log of every change (who, when, why). Insert-only.
-- An entry is never deleted: removing it marks it inactive (who, when, why). The database refuses to delete entries and
-- to change anything else about them, and refuses to change or delete the log.

CREATE TABLE risk_list (
  id         smallint     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code       varchar(30)  NOT NULL UNIQUE,
  name       varchar(120) NOT NULL,
  list_type  varchar(30)  NOT NULL,
  weight     smallint     NOT NULL,
  created_at timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_risk_list_type CHECK (list_type IN ('CONFIRMED_FRAUD', 'UNDER_INVESTIGATION', 'WATCH')),
  CONSTRAINT ck_risk_list_weight CHECK (weight BETWEEN 1 AND 100)
);

INSERT INTO risk_list (code, name, list_type, weight) VALUES
  ('FRAUDE_CONFIRMADO', 'Fraude confirmado', 'CONFIRMED_FRAUD', 100),
  ('EN_INVESTIGACION', 'En investigación', 'UNDER_INVESTIGATION', 60),
  ('VIGILANCIA', 'Vigilancia', 'WATCH', 30);

CREATE TABLE risk_list_entry (
  id                  bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  list_id             smallint     NOT NULL REFERENCES risk_list(id),
  data_type           varchar(10)  NOT NULL,
  value_hash          char(64)     NOT NULL,
  reason              varchar(250) NOT NULL,
  added_by            uuid         NOT NULL REFERENCES console_user(id),
  added_at            timestamptz  NOT NULL DEFAULT now(),
  active              boolean      NOT NULL DEFAULT true,
  deactivated_by      uuid         REFERENCES console_user(id),
  deactivated_at      timestamptz,
  deactivation_reason varchar(250),
  CONSTRAINT ck_risk_list_entry_type CHECK (data_type IN ('DOCUMENT', 'PHONE', 'DEVICE')),
  CONSTRAINT ck_risk_list_entry_hash CHECK (value_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT ck_risk_list_entry_deactivation CHECK (
    (active AND deactivated_by IS NULL AND deactivated_at IS NULL AND deactivation_reason IS NULL)
    OR (NOT active AND deactivated_by IS NOT NULL AND deactivated_at IS NOT NULL AND deactivation_reason IS NOT NULL))
);

-- The same value cannot be active twice in one list; an inactive one can be added again later.
CREATE UNIQUE INDEX uq_risk_list_entry_active ON risk_list_entry (list_id, value_hash) WHERE active;
-- The lookup of a request: its hashes against the active entries.
CREATE INDEX ix_risk_list_entry_lookup ON risk_list_entry (value_hash) WHERE active;

CREATE TABLE risk_list_entry_change (
  id         bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  entry_id   bigint       NOT NULL REFERENCES risk_list_entry(id),
  action     varchar(12)  NOT NULL,
  changed_by uuid         NOT NULL REFERENCES console_user(id),
  changed_at timestamptz  NOT NULL DEFAULT now(),
  reason     varchar(250) NOT NULL,
  CONSTRAINT ck_risk_list_entry_change_action CHECK (action IN ('ADDED', 'DEACTIVATED'))
);

CREATE INDEX ix_risk_list_entry_change_entry ON risk_list_entry_change (entry_id, changed_at);

CREATE FUNCTION prevent_risk_list_entry_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  -- The only change allowed is the deactivation: active from true to false with who, when and why.
  IF TG_OP = 'UPDATE' AND OLD.active AND NOT NEW.active
      AND to_jsonb(NEW) - 'active' - 'deactivated_by' - 'deactivated_at' - 'deactivation_reason'
        = to_jsonb(OLD) - 'active' - 'deactivated_by' - 'deactivated_at' - 'deactivation_reason' THEN
    RETURN NEW;
  END IF;
  RAISE EXCEPTION 'The risk list entry % cannot be deleted or modified, only deactivated', OLD.id
    USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE FUNCTION prevent_risk_list_entry_log_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  RAISE EXCEPTION 'The change log of the risk list entry % cannot be modified or deleted', OLD.entry_id
    USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER trg_risk_list_entry_immutable BEFORE UPDATE OR DELETE ON risk_list_entry
  FOR EACH ROW EXECUTE FUNCTION prevent_risk_list_entry_change();
CREATE TRIGGER trg_risk_list_entry_change_immutable BEFORE UPDATE OR DELETE ON risk_list_entry_change
  FOR EACH ROW EXECUTE FUNCTION prevent_risk_list_entry_log_change();

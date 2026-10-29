-- VDI-144 (story VDI-28): the outcome of a case. An analyst closes a request as confirmed fraud or as a false positive
-- (there is no alert closing yet, VDI-31, so the outcome hangs from the request). A confirmed fraud puts the DUI, the phone
-- and the device of the request on the internal risk list (VDI-142); a correction of the outcome takes them off again.
--   request_outcome        one row per outcome registered: which request, which outcome, who, when and why. Insert-only:
--                          a correction is a new row, never an edit of the previous one. The last row of a request is
--                          its current outcome; a false positive is kept so that it can be measured (VDI-32).
--   request_outcome_entry  which risk list entries an outcome touched: LISTED when it created the entry, REVOKED when it
--                          took it off (the entry is deactivated as VDI-142 allows, and this row says which outcome did it).
--                          Insert-only too. An entry that was already on the list (put there by hand, or by an earlier
--                          outcome) is not linked, so a correction never takes off what the outcome did not add.
-- Like the rest of the file (V23, V35), the database refuses to change or delete a row; the only deletion is the cascade
-- of deleting the whole request. The entries stay: they are hashes and do not point back to the request.

CREATE TABLE request_outcome (
  id            bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id    uuid         NOT NULL REFERENCES onboarding_request(id) ON DELETE CASCADE,
  outcome       varchar(20)  NOT NULL,
  reason        varchar(250) NOT NULL,
  registered_by uuid         NOT NULL REFERENCES console_user(id),
  registered_at timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_request_outcome CHECK (outcome IN ('FRAUDE_CONFIRMADO', 'FALSO_POSITIVO'))
);

CREATE INDEX ix_request_outcome_request ON request_outcome (request_id, id);

CREATE TABLE request_outcome_entry (
  id         bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  outcome_id bigint      NOT NULL REFERENCES request_outcome(id) ON DELETE CASCADE,
  entry_id   bigint      NOT NULL REFERENCES risk_list_entry(id),
  link       varchar(8)  NOT NULL,
  CONSTRAINT ck_request_outcome_entry_link CHECK (link IN ('LISTED', 'REVOKED')),
  -- An entry is created by one outcome and taken off by one outcome, at most.
  CONSTRAINT uq_request_outcome_entry UNIQUE (entry_id, link)
);

CREATE INDEX ix_request_outcome_entry_outcome ON request_outcome_entry (outcome_id);

CREATE FUNCTION prevent_request_outcome_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF TG_OP = 'DELETE' AND NOT EXISTS (SELECT 1 FROM onboarding_request WHERE id = OLD.request_id) THEN
    RETURN OLD;
  END IF;
  RAISE EXCEPTION 'The outcome % of request % cannot be modified or deleted', OLD.id, OLD.request_id
    USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE FUNCTION prevent_request_outcome_entry_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF TG_OP = 'DELETE' AND NOT EXISTS (SELECT 1 FROM request_outcome WHERE id = OLD.outcome_id) THEN
    RETURN OLD;
  END IF;
  RAISE EXCEPTION 'The link % of the outcome % to a risk list entry cannot be modified or deleted', OLD.id, OLD.outcome_id
    USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER trg_request_outcome_immutable BEFORE UPDATE OR DELETE ON request_outcome
  FOR EACH ROW EXECUTE FUNCTION prevent_request_outcome_change();
CREATE TRIGGER trg_request_outcome_entry_immutable BEFORE UPDATE OR DELETE ON request_outcome_entry
  FOR EACH ROW EXECUTE FUNCTION prevent_request_outcome_entry_change();

-- VDI-130 (story VDI-15): the customer of an approved request signs the account contract electronically. There is no
-- signature provider yet, so the service is simulated (provider 'simulated'), like the RNPN of VDI-111.
--
-- Numbered V33 because V31 (binding dossier, VDI-131) and V32 (its retention, VDI-134) are taken by work not merged yet;
-- this one has to reach main after them.
--
-- 1. The timeline gets an event when the contract is signed and when the signature service rejects it.
-- 2. contract_signature: the evidence of each signature, one per request. The contract version and the SHA-256
--    fingerprint (hex) of the exact text that was signed, when, the provider, and the device and IP of the session. It is
--    append-only: nothing is edited or deleted, the only deletion allowed is the cascade of deleting the whole request.
--
-- The new enum values are not used in this migration: PostgreSQL does not allow it in the transaction that adds them.

ALTER TYPE request_event_type ADD VALUE 'CONTRACT_SIGNED' AFTER 'RNPN_CHECKED';
ALTER TYPE request_event_type ADD VALUE 'CONTRACT_SIGNATURE_REJECTED' AFTER 'CONTRACT_SIGNED';

CREATE TABLE contract_signature (
  id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  -- One signature per request: a second one is refused even if two arrive at the same time.
  request_id           uuid         NOT NULL UNIQUE REFERENCES onboarding_request(id) ON DELETE CASCADE,
  contract_version     varchar(30)  NOT NULL,
  contract_fingerprint varchar(64)  NOT NULL,
  provider             varchar(60)  NOT NULL,
  ip                   inet,
  device               text,
  device_fingerprint   varchar(64),
  signed_at            timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_contract_signature_fingerprint CHECK (contract_fingerprint ~ '^[0-9a-f]{64}$'),
  CONSTRAINT ck_contract_signature_version CHECK (length(trim(contract_version)) > 0)
);

CREATE FUNCTION prevent_contract_signature_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF TG_OP = 'DELETE' AND NOT EXISTS (SELECT 1 FROM onboarding_request WHERE id = OLD.request_id) THEN
    RETURN OLD;
  END IF;
  RAISE EXCEPTION 'The contract signature % of request % cannot be modified or deleted', OLD.id, OLD.request_id
    USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER trg_contract_signature_immutable BEFORE UPDATE OR DELETE ON contract_signature
  FOR EACH ROW EXECUTE FUNCTION prevent_contract_signature_change();

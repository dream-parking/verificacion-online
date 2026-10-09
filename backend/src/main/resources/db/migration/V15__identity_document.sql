-- VDI-79 / VDI-80: identity document (DUI) step, right after the basic data (Sprint 2 prototype). The customer
-- photographs the front and back of the DUI, a vision model reads the data, and the customer confirms or corrects
-- it on its own screen. The images are kept encrypted (AES-256-GCM, by the application) in the KYC file.

-- 1. New step between the basic data and the income. The new values are not used in this migration
--    (PostgreSQL does not allow using an enum value in the transaction that adds it).
ALTER TYPE onboarding_step ADD VALUE 'IDENTITY_DOCUMENT' AFTER 'BASIC_DATA';
ALTER TYPE request_event_type ADD VALUE 'IDENTITY_DOCUMENT_CAPTURED' AFTER 'BASIC_DATA_COMPLETED';
ALTER TYPE request_event_type ADD VALUE 'IDENTITY_DOCUMENT_CONFIRMED' AFTER 'IDENTITY_DOCUMENT_CAPTURED';

-- 2. completed_steps counts one more step. Requests already past the basic data keep their progress, as if they had
--    skipped the DUI step (same as the income step does while the DUI is optional): they have no DUI on file.
--    The basic data rule (ck_request_basic_data, completed_steps >= 2) does not change.
ALTER TABLE onboarding_request
  DROP CONSTRAINT ck_request_completed_steps,
  DROP CONSTRAINT ck_request_completed;

UPDATE onboarding_request SET completed_steps = completed_steps + 1 WHERE completed_steps >= 3;

ALTER TABLE onboarding_request
  ADD CONSTRAINT ck_request_completed_steps CHECK (completed_steps BETWEEN 0 AND 6),
  ADD CONSTRAINT ck_request_completed CHECK (
    (status = 'COMPLETED' AND completed_steps = 6 AND number IS NOT NULL AND submitted_at IS NOT NULL)
    OR (status <> 'COMPLETED' AND number IS NULL AND submitted_at IS NULL));

-- 3. The DUI of the request, one row per request: the last capture and what the customer confirmed. ocr_status:
--    READ        the model read the document; dui..gender is what it read (null = could not read that field)
--    UNREADABLE  the photos cannot be read; unreadable_reason (and unreadable_side, when known) say why
--    FAILED      the provider is off or failed; the customer types the data on the confirmation screen
-- confirmed_* is what the customer confirmed or corrected (the step completes then); corrected_fields lists the
-- fields that differ from what was read; *_match_declared compare the confirmed data with the basic data.
CREATE TABLE identity_document (
  request_id           uuid PRIMARY KEY REFERENCES onboarding_request(id) ON DELETE CASCADE,
  ocr_status           varchar(12)  NOT NULL,
  ocr_model            varchar(60)  NOT NULL,
  attempts             smallint     NOT NULL DEFAULT 1,
  unreadable_reason    varchar(20),
  failure              varchar(250),
  dui                  varchar(10),
  first_names          varchar(100),
  last_names           varchar(100),
  birth_date           date,
  issue_date           date,
  expiry_date          date,
  gender               char(1),
  looks_authentic      boolean,
  confidence           numeric(3,2),
  unreadable_side       varchar(5),
  confirmed_dui         varchar(10),
  confirmed_first_names varchar(100),
  confirmed_last_names  varchar(100),
  confirmed_birth_date  date,
  confirmed_expiry_date date,
  confirmed_at          timestamptz,
  dui_matches_declared  boolean,
  names_match_declared  boolean,
  corrected_fields     text[]       NOT NULL DEFAULT '{}',
  processed_at         timestamptz  NOT NULL,
  registered_at        timestamptz  NOT NULL DEFAULT now(),
  updated_at           timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_identity_document_status CHECK (ocr_status IN ('READ', 'UNREADABLE', 'FAILED')),
  CONSTRAINT ck_identity_document_attempts CHECK (attempts > 0),
  CONSTRAINT ck_identity_document_reason CHECK ((ocr_status = 'UNREADABLE') = (unreadable_reason IS NOT NULL)),
  CONSTRAINT ck_identity_document_side CHECK (unreadable_side IS NULL OR unreadable_side IN ('FRONT', 'BACK', 'BOTH')),
  CONSTRAINT ck_identity_document_confirmed CHECK (confirmed_at IS NULL OR (confirmed_dui IS NOT NULL
    AND confirmed_first_names IS NOT NULL AND confirmed_last_names IS NOT NULL)),
  CONSTRAINT ck_identity_document_gender CHECK (gender IS NULL OR gender IN ('M', 'F')),
  CONSTRAINT ck_identity_document_confidence CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1)
);

-- 4. The photos, encrypted by the application. The ciphertext includes the GCM tag; the request id and the side are
--    the associated data, so a ciphertext copied to another row does not decrypt. sha256 is the hash of the original
--    image: the same photo used in two requests is a fraud signal.
CREATE TABLE identity_document_image (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  request_id    uuid         NOT NULL REFERENCES onboarding_request(id) ON DELETE CASCADE,
  side          varchar(5)   NOT NULL,
  content_type  varchar(20)  NOT NULL,
  ciphertext    bytea        NOT NULL,
  iv            bytea        NOT NULL,
  key_version   smallint     NOT NULL,
  sha256        char(64)     NOT NULL,
  size_bytes    integer      NOT NULL,
  captured_at   timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uq_identity_document_image_side UNIQUE (request_id, side),
  CONSTRAINT ck_identity_document_image_side CHECK (side IN ('FRONT', 'BACK')),
  CONSTRAINT ck_identity_document_image_type CHECK (content_type IN ('image/jpeg', 'image/png')),
  CONSTRAINT ck_identity_document_image_iv CHECK (octet_length(iv) = 12),
  CONSTRAINT ck_identity_document_image_size CHECK (size_bytes > 0)
);
CREATE INDEX ix_identity_document_image_sha256 ON identity_document_image (sha256);

-- 5. Immutable file (same rule as the declarations, VDI-23): once the request leaves IN_PROGRESS the document and
--    its images can be neither changed nor deleted.
CREATE TRIGGER trg_identity_document_immutable BEFORE UPDATE OR DELETE ON identity_document
  FOR EACH ROW EXECUTE FUNCTION prevent_declaration_change();
CREATE TRIGGER trg_identity_document_image_immutable BEFORE UPDATE OR DELETE ON identity_document_image
  FOR EACH ROW EXECUTE FUNCTION prevent_declaration_change();

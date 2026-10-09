-- VDI-79 / VDI-80: identity document (DUI) step. The customer photographs the front and back of the DUI right after
-- accepting the privacy notice; a vision model reads the data, which then pre-fills the basic data step for the
-- customer to confirm or correct. The images are kept encrypted (AES-256-GCM, by the application) in the KYC file.

-- 1. New step between the privacy notice and the basic data. The new values are not used in this migration
--    (PostgreSQL does not allow using an enum value in the transaction that adds it).
ALTER TYPE onboarding_step ADD VALUE 'IDENTITY_DOCUMENT' BEFORE 'BASIC_DATA';
ALTER TYPE request_event_type ADD VALUE 'IDENTITY_DOCUMENT_CAPTURED' BEFORE 'BASIC_DATA_COMPLETED';

-- 2. completed_steps counts one more step. Requests already past the privacy notice keep their progress, as if they
--    had skipped the DUI step (same as the basic data step does while the DUI is optional): they have no DUI on file.
ALTER TABLE onboarding_request
  DROP CONSTRAINT ck_request_completed_steps,
  DROP CONSTRAINT ck_request_completed,
  DROP CONSTRAINT ck_request_basic_data;

UPDATE onboarding_request SET completed_steps = completed_steps + 1 WHERE completed_steps >= 2;

ALTER TABLE onboarding_request
  ADD CONSTRAINT ck_request_completed_steps CHECK (completed_steps BETWEEN 0 AND 6),
  ADD CONSTRAINT ck_request_completed CHECK (
    (status = 'COMPLETED' AND completed_steps = 6 AND number IS NOT NULL AND submitted_at IS NOT NULL)
    OR (status <> 'COMPLETED' AND number IS NULL AND submitted_at IS NULL)),
  ADD CONSTRAINT ck_request_basic_data CHECK (
    completed_steps < 3 OR (customer_id IS NOT NULL AND dui IS NOT NULL AND first_names IS NOT NULL));

-- 3. What was read from the DUI, one row per request (the last attempt). ocr_status:
--    READ        the model read the document; the data below is what it read (null = could not read that field)
--    UNREADABLE  the photos cannot be read; unreadable_reason says why and the step does not advance
--    FAILED      the provider is off or failed; the customer types the data by hand and the step advances
-- dui_matches_declared and corrected_fields are filled in when the customer confirms the basic data.
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
  dui_matches_declared boolean,
  corrected_fields     text[]       NOT NULL DEFAULT '{}',
  processed_at         timestamptz  NOT NULL,
  registered_at        timestamptz  NOT NULL DEFAULT now(),
  updated_at           timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_identity_document_status CHECK (ocr_status IN ('READ', 'UNREADABLE', 'FAILED')),
  CONSTRAINT ck_identity_document_attempts CHECK (attempts > 0),
  CONSTRAINT ck_identity_document_reason CHECK ((ocr_status = 'UNREADABLE') = (unreadable_reason IS NOT NULL)),
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

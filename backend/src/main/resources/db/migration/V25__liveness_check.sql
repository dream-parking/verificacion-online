-- VDI-114: liveness check (selfie) of the request. One row per request: the last attempt and the confidence the
-- provider gave. The confidence is a number from 0 to 1, not a yes or no: the score model (VDI-115) turns it into a
-- severity, and no value approves or rejects a request by itself.
--   status CHECKED  the provider answered; confidence is its answer
--   status FAILED   the provider is off or failed; confidence is null and the signal does not count in the score
-- attempts counts every try of this request, including the ones the provider failed. The selfie is kept encrypted
-- (AES-256-GCM, by the application, same as the DUI photos); sha256 is the hash of the original image.

ALTER TYPE request_event_type ADD VALUE 'LIVENESS_CHECKED' AFTER 'IDENTITY_DOCUMENT_REJECTED';

CREATE TABLE liveness_check (
  request_id    uuid PRIMARY KEY REFERENCES onboarding_request(id) ON DELETE CASCADE,
  status        varchar(10)  NOT NULL,
  provider      varchar(60)  NOT NULL,
  confidence    numeric(3,2),
  attempts      smallint     NOT NULL DEFAULT 1,
  failure       varchar(250),
  content_type  varchar(20)  NOT NULL,
  ciphertext    bytea        NOT NULL,
  iv            bytea        NOT NULL,
  key_version   smallint     NOT NULL,
  sha256        char(64)     NOT NULL,
  size_bytes    integer      NOT NULL,
  checked_at    timestamptz  NOT NULL,
  registered_at timestamptz  NOT NULL DEFAULT now(),
  updated_at    timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_liveness_check_status CHECK (status IN ('CHECKED', 'FAILED')),
  CONSTRAINT ck_liveness_check_confidence CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
  CONSTRAINT ck_liveness_check_result CHECK ((status = 'CHECKED') = (confidence IS NOT NULL)),
  CONSTRAINT ck_liveness_check_attempts CHECK (attempts > 0),
  CONSTRAINT ck_liveness_check_type CHECK (content_type IN ('image/jpeg', 'image/png')),
  CONSTRAINT ck_liveness_check_iv CHECK (octet_length(iv) = 12),
  CONSTRAINT ck_liveness_check_size CHECK (size_bytes > 0)
);
CREATE INDEX ix_liveness_check_sha256 ON liveness_check (sha256);

-- Immutable file (same rule as the declarations, VDI-23): once the request leaves IN_PROGRESS the check and its
-- selfie can be neither changed nor deleted.
CREATE TRIGGER trg_liveness_check_immutable BEFORE UPDATE OR DELETE ON liveness_check
  FOR EACH ROW EXECUTE FUNCTION prevent_declaration_change();

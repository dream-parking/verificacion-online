-- History of the DUI step that a new capture no longer erases, so repeated attempts count in the score:
--   rejected_confirmations   confirmations rejected because the DUI was expired or its dates did not agree;
--   rejection_reasons        the distinct reasons of those rejections (EXPIRED, UNDER_AGE, EXPIRY_BEFORE_BIRTH);
--   ever_looked_inauthentic  some capture of this request did not look like the physical card, even if a later
--                            one did.
-- A rejected confirmation is also an entry of the request timeline.
ALTER TYPE request_event_type ADD VALUE 'IDENTITY_DOCUMENT_REJECTED' AFTER 'IDENTITY_DOCUMENT_CONFIRMED';

ALTER TABLE identity_document
  ADD COLUMN rejected_confirmations  smallint NOT NULL DEFAULT 0,
  ADD COLUMN rejection_reasons       text[]   NOT NULL DEFAULT '{}',
  ADD COLUMN ever_looked_inauthentic boolean  NOT NULL DEFAULT false,
  ADD CONSTRAINT ck_identity_document_rejected_confirmations CHECK (rejected_confirmations >= 0);

UPDATE identity_document SET ever_looked_inauthentic = true WHERE looks_authentic = false;

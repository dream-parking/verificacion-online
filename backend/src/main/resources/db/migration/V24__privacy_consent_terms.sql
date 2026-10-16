-- VDI-93 (criteria 3 and 6 of VDI-34): the consent also records whether the terms and conditions were accepted, next
-- to the notice version and the date and time it was accepted. Null when it was not recorded: consents given before
-- this change, or sent by an app version that does not report it yet («sin registro»). It can never be false: the
-- terms cannot be declined and still go on.
ALTER TABLE privacy_consent
  ADD COLUMN terms_accepted boolean,
  ADD CONSTRAINT ck_privacy_consent_terms_accepted CHECK (terms_accepted IS NULL OR terms_accepted);

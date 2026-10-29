-- VDI-143 (story VDI-36): an analyst escalates a request held for enhanced review to a video call or to a branch.
--
-- 1. The request has its own status while it is escalated, and keeps the channel it was sent to, which the app shows
--    (without the score). ck_request_completed (V27) already accepts any submitted status, so it is not touched: the
--    request keeps its steps, number and submission date.
-- 2. The timeline gets an event for the escalation.
-- 3. The escalation is one more decision in risk_decision_log, still append-only (V27): it records the channel, the
--    analyst who decided and the reason. It copies the score and threshold in force like the other decisions, but the
--    rule "held if and only if the score goes above the threshold" only applies to the automatic ones.
--
-- The new enum values are not used in this migration (PostgreSQL does not allow it in the transaction that adds
-- them): the checks compare the status as text.

ALTER TYPE request_status ADD VALUE 'ESCALATED' AFTER 'AWAITING_RNPN';
ALTER TYPE request_event_type ADD VALUE 'REQUEST_ESCALATED' AFTER 'RNPN_CHECKED';

ALTER TABLE onboarding_request ADD COLUMN escalation_channel varchar(12);
-- Written with IS NULL / IS NOT NULL so it never evaluates to NULL, which a CHECK would let through.
ALTER TABLE onboarding_request ADD CONSTRAINT ck_request_escalation_channel CHECK (
  (status::text = 'ESCALATED') = (escalation_channel IS NOT NULL)
  AND (escalation_channel IS NULL OR escalation_channel IN ('VIDEO_CALL', 'BRANCH')));

-- The analyst's full name is up to 150 characters (console_user.full_name).
ALTER TABLE risk_decision_log ALTER COLUMN decided_by TYPE varchar(150);
ALTER TABLE risk_decision_log ADD COLUMN channel varchar(12);
ALTER TABLE risk_decision_log ADD COLUMN decided_by_user uuid REFERENCES console_user(id);

ALTER TABLE risk_decision_log DROP CONSTRAINT ck_risk_decision_decision;
ALTER TABLE risk_decision_log ADD CONSTRAINT ck_risk_decision_decision
  CHECK (decision IN ('AUTOMATIC_FLOW', 'ENHANCED_REVIEW', 'ESCALATED'));

ALTER TABLE risk_decision_log DROP CONSTRAINT ck_risk_decision_rule;
ALTER TABLE risk_decision_log ADD CONSTRAINT ck_risk_decision_rule
  CHECK (decision = 'ESCALATED' OR (decision = 'ENHANCED_REVIEW') = (score > threshold));

-- An escalation always says where to and who decided it; an automatic decision has neither.
ALTER TABLE risk_decision_log ADD CONSTRAINT ck_risk_decision_escalation CHECK (
  (decision = 'ESCALATED') = (channel IS NOT NULL)
  AND (decision = 'ESCALATED') = (decided_by_user IS NOT NULL)
  AND (channel IS NULL OR channel IN ('VIDEO_CALL', 'BRANCH')));

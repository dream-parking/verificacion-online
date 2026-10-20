-- VDI-115: score model v2, with a weight for the RNPN match (VDI-111) and the liveness check (VDI-114). A version is
-- never edited in place: v1 is closed and v2 starts now, so the assessments already made keep v1 and the next ones
-- use v2. The weights add up to 100 again (the city counts once VDI-72 captures it):
--   AMOUNT 20, IDENTITY_DOCUMENT 15, TYPING_PACE 10, DEVICE 10, TIME_OF_DAY 5, CITY 15, RNPN 15, LIVENESS 10.
-- The ranges (LOW, MEDIUM, HIGH) are the ones of v1. INTERNAL_LISTS (VDI-13) still has no weight.

-- 1. A model can weigh every signal it combines, not only the six of v1.
ALTER TABLE score_model_weight DROP CONSTRAINT ck_score_weight_signal;
ALTER TABLE score_model_weight ADD CONSTRAINT ck_score_weight_signal CHECK (signal IN
  ('AMOUNT', 'IDENTITY_DOCUMENT', 'TYPING_PACE', 'DEVICE', 'TIME_OF_DAY', 'CITY', 'RNPN', 'LIVENESS', 'INTERNAL_LISTS'));

-- 2. Close v1 first: the database allows only one version in force.
UPDATE score_model SET valid_to = now() WHERE version = 1 AND valid_to IS NULL;

INSERT INTO score_model (version, name, low_max, medium_max, valid_from)
SELECT 2, 'Modelo de score v2', low_max, medium_max, now() FROM score_model WHERE version = 1;

INSERT INTO score_model_weight (model_version, signal, max_points) VALUES
  (2, 'AMOUNT', 20), (2, 'IDENTITY_DOCUMENT', 15), (2, 'TYPING_PACE', 10), (2, 'DEVICE', 10),
  (2, 'TIME_OF_DAY', 5), (2, 'CITY', 15), (2, 'RNPN', 15), (2, 'LIVENESS', 10);

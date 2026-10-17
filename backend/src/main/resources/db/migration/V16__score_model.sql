-- VDI-82: weighted score model. The request gets one score from 0 to 100 that adds up the points of every signal
-- available (declared amount, DUI, typing pace, device, time of day). No signal approves or rejects by itself, and
-- each signal's contribution is kept with the assessment so the console can show where the score comes from.
-- It replaces rule R-01 (low risk when the declared amount does not go above a threshold), which is retired.

-- 1. The model: a version with the score ranges and the points each signal can add (its weight). A version is never
--    edited in place; a new weighting is a new version. Only one version is in force at a time.
CREATE TABLE score_model (
  version     smallint    PRIMARY KEY,
  name        varchar(120) NOT NULL,
  low_max     smallint    NOT NULL,                 -- the score is LOW up to this value, MEDIUM from the next one
  medium_max  smallint    NOT NULL,                 -- ... MEDIUM up to this value, HIGH above it
  valid_from  timestamptz NOT NULL DEFAULT now(),
  valid_to    timestamptz,
  CONSTRAINT ck_score_model_ranges CHECK (0 <= low_max AND low_max < medium_max AND medium_max < 100),
  CONSTRAINT ck_score_model_validity CHECK (valid_to IS NULL OR valid_to > valid_from)
);
CREATE UNIQUE INDEX uq_score_model_in_force ON score_model ((true)) WHERE valid_to IS NULL;

CREATE TABLE score_model_weight (
  model_version smallint    NOT NULL REFERENCES score_model(version),
  signal        varchar(30) NOT NULL,
  max_points    smallint    NOT NULL,
  PRIMARY KEY (model_version, signal),
  CONSTRAINT ck_score_weight_points CHECK (max_points BETWEEN 1 AND 100),
  CONSTRAINT ck_score_weight_signal CHECK (signal IN
    ('AMOUNT', 'IDENTITY_DOCUMENT', 'TYPING_PACE', 'DEVICE', 'TIME_OF_DAY', 'CITY'))
);

-- Model v1: weights of the Sprint 2 prototype. They add up to 100 with the city, which arrives with VDI-72 (until
-- then it adds nothing). RNPN (VDI-17), liveness (VDI-11) and internal lists (VDI-13) have no weight yet.
INSERT INTO score_model (version, name, low_max, medium_max, valid_from)
VALUES (1, 'Modelo de score v1', 39, 69, now());
INSERT INTO score_model_weight (model_version, signal, max_points) VALUES
  (1, 'AMOUNT', 25), (1, 'IDENTITY_DOCUMENT', 20), (1, 'TYPING_PACE', 15), (1, 'DEVICE', 10),
  (1, 'TIME_OF_DAY', 10), (1, 'CITY', 20);

-- 2. The assessment keeps its score and the model version that produced it. Assessments made by rule R-01 before
--    this model have neither.
ALTER TABLE risk_assessment
  ADD COLUMN score         smallint,
  ADD COLUMN model_version smallint REFERENCES score_model(version),
  ADD CONSTRAINT ck_risk_assessment_score CHECK (score IS NULL OR score BETWEEN 0 AND 100),
  ADD CONSTRAINT ck_risk_assessment_model CHECK ((score IS NULL) = (model_version IS NULL));

-- 3. What each signal contributed. points is null when the signal was not available (it adds nothing); max_points
--    is null for a signal that has no weight in the model yet.
CREATE TABLE risk_assessment_signal (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  assessment_id bigint      NOT NULL REFERENCES risk_assessment(id) ON DELETE CASCADE,
  signal        varchar(30) NOT NULL,
  points        smallint,
  max_points    smallint,
  observation   text        NOT NULL,
  CONSTRAINT uq_risk_assessment_signal UNIQUE (assessment_id, signal),
  CONSTRAINT ck_risk_assessment_signal_points CHECK (points IS NULL OR (max_points IS NOT NULL AND points BETWEEN 0 AND max_points)),
  CONSTRAINT ck_risk_assessment_signal_name CHECK (signal IN
    ('AMOUNT', 'IDENTITY_DOCUMENT', 'TYPING_PACE', 'DEVICE', 'TIME_OF_DAY', 'CITY', 'RNPN', 'LIVENESS', 'INTERNAL_LISTS'))
);

-- 4. R-01 is retired: the model scores the amount now. Its versions stay as history of the assessments already made.
UPDATE score_rule
   SET status = 'RETIRED', valid_to = now(),
       status_note = 'Sustituida por el modelo de score v1 (VDI-82)'
 WHERE code = 'R-01' AND valid_to IS NULL;

-- The database function applied R-01 only, as the service did; the service scores with the model now.
DROP FUNCTION evaluate_risk(uuid);

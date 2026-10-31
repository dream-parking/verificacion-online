-- The city leaves the score (finding B-14 of the Sprint 4 QA): it is captured and shown with the device signals, but it
-- cannot be scored with certainty, so it no longer counts. A version is never edited in place: the version in force is
-- closed and the next one starts now with the same ranges and the same weights, except the city. The evaluations
-- already made keep the version they were computed with, and so do the frozen dossiers.
--
-- The other weights stay as they are. The city never added points (it was listed as not available), so the scores
-- are the same as before and the ranges, which were tuned for them, still fit. What changes is that the weights add
-- up to 85 instead of 100, and a new evaluation has no row for the city.
--
-- 'CITY' stays allowed in score_model_weight and risk_assessment_signal: the older versions and evaluations use it.

DO $$
DECLARE
  in_force     score_model%ROWTYPE;
  next_version smallint;
BEGIN
  SELECT * INTO in_force FROM score_model WHERE valid_to IS NULL FOR UPDATE;
  SELECT max(version) + 1 INTO next_version FROM score_model;

  -- Close the one in force first: the database allows only one version in force.
  UPDATE score_model SET valid_to = now() WHERE version = in_force.version;

  INSERT INTO score_model (version, name, low_max, medium_max, valid_from)
  VALUES (next_version, 'Modelo de score v' || next_version, in_force.low_max, in_force.medium_max, now());

  INSERT INTO score_model_weight (model_version, signal, max_points)
  SELECT next_version, signal, max_points
    FROM score_model_weight
   WHERE model_version = in_force.version AND signal <> 'CITY';
END $$;

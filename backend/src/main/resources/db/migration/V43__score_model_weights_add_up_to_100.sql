-- The weights add up to 100 again (QA of Sprint 4). With the city out of the score (V42) they added up to 85, so no
-- request could score more than 85 while the screens say "of 100". The 15 points of the city are spread in proportion
-- to the weight each signal already had, rounded so that the total is exactly 100. A version is never edited in place:
-- the version in force is closed and the next one starts now. The ranges stay as they are. The evaluations already made
-- keep the version they were computed with, and so do the frozen dossiers.
--
-- New weights: AMOUNT 20 -> 23, IDENTITY_DOCUMENT 15 -> 18, TYPING_PACE 10 -> 12, DEVICE 10 -> 12, TIME_OF_DAY 5 -> 6,
-- RNPN 15 -> 18, LIVENESS 10 -> 11. INTERNAL_LISTS has no weight yet and stays without one.

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
  SELECT next_version, signal,
         CASE signal
           WHEN 'AMOUNT' THEN 23
           WHEN 'IDENTITY_DOCUMENT' THEN 18
           WHEN 'TYPING_PACE' THEN 12
           WHEN 'DEVICE' THEN 12
           WHEN 'TIME_OF_DAY' THEN 6
           WHEN 'RNPN' THEN 18
           WHEN 'LIVENESS' THEN 11
           ELSE max_points
         END
    FROM score_model_weight
   WHERE model_version = in_force.version;
END $$;

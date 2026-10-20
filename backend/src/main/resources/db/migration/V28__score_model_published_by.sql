-- VDI-120: the score thresholds (where LOW, MEDIUM and HIGH end) can be changed from the console without a deploy.
-- A change publishes a new model version, copied from the one in force, so each version keeps who published it
-- (the console user) and when (valid_from). Versions published by a migration (v1, v2) have no publisher.
ALTER TABLE score_model ADD COLUMN published_by uuid REFERENCES console_user(id);

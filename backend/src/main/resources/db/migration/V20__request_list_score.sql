-- VDI-84: the console request list shows the score (0 to 100) next to the risk level.
-- It is a scalar subquery on the current assessment, not a join: when the list only asks the view for id and date
-- (see V12), PostgreSQL still drops the view's joins and walks ix_request_activity alone.
-- It is null while the request has no assessment and in the ones made by rule R-01, before the score model.
CREATE OR REPLACE VIEW v_request_list AS
SELECT r.id, r.number,
       trim(coalesce(r.first_names, '') || ' ' || coalesce(r.last_names, '')) AS name,
       coalesce(r.submitted_at, r.last_activity_at) AS activity_date,
       tt.label AS transaction_type_label, mr.label AS monthly_amount_range_label,
       r.risk_level, r.status, r.completed_steps,
       (SELECT a.score FROM risk_assessment a WHERE a.request_id = r.id AND a.is_current) AS score
FROM onboarding_request r
LEFT JOIN expected_activity ea ON ea.request_id = r.id
LEFT JOIN transaction_type tt ON tt.code = ea.transaction_type_code
LEFT JOIN monthly_amount_range mr ON mr.code = ea.monthly_amount_range_code;

-- VDI-12 criterion 6 / VDI-70: the console shows when the signals were captured. The app sends every signal of the
-- session in the same call, which replaces the previous values, so one capture time covers all of them. started_at
-- is when the session was created and does not move when the signals are sent again.
-- Existing sessions were captured once, when they were created.
ALTER TABLE onboarding_session ADD COLUMN captured_at timestamptz;
UPDATE onboarding_session SET captured_at = started_at;
ALTER TABLE onboarding_session
  ALTER COLUMN captured_at SET NOT NULL,
  ALTER COLUMN captured_at SET DEFAULT now();

-- New columns go at the end so the view can be replaced in place.
CREATE OR REPLACE VIEW v_request_signals AS
SELECT r.id AS request_id, s.ip, s.approximate_location, d.fingerprint AS device_fingerprint,
       d.model || ' · ' || d.operating_system AS device,
       s.typing_speed_cpm, s.typing_pace,
       r.started_at, r.submitted_at,
       -- night time: 21:00–04:59 local time (same rule as the console front end)
       (r.submitted_at IS NOT NULL AND (
          EXTRACT(HOUR FROM r.submitted_at AT TIME ZONE 'America/El_Salvador') >= 21 OR
          EXTRACT(HOUR FROM r.started_at   AT TIME ZONE 'America/El_Salvador') < 5)) AS night_time,
       (SELECT sum(duration_seconds) FROM request_step st WHERE st.request_id = r.id) AS total_duration_seconds,
       (SELECT count(DISTINCT r2.id) FROM onboarding_request r2 WHERE r2.device_id = r.device_id) AS requests_from_same_device,
       s.location_status, s.geo_latitude, s.geo_longitude, s.geo_country, s.geo_country_code, s.geo_region,
       s.geo_region_name, s.geo_city, s.geo_zip, s.geo_timezone, s.geo_isp, s.geo_org, s.geo_as, s.geo_failure,
       s.geo_looked_up_at, s.captured_at
FROM onboarding_request r
LEFT JOIN device d ON d.id = r.device_id
LEFT JOIN LATERAL (SELECT * FROM onboarding_session x WHERE x.request_id = r.id
                   ORDER BY x.started_at DESC LIMIT 1) s ON true;

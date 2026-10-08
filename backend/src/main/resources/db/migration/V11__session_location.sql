-- =====================================================================
--  VDI-41 (VDI-12, criterios 2 y 4): approximate location of the onboarding session.
--  The app reads it only if the applicant grants the location permission. Without
--  permission, or when the phone cannot get a position, the location is stored as
--  not available and the other signals are captured as usual.
-- =====================================================================

CREATE TYPE location_status AS ENUM (
  'AVAILABLE',          -- latitude and longitude were captured
  'PERMISSION_DENIED',  -- the applicant did not grant the location permission
  'UNAVAILABLE'         -- permission granted, but no position (location off, error or timeout)
);

ALTER TABLE onboarding_session
  ADD COLUMN location_status     location_status,   -- NULL: the app did not report it (older versions)
  ADD COLUMN latitude            numeric(9,6),
  ADD COLUMN longitude           numeric(9,6),
  ADD COLUMN location_accuracy_m integer,           -- radius reported by the phone, in meters
  ADD CONSTRAINT ck_onboarding_session_location CHECK (
    (location_status = 'AVAILABLE' AND latitude IS NOT NULL AND longitude IS NOT NULL)
    OR (location_status IS DISTINCT FROM 'AVAILABLE'
        AND latitude IS NULL AND longitude IS NULL AND location_accuracy_m IS NULL)),
  ADD CONSTRAINT ck_onboarding_session_latitude CHECK (latitude BETWEEN -90 AND 90),
  ADD CONSTRAINT ck_onboarding_session_longitude CHECK (longitude BETWEEN -180 AND 180),
  ADD CONSTRAINT ck_onboarding_session_accuracy CHECK (location_accuracy_m >= 0);

-- The console reads the signals from this view: recreate it with the location.
DROP VIEW v_request_signals;
CREATE VIEW v_request_signals AS
SELECT r.id AS request_id, s.ip, s.approximate_location,
       s.location_status, s.latitude, s.longitude, s.location_accuracy_m,
       d.fingerprint AS device_fingerprint,
       d.model || ' · ' || d.operating_system AS device,
       s.typing_speed_cpm, s.typing_pace,
       r.started_at, r.submitted_at,
       -- night time: 21:00–04:59 local time (same rule as the console front end)
       (r.submitted_at IS NOT NULL AND (
          EXTRACT(HOUR FROM r.submitted_at AT TIME ZONE 'America/El_Salvador') >= 21 OR
          EXTRACT(HOUR FROM r.started_at   AT TIME ZONE 'America/El_Salvador') < 5)) AS night_time,
       (SELECT sum(duration_seconds) FROM request_step st WHERE st.request_id = r.id) AS total_duration_seconds,
       (SELECT count(DISTINCT r2.id) FROM onboarding_request r2 WHERE r2.device_id = r.device_id) AS requests_from_same_device
FROM onboarding_request r
LEFT JOIN device d ON d.id = r.device_id
LEFT JOIN LATERAL (SELECT * FROM onboarding_session x WHERE x.request_id = r.id
                   ORDER BY x.started_at DESC LIMIT 1) s ON true;

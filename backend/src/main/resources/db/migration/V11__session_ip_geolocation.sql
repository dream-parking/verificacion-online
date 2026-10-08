-- VDI-67: approximate location of the session, resolved by the server from the client IP (ip-api.com).
-- Everything the provider returns is kept; latitude and longitude are what the risk rules will use.
-- location_status is AVAILABLE only when latitude and longitude were obtained; otherwise UNAVAILABLE and
-- geo_failure says why (private address, provider error, timeout...).
ALTER TABLE onboarding_session
  ADD COLUMN location_status varchar(12) NOT NULL DEFAULT 'UNAVAILABLE',
  ADD COLUMN geo_country varchar(80),
  ADD COLUMN geo_country_code char(2),
  ADD COLUMN geo_region varchar(10),
  ADD COLUMN geo_region_name varchar(80),
  ADD COLUMN geo_city varchar(80),
  ADD COLUMN geo_zip varchar(20),
  ADD COLUMN geo_latitude numeric(9,6),
  ADD COLUMN geo_longitude numeric(9,6),
  ADD COLUMN geo_timezone varchar(60),
  ADD COLUMN geo_isp varchar(120),
  ADD COLUMN geo_org varchar(120),
  ADD COLUMN geo_as varchar(120),
  ADD COLUMN geo_failure varchar(120),
  ADD COLUMN geo_looked_up_at timestamptz,
  ADD CONSTRAINT ck_session_location_status CHECK (location_status IN ('AVAILABLE', 'UNAVAILABLE')),
  ADD CONSTRAINT ck_session_geo_latitude CHECK (geo_latitude BETWEEN -90 AND 90),
  ADD CONSTRAINT ck_session_geo_longitude CHECK (geo_longitude BETWEEN -180 AND 180),
  ADD CONSTRAINT ck_session_location_available CHECK (
    location_status <> 'AVAILABLE' OR (geo_latitude IS NOT NULL AND geo_longitude IS NOT NULL));

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
       s.geo_looked_up_at
FROM onboarding_request r
LEFT JOIN device d ON d.id = r.device_id
LEFT JOIN LATERAL (SELECT * FROM onboarding_session x WHERE x.request_id = r.id
                   ORDER BY x.started_at DESC LIMIT 1) s ON true;

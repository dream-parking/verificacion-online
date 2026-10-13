-- VDI-73 / VDI-96: city the phone reports, with the customer's permission (approximate location only: the app sends
-- "City, Country" and the ISO country, never coordinates). It is kept apart from the IP location (VDI-67) so the
-- console can compare both. device_location_status:
--   AVAILABLE   permission granted and the phone found the city;
--   DENIED      the customer declined ("Ahora no") or refused the system permission;
--   UNAVAILABLE permission granted but the phone could not find it (location off, timeout...).
-- NULL means the app did not send it (versions before this change).
ALTER TABLE onboarding_session
  ADD COLUMN device_location_status varchar(12),
  ADD COLUMN device_location varchar(120),
  ADD COLUMN device_country_iso char(2),
  ADD CONSTRAINT ck_session_device_location_status
    CHECK (device_location_status IN ('AVAILABLE', 'DENIED', 'UNAVAILABLE')),
  ADD CONSTRAINT ck_session_device_location_only_when_available CHECK (
    device_location_status = 'AVAILABLE' OR (device_location IS NULL AND device_country_iso IS NULL));

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
       s.geo_looked_up_at, s.captured_at, s.device_location_status, s.device_location, s.device_country_iso
FROM onboarding_request r
LEFT JOIN device d ON d.id = r.device_id
LEFT JOIN LATERAL (SELECT * FROM onboarding_session x WHERE x.request_id = r.id
                   ORDER BY x.started_at DESC LIMIT 1) s ON true;

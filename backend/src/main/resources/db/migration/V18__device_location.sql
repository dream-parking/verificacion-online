-- VDI-72: city and country of the request as the phone reports them, with the customer's permission. It is kept apart
-- from the location the server resolves from the IP (geo_*): the console compares the two, and «no disponible» (the
-- customer denied the permission or the phone could not read the location) has to be told apart from «not captured
-- yet». Only the city and the country are stored, never coordinates or an address.
--   device_location_status  NULL         the app has not reported a location yet
--                           AVAILABLE    city and country reported
--                           UNAVAILABLE  the app reported that it has no location; city and country stay empty
ALTER TABLE onboarding_session
  ADD COLUMN device_location_status      varchar(12),
  ADD COLUMN device_city                 varchar(120),
  ADD COLUMN device_country_iso          char(2),
  ADD COLUMN device_location_captured_at timestamptz,
  ADD CONSTRAINT ck_session_device_location_status CHECK (device_location_status IN ('AVAILABLE', 'UNAVAILABLE')),
  ADD CONSTRAINT ck_session_device_location CHECK (
    (device_location_status IS NULL
       AND device_city IS NULL AND device_country_iso IS NULL AND device_location_captured_at IS NULL)
    OR (device_location_status = 'AVAILABLE'
       AND device_city IS NOT NULL AND device_country_iso IS NOT NULL AND device_location_captured_at IS NOT NULL)
    OR (device_location_status = 'UNAVAILABLE'
       AND device_city IS NULL AND device_country_iso IS NULL AND device_location_captured_at IS NOT NULL));

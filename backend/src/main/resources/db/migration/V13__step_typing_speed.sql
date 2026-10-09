-- VDI-12 / VDI-42: interaction patterns are measured per screen. The time on the screen is already duration_seconds
-- (computed from started_at and completed_at); this adds the typing speed of the screen in characters per second.
-- Null for screens where the customer types nothing. The text itself is never stored.
ALTER TABLE request_step
    ADD COLUMN typing_speed_cps numeric(4,2),
    ADD CONSTRAINT ck_request_step_typing_speed CHECK (typing_speed_cps BETWEEN 0 AND 50);

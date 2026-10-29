-- VDI-148 (story VDI-19): the product an approved request chooses from the catalog (V39). The app sends only the
-- product code: everything else (the person, the DUI, the declared profile) comes from the request already registered.
--   product_request  one row per request, at most (unique request_id): which product and when. Insert-only, like the
--                    rest of the file: choosing again is refused, never an edit. The only deletion is the cascade of
--                    deleting the whole request.
-- The timeline gets an event for the choice. The new enum value is not used in this migration (PostgreSQL does not
-- allow it in the transaction that adds it).

ALTER TYPE request_event_type ADD VALUE 'PRODUCT_REQUESTED' AFTER 'CONTRACT_SIGNATURE_REJECTED';

CREATE TABLE product_request (
  id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id   uuid        NOT NULL REFERENCES onboarding_request(id) ON DELETE CASCADE,
  product_id   smallint    NOT NULL REFERENCES product(id),
  requested_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_product_request_request UNIQUE (request_id)
);

CREATE FUNCTION prevent_product_request_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF TG_OP = 'DELETE' AND NOT EXISTS (SELECT 1 FROM onboarding_request WHERE id = OLD.request_id) THEN
    RETURN OLD;
  END IF;
  RAISE EXCEPTION 'The product request % of request % cannot be modified or deleted', OLD.id, OLD.request_id
    USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER trg_product_request_immutable BEFORE UPDATE OR DELETE ON product_request
  FOR EACH ROW EXECUTE FUNCTION prevent_product_request_change();

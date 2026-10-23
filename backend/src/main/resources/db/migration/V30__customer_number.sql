-- VDI-122 (story VDI-27, also covers VDI-26): every customer gets a unique, permanent customer number such as
-- CLI-0000001, assigned when the customer's first request is submitted.
--
-- 1. The number comes from a database sequence, so two concurrent submissions can never get the same one. A sequence
--    can leave gaps (a rolled back submission uses a number), but it never gives the same number twice.
-- 2. customer.number is unique and has the CLI-NNNNNNN format (seven digits at least, more once the sequence grows).
--    It stays null until the customer submits a request.
-- 3. Once assigned, the number can neither change nor be removed: a trigger refuses it, so not even SQL can reuse or
--    rewrite it.
-- 4. Customers that already submitted a request get their number now, in the order of their first submission, and
--    the sequence continues after the last one.

CREATE SEQUENCE customer_number_seq AS bigint START WITH 1 INCREMENT BY 1 NO CYCLE;

ALTER TABLE customer ADD COLUMN number varchar(20);
ALTER TABLE customer
  ADD CONSTRAINT uq_customer_number UNIQUE (number),
  ADD CONSTRAINT ck_customer_number CHECK (number IS NULL OR number ~ '^CLI-[0-9]{7,}$');

CREATE FUNCTION prevent_customer_number_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF OLD.number IS NOT NULL AND NEW.number IS DISTINCT FROM OLD.number THEN
    RAISE EXCEPTION 'The number % of customer % cannot be changed or removed', OLD.number, OLD.id
      USING ERRCODE = 'integrity_constraint_violation';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_customer_number_immutable BEFORE UPDATE OF number ON customer
  FOR EACH ROW EXECUTE FUNCTION prevent_customer_number_change();

-- 4. Customers that already submitted a request.
WITH first_submission AS (
  SELECT customer_id, min(submitted_at) AS submitted_at
    FROM onboarding_request
   WHERE customer_id IS NOT NULL AND submitted_at IS NOT NULL
   GROUP BY customer_id
), ordered AS (
  SELECT customer_id, row_number() OVER (ORDER BY submitted_at, customer_id) AS position
    FROM first_submission
)
UPDATE customer c
   SET number = 'CLI-' || lpad(o.position::text, 7, '0')
  FROM ordered o
 WHERE c.id = o.customer_id;

-- The next number is the one after the last assigned (the first one when there is none yet).
SELECT setval('customer_number_seq',
              GREATEST((SELECT count(*) FROM customer WHERE number IS NOT NULL), 1),
              (SELECT count(*) > 0 FROM customer WHERE number IS NOT NULL));

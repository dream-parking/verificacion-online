-- VDI-146 (story VDI-19): catalog of the accounts an approved applicant can choose from.
-- The products and their conditions below are EXAMPLES to start with: which products exist and who qualifies for each
-- is still to be defined with the PO. The eligibility rule lives in ProductEligibilityService, not in the database, so
-- it can change without a migration. A product is never deleted: switching `active` off stops offering it.

CREATE TABLE product (
  id                          smallint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code                        varchar(30)   NOT NULL UNIQUE,
  name                        varchar(120)  NOT NULL,
  description                 varchar(250)  NOT NULL,
  monthly_fee_usd             numeric(8,2)  NOT NULL,
  minimum_opening_deposit_usd numeric(10,2) NOT NULL,
  annual_interest_rate        numeric(5,2)  NOT NULL,
  active                      boolean       NOT NULL DEFAULT true,
  created_at                  timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT ck_product_code CHECK (code ~ '^[A-Z0-9_]+$'),
  CONSTRAINT ck_product_monthly_fee CHECK (monthly_fee_usd >= 0),
  CONSTRAINT ck_product_minimum_deposit CHECK (minimum_opening_deposit_usd >= 0),
  CONSTRAINT ck_product_interest_rate CHECK (annual_interest_rate BETWEEN 0 AND 100)
);

INSERT INTO product (code, name, description, monthly_fee_usd, minimum_opening_deposit_usd, annual_interest_rate) VALUES
  ('AHORRO_BASICA', 'Cuenta de ahorro básica', 'Para empezar a ahorrar sin costo mensual ni saldo mínimo alto.', 0.00, 10.00, 0.50),
  ('AHORRO_PLUS', 'Cuenta de ahorro plus', 'Mejor interés para quien mantiene un saldo mayor.', 2.50, 100.00, 2.00),
  ('CORRIENTE', 'Cuenta corriente', 'Para pagos y cobros frecuentes, con chequera y sin interés.', 5.00, 250.00, 0.00);

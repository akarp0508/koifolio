-- In percent. Null means not set yet, which is different from a tax-free 0.
ALTER TABLE accounts ADD COLUMN tax_rate numeric(7, 4) CHECK (tax_rate BETWEEN 0 AND 100);

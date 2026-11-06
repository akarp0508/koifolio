ALTER TABLE instruments ADD COLUMN quote_isin text;

-- Look every ticker up again, to pick up its ISIN
UPDATE instruments SET quote_symbol = NULL, quote_currency = NULL, quote_checked_at = NULL;

-- Shared between users, like quotes. Finished trading days only.
CREATE TABLE daily_closes (
    symbol text NOT NULL,
    date   date NOT NULL,
    close  numeric(19, 6) NOT NULL,
    PRIMARY KEY (symbol, date)
);

-- How far each symbol's closes go, so gaps can be found without scanning them
CREATE TABLE price_history (
    symbol       text PRIMARY KEY,
    covered_from date NOT NULL,
    checked_on   date NOT NULL
);

-- Past rates: how many PLN one unit bought. Shared between users.
CREATE TABLE fx_history (
    currency text NOT NULL,
    date     date NOT NULL,
    rate     numeric(19, 8) NOT NULL,
    PRIMARY KEY (currency, date)
);

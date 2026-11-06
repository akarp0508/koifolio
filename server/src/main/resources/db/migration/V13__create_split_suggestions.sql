-- Shared: a detected split is a fact about the stock. Whether it's entered is each user's own.
CREATE TABLE split_suggestions (
    id         uuid PRIMARY KEY,
    symbol     text NOT NULL,
    date       date NOT NULL,
    split_from integer NOT NULL,
    split_to   integer NOT NULL,
    UNIQUE (symbol, date)
);

CREATE TABLE split_suggestion_dismissals (
    suggestion_id uuid NOT NULL REFERENCES split_suggestions (id) ON DELETE CASCADE,
    user_id       uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (suggestion_id, user_id)
);

CREATE TABLE trades (
    trade_id         VARCHAR(64) PRIMARY KEY,
    version          INTEGER     NOT NULL CHECK (version > 0),
    counter_party_id VARCHAR(64) NOT NULL,
    book_id          VARCHAR(64) NOT NULL,
    maturity_date    DATE        NOT NULL,
    created_date     DATE        NOT NULL,
    expired          BOOLEAN     NOT NULL DEFAULT FALSE
);

-- Supports the expiry sweep (maturity_date < today AND NOT expired) without a full scan.
CREATE INDEX idx_trades_active_maturity ON trades (maturity_date) WHERE expired = FALSE;

CREATE TABLE IF NOT EXISTS oaths (
    id TEXT PRIMARY KEY,
    data TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS protection_groups (
    id TEXT PRIMARY KEY,
    data TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id TEXT PRIMARY KEY,
    oath_id TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    data TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ledger_oath_id ON ledger_entries(oath_id);
CREATE INDEX IF NOT EXISTS idx_ledger_timestamp ON ledger_entries(timestamp);

CREATE TABLE IF NOT EXISTS player_balances (
    player_id TEXT NOT NULL,
    currency_id TEXT NOT NULL,
    amount INTEGER NOT NULL,
    PRIMARY KEY (player_id, currency_id)
);

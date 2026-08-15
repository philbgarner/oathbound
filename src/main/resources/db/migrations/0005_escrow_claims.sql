CREATE TABLE IF NOT EXISTS escrow_claims (
    id TEXT PRIMARY KEY,
    oath_id TEXT NOT NULL,
    data TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_escrow_claims_oath_id ON escrow_claims(oath_id);

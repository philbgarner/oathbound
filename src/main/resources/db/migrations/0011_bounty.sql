CREATE TABLE IF NOT EXISTS bounties (
    id TEXT PRIMARY KEY,
    data TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS pve_contract_progress (
    id TEXT PRIMARY KEY,
    data TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS banishments (
    id TEXT PRIMARY KEY,
    data TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS bounty_notification_opt_outs (
    player_id TEXT PRIMARY KEY
);

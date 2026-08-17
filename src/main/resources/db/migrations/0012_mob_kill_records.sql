CREATE TABLE IF NOT EXISTS mob_kill_records (
    id TEXT PRIMARY KEY,
    killer_id TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    data TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_mob_kill_records_killer_id ON mob_kill_records(killer_id);
CREATE INDEX IF NOT EXISTS idx_mob_kill_records_timestamp ON mob_kill_records(timestamp);

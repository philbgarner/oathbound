CREATE TABLE IF NOT EXISTS death_records (
    id TEXT PRIMARY KEY,
    player_id TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    data TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_death_records_player_id ON death_records(player_id);
CREATE INDEX IF NOT EXISTS idx_death_records_timestamp ON death_records(timestamp);

CREATE TABLE IF NOT EXISTS diplomatic_relations (
    group_a_id TEXT NOT NULL,
    group_b_id TEXT NOT NULL,
    state TEXT NOT NULL,
    since TEXT NOT NULL,
    PRIMARY KEY (group_a_id, group_b_id)
);

CREATE INDEX IF NOT EXISTS idx_diplomatic_relations_group_b_id ON diplomatic_relations(group_b_id);

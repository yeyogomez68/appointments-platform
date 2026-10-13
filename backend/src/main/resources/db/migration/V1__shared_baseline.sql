CREATE TABLE schema_info (
    key        text PRIMARY KEY,
    value      text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO schema_info (key, value) VALUES ('baseline', 'plataforma-citas');

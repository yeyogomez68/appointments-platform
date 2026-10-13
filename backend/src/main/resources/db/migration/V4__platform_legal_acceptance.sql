CREATE TABLE legal_acceptance (
    id          uuid PRIMARY KEY,
    business_id uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    user_id     uuid NOT NULL REFERENCES user_account (id),
    document    text NOT NULL CHECK (document IN ('TERMS', 'DPA')),
    version     text NOT NULL,
    accepted_at timestamptz NOT NULL,
    ip_address  text
);
CREATE INDEX legal_acceptance_business_idx ON legal_acceptance (business_id);

ALTER TABLE legal_acceptance ENABLE ROW LEVEL SECURITY;
ALTER TABLE legal_acceptance FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON legal_acceptance
    USING (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid);

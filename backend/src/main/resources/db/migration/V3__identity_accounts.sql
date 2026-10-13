CREATE TABLE user_account (
    id                uuid PRIMARY KEY,
    email             text NOT NULL CHECK (email = lower(email) AND char_length(email) <= 254),
    password_hash     text NOT NULL,
    name              text NOT NULL CHECK (char_length(name) BETWEEN 2 AND 120),
    status            text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    email_verified_at timestamptz,
    platform_role     text CHECK (platform_role IN ('PLATFORM_ADMIN')),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX user_account_email_uq ON user_account (email);

CREATE TABLE membership (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    business_id uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    role        text NOT NULL CHECK (role IN ('OWNER', 'RECEPTION', 'EMPLOYEE')),
    resource_id uuid,
    active      boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT membership_user_business_uq UNIQUE (user_id, business_id)
);
CREATE INDEX membership_business_idx ON membership (business_id);

ALTER TABLE membership ENABLE ROW LEVEL SECURITY;
ALTER TABLE membership FORCE ROW LEVEL SECURITY;
-- La cuenta ve sus propias membresías (login, selector) o las del negocio activo; solo escribe en el negocio activo.
CREATE POLICY membership_access ON membership
    USING (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid
           OR user_id = NULLIF(current_setting('app.user_id', true), '')::uuid)
    WITH CHECK (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid);

CREATE TABLE refresh_token (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    business_id uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    family_id   uuid NOT NULL,
    token_hash  text NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    replaced_by uuid,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX refresh_token_user_idx ON refresh_token (user_id);
CREATE INDEX refresh_token_family_idx ON refresh_token (family_id);

CREATE TABLE one_time_token (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    purpose    text NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD', 'INVITATION')),
    token_hash text NOT NULL UNIQUE,
    payload    jsonb NOT NULL DEFAULT '{}'::jsonb,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX one_time_token_user_purpose_idx ON one_time_token (user_id, purpose);

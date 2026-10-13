CREATE TABLE business (
    id         uuid PRIMARY KEY,
    name       text NOT NULL CHECK (char_length(name) BETWEEN 2 AND 120),
    slug       text NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    timezone   text NOT NULL DEFAULT 'America/Bogota',
    currency   char(3) NOT NULL DEFAULT 'COP',
    vertical   text NOT NULL,
    status     text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE subscription (
    business_id        uuid PRIMARY KEY REFERENCES business (id) ON DELETE CASCADE,
    plan_code          text NOT NULL,
    status             text NOT NULL CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED')),
    trial_ends_at      timestamptz,
    current_period_end timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE location (
    id          uuid PRIMARY KEY,
    business_id uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    name        text NOT NULL,
    address     text,
    phone       text,
    is_main     boolean NOT NULL DEFAULT false,
    active      boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX location_business_idx ON location (business_id);
CREATE UNIQUE INDEX location_one_main_per_business ON location (business_id) WHERE is_main;

CREATE TABLE business_settings (
    business_id                uuid PRIMARY KEY REFERENCES business (id) ON DELETE CASCADE,
    slot_step_min              integer NOT NULL DEFAULT 15 CHECK (slot_step_min IN (15, 20, 30, 60)),
    buffer_min                 integer NOT NULL DEFAULT 0 CHECK (buffer_min BETWEEN 0 AND 240),
    walk_ins_enabled           boolean NOT NULL DEFAULT true,
    opening_cash_default_minor bigint NOT NULL DEFAULT 0 CHECK (opening_cash_default_minor >= 0),
    tips_enabled               boolean NOT NULL DEFAULT true,
    tips_recipient             text NOT NULL DEFAULT 'EMPLOYEE' CHECK (tips_recipient IN ('EMPLOYEE', 'BUSINESS')),
    max_discount_pct_reception integer NOT NULL DEFAULT 10 CHECK (max_discount_pct_reception BETWEEN 0 AND 100),
    labels                     jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at                 timestamptz NOT NULL DEFAULT now(),
    updated_at                 timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE location ENABLE ROW LEVEL SECURITY;
ALTER TABLE location FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON location
    USING (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE business_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE business_settings FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON business_settings
    USING (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid);

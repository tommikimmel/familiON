-- =============================================================================
-- FamiliON — Esquema Inicial de Base de Datos (PostgreSQL 17)
-- Base para migración Flyway: V1__init_schema.sql
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- -----------------------------------------------------------------------------
-- 1. FAMILIA, USUARIOS, ROLES Y SEGURIDAD JWT
-- -----------------------------------------------------------------------------

CREATE TABLE families (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    VARCHAR(120) NOT NULL,
    base_currency           VARCHAR(10)  NOT NULL DEFAULT 'ARS',
    reference_currency      VARCHAR(10)  NOT NULL DEFAULT 'USD',
    reference_rate_source   VARCHAR(30)  NOT NULL DEFAULT 'USD_MEP',
    timezone                VARCHAR(60)  NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE family_currencies (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id               UUID         NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    currency_code           VARCHAR(10)  NOT NULL,
    preferred_rate_source   VARCHAR(30), -- ej. USD_MEP, USD_BLUE, EUR_OFICIAL (NULL si es la base_currency)
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_family_currency UNIQUE (family_id, currency_code)
);

CREATE TABLE users (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id               UUID         NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    email                   VARCHAR(180) NOT NULL,
    password_hash           VARCHAR(255) NOT NULL,
    full_name               VARCHAR(120) NOT NULL,
    role                    VARCHAR(20)  NOT NULL CHECK (role IN ('ADULTO', 'HIJO')),
    family_visibility       VARCHAR(20)  NOT NULL DEFAULT 'FAMILY_FULL'
                                CHECK (family_visibility IN ('OWN_ONLY', 'FAMILY_SUMMARY', 'FAMILY_FULL')),
    theme_preference        VARCHAR(10)  NOT NULL DEFAULT 'SYSTEM'
                                CHECK (theme_preference IN ('LIGHT', 'DARK', 'SYSTEM')),
    accent_color            VARCHAR(7)   NOT NULL DEFAULT '#0F766E'
                                CHECK (accent_color ~ '^#[0-9A-Fa-f]{6}$'),
    email_verified_at       TIMESTAMPTZ,
    two_factor_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_adult_full_visibility CHECK (role <> 'ADULTO' OR family_visibility = 'FAMILY_FULL')
);

CREATE TABLE family_invitations (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id               UUID         NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    invited_by_user_id      UUID         NOT NULL REFERENCES users(id),
    email                   VARCHAR(180) NOT NULL,
    role                    VARCHAR(20)  NOT NULL CHECK (role IN ('ADULTO', 'HIJO')),
    family_visibility       VARCHAR(20)  NOT NULL DEFAULT 'OWN_ONLY'
                                CHECK (family_visibility IN ('OWN_ONLY', 'FAMILY_SUMMARY', 'FAMILY_FULL')),
    token_hash              VARCHAR(255) NOT NULL UNIQUE,
    expires_at              TIMESTAMPTZ  NOT NULL,
    accepted_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash              VARCHAR(255) NOT NULL UNIQUE,
    expires_at              TIMESTAMPTZ  NOT NULL,
    revoked_at              TIMESTAMPTZ,
    device_info             VARCHAR(255),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE email_security_codes (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose                 VARCHAR(25)  NOT NULL
                                CHECK (purpose IN ('TWO_FACTOR_LOGIN', 'EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    code_hash               VARCHAR(255) NOT NULL, -- SHA-256 / Argon2 del OTP de 6 dígitos o token de reset
    attempts                SMALLINT     NOT NULL DEFAULT 0,
    max_attempts            SMALLINT     NOT NULL DEFAULT 5,
    expires_at              TIMESTAMPTZ  NOT NULL,
    consumed_at             TIMESTAMPTZ,
    request_ip              VARCHAR(45),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE trusted_devices (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_token_hash       VARCHAR(255) NOT NULL UNIQUE,
    device_name             VARCHAR(150),
    expires_at              TIMESTAMPTZ  NOT NULL,
    revoked_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 2. MOTOR ECONÓMICO (COTIZACIONES E ÍNDICES DE INFLACIÓN)
-- -----------------------------------------------------------------------------

CREATE TABLE economic_indices (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    index_code              VARCHAR(30)    NOT NULL, -- USD_OFICIAL, USD_MEP, USD_BLUE, EUR_OFICIAL, UVA, IPC_MENSUAL, IPC_ACUMULADO
    effective_date          DATE           NOT NULL,
    value                   NUMERIC(19, 6) NOT NULL CHECK (value > 0),
    source                  VARCHAR(20)    NOT NULL DEFAULT 'API' CHECK (source IN ('API', 'MANUAL')),
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_economic_index_code_date UNIQUE (index_code, effective_date)
);

-- -----------------------------------------------------------------------------
-- 3. CUENTAS, CATEGORÍAS Y MENSUALIDADES
-- -----------------------------------------------------------------------------

CREATE TABLE accounts (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id               UUID           NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    owner_user_id           UUID           REFERENCES users(id), -- NULL = cuenta compartida de la familia
    name                    VARCHAR(100)   NOT NULL,
    account_type            VARCHAR(20)    NOT NULL
                                CHECK (account_type IN ('CASH', 'BANK', 'WALLET', 'DEBIT_CARD', 'CREDIT_CARD', 'SAVINGS')),
    currency_code           VARCHAR(10)    NOT NULL,
    initial_balance         NUMERIC(19, 4) NOT NULL DEFAULT 0,
    credit_limit            NUMERIC(19, 4),
    closing_day             SMALLINT       CHECK (closing_day BETWEEN 1 AND 31),
    due_day                 SMALLINT       CHECK (due_day BETWEEN 1 AND 31),
    is_active               BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ
);

CREATE TABLE categories (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id               UUID         REFERENCES families(id) ON DELETE CASCADE, -- NULL = categoría global del sistema
    parent_category_id      UUID         REFERENCES categories(id),
    name                    VARCHAR(80)  NOT NULL,
    kind                    VARCHAR(15)  NOT NULL CHECK (kind IN ('EXPENSE', 'INCOME')),
    icon                    VARCHAR(50),
    color_hex               VARCHAR(7),
    is_system               BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE allowances (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id               UUID           NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    assigned_by_user_id     UUID           NOT NULL REFERENCES users(id),
    child_user_id           UUID           NOT NULL REFERENCES users(id),
    source_account_id       UUID           REFERENCES accounts(id),
    target_account_id       UUID           NOT NULL REFERENCES accounts(id),
    amount                  NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency_code           VARCHAR(10)    NOT NULL,
    frequency               VARCHAR(15)    NOT NULL DEFAULT 'MONTHLY' CHECK (frequency IN ('WEEKLY', 'MONTHLY')),
    day_of_period           SMALLINT       NOT NULL DEFAULT 1 CHECK (day_of_period BETWEEN 1 AND 31),
    adjustment_rule         VARCHAR(25)    NOT NULL DEFAULT 'MANUAL'
                                CHECK (adjustment_rule IN ('MANUAL', 'IPC', 'REFERENCE_CURRENCY')),
    is_active               BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 4. CUOTAS, GASTOS FIJOS, CAMBIO DE DIVISAS, TICKETS Y TRANSACCIONES
-- -----------------------------------------------------------------------------

CREATE TABLE installment_plans (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id               UUID           NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    user_id                 UUID           NOT NULL REFERENCES users(id),
    credit_card_account_id  UUID           NOT NULL REFERENCES accounts(id),
    category_id             UUID           NOT NULL REFERENCES categories(id),
    description             VARCHAR(200)   NOT NULL,
    currency_code           VARCHAR(10)    NOT NULL,
    total_installments      SMALLINT       NOT NULL CHECK (total_installments >= 2),
    has_interest            BOOLEAN        NOT NULL DEFAULT FALSE,
    cash_price              NUMERIC(19, 4) CHECK (cash_price IS NULL OR cash_price > 0),
    financed_total          NUMERIC(19, 4) NOT NULL CHECK (financed_total > 0),
    installment_amount      NUMERIC(19, 4) NOT NULL CHECK (installment_amount > 0),
    purchase_date           DATE           NOT NULL,
    first_billing_date      DATE           NOT NULL,
    status                  VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE'
                                CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE recurring_expenses (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id               UUID           NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    created_by_user_id      UUID           NOT NULL REFERENCES users(id),
    account_id              UUID           REFERENCES accounts(id),
    category_id             UUID           NOT NULL REFERENCES categories(id),
    name                    VARCHAR(120)   NOT NULL,
    scope                   VARCHAR(15)    NOT NULL DEFAULT 'FAMILY' CHECK (scope IN ('PERSONAL', 'FAMILY')),
    base_amount             NUMERIC(19, 4) NOT NULL CHECK (base_amount > 0),
    currency_code           VARCHAR(10)    NOT NULL,
    due_day                 SMALLINT       NOT NULL CHECK (due_day BETWEEN 1 AND 31),
    adjustment_rule         VARCHAR(25)    NOT NULL DEFAULT 'MANUAL'
                                CHECK (adjustment_rule IN ('MANUAL', 'IPC', 'REFERENCE_CURRENCY', 'LAST_PAID')),
    start_date              DATE           NOT NULL,
    end_date                DATE,
    is_active               BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE currency_exchanges (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id               UUID           NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    user_id                 UUID           NOT NULL REFERENCES users(id),
    source_account_id       UUID           NOT NULL REFERENCES accounts(id),
    target_account_id       UUID           NOT NULL REFERENCES accounts(id),
    source_amount           NUMERIC(19, 4) NOT NULL CHECK (source_amount > 0),
    source_currency         VARCHAR(10)    NOT NULL,
    target_amount           NUMERIC(19, 4) NOT NULL CHECK (target_amount > 0),
    target_currency         VARCHAR(10)    NOT NULL,
    applied_exchange_rate   NUMERIC(19, 6) NOT NULL CHECK (applied_exchange_rate > 0),
    fee_amount              NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
    fee_currency            VARCHAR(10),
    exchange_date           DATE           NOT NULL,
    notes                   VARCHAR(255),
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_exchange_distinct_currencies CHECK (source_currency <> target_currency),
    CONSTRAINT chk_exchange_distinct_accounts   CHECK (source_account_id <> target_account_id)
);

CREATE TABLE receipt_attachments (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id               UUID         NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    uploaded_by_user_id     UUID         NOT NULL REFERENCES users(id),
    storage_key             VARCHAR(500) NOT NULL,
    mime_type               VARCHAR(100) NOT NULL,
    file_size_bytes         BIGINT       NOT NULL CHECK (file_size_bytes > 0),
    ocr_status              VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED'
                                CHECK (ocr_status IN ('UPLOADED', 'PROCESSING', 'PARSED', 'FAILED')),
    ocr_raw_payload         JSONB,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE transactions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id               UUID           NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    user_id                 UUID           NOT NULL REFERENCES users(id), -- quien registra / paga
    attributed_to_user_id   UUID           NOT NULL REFERENCES users(id), -- a quien se atribuye el gasto (por defecto = user_id; editable por ADULTO)
    account_id              UUID           NOT NULL REFERENCES accounts(id),
    category_id             UUID           REFERENCES categories(id),
    type                    VARCHAR(20)    NOT NULL
                                CHECK (type IN (
                                    'EXPENSE', 'INCOME',
                                    'TRANSFER_OUT', 'TRANSFER_IN',
                                    'EXCHANGE_OUT', 'EXCHANGE_IN',
                                    'ALLOWANCE_OUT', 'ALLOWANCE_IN'
                                )),
    scope                   VARCHAR(15)    NOT NULL DEFAULT 'PERSONAL' CHECK (scope IN ('PERSONAL', 'FAMILY')),
    status                  VARCHAR(15)    NOT NULL DEFAULT 'POSTED'
                                CHECK (status IN ('POSTED', 'PROJECTED', 'CANCELLED')),
    amount                  NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency_code           VARCHAR(10)    NOT NULL,
    transaction_date        DATE           NOT NULL,
    description             VARCHAR(255),
    notes                   TEXT,
    installment_plan_id     UUID           REFERENCES installment_plans(id) ON DELETE CASCADE,
    installment_number      SMALLINT       CHECK (installment_number IS NULL OR installment_number >= 1),
    recurring_expense_id    UUID           REFERENCES recurring_expenses(id),
    currency_exchange_id    UUID           REFERENCES currency_exchanges(id) ON DELETE CASCADE,
    allowance_id            UUID           REFERENCES allowances(id),
    receipt_attachment_id   UUID           REFERENCES receipt_attachments(id),
    transfer_group_id       UUID,          -- vincula pares TRANSFER_OUT / TRANSFER_IN o ALLOWANCE_OUT / ALLOWANCE_IN
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ
);

CREATE TABLE economic_snapshots (
    transaction_id              UUID PRIMARY KEY REFERENCES transactions(id) ON DELETE CASCADE,
    reference_currency          VARCHAR(10)    NOT NULL,
    reference_rate_source       VARCHAR(30)    NOT NULL,
    reference_exchange_rate     NUMERIC(19, 6) NOT NULL CHECK (reference_exchange_rate > 0),
    amount_in_reference         NUMERIC(19, 4) NOT NULL,
    base_currency_amount        NUMERIC(19, 4) NOT NULL,
    ipc_index_value             NUMERIC(19, 6),
    uva_value                   NUMERIC(19, 6),
    snapshot_date               DATE           NOT NULL,
    created_at                  TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 5. METAS DE AHORRO
-- -----------------------------------------------------------------------------

CREATE TABLE savings_goals (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id               UUID           NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    owner_user_id           UUID           REFERENCES users(id), -- NULL si es meta familiar
    scope                   VARCHAR(15)    NOT NULL DEFAULT 'FAMILY' CHECK (scope IN ('PERSONAL', 'FAMILY')),
    name                    VARCHAR(120)   NOT NULL,
    target_amount           NUMERIC(19, 4) NOT NULL CHECK (target_amount > 0),
    target_unit             VARCHAR(15)    NOT NULL DEFAULT 'USD'
                                CHECK (target_unit IN ('ARS', 'USD', 'EUR', 'UVA', 'ARS_IPC')),
    target_date             DATE,
    status                  VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE'
                                CHECK (status IN ('ACTIVE', 'COMPLETED', 'PAUSED', 'CANCELLED')),
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE goal_contributions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    savings_goal_id         UUID           NOT NULL REFERENCES savings_goals(id) ON DELETE CASCADE,
    user_id                 UUID           NOT NULL REFERENCES users(id),
    transaction_id          UUID           REFERENCES transactions(id),
    amount                  NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency_code           VARCHAR(10)    NOT NULL,
    amount_in_goal_unit     NUMERIC(19, 4) NOT NULL CHECK (amount_in_goal_unit > 0),
    contribution_date       DATE           NOT NULL,
    notes                   VARCHAR(255),
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 6. MÓDULO DE VEHÍCULOS (CONTABILIDAD, COMBUSTIBLE Y DESGASTE)
-- -----------------------------------------------------------------------------

CREATE TABLE vehicles (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id               UUID         NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    nickname                VARCHAR(80)  NOT NULL,
    brand                   VARCHAR(60)  NOT NULL,
    model                   VARCHAR(80)  NOT NULL,
    model_year              SMALLINT     NOT NULL CHECK (model_year BETWEEN 1900 AND 2100),
    license_plate           VARCHAR(20)  NOT NULL,
    primary_fuel_type       VARCHAR(25)  NOT NULL
                                CHECK (primary_fuel_type IN ('NAFTA_SUPER', 'NAFTA_PREMIUM', 'DIESEL', 'DIESEL_PREMIUM', 'GNC', 'ELECTRICO', 'HIBRIDO')),
    secondary_fuel_type     VARCHAR(25)
                                CHECK (secondary_fuel_type IS NULL OR secondary_fuel_type IN ('NAFTA_SUPER', 'NAFTA_PREMIUM', 'DIESEL', 'DIESEL_PREMIUM', 'GNC')),
    current_odometer_km     INTEGER      NOT NULL DEFAULT 0 CHECK (current_odometer_km >= 0),
    vtv_expiration_date     DATE,
    insurance_expiration    DATE,
    insurance_company       VARCHAR(100),
    notes                   TEXT,
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,
    CONSTRAINT uq_vehicle_family_plate UNIQUE (family_id, license_plate)
);

CREATE TABLE fuel_logs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id               UUID           NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    vehicle_id              UUID           NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    transaction_id          UUID           NOT NULL UNIQUE REFERENCES transactions(id) ON DELETE CASCADE,
    user_id                 UUID           NOT NULL REFERENCES users(id),
    fuel_date               DATE           NOT NULL,
    odometer_km             INTEGER        NOT NULL CHECK (odometer_km >= 0),
    fuel_type               VARCHAR(25)    NOT NULL
                                CHECK (fuel_type IN ('NAFTA_SUPER', 'NAFTA_PREMIUM', 'DIESEL', 'DIESEL_PREMIUM', 'GNC', 'ELECTRICO')),
    liters                  NUMERIC(12, 3) NOT NULL CHECK (liters > 0),
    price_per_liter         NUMERIC(12, 4) NOT NULL CHECK (price_per_liter > 0),
    total_amount            NUMERIC(19, 4) NOT NULL CHECK (total_amount > 0),
    currency_code           VARCHAR(10)    NOT NULL,
    full_tank               BOOLEAN        NOT NULL DEFAULT TRUE,
    station_name            VARCHAR(100),
    notes                   VARCHAR(255),
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE maintenance_logs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id               UUID           NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    vehicle_id              UUID           NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    transaction_id          UUID           UNIQUE REFERENCES transactions(id) ON DELETE SET NULL,
    user_id                 UUID           NOT NULL REFERENCES users(id),
    maintenance_type        VARCHAR(30)    NOT NULL
                                CHECK (maintenance_type IN (
                                    'SERVICE', 'OIL_CHANGE', 'FILTERS', 'TIRES', 'BRAKES',
                                    'TIMING_BELT', 'BATTERY', 'SUSPENSION', 'ALIGNMENT',
                                    'VTV', 'REPAIR', 'OTHER'
                                )),
    maintenance_date        DATE           NOT NULL,
    odometer_km             INTEGER        NOT NULL CHECK (odometer_km >= 0),
    description             VARCHAR(255)   NOT NULL,
    workshop_name           VARCHAR(120),
    cost_amount             NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (cost_amount >= 0),
    currency_code           VARCHAR(10)    NOT NULL,
    next_due_km             INTEGER        CHECK (next_due_km IS NULL OR next_due_km > odometer_km),
    next_due_date           DATE,
    notes                   TEXT,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 7. NOTIFICACIONES, WEB PUSH (PWA) Y VERSIONES DE LA APP
-- -----------------------------------------------------------------------------

CREATE TABLE notifications (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id               UUID         REFERENCES families(id) ON DELETE CASCADE, -- NULL = notificación global (ej. nueva versión)
    user_id                 UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category                VARCHAR(30)  NOT NULL
                                CHECK (category IN (
                                    'APP_UPDATE', 'EXPENSE_ATTRIBUTED', 'ALLOWANCE',
                                    'FIXED_EXPENSE_DUE', 'VEHICLE_ALERT', 'SYSTEM'
                                )),
    title                   VARCHAR(140) NOT NULL,
    body                    TEXT         NOT NULL,
    metadata                JSONB,
    read_at                 TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE push_subscriptions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint                TEXT         NOT NULL UNIQUE,
    p256dh_key              TEXT         NOT NULL,
    auth_key                TEXT         NOT NULL,
    user_agent              VARCHAR(255),
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE app_releases (
    version                 VARCHAR(30)  PRIMARY KEY, -- ej. 0.1.0, 0.2.1
    title                   VARCHAR(140) NOT NULL,
    changelog_markdown      TEXT         NOT NULL,
    is_breaking             BOOLEAN      NOT NULL DEFAULT FALSE,
    released_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 8. ÍNDICES ESTRATÉGICOS
-- -----------------------------------------------------------------------------

-- Usuarios, tokens y códigos 2FA / reset
CREATE INDEX idx_users_family              ON users (family_id) WHERE is_active = TRUE;
CREATE INDEX idx_refresh_tokens_user       ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_email_codes_active        ON email_security_codes (user_id, purpose, created_at DESC) WHERE consumed_at IS NULL;
CREATE INDEX idx_trusted_devices_user      ON trusted_devices (user_id) WHERE revoked_at IS NULL;

-- Índices económicos
CREATE INDEX idx_economic_indices_lookup   ON economic_indices (index_code, effective_date DESC);

-- Transacciones (temporalidad año/mes/semana/día, vista personal/familiar y atribución por persona)
CREATE INDEX idx_tx_family_date_active     ON transactions (family_id, transaction_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_tx_user_date_active       ON transactions (family_id, user_id, transaction_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_tx_attributed_date_active ON transactions (family_id, attributed_to_user_id, transaction_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_tx_account_date_active    ON transactions (account_id, transaction_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_tx_category_date_active   ON transactions (family_id, category_id, transaction_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_tx_installment_plan       ON transactions (installment_plan_id, installment_number) WHERE installment_plan_id IS NOT NULL;
CREATE INDEX idx_tx_recurring_expense      ON transactions (recurring_expense_id, transaction_date DESC) WHERE recurring_expense_id IS NOT NULL;
CREATE INDEX idx_tx_status_projected       ON transactions (family_id, transaction_date) WHERE status = 'PROJECTED' AND deleted_at IS NULL;

-- Cambio de divisas (para cálculo de precio promedio de compra por moneda)
CREATE INDEX idx_currency_exch_family_curr ON currency_exchanges (family_id, target_currency, exchange_date DESC);

-- Metas
CREATE INDEX idx_goal_contrib_goal_date    ON goal_contributions (savings_goal_id, contribution_date DESC);

-- Vehículos (consumo y alertas de desgaste)
CREATE INDEX idx_fuel_vehicle_date         ON fuel_logs (vehicle_id, fuel_date DESC, odometer_km DESC);
CREATE INDEX idx_maint_vehicle_date        ON maintenance_logs (vehicle_id, maintenance_date DESC);
CREATE INDEX idx_maint_vehicle_next_due    ON maintenance_logs (vehicle_id, next_due_date, next_due_km);

-- Notificaciones in-app
CREATE INDEX idx_notif_user_unread         ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX idx_push_sub_user_active      ON push_subscriptions (user_id) WHERE is_active = TRUE;

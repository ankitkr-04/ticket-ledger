-- =========================================
-- V1: Complete TicketLedger Schema
-- =========================================
-- Purpose: Initialize complete database schema for TicketLedger
-- Date: 2026-01-21
-- Reference: docs/architecture/04_database_schema.md
-- =========================================

-- ============================================
-- 1. EXTENSIONS
-- ============================================

CREATE EXTENSION IF NOT EXISTS "btree_gist";  -- Required for exclusion constraints

-- ============================================
-- 2. ENUM TYPES
-- ============================================

CREATE TYPE user_role AS ENUM ('CUSTOMER', 'ADMIN');

CREATE TYPE seat_status AS ENUM ('AVAILABLE', 'HELD', 'SOLD');

CREATE TYPE booking_status AS ENUM (
    'HELD',
    'CONFIRMED',
    'EXPIRED',
    'CANCELLED',
    'COMPLETED',
    'REFUND_REQUIRED',
    'REFUND_INITIATED',
    'REFUNDED'
);

CREATE TYPE payment_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED');

CREATE TYPE showtime_status AS ENUM ('ACTIVE', 'PAUSED', 'INACTIVE');

-- ============================================
-- 3. TABLES
-- ============================================

-- 3.1 Users table (Identity & Authentication)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role user_role DEFAULT 'CUSTOMER',
    is_verified BOOLEAN DEFAULT FALSE,
    full_name VARCHAR(255),
    profile_image_url TEXT,
    version BIGINT DEFAULT 0 NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3.2 Refresh tokens table (JWT Token Storage)
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3.3 Idempotency keys table (Request Deduplication)
CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_hash VARCHAR(64),
    response_status INT,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

-- 3.4 Theaters table (Physical Buildings - Scope Root)
CREATE TABLE theaters (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    address TEXT,
    version BIGINT DEFAULT 0 NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3.5 Admin theater access table (Admin Authorization)
CREATE TABLE admin_theater_access (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    theater_id UUID NOT NULL REFERENCES theaters(id) ON DELETE CASCADE,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uq_admin_theater UNIQUE (user_id, theater_id)
);

-- 3.6 Screens table (Physical Theater Rooms)
CREATE TABLE screens (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    theater_id UUID NOT NULL,
    name VARCHAR(50) NOT NULL,
    total_seats INT DEFAULT 0,
    version BIGINT DEFAULT 0 NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_screen_theater FOREIGN KEY (theater_id) 
        REFERENCES theaters(id) ON DELETE RESTRICT
);

-- 3.7 Movies table (Content Metadata)
CREATE TABLE movies (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    title VARCHAR(255) NOT NULL,
    duration_minutes INT NOT NULL,
    description TEXT,
    thumbnail_url TEXT,
    genre VARCHAR(100),
    language VARCHAR(50),
    version BIGINT DEFAULT 0 NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_movie_duration CHECK (duration_minutes > 0 AND duration_minutes <= 600)
);

-- 3.8 Seat tiers table (Pricing Categories)
CREATE TABLE seat_tiers (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(50) NOT NULL,
    price_multiplier DECIMAL(3, 2) DEFAULT 1.0,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3.9 Showtimes table (Scheduled Screenings)
CREATE TABLE showtimes (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    movie_id UUID NOT NULL REFERENCES movies(id),
    screen_id UUID NOT NULL REFERENCES screens(id),
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status showtime_status DEFAULT 'ACTIVE',
    version BIGINT DEFAULT 0 NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3.10 Seats table (Inventory Units)
CREATE TABLE seats (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    showtime_id UUID NOT NULL REFERENCES showtimes(id) ON DELETE CASCADE,
    tier_id UUID NOT NULL REFERENCES seat_tiers(id),
    seat_row VARCHAR(5) NOT NULL,
    seat_number VARCHAR(5) NOT NULL,
    status seat_status DEFAULT 'AVAILABLE',
    version INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uq_seat_showtime_row_number UNIQUE (showtime_id, seat_row, seat_number)
);

-- 3.11 Bookings table (Reservation Ledger)
CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL REFERENCES users(id),
    showtime_id UUID NOT NULL REFERENCES showtimes(id),
    status booking_status DEFAULT 'HELD',
    locked_until TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    version BIGINT DEFAULT 0 NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3.12 Booking seats junction table
CREATE TABLE booking_seats (
    booking_id UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    seat_id UUID NOT NULL REFERENCES seats(id) ON DELETE CASCADE,
    price_at_booking DECIMAL(10, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    PRIMARY KEY (booking_id, seat_id)
);

-- 3.13 Payments table (Financial Transactions)
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    provider VARCHAR(50) NOT NULL,
    method VARCHAR(50),  -- Payment method (CREDIT_CARD, UPI, NETBANKING) - captured from gateway response
    status payment_status DEFAULT 'PENDING',
    provider_transaction_id VARCHAR(255),
    provider_response JSONB,
    provider_captured_at TIMESTAMPTZ,
    attempt_number INT DEFAULT 1 NOT NULL,
    version BIGINT DEFAULT 0 NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_payment_amount_positive CHECK (amount > 0)
);

-- 3.14 Admin audit log table (Privileged Operation Audit Trail)
CREATE TABLE admin_audit_log (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    
    -- Targets (Explicit FKs ensure integrity)
    booking_id UUID REFERENCES bookings(id) ON DELETE RESTRICT,
    showtime_id UUID REFERENCES showtimes(id) ON DELETE RESTRICT,
    theater_id UUID REFERENCES theaters(id) ON DELETE RESTRICT,
    
    -- Actor
    admin_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    
    -- Action
    action VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason TEXT NOT NULL,
    
    -- Safety
    idempotency_key VARCHAR(64) UNIQUE,
    
    -- Provider context (for payment-related actions)
    provider VARCHAR(50),
    provider_refund_id VARCHAR(100),
    
    -- Timestamps
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- At least one target must be specified
    CONSTRAINT chk_audit_has_target CHECK (
        booking_id IS NOT NULL OR 
        showtime_id IS NOT NULL OR 
        theater_id IS NOT NULL
    )
);

-- ============================================
-- 4. CONSTRAINTS
-- ============================================

-- 4.1 GiST Exclusion Constraint for Showtime Overlaps
ALTER TABLE showtimes
ADD CONSTRAINT no_screen_overlap EXCLUDE USING gist (
    screen_id WITH =,
    tstzrange(start_time, end_time) WITH &&
) WHERE (deleted_at IS NULL);

-- ============================================
-- 5. INDEXES
-- ============================================

-- 5.1 Users indexes
CREATE UNIQUE INDEX idx_users_email_active ON users(email) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_deleted ON users(deleted_at);

-- 5.2 Refresh tokens indexes
CREATE INDEX idx_refresh_token_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_token_active ON refresh_tokens(token) WHERE revoked = FALSE;
CREATE INDEX idx_refresh_token_valid_user ON refresh_tokens(user_id, expires_at) WHERE revoked = FALSE;

-- 5.3 Idempotency keys indexes
CREATE INDEX idx_idempotency_user_id ON idempotency_keys(user_id);
CREATE INDEX idx_idempotency_expires_at ON idempotency_keys(expires_at);

-- 5.4 Theaters indexes
CREATE UNIQUE INDEX idx_theaters_name_city_active ON theaters(name, city) WHERE deleted_at IS NULL;
CREATE INDEX idx_theaters_name_city ON theaters(name, city) WHERE deleted_at IS NULL;
CREATE INDEX idx_theaters_city ON theaters(city) WHERE deleted_at IS NULL;
CREATE INDEX idx_theaters_deleted ON theaters(deleted_at);

-- 5.5 Admin theater access indexes
CREATE INDEX idx_admin_access_user ON admin_theater_access(user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_admin_access_theater ON admin_theater_access(theater_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_admin_access_revoked ON admin_theater_access(revoked_at);

-- 5.6 Screens indexes
CREATE UNIQUE INDEX idx_screens_name_theater_active ON screens(theater_id, name) WHERE deleted_at IS NULL;
CREATE INDEX idx_screens_theater ON screens(theater_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_screens_deleted ON screens(deleted_at);

-- 5.7 Movies indexes
CREATE INDEX idx_movies_active ON movies(created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_movies_deleted ON movies(deleted_at);

-- 5.8 Seat tiers indexes
CREATE UNIQUE INDEX idx_seat_tiers_name_active ON seat_tiers(name) WHERE deleted_at IS NULL;
CREATE INDEX idx_seat_tiers_active ON seat_tiers(name) WHERE deleted_at IS NULL;
CREATE INDEX idx_seat_tiers_deleted ON seat_tiers(deleted_at);

-- 5.9 Showtimes indexes
CREATE INDEX idx_showtimes_active ON showtimes(start_time) WHERE status = 'ACTIVE' AND deleted_at IS NULL;
CREATE INDEX idx_showtimes_expiry ON showtimes(start_time, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_showtimes_deleted ON showtimes(deleted_at);

-- 5.10 Seats indexes
CREATE INDEX idx_seats_avail ON seats(showtime_id, status);
CREATE INDEX idx_seats_version ON seats(id, version);

-- 5.11 Bookings indexes
CREATE INDEX idx_bookings_reaper ON bookings(status, locked_until);
CREATE INDEX idx_bookings_user ON bookings(user_id, created_at DESC);
CREATE INDEX idx_bookings_showtime ON bookings(showtime_id, status);

-- 5.12 Booking seats indexes
CREATE INDEX idx_booking_seats_booking ON booking_seats(booking_id);
CREATE INDEX idx_booking_seats_seat ON booking_seats(seat_id);

-- 5.13 Payments indexes
CREATE INDEX idx_payments_provider_id ON payments(provider_transaction_id);
CREATE INDEX idx_payments_booking ON payments(booking_id);

-- 5.14 Admin audit log indexes
CREATE INDEX idx_audit_admin ON admin_audit_log(admin_user_id, created_at DESC);
CREATE INDEX idx_audit_booking ON admin_audit_log(booking_id) WHERE booking_id IS NOT NULL;
CREATE INDEX idx_audit_showtime ON admin_audit_log(showtime_id) WHERE showtime_id IS NOT NULL;
CREATE INDEX idx_audit_theater ON admin_audit_log(theater_id) WHERE theater_id IS NOT NULL;
CREATE UNIQUE INDEX idx_audit_idempotency ON admin_audit_log(idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_audit_failed ON admin_audit_log(status, created_at DESC) WHERE status = 'FAILED';

-- ============================================
-- 6. TABLE COMMENTS
-- ============================================

COMMENT ON TABLE users IS 'User accounts and authentication credentials';
COMMENT ON TABLE refresh_tokens IS 'JWT refresh tokens for token rotation and revocation';
COMMENT ON TABLE idempotency_keys IS 'Request deduplication using database-backed idempotency keys';
COMMENT ON TABLE theaters IS 'Physical theater/multiplex buildings - the ownership boundary for admin operations';
COMMENT ON TABLE admin_theater_access IS 'Many-to-many relationship between admins and theaters they can manage';
COMMENT ON TABLE screens IS 'Physical screening rooms within a theater building';
COMMENT ON TABLE movies IS 'Movie information for scheduling and catalog display';
COMMENT ON TABLE seat_tiers IS 'Seat pricing tiers (e.g., VIP, Regular, Balcony)';
COMMENT ON TABLE showtimes IS 'Time-bound events linking movies to screens with overlap prevention';
COMMENT ON TABLE seats IS 'Individual bookable seats for each showtime';
COMMENT ON TABLE bookings IS 'Central ledger for all booking transactions';
COMMENT ON TABLE booking_seats IS 'Many-to-many relationship between bookings and seats with price snapshot';
COMMENT ON TABLE payments IS 'Payment gateway interactions and status tracking';
COMMENT ON TABLE admin_audit_log IS 'Immutable audit trail for all privileged admin operations';

-- Column comments for key fields
COMMENT ON COLUMN admin_audit_log.idempotency_key IS 'Prevents duplicate admin operations (e.g., double refunds)';
COMMENT ON COLUMN admin_audit_log.status IS 'INITIATED: started, COMPLETED: finished successfully, FAILED: operation failed';
COMMENT ON COLUMN screens.theater_id IS 'Parent theater - all screens must belong to a theater';
COMMENT ON COLUMN users.full_name IS 'Display name for user profiles and customer support';
COMMENT ON COLUMN users.profile_image_url IS 'Avatar URL (S3/CDN link) for personalized UI';
COMMENT ON COLUMN movies.description IS 'Movie synopsis/plot for detail pages';
COMMENT ON COLUMN movies.thumbnail_url IS 'Poster image URL for movie listings';
COMMENT ON COLUMN movies.genre IS 'Movie genre (Action, Drama, Comedy, etc.) for filtering';
COMMENT ON COLUMN movies.language IS 'Primary language (Hindi, English, Tamil, etc.) for filtering';
COMMENT ON COLUMN payments.attempt_number IS 'Sequential payment attempt counter for this booking';
COMMENT ON COLUMN payments.version IS 'Optimistic locking counter for concurrent update protection';

-- =========================================
-- V6: Theater-Scoped Admin & Schema Enrichment
-- =========================================
-- Purpose: Add theater-scoped administration, enrich user/movie entities,
--          create audit trail, and add booking refund states
-- Date: 2026-01-20
-- Reference: docs/decisions/006_admin_scope_and_schema.md
-- =========================================

-- 1. Update Enums (Privileged States)
ALTER TYPE booking_status ADD VALUE IF NOT EXISTS 'REFUND_INITIATED';
ALTER TYPE booking_status ADD VALUE IF NOT EXISTS 'REFUNDED';
ALTER TYPE booking_status ADD VALUE IF NOT EXISTS 'REFUND_REQUIRED';
ALTER TYPE showtime_status ADD VALUE IF NOT EXISTS 'PAUSED';

-- 2. Create Theater Entity (The Scope Root)
CREATE TABLE theaters (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    address TEXT,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT DEFAULT 0 NOT NULL
);

-- 3. Link Screens to Theaters
-- Adding column, making Theater the parent of Screen
ALTER TABLE screens ADD COLUMN theater_id UUID;

-- Safe Dev Migration: Delete orphaned screens if any exist, then enforce FK
-- NOTE: This will delete existing screens - acceptable for Phase 2 dev
DELETE FROM seats WHERE screen_id IN (SELECT id FROM screens);
DELETE FROM showtimes WHERE screen_id IN (SELECT id FROM screens);
DELETE FROM screens WHERE 1=1;

-- Now enforce NOT NULL and FK constraint
ALTER TABLE screens ALTER COLUMN theater_id SET NOT NULL;
ALTER TABLE screens ADD CONSTRAINT fk_screens_theater 
    FOREIGN KEY (theater_id) REFERENCES theaters(id) ON DELETE CASCADE;

-- 4. Create Admin Theater Access (The Scope Map)
CREATE TABLE admin_theater_access (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    theater_id UUID NOT NULL REFERENCES theaters(id) ON DELETE CASCADE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    version BIGINT DEFAULT 0 NOT NULL,
    
    CONSTRAINT uq_admin_theater UNIQUE (user_id, theater_id)
);

-- 5. Enrich User Table (Profile Data)
ALTER TABLE users ADD COLUMN IF NOT EXISTS full_name VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_image_url TEXT;

-- 6. Enrich Movie Table (Metadata)
ALTER TABLE movies ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS thumbnail_url TEXT;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS genre VARCHAR(100);
ALTER TABLE movies ADD COLUMN IF NOT EXISTS language VARCHAR(50);

-- Ensure duration_minutes exists (might be added in V2)
-- This is idempotent if column already exists
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name='movies' AND column_name='duration_minutes'
    ) THEN
        ALTER TABLE movies ADD COLUMN duration_minutes INTEGER;
    END IF;
END $$;

-- 7. Create Admin Audit Log (Explicit FKs for Integrity)
CREATE TABLE admin_audit_log (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    
    -- 🎯 TARGETS (Explicit FKs ensure integrity)
    -- Use ON DELETE RESTRICT to prevent accidental deletion of audited resources
    booking_id UUID REFERENCES bookings(booking_id) ON DELETE RESTRICT,
    showtime_id UUID REFERENCES showtimes(id) ON DELETE RESTRICT,
    theater_id UUID REFERENCES theaters(id) ON DELETE RESTRICT,
    
    -- 👮 ACTOR
    admin_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    
    -- 📝 ACTION
    action VARCHAR(50) NOT NULL,  -- Enum: REFUND, PAUSE_SHOWTIME, FORCE_EXPIRE
    status VARCHAR(50) NOT NULL,  -- Enum: INITIATED, COMPLETED, FAILED
    reason TEXT NOT NULL,
    
    -- 🛡️ SAFETY
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    stripe_refund_id VARCHAR(100),
    
    -- ⏱️ TIMESTAMPS
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    version BIGINT DEFAULT 0 NOT NULL,
    
    -- ✅ CONSTRAINTS
    -- At least one target must be specified
    CONSTRAINT chk_audit_has_target CHECK (
        booking_id IS NOT NULL OR 
        showtime_id IS NOT NULL OR 
        theater_id IS NOT NULL
    )
);

-- 8. Indexes for Performance
CREATE INDEX idx_audit_booking ON admin_audit_log(booking_id) WHERE booking_id IS NOT NULL;
CREATE INDEX idx_audit_showtime ON admin_audit_log(showtime_id) WHERE showtime_id IS NOT NULL;
CREATE INDEX idx_audit_theater ON admin_audit_log(theater_id) WHERE theater_id IS NOT NULL;
CREATE INDEX idx_audit_admin_user ON admin_audit_log(admin_user_id);
CREATE INDEX idx_audit_status ON admin_audit_log(status);
CREATE INDEX idx_audit_created_at ON admin_audit_log(created_at DESC);
CREATE INDEX idx_audit_idempotency ON admin_audit_log(idempotency_key);

CREATE INDEX idx_admin_access_user ON admin_theater_access(user_id);
CREATE INDEX idx_admin_access_theater ON admin_theater_access(theater_id);
CREATE INDEX idx_screens_theater ON screens(theater_id);

-- 9. Comments for Documentation
COMMENT ON TABLE theaters IS 'Physical theater/multiplex buildings - the ownership boundary for admin operations';
COMMENT ON TABLE admin_theater_access IS 'Many-to-many relationship between admins and theaters they can manage';
COMMENT ON TABLE admin_audit_log IS 'Immutable audit trail for all privileged admin operations';
COMMENT ON COLUMN admin_audit_log.idempotency_key IS 'Prevents duplicate admin operations (e.g., double refunds)';
COMMENT ON COLUMN admin_audit_log.status IS 'INITIATED: started, COMPLETED: finished successfully, FAILED: operation failed';
COMMENT ON COLUMN screens.theater_id IS 'Parent theater - all screens must belong to a theater';
COMMENT ON COLUMN users.full_name IS 'Display name for user profiles and customer support';
COMMENT ON COLUMN users.profile_image_url IS 'Avatar URL (S3/CDN link) for personalized UI';
COMMENT ON COLUMN movies.description IS 'Movie synopsis/plot for detail pages';
COMMENT ON COLUMN movies.thumbnail_url IS 'Poster image URL for movie listings';
COMMENT ON COLUMN movies.genre IS 'Movie genre (Action, Drama, Comedy, etc.) for filtering';
COMMENT ON COLUMN movies.language IS 'Primary language (Hindi, English, Tamil, etc.) for filtering';
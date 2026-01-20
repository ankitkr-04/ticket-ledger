-- V6__admin_audit_and_enums.sql

-- 1. Add new states to booking_status enum
-- Note: PostgreSQL ALTER TYPE cannot run inside a transaction block easily in some versions,
-- but Flyway usually handles this. If it fails, we might need a non-transactional execution.
ALTER TYPE booking_status ADD VALUE IF NOT EXISTS 'REFUND_INITIATED';
ALTER TYPE booking_status ADD VALUE IF NOT EXISTS 'REFUNDED';
ALTER TYPE booking_status ADD VALUE IF NOT EXISTS 'REFUND_REQUIRED';

-- 2. Add PAUSED to showtime_status (if not already present)
ALTER TYPE showtime_status ADD VALUE IF NOT EXISTS 'PAUSED';

-- 3. Create Admin Audit Log Table
CREATE TABLE admin_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(booking_id),
    admin_user_id UUID NOT NULL REFERENCES users(user_id),
    action VARCHAR(50) NOT NULL, -- e.g., 'REFUND', 'PAUSE_SHOWTIME'
    reason TEXT NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    stripe_refund_id VARCHAR(100), -- Nullable, populated if refund successful
    status VARCHAR(50) NOT NULL, -- 'INITIATED', 'COMPLETED', 'FAILED'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE
);

-- 4. Indexes for performance and reconciliation
CREATE INDEX idx_audit_booking_id ON admin_audit_log(booking_id);
CREATE INDEX idx_audit_status ON admin_audit_log(status); -- Critical for Reconciliation Job finding 'INITIATED' rows
CREATE INDEX idx_audit_created_at ON admin_audit_log(created_at);
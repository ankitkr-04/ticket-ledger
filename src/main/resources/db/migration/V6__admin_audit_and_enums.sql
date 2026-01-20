-- 1. Update Enums
ALTER TYPE booking_status ADD VALUE IF NOT EXISTS 'REFUND_INITIATED';
ALTER TYPE booking_status ADD VALUE IF NOT EXISTS 'REFUNDED';
ALTER TYPE booking_status ADD VALUE IF NOT EXISTS 'REFUND_REQUIRED';
ALTER TYPE showtime_status ADD VALUE IF NOT EXISTS 'PAUSED';

-- 2. Create Admin Audit Log Table (Compatible with BaseEntity)
CREATE TABLE admin_audit_log (
    id UUID PRIMARY KEY DEFAULT uuidv7(), -- BaseEntity ID
    
    booking_id UUID NOT NULL REFERENCES bookings(booking_id),
    admin_user_id UUID NOT NULL REFERENCES users(user_id),
    
    action VARCHAR(50) NOT NULL,        -- ENUM mapped as String
    status VARCHAR(50) NOT NULL,        -- ENUM mapped as String
    reason TEXT NOT NULL,
    
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    stripe_refund_id VARCHAR(100),
    
    completed_at TIMESTAMP WITH TIME ZONE,
    
    -- BaseEntity fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    version BIGINT DEFAULT 0 NOT NULL
);

-- 3. Indexes
CREATE INDEX idx_audit_booking_id ON admin_audit_log(booking_id);
CREATE INDEX idx_audit_status ON admin_audit_log(status);
CREATE INDEX idx_audit_created_at ON admin_audit_log(created_at);
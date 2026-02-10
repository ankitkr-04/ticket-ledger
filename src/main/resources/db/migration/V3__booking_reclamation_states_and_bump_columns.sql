-- =========================================
-- V3: Booking reclamation states and bump metadata
-- =========================================
-- Purpose: Add Phase 2 stability states and booking linkage for bump/reclamation flows
-- Date: 2026-02-10
-- =========================================

-- 1) Add new states to enum types
ALTER TYPE booking_status ADD VALUE IF NOT EXISTS 'SYSTEM_CANCELLED';
ALTER TYPE booking_status ADD VALUE IF NOT EXISTS 'REFUND_REQUIRED_MANUAL';
ALTER TYPE payment_status ADD VALUE IF NOT EXISTS 'REFUND_PENDING';
ALTER TYPE payment_status ADD VALUE IF NOT EXISTS 'REFUND_FAILED';

-- 2) Add audit and relationship columns to bookings
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS bumped_by_booking_id UUID REFERENCES bookings(id),
    ADD COLUMN IF NOT EXISTS system_cancellation_reason TEXT;

-- 3) Operational clarity comments
COMMENT ON COLUMN bookings.bumped_by_booking_id IS
    'Refers to the original User 1 booking that displaced this User 2 booking';
COMMENT ON COLUMN bookings.system_cancellation_reason IS
    'System-provided reason when booking is force-cancelled for reliability/integrity recovery';

-- 4) Insert a system user for internal operations (if not already present)
INSERT INTO users (id, email, password_hash, role, full_name, is_verified)
VALUES (
    '00000000-0000-0000-0000-000000000000', 
    'system@ticketledger.internal', 
    'SYSTEM_INTERNAL_ONLY', 
    'ADMIN', 
    'TicketLedger System', 
    true
) ON CONFLICT (id) DO NOTHING;
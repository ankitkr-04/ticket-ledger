-- =========================================
-- V2: Refund failure recovery states
-- =========================================
-- Purpose: Add terminal refund failure status and optimize broken-job lookup
-- Date: 2026-02-09
-- =========================================

-- Add booking terminal state used for manual intervention flows
ALTER TYPE booking_status ADD VALUE IF NOT EXISTS 'REFUND_FAILED';

-- Optimize monitoring queries for broken jobs
DROP INDEX IF EXISTS idx_audit_failed;
CREATE INDEX idx_audit_failed ON admin_audit_log(status)
WHERE status IN ('FAILED', 'PERMANENT_FAILURE');

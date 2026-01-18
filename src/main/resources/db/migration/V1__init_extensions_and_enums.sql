-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "btree_gist";

-- Create ENUM types
CREATE TYPE user_role AS ENUM ('CUSTOMER', 'ADMIN');

CREATE TYPE seat_status AS ENUM ('AVAILABLE', 'HELD', 'SOLD');

CREATE TYPE booking_status AS ENUM (
    'HELD',
    'CONFIRMED',
    'EXPIRED',
    'CANCELLED',
    'COMPLETED',
    'REFUND_REQUIRED'
);

CREATE TYPE payment_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED');

CREATE TYPE showtime_status AS ENUM ('ACTIVE', 'PAUSED', 'INACTIVE');

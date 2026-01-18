-- ============================================
-- GiST Exclusion Constraint for Showtime Overlaps
-- ============================================

ALTER TABLE showtimes
ADD CONSTRAINT no_screen_overlap EXCLUDE USING gist (
    screen_id WITH =,
    tstzrange(start_time, end_time) WITH &&
) WHERE (deleted_at IS NULL);

-- ============================================
-- Partial Unique Indexes (Soft Delete Aware)
-- ============================================

CREATE UNIQUE INDEX idx_users_email_active ON users(email)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX idx_screens_name_active ON screens(name)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX idx_seat_tiers_name_active ON seat_tiers(name)
WHERE deleted_at IS NULL;

-- ============================================
-- Soft Delete Query Indexes
-- ============================================

CREATE INDEX idx_users_deleted ON users(deleted_at);
CREATE INDEX idx_movies_deleted ON movies(deleted_at);
CREATE INDEX idx_screens_deleted ON screens(deleted_at);
CREATE INDEX idx_showtimes_deleted ON showtimes(deleted_at);
CREATE INDEX idx_seat_tiers_deleted ON seat_tiers(deleted_at);

-- ============================================
-- Active Record Indexes
-- ============================================

CREATE INDEX idx_movies_active ON movies(created_at DESC)
WHERE deleted_at IS NULL;

CREATE INDEX idx_showtimes_active ON showtimes(start_time)
WHERE status = 'ACTIVE' AND deleted_at IS NULL;

CREATE INDEX idx_seat_tiers_active ON seat_tiers(name)
WHERE deleted_at IS NULL;

-- ============================================
-- Booking Reaper Index
-- ============================================

CREATE INDEX idx_bookings_reaper ON bookings(status, locked_until);

-- ============================================
-- Additional Performance Indexes
-- ============================================

CREATE INDEX idx_seats_avail ON seats(showtime_id, status);
CREATE INDEX idx_seats_version ON seats(id, version);
CREATE INDEX idx_bookings_user ON bookings(user_id, created_at DESC);
CREATE INDEX idx_bookings_showtime ON bookings(showtime_id, status);
CREATE INDEX idx_booking_seats_booking ON booking_seats(booking_id);
CREATE INDEX idx_booking_seats_seat ON booking_seats(seat_id);
CREATE INDEX idx_payments_provider_id ON payments(provider_transaction_id);
CREATE INDEX idx_payments_booking ON payments(booking_id);

-- ============================================
-- Showtime Expiry Index
-- ============================================

CREATE INDEX idx_showtimes_expiry ON showtimes(start_time, status)
WHERE deleted_at IS NULL;

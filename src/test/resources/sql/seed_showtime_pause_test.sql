-- Seed data for Admin Showtime Pause Integration Test

-- 1. Create a Movie
INSERT INTO movies (id, title, duration_minutes) 
VALUES ('01937b5c-9111-7000-8000-111111111111', 'Test Movie', 120);

-- 2. Create a Theater
INSERT INTO theaters (id, name, city)
VALUES ('01937b5c-9000-7000-8000-000000000001', 'Test Theater', 'Test City');

-- 3. Create a Screen
INSERT INTO screens (id, theater_id, name, total_seats) 
VALUES ('01937b5c-9222-7000-8000-222222222222', '01937b5c-9000-7000-8000-000000000001', 'Screen 1', 50);

-- 4. Create a Seat Tier
INSERT INTO seat_tiers (id, name, price_multiplier) 
VALUES ('01937b5c-9333-7000-8000-333333333333', 'Standard', 1.0);

-- 5. Create Showtimes
-- Showtime A: Will have HELD bookings (can be paused)
INSERT INTO showtimes (id, movie_id, screen_id, start_time, end_time, status) 
VALUES (
    '01937b5c-9444-7000-8000-000000000001', 
    '01937b5c-9111-7000-8000-111111111111', 
    '01937b5c-9222-7000-8000-222222222222', 
    NOW() + INTERVAL '2 days', 
    NOW() + INTERVAL '2 days' + INTERVAL '2 hours', 
    'ACTIVE'
);

-- Showtime B: Will have CONFIRMED bookings (cannot be paused)
INSERT INTO showtimes (id, movie_id, screen_id, start_time, end_time, status) 
VALUES (
    '01937b5c-9444-7000-8000-000000000002', 
    '01937b5c-9111-7000-8000-111111111111', 
    '01937b5c-9222-7000-8000-222222222222', 
    NOW() + INTERVAL '3 days', 
    NOW() + INTERVAL '3 days' + INTERVAL '2 hours', 
    'ACTIVE'
);

-- 6. Create Seats for Showtime A (HELD seats)
INSERT INTO seats (id, showtime_id, tier_id, seat_row, seat_number, status, version) VALUES 
('01937b5c-9555-7000-8000-000000000001', '01937b5c-9444-7000-8000-000000000001', '01937b5c-9333-7000-8000-333333333333', 'A', '1', 'HELD', 0),
('01937b5c-9555-7000-8000-000000000002', '01937b5c-9444-7000-8000-000000000001', '01937b5c-9333-7000-8000-333333333333', 'A', '2', 'HELD', 0);

-- 7. Create Seats for Showtime B (SOLD seats)
INSERT INTO seats (id, showtime_id, tier_id, seat_row, seat_number, status, version) VALUES 
('01937b5c-9555-7000-8000-000000000003', '01937b5c-9444-7000-8000-000000000002', '01937b5c-9333-7000-8000-333333333333', 'B', '1', 'SOLD', 0),
('01937b5c-9555-7000-8000-000000000004', '01937b5c-9444-7000-8000-000000000002', '01937b5c-9333-7000-8000-333333333333', 'B', '2', 'SOLD', 0);

-- 8. Create Users (Admin and Customer)
INSERT INTO users (id, email, password_hash, role, is_verified) VALUES 
('01937b5c-9666-7000-8000-000000000001', 'admin@test.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRryg0L.RzqlSheSFJmpzh.MGdy', 'ADMIN', true),
('01937b5c-9666-7000-8000-000000000002', 'customer@test.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRryg0L.RzqlSheSFJmpzh.MGdy', 'CUSTOMER', true);

-- 9. Grant Admin access to the theater
INSERT INTO admin_theater_access (id, user_id, theater_id) 
VALUES ('01937b5c-9777-7000-8000-000000000001', '01937b5c-9666-7000-8000-000000000001', '01937b5c-9000-7000-8000-000000000001');

-- 10. Create HELD Bookings for Showtime A
INSERT INTO bookings (id, user_id, showtime_id, status, locked_until, version) VALUES 
('01937b5c-9888-7000-8000-000000000001', '01937b5c-9666-7000-8000-000000000002', '01937b5c-9444-7000-8000-000000000001', 'HELD', NOW() + INTERVAL '15 minutes', 0),
('01937b5c-9888-7000-8000-000000000002', '01937b5c-9666-7000-8000-000000000002', '01937b5c-9444-7000-8000-000000000001', 'HELD', NOW() + INTERVAL '15 minutes', 0);

-- 11. Link bookings to seats (Showtime A - HELD)
INSERT INTO booking_seats (booking_id, seat_id, price_at_booking) VALUES 
('01937b5c-9888-7000-8000-000000000001', '01937b5c-9555-7000-8000-000000000001', 10.00),
('01937b5c-9888-7000-8000-000000000002', '01937b5c-9555-7000-8000-000000000002', 10.00);

-- 12. Create CONFIRMED Booking for Showtime B (sold ticket)
INSERT INTO bookings (id, user_id, showtime_id, status, confirmed_at, version) VALUES 
('01937b5c-9888-7000-8000-000000000003', '01937b5c-9666-7000-8000-000000000002', '01937b5c-9444-7000-8000-000000000002', 'CONFIRMED', NOW(), 0);

-- 13. Link confirmed booking to seats (Showtime B - CONFIRMED)
INSERT INTO booking_seats (booking_id, seat_id, price_at_booking) VALUES 
('01937b5c-9888-7000-8000-000000000003', '01937b5c-9555-7000-8000-000000000003', 10.00),
('01937b5c-9888-7000-8000-000000000003', '01937b5c-9555-7000-8000-000000000004', 10.00);

-- 14. Create a successful payment for the confirmed booking
INSERT INTO payments (id, booking_id, provider, provider_transaction_id, amount, currency, status) VALUES 
('01937b5c-9999-7000-8000-000000000001', '01937b5c-9888-7000-8000-000000000003', 'STRIPE', 'txn_test_confirmed', 20.00, 'INR', 'SUCCESS');

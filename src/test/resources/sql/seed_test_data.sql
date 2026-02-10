-- 1. Create a Movie
INSERT INTO movies (id, title, duration_minutes) 
VALUES ('01937b5c-a111-7000-8000-111111111111', 'Inception', 148);

-- 1.5 Create a Theater
INSERT INTO theaters (id, name, city)
VALUES ('01937b5c-a000-7000-8000-000000000001', 'PVR Icon', 'Mumbai');

-- 2. Create a Screen
INSERT INTO screens (id, theater_id, name, total_seats) 
VALUES ('01937b5c-a222-7000-8000-222222222222', '01937b5c-a000-7000-8000-000000000001', 'IMAX Hall 1', 100);

-- 3. Create a Seat Tier (VIP)
INSERT INTO seat_tiers (id, name, price_multiplier) 
VALUES ('01937b5c-a333-7000-8000-333333333333', 'VIP', 1.5);

-- 4. Create a Showtime (Active, in the future)
INSERT INTO showtimes (id, movie_id, screen_id, start_time, end_time, status) 
VALUES (
    '01937b5c-a444-7000-8000-444444444444', 
    '01937b5c-a111-7000-8000-111111111111', 
    '01937b5c-a222-7000-8000-222222222222', 
    NOW() + INTERVAL '1 day', 
    NOW() + INTERVAL '1 day' + INTERVAL '2 hours', 
    'ACTIVE'
);

-- 5. Create Seats (A1, A2, A3)
INSERT INTO seats (id, showtime_id, tier_id, seat_row, seat_number, status, version) VALUES 
('01937b5c-a555-7000-8000-555555555551', '01937b5c-a444-7000-8000-444444444444', '01937b5c-a333-7000-8000-333333333333', 'A', '1', 'AVAILABLE', 0),
('01937b5c-a555-7000-8000-555555555552', '01937b5c-a444-7000-8000-444444444444', '01937b5c-a333-7000-8000-333333333333', 'A', '2', 'AVAILABLE', 0),
('01937b5c-a555-7000-8000-555555555553', '01937b5c-a444-7000-8000-444444444444', '01937b5c-a333-7000-8000-333333333333', 'A', '3', 'AVAILABLE', 0);

-- 6. Create a User (for the test to login/act as)
INSERT INTO users (id, email, password_hash, role, is_verified) 
VALUES ('01937b5c-a666-7000-8000-666666666666', 'test@example.com', '$2a$10$NotRealHashJustForTest', 'CUSTOMER', true);

-- 7. Ensure System Actor exists for audit/system operations
INSERT INTO users (id, email, password_hash, role, full_name, is_verified)
VALUES (
    '00000000-0000-0000-0000-000000000000',
    'system@ticketledger.internal',
    'SYSTEM_INTERNAL_ONLY',
    'ADMIN',
    'System Reaper',
    true
)
ON CONFLICT (id) DO NOTHING;

-- 8. Dedicated deterministic showtime + seats for reclamation/state-machine tests
INSERT INTO showtimes (id, movie_id, screen_id, start_time, end_time, status)
VALUES (
    '77777777-7777-7777-7777-777777777777',
    '01937b5c-a111-7000-8000-111111111111',
    '01937b5c-a222-7000-8000-222222222222',
    NOW() + INTERVAL '2 day',
    NOW() + INTERVAL '2 day' + INTERVAL '2 hour',
    'ACTIVE'
);

INSERT INTO seats (id, showtime_id, tier_id, seat_row, seat_number, status, version) VALUES
('88888888-8888-8888-8888-888888888881', '77777777-7777-7777-7777-777777777777', '01937b5c-a333-7000-8000-333333333333', 'Z', '1', 'AVAILABLE', 0),
('88888888-8888-8888-8888-888888888882', '77777777-7777-7777-7777-777777777777', '01937b5c-a333-7000-8000-333333333333', 'Z', '2', 'AVAILABLE', 0),
('88888888-8888-8888-8888-888888888883', '77777777-7777-7777-7777-777777777777', '01937b5c-a333-7000-8000-333333333333', 'Z', '3', 'AVAILABLE', 0);

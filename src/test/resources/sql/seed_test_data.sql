-- 1. Create a Movie
INSERT INTO movies (id, title, duration_minutes) 
VALUES ('11111111-1111-1111-1111-111111111111', 'Inception', 148);

-- 2. Create a Screen
INSERT INTO screens (id, name, total_seats) 
VALUES ('22222222-2222-2222-2222-222222222222', 'IMAX Hall 1', 100);

-- 3. Create a Seat Tier (VIP)
INSERT INTO seat_tiers (id, name, price_multiplier) 
VALUES ('33333333-3333-3333-3333-333333333333', 'VIP', 1.5);

-- 4. Create a Showtime (Active, in the future)
INSERT INTO showtimes (id, movie_id, screen_id, start_time, end_time, status) 
VALUES (
    '44444444-4444-4444-4444-444444444444', 
    '11111111-1111-1111-1111-111111111111', 
    '22222222-2222-2222-2222-222222222222', 
    NOW() + INTERVAL '1 day', 
    NOW() + INTERVAL '1 day' + INTERVAL '2 hours', 
    'ACTIVE'
);

-- 5. Create Seats (A1, A2, A3)
INSERT INTO seats (id, showtime_id, tier_id, seat_row, seat_number, status, version) VALUES 
('55555555-5555-5555-5555-555555555551', '44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333333', 'A', '1', 'AVAILABLE', 0),
('55555555-5555-5555-5555-555555555552', '44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333333', 'A', '2', 'AVAILABLE', 0),
('55555555-5555-5555-5555-555555555553', '44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333333', 'A', '3', 'AVAILABLE', 0);

-- 6. Create a User (for the test to login/act as)
INSERT INTO users (id, email, password_hash, role, is_verified) 
VALUES ('66666666-6666-6666-6666-666666666666', 'test@example.com', '$2a$10$NotRealHashJustForTest', 'CUSTOMER', true);
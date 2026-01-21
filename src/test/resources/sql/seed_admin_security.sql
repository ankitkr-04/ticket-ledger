-- 1. Create Movie
INSERT INTO movies (id, title, duration_minutes) VALUES 
('01937b5c-1111-7000-8000-111111111111', 'Test Movie', 120);

-- 2. Create Theaters (A & B)
INSERT INTO theaters (id, name, city) VALUES 
('01937b5c-2222-7000-8000-00000000000a', 'Theater A', 'Mumbai'),
('01937b5c-2222-7000-8000-00000000000b', 'Theater B', 'Delhi');

-- 3. Create Admins (Admin A & Admin B)
-- Password for both is 'password' -> $2y$12$XcXK5xc7yqn4BLDMiB6Z3umRMr3UAwmUKBBOLMeyVdeI.UhmF7eAO
INSERT INTO users (id, email, password_hash, role, is_verified, full_name) VALUES 
('01937b5c-3333-7000-8000-00000000000a', 'adminA@example.com', '$2y$12$XcXK5xc7yqn4BLDMiB6Z3umRMr3UAwmUKBBOLMeyVdeI.UhmF7eAO', 'ADMIN', true, 'Admin A'),
('01937b5c-3333-7000-8000-00000000000b', 'adminB@example.com', '$2y$12$XcXK5xc7yqn4BLDMiB6Z3umRMr3UAwmUKBBOLMeyVdeI.UhmF7eAO', 'ADMIN', true, 'Admin B');

-- 4. Grant Access (Admin A -> Theater A, Admin B -> Theater B)
INSERT INTO admin_theater_access (id, user_id, theater_id) VALUES 
(uuidv7(), '01937b5c-3333-7000-8000-00000000000a', '01937b5c-2222-7000-8000-00000000000a'),
(uuidv7(), '01937b5c-3333-7000-8000-00000000000b', '01937b5c-2222-7000-8000-00000000000b');

-- 5. Create Screens (Screen A in Theater A, Screen B in Theater B)
INSERT INTO screens (id, theater_id, name, total_seats) VALUES 
('01937b5c-4444-7000-8000-00000000000a', '01937b5c-2222-7000-8000-00000000000a', 'Screen A', 100),
('01937b5c-4444-7000-8000-00000000000b', '01937b5c-2222-7000-8000-00000000000b', 'Screen B', 100);

-- 6. Create Seat Tier
INSERT INTO seat_tiers (id, name, price_multiplier) VALUES 
('01937b5c-5555-7000-8000-333333333333', 'Standard', 1.0);

-- 7. Create Showtimes (Showtime A in Theater A, Showtime B in Theater B)
INSERT INTO showtimes (id, movie_id, screen_id, start_time, end_time, status) VALUES 
('01937b5c-6666-7000-8000-00000000000a', '01937b5c-1111-7000-8000-111111111111', '01937b5c-4444-7000-8000-00000000000a', NOW() + INTERVAL '1 day', NOW() + INTERVAL '1 day 2 hours', 'ACTIVE'),
('01937b5c-6666-7000-8000-00000000000b', '01937b5c-1111-7000-8000-111111111111', '01937b5c-4444-7000-8000-00000000000b', NOW() + INTERVAL '1 day', NOW() + INTERVAL '1 day 2 hours', 'ACTIVE');

-- 8. Create Customer (for booking)
INSERT INTO users (id, email, password_hash, role, is_verified) VALUES 
('01937b5c-7777-7000-8000-666666666666', 'customer@example.com', '$2a$10$Xk2Q8.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1', 'CUSTOMER', true);

-- 9. Create Booking (Booking B in Theater B) - Target for Attack
INSERT INTO bookings (id, user_id, showtime_id, status) VALUES 
('01937b5c-8888-7000-8000-999999999999', '01937b5c-7777-7000-8000-666666666666', '01937b5c-6666-7000-8000-00000000000b', 'HELD');

-- 10. Create Payment for Booking B (Status PENDING)
INSERT INTO payments (id, booking_id, amount, currency, provider, status) VALUES 
('01937b5c-9999-7000-8000-888888888888', '01937b5c-8888-7000-8000-999999999999', 100.00, 'USD', 'STRIPE', 'PENDING');

-- =========================================
-- V6: Bootstrap OWNER user
-- =========================================

INSERT INTO users (id, email, password_hash, role, full_name, is_verified)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'owner@ticketledger.internal',
    '$2a$10$5fKxcM6T8YJ4B2k/8rQXfOsPgL7H.nqp7s1x5aM5dxD8if9X.T2g.',
    'OWNER',
    'Platform Owner',
    true
)
ON CONFLICT (id) DO NOTHING;
